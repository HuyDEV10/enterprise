package com.huy.enterprise.ai.external;
import com.huy.enterprise.supplier.Supplier;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class TargetedSentimentScopingTest {
    private Supplier supplier(String name) {
        Supplier value=new Supplier(); value.setId(UUID.randomUUID()); value.setName(name); value.setSupplierCode("CODE-"+name); return value;
    }
    private ExternalEventSentiment evidence(Supplier supplier,String target,EntitySentiment label,boolean review,int minute) {
        ExternalEventSentiment value=new ExternalEventSentiment(); value.setSupplier(supplier); value.setTargetEntity(target);
        value.setTargetKey(SentimentTargetKey.normalize(target)); value.setSentiment(label); value.setNeedsReview(review);
        value.setAnalyzedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z").plusMinutes(minute)); return value;
    }
    @Test void companies_in_one_event_keep_opposite_labels() {
        Supplier acme=supplier("Acme"),beta=supplier("Beta");
        var negative=evidence(acme,"Acme",EntitySentiment.NEGATIVE,false,0);
        var positive=evidence(beta,"Beta",EntitySentiment.POSITIVE,false,0);
        var rows=List.of(negative,positive);
        assertThat(ExternalEventImpactService.acceptedSentiment(ExternalEventImpactService.evidenceFor(acme,rows))).isEqualTo(EntitySentiment.NEGATIVE);
        assertThat(ExternalEventImpactService.acceptedSentiment(ExternalEventImpactService.evidenceFor(beta,rows))).isEqualTo(EntitySentiment.POSITIVE);
    }
    @Test void uncertain_or_missing_evidence_is_excluded() {
        Supplier acme=supplier("Acme"),beta=supplier("Beta");
        var uncertain=evidence(acme,"Acme",EntitySentiment.NEGATIVE,true,0);
        assertThat(ExternalEventImpactService.acceptedSentiment(uncertain)).isEqualTo(EntitySentiment.NOT_ANALYZED);
        assertThat(ExternalEventImpactService.evidenceFor(beta,List.of(uncertain))).isNull();
        assertThat(ExternalEventImpactService.acceptedSentiment(null)).isEqualTo(EntitySentiment.NOT_ANALYZED);
    }
    @Test void latest_uncertain_alias_supersedes_old_accepted_result() {
        Supplier acme=supplier("Acme");
        var old=evidence(acme,"Acme Corporation",EntitySentiment.POSITIVE,false,0);
        var latest=evidence(acme,"ACME",EntitySentiment.NEGATIVE,true,1);
        assertThat(ExternalEventImpactService.evidenceFor(acme,List.of(old,latest))).isSameAs(latest);
        assertThat(ExternalEventImpactService.acceptedSentiment(latest)).isEqualTo(EntitySentiment.NOT_ANALYZED);
    }
    @Test void unlinked_entities_cannot_acquire_supplier_scope_from_name_alone() {
        Supplier supplier=supplier("ABC Logistics");
        assertThat(ExternalEventImpactService.evidenceFor(supplier,List.of(evidence(null,"ABC",EntitySentiment.NEGATIVE,false,0)))).isNull();
        var exact=evidence(null,"  ABC Logistics ",EntitySentiment.POSITIVE,false,0);
        assertThat(ExternalEventImpactService.evidenceFor(supplier,List.of(exact))).isNull();
        Supplier sameName=supplier("ABC Logistics");
        assertThat(ExternalEventImpactService.evidenceFor(sameName,List.of(exact))).isNull();
        var linked=evidence(supplier,"  ABC Logistics ",EntitySentiment.POSITIVE,false,0);
        assertThat(ExternalEventImpactService.evidenceFor(supplier,List.of(linked))).isSameAs(linked);
        assertThat(ExternalEventImpactService.evidenceFor(sameName,List.of(linked))).isNull();
    }
}
