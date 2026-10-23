# Pipeline sentiment theo thực thể — tiếng Anh và tiếng Việt

Pipeline dự đoán cảm xúc **đối với một thực thể được nêu rõ trong văn bản**, với ba nhãn `NEGATIVE`, `NEUTRAL`, `POSITIVE`. Một bài có thể tiêu cực với công ty A và tích cực với công ty B; mỗi cặp văn bản/thực thể là một đầu vào riêng. Spring Boot kết hợp kết quả phù hợp với nhà cung cấp và dữ liệu vận hành để tính ảnh hưởng, tạo RiskEvent và Alert.

Kết quả đo chính thức nằm trong `evaluation.json` và `MODEL-CARD.md` của từng run, do chương trình sinh sau khi chọn model bằng validation. Tài liệu này không điền trước kết quả khi huấn luyện chưa hoàn tất.

## 1. Nguồn dữ liệu và giới hạn miền

Nguồn, phiên bản, đường dẫn, SHA-256 và phạm vi nhãn được lưu trong `ai-service/training/sentiment/sources.json`.

| Nguồn gốc | Ngôn ngữ/phạm vi | Phiên bản cố định | Giấy phép được công bố |
| --- | --- | --- | --- |
| [FinEntity](https://github.com/yixuantt/FinEntity), [bài báo EMNLP](https://aclanthology.org/2023.emnlp-main.956/) | Tiếng Anh, sentiment thực thể trong tin tài chính | `3b6cedc5485b669c2ed168f1d949f517636eb7b8` | ODC-BY |
| [SEntFiN](https://github.com/pyRis/SEntFiN) | Tiếng Anh, sentiment từng thực thể trong tiêu đề tài chính | `eba43610fe3cb57a3e5773cd6c037da1f8992ad5` | MIT trong repository nguồn |
| [ViTASA](https://github.com/kh4nh12/ViTASA) | Tiếng Việt, target/aspect điện thoại, khách sạn, nhà hàng | `cda6a4525bfdf7a7632a7d7c7ccdbe00af5b3094` | MIT trong repository nguồn |
| [UTS2017_Bank](https://huggingface.co/datasets/undertheseanlp/UTS2017_Bank) | Tiếng Việt, chỉ aspect `TRADEMARK` khi nêu rõ đúng một ngân hàng | `4d1319fa229720d392c1dc0e323d10b40eeacbd0` | Apache-2.0 theo dataset card của bên công bố |

Model khởi tạo là [DistilBERT multilingual cased](https://huggingface.co/distilbert/distilbert-base-multilingual-cased), revision `45c032ab32cc946ad88a166f7cb282f58c753c2e`, Apache-2.0. Pipeline lưu provenance và checksum pretrained; phục vụ dự đoán không tự tải model từ mạng.

Không đưa dữ liệu QA doanh nghiệp, dữ liệu giả, bản dịch máy, sentiment do model sinh, hoặc GDELT `AvgTone` vào train/validation/test. Fixture kỹ thuật trong test được giữ riêng. Nguồn không rõ provenance/giấy phép hoặc thiếu nhãn thực thể được ghi trong `excludedSources`.

**Giới hạn tiếng Việt:** ViTASA là đánh giá tiêu dùng/dịch vụ; nhãn ngân hàng là đánh giá thương hiệu có neo thực thể. Chúng chưa phải tập tin tức nhiều công ty về gián đoạn chuỗi cung ứng. Điểm đo trên các nguồn này không chứng minh chất lượng trong miền tin nhà cung cấp tiếng Việt. Metadata giữ `vietnameseSupplyChainNewsGoldValidated=false`. Muốn xác nhận cần một tập tin thực tế được người gán nhãn theo từng công ty, đánh giá riêng ngoài miền, với URL, ngày, quyền sử dụng và hướng dẫn gán nhãn. Không đổi cờ chỉ vì model đạt điểm tốt trên ViTASA.

Smoke thực tế đã gặp trường hợp hai công ty trong cùng câu tiếng Việt bị gán cùng nhãn tiêu cực dù công ty thứ hai báo lãi, với confidence cao. Do đó request `domain=supply_chain_news` bằng tiếng Việt bắt buộc `needsReview=true`, `reviewReason=DOMAIN_NOT_VALIDATED` khi cờ gold còn false. Spring Boot luôn gửi domain này cho external-event sentiment. Các xác suất vẫn được trả để thử nghiệm, nhưng không được dùng như bằng chứng sentiment đã chấp nhận để chấm điểm rủi ro. Metric public-corpus giữ nguyên; không lấy ca smoke để train thêm hoặc ghi đè test đã khóa.

## 2. Môi trường Windows

Chạy từ `D:\Java\enterprise-risk-backend\ai-service`, Python 3.12. Giữ môi trường đang có nếu `.venv` đã được tạo.

```powershell
cd D:\Java\enterprise-risk-backend\ai-service
py -3.12 -m venv .venv
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
```

Máy NVIDIA cần wheel CUDA phù hợp với driver. Cấu hình lần chạy cục bộ này: PyTorch 2.8.0/CUDA 12.6 trên RTX 3050 Laptop 4 GB. Lệnh cài wheel chính thức:

```powershell
python -m pip install torch==2.8.0 --index-url https://download.pytorch.org/whl/cu126
python -m pip install -r requirements-sentiment.txt
python -m pip check
python -c "import torch; print(torch.__version__); print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU')"
```

Phần bổ sung pin torch 2.8.0, transformers 4.57.1, tokenizers 0.22.1, safetensors 0.6.2, huggingface-hub 0.36.0, tqdm 4.67.1. Máy không có GPU có thể cài wheel CPU tương ứng từ [PyTorch](https://pytorch.org/get-started/previous-versions/) và dùng cùng CLI; thời gian khác. Kiểm tra CUDA thực tế trước khi bắt đầu.

Giới hạn thread và dùng thư mục tạm thuộc dự án tránh lỗi quyền/tài nguyên trên laptop:

```powershell
New-Item -ItemType Directory -Force .cache\tmp | Out-Null
$env:TMPDIR = (Resolve-Path .cache\tmp).Path
$env:PYTHONUTF8 = '1'
$env:OPENBLAS_NUM_THREADS = '1'
$env:OMP_NUM_THREADS = '4'
$env:MKL_NUM_THREADS = '4'
$env:TOKENIZERS_PARALLELISM = 'false'
```

Raw data, pretrained weights, candidate và checkpoint được Git-ignore. Cần đủ đĩa cho wheel CUDA, môi trường, hai candidate và optimizer checkpoint. Không sửa/xóa checkpoint của job đang chạy. Chỉ nạp artifact đáng tin cậy; optimizer checkpoint và joblib không phải định dạng an toàn để nhận từ nguồn lạ.

## 3. Download và prepare

```powershell
python -m training.sentiment.pipeline download
python -m training.sentiment.pipeline prepare
```

`download` kiểm tra SHA-256, tải file thiếu/sai checksum, tiếp tục file `.part`, giữ kiểm tra TLS, sinh `.cache/pretrained/distilmbert/source-manifest.json`. `download --datasets-only` chỉ tải dữ liệu; `train` vẫn cần pretrained manifest hợp lệ.

`prepare` thực hiện:

1. Kiểm tra hash nguồn, đọc annotation thật, chuẩn hóa ba nhãn.
2. Chuẩn hóa Unicode NFC/khoảng trắng. Target phải xuất hiện thành mention độc lập, tránh ticker khớp vào giữa từ khác.
3. Loại nhãn mâu thuẫn cho cùng văn bản/target, bản ghi trùng, target vắng mặt, ngân hàng mơ hồ. Lý do nằm trong `quarantine.jsonl`; không tự đoán nhãn thay thế.
4. Giữ các target của cùng bài trong một nhóm. Canonical hóa văn bản/số; hợp các bản gần trùng bằng character n-gram 4–5 cosine ≥ 0,96 trên toàn bộ nguồn. Đặc trưng này chỉ dùng dò trùng, không làm input model.
5. Chia xấp xỉ 80/10/10 bằng `StratifiedGroupKFold`, seed 42, cân bằng ngôn ngữ/nhãn. Giữ official bank test ở test; official train chỉ vào train/validation. Kiểm tra nhóm không giao nhau và mỗi ngôn ngữ/split có đủ ba lớp.

Snapshot đã prepare với cấu hình nguồn trên:

| Split | Cặp target/văn bản | Tiếng Anh | Tiếng Việt |
| --- | ---: | ---: | ---: |
| Train | 25.784 | 12.871 | 12.913 |
| Validation | 3.211 | 1.609 | 1.602 |
| Test | 3.186 | 1.608 | 1.578 |

Đây là số annotation sau lọc, không phải số bài độc lập hay điểm chất lượng. Chi tiết nguồn/nhãn/nhóm nằm trong `data-report.json`; nguồn ngân hàng chỉ còn 30 mẫu test, cần đọc support từng lớp. Khi đổi nguồn/quy tắc lọc, lấy số từ báo cáo mới và tạo run mới.

```text
data/processed/sentiment-v2/
  train.jsonl
  validation.jsonl
  test.jsonl
  quarantine.jsonl
  data-report.json
  split-manifest.json
```

## 4. Train đầy đủ và resume

Từ thư mục gốc repo, script Windows chạy nối tiếp các bước và dừng ngay khi có lỗi:

```powershell
.\scripts\Train-EntitySentiment.ps1 -RunDir data/processed/sentiment-runs/my-run -Promote
# Tiếp tục một run bị ngắt, giữ nguyên hyperparameters:
.\scripts\Train-EntitySentiment.ps1 -RunDir data/processed/sentiment-runs/my-run -Resume -SkipDownload -Promote
```

Nếu bỏ `-Promote`, script dừng ở báo cáo đánh giá. Promotion kiểm tra quality gates và hash weights/tokenizer đã đánh giá trước khi đổi active pointer.

Sau download có thể khóa Hugging Face network trong train/serve:

```powershell
$env:HF_HUB_OFFLINE = '1'
$env:TRANSFORMERS_OFFLINE = '1'
$runDir = 'data/processed/sentiment-runs/bilingual-v2-20261023'
python -u -m training.sentiment.pipeline train --run-dir $runDir --epochs 5 --batch-size 8 --max-length 128
```

Mỗi run train lại bốn TF-IDF + Logistic Regression baseline: toàn văn có prefix target và context đánh dấu target, `C=0.5/2`. Toàn văn chỉ là đối chiếu; target-context có thể được chọn nếu validation tốt hơn transformer.

Hai transformer candidate fine-tune trên **toàn bộ train**: seed 42/lr `3e-5`, seed 43/lr `2e-5`. Không lấy một tập nhỏ rồi gọi là train đầy đủ. Mặc định:

- Input pair: tên target + context quanh mention đầu, tối đa 80 từ, tối đa 20 từ trước target; special tokens `<target>`/`</target>`.
- Max length 128, batch 8, gradient accumulation 4, batch hiệu dụng tối đa 32.
- AdamW, weight decay 0,01, warmup 10%, gradient clipping 1,0; class weight tính từ train bằng căn nghịch đảo tần suất.
- CUDA FP16/GradScaler và gradient checkpointing; CPU cũng hỗ trợ.
- Tối đa 5 epoch/candidate, early stopping patience 2 trên validation, giữ weights tốt nhất.
- Điểm chọn `0.45 × EN macro-F1 + 0.45 × VI macro-F1 + 0.10 × mixed-target macro-F1`. Nếu không có bài nhiều target khác nhãn, dùng trung bình EN/VI.

Tokenization phải giữ marker target; không giữ được thì từ chối input. Training/serving dùng cùng contract `entity-pair-markers-v2` từ `app/ml/sentiment_text.py`.

Cuối mỗi epoch lưu atomically checkpoint model/optimizer/scheduler/scaler/RNG và `history.json`. Tiếp tục với đúng cấu hình ban đầu:

```powershell
python -u -m training.sentiment.pipeline train --run-dir $runDir --epochs 5 --batch-size 8 --max-length 128 --resume
```

Resume bắt đầu từ epoch hoàn tất gần nhất; phần epoch chưa checkpoint chạy lại. Candidate hoàn tất được tái sử dụng. CLI từ chối resume khi checksum split, batch, số epoch, max length hoặc preprocessing đổi. `run-config.json` ghi cấu hình, phiên bản thư viện, thiết bị, pretrained provenance và source fingerprint. Giữ nguyên code khi job đang chạy; thay đổi có chủ đích thì tạo run mới.

Khi hai candidate hoàn tất, `selection.json` ghi winner theo validation và `testUsedForSelection=false`; bước train chưa đo holdout test.

## 5. Holdout, calibration và quality gates

```powershell
python -m training.sentiment.pipeline evaluate --run-dir $runDir
```

Chỉ chạy sau khi có `selection.json`. Evaluation kiểm tra checksum split, fit temperature scaling riêng EN/VI trên **validation**, chọn review threshold trên validation, rồi đo model đã chọn trên test. Hai baseline đối chiếu cũng chọn cấu hình bằng validation trước khi đo test.

Review threshold tìm confidence nhỏ nhất trong 0,45–0,95 với validation accepted accuracy và negative precision ≥ 0,80, coverage ≥ 25% và đủ mẫu hỗ trợ. Không đạt policy thì ghi `policyMet=false` và tất cả dự đoán của nhánh đó cần duyệt; confidence cao không bỏ qua điều kiện này. Policy không bảo đảm precision ngoài miền.

Báo cáo gồm accuracy, macro precision/recall/F1, từng lớp, confusion matrix theo ngôn ngữ/nguồn, nhóm bài target khác nhãn, tỉ lệ đúng toàn bộ target/bài, bootstrap CI 95% theo nhóm văn bản (500 lần), NLL/Brier/ECE trước/sau calibration, review thresholds. Đọc support cùng metric; không xem tập rất nhỏ/thiếu lớp là bằng chứng miền ứng dụng.

`evaluation.json` được khóa; gọi evaluate lại đọc kết quả hiện có. Không xóa báo cáo để tối ưu theo test rồi gọi đó là test mới. Dùng `test-errors.csv` thiết kế vòng tiếp theo với tập đánh giá độc lập.

Gate local promotion trong code:

- Không giao nhóm giữa splits; không dùng test để chọn model.
- Xác suất hữu hạn.
- Overall và macro-F1 riêng từng ngôn ngữ không giảm quá 0,02 so với target-context baseline train lại cùng split.
- EN macro-F1 ≥ 0,60; VI macro-F1 ≥ 0,50.

Đây là gate kỹ thuật ban đầu, không thay thế gold benchmark tin chuỗi cung ứng. Gate thất bại thì không promote, giữ model đang phục vụ.

## 6. Promote, serving và rollback

```powershell
python -m training.sentiment.pipeline promote --run-dir $runDir
```

Lệnh yêu cầu `promotable=true`, chép model/tokenizer/metadata/card vào versioned release, tạo SHA-256 manifest, preflight load/dự đoán một mẫu validation thật của mỗi ngôn ngữ, rồi thay `active.json` atomically. Active v2 cũ được sao lưu thành `active.previous.json`; legacy artifact không bị ghi đè.

```text
models/entity_sentiment_model/
  active.json
  active.previous.json             # sau khi thay một active v2
  releases/bilingual-entity-v2-.../
    model.safetensors              # hoặc model.joblib nếu baseline thắng
    config.json / tokenizer files
    metadata.json
    MODEL-CARD.md
```

Runtime kiểm tra contract/path/SHA-256, chỉ đọc cục bộ; transformer dùng safetensors, `trust_remote_code=false`. Serving chạy CPU với giới hạn thread, dành GPU cho train. Cache tự tải lại khi active manifest thay đổi; restart service giúp loại bỏ cache cũ khi vận hành.

```powershell
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
Invoke-RestMethod http://127.0.0.1:8000/health
Invoke-RestMethod http://127.0.0.1:8000/api/v1/models
```

Rollback v2 khi có previous manifest; dừng AI service trước, rồi khởi động lại:

```powershell
$modelDir = Join-Path (Get-Location) 'models/entity_sentiment_model'
$active = Join-Path $modelDir 'active.json'
$previous = Join-Path $modelDir 'active.previous.json'
$rollback = Join-Path $modelDir 'active.rollback.json'
$backup = Join-Path $modelDir ('active.before-rollback.' + (Get-Date -Format 'yyyyMMddHHmmss') + '.json')
Copy-Item -LiteralPath $previous -Destination $rollback
[System.IO.File]::Replace($rollback, $active, $backup)
```

Giữ release mà previous trỏ tới. Với lần v2 đầu chưa có previous, nếu muốn dùng legacy có sẵn, đổi tên `active.json` thành tên lưu trữ khác trong cùng thư mục rồi restart. Khi vắng active v2 service mới dùng legacy; kết quả legacy luôn `needsReview=true`, `LEGACY_UNCALIBRATED`. Active hỏng trả `MODEL_UNAVAILABLE`, không âm thầm đổi artifact. Kiểm tra health/model version và một input thật sau rollback.

## 7. GDELT → target sentiment → supplier exposure → alert

```text
GDELT event/source URL
  → actual headline/article text + explicit target
  → FastAPI sentiment EN/VI + calibrated probabilities + review
  → Spring Boot supplier identity + event severity/location
  → open purchase orders + shipments + dependent products + inventory
  → business exposure score → RiskEvent / Alert
```

**GDELT event export không cung cấp toàn văn bài báo.** Ingestion hiện lấy metadata/source URL giới hạn theo bộ lọc. Người dùng cần nhập tiêu đề/nội dung thật trong panel sentiment; chưa có automatic article-body crawler. Không thay bài thật bằng CAMEO description, actor string hay `AvgTone`. Crawler tương lai cần xử lý quyền nội dung, provenance, thiếu nội dung và bài trùng.

Connector dùng HTTPS với GDELT. Nếu manifest trỏ tới export trả 404, connector thử tối đa bốn batch trước đó, cách nhau 15 phút, chỉ với tên file timestamp và host chính thức. `sourceFile` trong response là đúng URL tải thành công. Những lỗi upstream khác và trường hợp hết batch trả `503 / GDELT_UNAVAILABLE`; không sinh sự kiện thay thế. Timeout kết nối/đọc là 10/30 giây mỗi request.

Nếu JDK trên Windows không tin certificate chain mà hệ điều hành đã tin, có thể đặt riêng cho process chạy backend (không thay đổi kho chứng chỉ hệ thống):

```powershell
$env:JAVA_TOOL_OPTIONS = '-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE'
```

Đây là cách sử dụng Windows ROOT qua [SunMSCAPI của JDK](https://docs.oracle.com/en/java/javase/21/security/oracle-providers.html). Chỉ dùng trên Windows có kho root phù hợp; môi trường khác dùng truststore được quản trị cho môi trường đó.

FastAPI:

```text
POST /api/v1/sentiment/entity    {entity, text, language: auto|en|vi}
POST /api/v1/sentiment/entities  {entities: [...], text, language: auto|en|vi}
```

Cả hai request nhận `domain: general|supply_chain_news` (mặc định `general`). Metric/calibration trong báo cáo đo trên corpus chung. Domain news có cổng review bổ sung cho tiếng Việt như mô tả ở trên; confidence cao không vượt qua cổng này.

Batch có 1–16 target duy nhất, mỗi target phải có trong text. `auto` chỉ dò dấu Việt để chọn nhánh calibration, không phải nhận dạng ngôn ngữ tổng quát. Với tiếng Việt không dấu/văn bản trộn, chọn ngôn ngữ rõ ràng. Response: `sentiment`, `confidence`, ba `probabilities`, `modelVersion`, `language`, `needsReview`, `reviewReason`, `inputTruncated`. Confidence không phải xác suất nhà cung cấp mất khả năng cung ứng. `inputTruncated` báo cửa sổ context hoặc tokenizer cắt văn bản; không coi model đã đọc toàn bài dài.

React đi qua Spring Boot để giữ JWT/RBAC:

```text
POST /api/ai/external-events/{id}/sentiment
     {entity, text, supplierId?: UUID, language: auto|en|vi}
GET  /api/ai/external-events/{id}/sentiments
POST /api/ai/external-events/{id}/impacts/calculate
GET  /api/ai/external-events/{id}/impacts
```

Panel chi tiết External Events cho chọn target/ngôn ngữ/supplier tùy chọn và nhập văn bản. `supplierId` là xác nhận danh tính của người dùng. Nếu để trống chỉ tự khớp duy nhất theo tên/mã supplier chính xác sau chuẩn hóa; không fuzzy-match tên công ty ngắn/mơ hồ.

Migration `V8__targeted_event_sentiment.sql` lưu theo event/target: supplier mapping, nhãn/xác suất, model version, ngôn ngữ, review, timestamp, text/SHA-256, match method. Phân tích lại cùng target cập nhật record. Sentiment trên event chỉ là tóm tắt phân tích gần nhất cho UI cũ.

Impact calculation lấy bằng chứng mới nhất có supplier ID đã liên kết duy nhất; không lấy sentiment chung của event gán tất cả supplier. Bằng chứng mới nhất cần review được coi là `NOT_ANALYZED` ở phần sentiment; không lấy nhãn cũ tự tin hơn để bỏ qua review. Review hiện là cờ cần xử lý, chưa có quy trình tự gán gold/human approval.

Severity/location và exposure từ đơn mua, vận chuyển, sản phẩm phụ thuộc, tồn kho tính riêng. Ví dụ kỹ thuật “A ngừng sản xuất + đơn đang về + tồn kho thấp” dùng test quy tắc, không phải train data hay bằng chứng chất lượng model. Sentiment tiêu cực tự nó không chứng minh ảnh hưởng doanh nghiệp. Calculate quản lý risk/alert theo nguồn để chạy lại không nhân bản cùng cảnh báo.

## 8. Artifact và kiểm chứng

Giữ `run-config.json`, `data-report.json`, candidate config/history, `candidates.json`, `selection.json`, `evaluation.json`, `MODEL-CARD.md`, `test-predictions.jsonl`, `test-errors.csv`, và `promotion.json` sau promote. Split manifest ở prepared folder. Lưu cùng source revision/môi trường Python để tái lập; không commit corpora/weights lớn.

Script Windows lưu snapshot `pip freeze --all` cho mỗi lần chạy hoặc resume. Lần train cục bộ này còn có `environment-during-training.txt` trong run để ghi lại môi trường thực tế.

Khi khởi động FastAPI, service xác minh và nạp active sentiment release trước khi nhận request đầu tiên, tránh dồn thời gian load weights vào timeout gọi từ backend. Artifact không nạp được sẽ được ghi log; các endpoint khác vẫn chạy và sentiment trả `MODEL_UNAVAILABLE`.

```powershell
python -m pytest -q --basetemp .cache\pytest-sentiment
python -m pip check
```

AI test kiểm tra parser, mention boundary, context Việt, chống leakage, calibration, manifest hỏng/path traversal và API. Backend có test scoping, AI protocol và persistence → exposure → risk/alert trên database test riêng; đặt `DB_NAME` tới database test, không dùng database doanh nghiệp đang thử thủ công. Frontend kiểm tra form/payload, quyền, review và build. Kết quả thực tế phải đọc test runner; tài liệu không mặc định mọi test đã pass.

Trước khi mở rộng sử dụng, đối chiếu model version qua API, đọc metric từng ngôn ngữ/nguồn, kiểm tra review policy và thử tin thật có nhiều công ty khác chiều. Cần tập gold tin nhà cung cấp tiếng Việt độc lập trước khi tuyên bố chất lượng trong miền này.
