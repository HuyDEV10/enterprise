package com.huy.enterprise.ai.external;

import com.huy.enterprise.ai.client.AiServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class ExternalEventSentimentIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired ExternalEventService events;
    @Autowired ExternalEventImpactService impacts;
    @MockitoBean AiServiceClient ai;

    @Test void targeted_results_survive_latest_event_summary_and_drive_idempotent_alerts() {
        // Isolated technical integration fixtures, never ML training/evaluation data.
        UUID acme=id("INSERT INTO suppliers(supplier_code,name,country) VALUES('SENT-A','Acme Supplier','Vietnam') RETURNING id");
        UUID beta=id("INSERT INTO suppliers(supplier_code,name,country) VALUES('SENT-B','Beta Supplier','Japan') RETURNING id");
        UUID product=id("INSERT INTO products(product_code,name,unit,reference_price) VALUES('SENT-P','Technical Product','pcs',5) RETURNING id");
        UUID warehouse=id("INSERT INTO warehouses(warehouse_code,name) VALUES('SENT-W','Technical Warehouse') RETURNING id");
        jdbc.update("INSERT INTO inventory_items(warehouse_id,product_id,quantity,low_stock_threshold) VALUES(?,?,2,40)",warehouse,product);
        UUID order=id("INSERT INTO purchase_orders(order_code,supplier_id,order_date,expected_delivery_date,status,total_amount) VALUES('SENT-PO',?,CURRENT_DATE,CURRENT_DATE+3,'IN_TRANSIT',500) RETURNING id",acme);
        jdbc.update("INSERT INTO purchase_order_items(purchase_order_id,product_id,quantity,unit_price,line_total) VALUES(?,?,100,5,500)",order,product);
        jdbc.update("INSERT INTO shipments(shipment_code,purchase_order_id,status) VALUES('SENT-SH',?,'SHIPPING')",order);
        UUID event=id("INSERT INTO external_events(external_source,external_event_id,event_date,event_type) VALUES('TECHNICAL_TEST','SENT-EVENT',CURRENT_DATE,'SUPPLIER_NEWS') RETURNING id");
        when(ai.analyzeEntitySentiment(eq("Acme"),anyString(),eq("en"))).thenReturn(result("Acme","NEGATIVE",.93,false));
        when(ai.analyzeEntitySentiment(eq("Beta"),anyString(),eq("en"))).thenReturn(result("Beta","POSITIVE",.96,false));
        String text="Acme stopped production, while Beta expanded capacity.";
        events.analyze(event,new AnalyzeExternalEventRequest("Acme",text,acme,"en"));
        events.analyze(event,new AnalyzeExternalEventRequest("Beta",text,beta,"en"));
        assertThat(events.one(event).sentiment()).isEqualTo(EntitySentiment.POSITIVE);
        assertThat(events.sentiments(event)).hasSize(2);
        var first=impacts.calculate(event);
        var acmeImpact=first.stream().filter(value->value.supplierId().equals(acme)).findFirst().orElseThrow();
        var betaImpact=first.stream().filter(value->value.supplierId().equals(beta)).findFirst().orElseThrow();
        assertThat(acmeImpact.impactType()).isEqualTo(ExternalEventSignal.RISK);
        assertThat(acmeImpact.impactScore()).isEqualByComparingTo("75.00");
        assertThat(betaImpact.impactType()).isEqualTo(ExternalEventSignal.NEUTRAL);
        impacts.calculate(event);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM risk_events WHERE supplier_id=? AND source='EXTERNAL_EVENT'",Integer.class,acme)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alerts a JOIN risk_events r ON r.id=a.risk_event_id WHERE r.supplier_id=?",Integer.class,acme)).isEqualTo(1);
        when(ai.analyzeEntitySentiment(eq("Acme"),anyString(),eq("en"))).thenReturn(result("Acme","NEGATIVE",.55,true));
        events.analyze(event,new AnalyzeExternalEventRequest("Acme",text,acme,"en"));
        assertThat(events.sentiments(event)).hasSize(2);
        var reviewed=impacts.calculate(event).stream().filter(value->value.supplierId().equals(acme)).findFirst().orElseThrow();
        assertThat(reviewed.impactType()).isEqualTo(ExternalEventSignal.NEUTRAL);
        assertThat(reviewed.explanation()).contains("needsReview=true","excluded");
        assertThat(jdbc.queryForObject("SELECT status FROM risk_events WHERE supplier_id=? AND source='EXTERNAL_EVENT'",String.class,acme)).isEqualTo("RESOLVED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM external_event_sentiments WHERE external_event_id=?",Integer.class,event)).isEqualTo(2);
    }

    @Test void ambiguous_supplier_names_remain_unlinked_and_cannot_drive_impacts() {
        UUID first=id("INSERT INTO suppliers(supplier_code,name) VALUES('SENT-DUP-1','Shared Supplier') RETURNING id");
        UUID second=id("INSERT INTO suppliers(supplier_code,name) VALUES('SENT-DUP-2','Shared Supplier') RETURNING id");
        UUID event=id("INSERT INTO external_events(external_source,external_event_id,event_date,event_type) VALUES('TECHNICAL_TEST','SENT-AMBIGUOUS',CURRENT_DATE,'SUPPLIER_NEWS') RETURNING id");
        when(ai.analyzeEntitySentiment(eq("Shared Supplier"),anyString(),eq("en")))
                .thenReturn(result("Shared Supplier","NEGATIVE",.93,false));
        events.analyze(event,new AnalyzeExternalEventRequest("Shared Supplier","Shared Supplier stopped production.",null,"en"));
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM external_event_sentiments WHERE external_event_id=? AND supplier_id IS NULL AND match_method='UNMATCHED'",Integer.class,event)).isEqualTo(1);
        assertThat(impacts.calculate(event)).noneMatch(value->value.supplierId().equals(first)||value.supplierId().equals(second));
    }

    private UUID id(String sql,Object... args) { return jdbc.queryForObject(sql,UUID.class,args); }
    private AiServiceClient.EntitySentimentResponse result(String entity,String label,double confidence,boolean review) {
        Map<String,Double> probabilities=label.equals("NEGATIVE")?Map.of("NEGATIVE",confidence,"NEUTRAL",.04,"POSITIVE",1-confidence-.04)
                :Map.of("NEGATIVE",.01,"NEUTRAL",1-confidence-.01,"POSITIVE",confidence);
        return new AiServiceClient.EntitySentimentResponse(entity,label,confidence,"technical-contract",probabilities,"en",review,review?"LOW_CONFIDENCE":null,false);
    }
}
