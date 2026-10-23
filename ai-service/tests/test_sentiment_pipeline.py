"""Technical fixtures only; never training/evaluation data."""
import json
import numpy as np
import pandas as pd
import pytest
from fastapi.testclient import TestClient
from app.main import app
from app.ml.sentiment_runtime import SentimentRuntime, route_language
from app.ml.sentiment_text import SentimentInputError, infer_language, prepare_target_context
from app.services.entity_sentiment_service import EntitySentimentService, EntitySentimentUnavailableError
from training.sentiment.data import canonical_content, parse_sentfin_decisions, split_frame
from training.sentiment.evaluation import calibrated_probabilities, fit_temperatures, review_thresholds


def test_upstream_apostrophe_preserves_labels_without_eval():
    assert parse_sentfin_decisions("{'Sebi': 'neutral', 'Osian's Art Fund': 'negative'}") == {"Sebi":"neutral", "Osian's Art Fund":"negative"}
    with pytest.raises(ValueError):
        parse_sentfin_decisions("__import__('os').system('do-not-run')")


def test_target_does_not_match_inside_an_unrelated_word():
    with pytest.raises(SentimentInputError, match="explicitly mentioned"):
        prepare_target_context("CAT", "Education improved this year.")
    assert "<target> CAT </target>" in prepare_target_context("CAT", "CAT improved; another firm declined.").context


def test_vietnamese_target_and_centered_context():
    text = "Mở đầu. "*100 + "Công ty An Việt ngừng hoạt động, còn công ty Bình Minh tăng sản xuất."
    context = prepare_target_context("An Việt", text)
    assert "<target> An Việt </target>" in context.context
    assert "ngừng hoạt động" in context.context
    assert context.truncated and infer_language(text) == "vi"


def test_serving_language_handles_basic_tones_case_and_decomposed_unicode():
    import unicodedata
    assert route_language("ACME CÓ LÃI.") == "vi"
    assert route_language(unicodedata.normalize("NFD","Acme có lãi.")) == "vi"
    assert route_language("Acme reported a profit.") == "en"


def test_numeric_headline_variants_share_a_document():
    assert canonical_content("Acme profit rose 20.5%!") == canonical_content("ACME profit rose 12%")


def test_group_split_keeps_entities_and_official_test_together():
    rows = [{"group_id":f"group-{group}","language":"en" if group%2 == 0 else "vi","label":label,
             "official_split":"test" if group == 0 else ""}
            for group in range(240) for label in ("NEGATIVE","NEUTRAL","POSITIVE")]
    parts = split_frame(pd.DataFrame(rows))
    groups = {name:set(frame.group_id) for name,frame in parts.items()}
    assert not groups["train"] & groups["test"]
    assert not groups["validation"] & groups["test"]
    assert not groups["train"] & groups["validation"]
    assert "group-0" in groups["test"]
    assert sum(len(frame) for frame in parts.values()) == len(rows)


def test_calibration_returns_valid_language_distributions():
    frame = pd.DataFrame({"language":["en","vi"]*60,"label":["NEGATIVE","NEUTRAL","POSITIVE"]*40})
    logits = np.eye(3)[[0,1,2]*40]*3.
    temperatures = fit_temperatures(frame, logits)
    probabilities = calibrated_probabilities(frame, logits, temperatures)
    assert set(temperatures) == {"en","vi"}
    assert np.allclose(probabilities.sum(axis=1),1.) and np.isfinite(probabilities).all()
    assert set(review_thresholds(frame,probabilities)) == {"en","vi"}


def test_release_cannot_escape_model_root(tmp_path):
    manifest = tmp_path/"active.json"
    manifest.write_text(json.dumps({"artifactVersion":2,"inputContract":"entity-pair-markers-v2","release":"../../outside","kind":"transformer","files":{}}))
    with pytest.raises(ValueError,match="leaves model directory"):
        SentimentRuntime(manifest)


def test_corrupt_manifest_returns_model_unavailable(tmp_path):
    (tmp_path/"active.json").write_text('{"artifactVersion":2}')
    service = EntitySentimentService(tmp_path/"legacy.joblib")
    with pytest.raises(EntitySentimentUnavailableError):
        service._load_bundle()


def test_batch_rejects_duplicates_blank_text_and_unsupported_language():
    client = TestClient(app)
    assert client.post("/api/v1/sentiment/entities",json={"entities":["ACME","acme"],"text":"Acme rose."}).status_code == 422
    assert client.post("/api/v1/sentiment/entities",json={"entities":["Acme  Corp","Acme Corp"],"text":"Acme Corp rose."}).status_code == 422
    assert client.post("/api/v1/sentiment/entities",json={"entities":["Acme"],"text":"   "}).status_code == 422
    assert client.post("/api/v1/sentiment/entity",json={"entity":"Acme","text":"Acme rose.","language":"fr"}).status_code == 422


def test_manifest_cannot_omit_weight_checksums(tmp_path):
    manifest = tmp_path/"active.json"
    manifest.write_text(json.dumps({"artifactVersion":2,"inputContract":"entity-pair-markers-v2",
                                   "release":"releases/technical", "kind":"transformer", "files":{}}))
    with pytest.raises(ValueError,match="required artifact files"):
        SentimentRuntime(manifest)


def test_calibration_bin_boundary_is_counted_once():
    from training.sentiment.evaluation import calibration_metrics
    frame = pd.DataFrame({"label":["NEGATIVE","NEGATIVE"]})
    result = calibration_metrics(frame,np.asarray([[.5,.25,.25],[1.,0.,0.]]))
    assert result["ece10"] == pytest.approx(.25)


def test_unmet_validation_policy_requires_review_even_at_high_confidence():
    from types import SimpleNamespace
    class TechnicalClassifier:
        classes_ = np.asarray(["NEGATIVE","NEUTRAL","POSITIVE"])
        def predict_proba(self, values):
            return np.asarray([[.998,.001,.001]])
    runtime = SentimentRuntime.__new__(SentimentRuntime)
    runtime.kind = "sklearn"
    runtime.pipeline = TechnicalClassifier()
    runtime.metadata = {"modelVersion":"technical", "calibration":{
        "temperatures":{"en":1.}, "reviewThresholds":{"en":{"confidence":.95,"policyMet":False}}}}
    result = runtime.predict([SimpleNamespace(entity="Acme",text="Acme stopped production.",language="en")])[0]
    assert result["confidence"] > .95 and result["needsReview"]
    assert result["reviewReason"] == "VALIDATION_POLICY_UNMET"


@pytest.mark.parametrize("domain,expected_review",[("general",False),("supply_chain_news",True)])
def test_vietnamese_news_domain_requires_review_independently_of_confidence(domain,expected_review):
    from types import SimpleNamespace
    class TechnicalClassifier:
        classes_ = np.asarray(["NEGATIVE","NEUTRAL","POSITIVE"])
        def predict_proba(self, values): return np.asarray([[.998,.001,.001]])
    runtime = SentimentRuntime.__new__(SentimentRuntime)
    runtime.kind = "sklearn"
    runtime.pipeline = TechnicalClassifier()
    runtime.metadata = {"modelVersion":"technical", "vietnameseSupplyChainNewsGoldValidated":False,
        "calibration":{"temperatures":{"vi":1.},"reviewThresholds":{"vi":{"confidence":.45,"policyMet":True}}}}
    result = runtime.predict([SimpleNamespace(entity="An Việt",text="An Việt ngừng sản xuất.",language="vi",domain=domain)])[0]
    assert result["confidence"] > .95
    assert result["needsReview"] is expected_review
    assert result["reviewReason"] == ("DOMAIN_NOT_VALIDATED" if expected_review else None)


def test_failed_sentiment_warmup_does_not_stop_other_service_endpoints(tmp_path,monkeypatch):
    from app.services.entity_sentiment_service import entity_sentiment_service
    pointer = tmp_path/"active.json"
    pointer.write_text("invalid technical fixture")
    monkeypatch.setattr(entity_sentiment_service,"manifest_path",pointer)
    def unavailable():
        raise EntitySentimentUnavailableError("Technical fixture unavailable")
    monkeypatch.setattr(entity_sentiment_service,"_load_bundle",unavailable)
    with TestClient(app) as client:
        assert client.get("/health").status_code == 200
        assert client.post("/api/v1/sentiment/entity",json={"entity":"Acme","text":"Acme rose."}).status_code == 503


def test_promotion_refuses_changed_evaluated_weights_before_touching_active_model(tmp_path):
    from training.sentiment.lifecycle import candidate_artifact_hashes, promote_run
    run = tmp_path/"run"
    candidate = run/"selected"
    candidate.mkdir(parents=True)
    weights = candidate/"model.joblib"
    weights.write_bytes(b"technical evaluation fixture; never a real model")
    report = {"promotable": True, "qualityGates": {"technical": True},
              "selected": {"candidate": "selected", "kind": "sklearn"},
              "selectedArtifactSha256": candidate_artifact_hashes(candidate, "sklearn")}
    (run/"evaluation.json").write_text(json.dumps(report), encoding="utf-8")
    active = tmp_path/"models/entity_sentiment_model/active.json"
    active.parent.mkdir(parents=True)
    active.write_text("previous active pointer", encoding="utf-8")
    weights.write_bytes(b"different, unevaluated technical fixture")
    with pytest.raises(ValueError, match="changed since holdout evaluation"):
        promote_run(tmp_path, run)
    assert active.read_text(encoding="utf-8") == "previous active pointer"
    assert not (active.parent/"releases").exists()


def test_tokenizer_overrides_are_part_of_evaluated_artifact_fingerprint(tmp_path):
    from training.sentiment.lifecycle import assert_evaluated_artifacts, candidate_artifact_hashes
    candidate = tmp_path/"selected"
    candidate.mkdir()
    for name in ("config.json", "model.safetensors", "tokenizer.json", "tokenizer_config.json"):
        (candidate/name).write_bytes(b"technical fixture")
    report = {"selected": {"candidate": "selected", "kind": "transformer"},
              "selectedArtifactSha256": candidate_artifact_hashes(candidate, "transformer")}
    assert_evaluated_artifacts(tmp_path, report)
    (candidate/"added_tokens.json").write_text("{}", encoding="utf-8")
    with pytest.raises(ValueError, match="changed since holdout evaluation"):
        assert_evaluated_artifacts(tmp_path, report)
