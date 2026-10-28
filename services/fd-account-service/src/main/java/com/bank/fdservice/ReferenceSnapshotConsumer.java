package com.bank.fdservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Iterator;
import java.math.BigDecimal;

@Component
@ConditionalOnProperty(name="app.kafka.enabled", havingValue="true")
public class ReferenceSnapshotConsumer {
    private static final Logger log = LoggerFactory.getLogger(ReferenceSnapshotConsumer.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ReferenceSnapshotConsumer(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    @KafkaListener(topics="user.snapshot", groupId="fd-account-service")
    @Transactional
    public void userSnapshot(String payload, Acknowledgment ack) {
        try {
            JsonNode root = json.readTree(payload);
            JsonNode user = root.path("user");
            long id = user.path("userId").asLong();
            long version = root.path("sourceVersion").asLong();
            if (id <= 0 || version <= 0) throw new IllegalArgumentException("Missing user identity/version");
            jdbc.update("INSERT INTO fd_user_ref(fur_user_id,fur_user_uuid,fur_customer_number,fur_full_name,fur_user_status,fur_source_version,fur_synced_at,fur_username,fur_kyc_status,fur_risk_category) VALUES(?,?,?,?,?,?,UTC_TIMESTAMP(6),?,?,?) ON DUPLICATE KEY UPDATE fur_user_uuid=IF(VALUES(fur_source_version)>fur_source_version,VALUES(fur_user_uuid),fur_user_uuid),fur_customer_number=IF(VALUES(fur_source_version)>fur_source_version,VALUES(fur_customer_number),fur_customer_number),fur_full_name=IF(VALUES(fur_source_version)>fur_source_version,VALUES(fur_full_name),fur_full_name),fur_user_status=IF(VALUES(fur_source_version)>fur_source_version,VALUES(fur_user_status),fur_user_status),fur_username=IF(VALUES(fur_source_version)>fur_source_version,VALUES(fur_username),fur_username),fur_synced_at=IF(VALUES(fur_source_version)>fur_source_version,UTC_TIMESTAMP(6),fur_synced_at),fur_kyc_status=IF(VALUES(fur_source_version)>fur_source_version,VALUES(fur_kyc_status),fur_kyc_status),fur_risk_category=IF(VALUES(fur_source_version)>fur_source_version,VALUES(fur_risk_category),fur_risk_category),fur_source_version=GREATEST(fur_source_version,VALUES(fur_source_version))", id, user.path("userUuid").asText(), user.path("customerNumber").asText(null), user.path("fullName").asText(null), user.path("status").asText("ACTIVE"), version, user.path("username").asText(null), user.path("kycStatus").asText(null), user.path("riskCategory").asText(null));
            ack.acknowledge();
        } catch (Exception e) { log.error("Invalid user.snapshot event; not acknowledging", e); throw new IllegalArgumentException("Could not apply user snapshot", e); }
    }

    @KafkaListener(topics="product.catalog", groupId="fd-account-service")
    @Transactional
    public void productSnapshot(String payload, Acknowledgment ack) {
        try {
            JsonNode root = json.readTree(payload);
            JsonNode p = root.path("product");
            long id = Long.parseLong(root.path("aggregateId").asText());
            long version = root.path("sourceVersion").asLong();
            if (id <= 0 || version <= 0) throw new IllegalArgumentException("Missing product identity/version");
            Long current=jdbc.query("SELECT fpr_source_version FROM fd_product_ref WHERE fpr_product_id=?",rs->rs.next()?rs.getLong(1):null,id);
            if(current!=null&&current>=version){ack.acknowledge();return;}
            jdbc.update("INSERT INTO fd_product_ref(fpr_product_id,fpr_product_code,fpr_product_name,fpr_status,fpr_source_version,fpr_synced_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE fpr_product_code=VALUES(fpr_product_code),fpr_product_name=VALUES(fpr_product_name),fpr_status=VALUES(fpr_status),fpr_source_version=VALUES(fpr_source_version),fpr_synced_at=UTC_TIMESTAMP(6)", id, p.path("pro_product_code").asText(), p.path("pro_product_name").asText(), p.path("pro_status").asText("ACTIVE"), version);
            jdbc.update("DELETE FROM fd_product_currency_ref WHERE product_id=?",id);
            jdbc.update("DELETE FROM fd_product_rate_ref WHERE product_id=?",id);
            Iterator<JsonNode> currencies = root.path("currencies").elements();
            while (currencies.hasNext()) {
                JsonNode c = currencies.next();
                long currencyId=c.path("cur_currency_id").asLong();
                jdbc.update("INSERT INTO fd_currency_ref(fcr_currency_id,fcr_iso_code,fcr_currency_name,fcr_decimal_places) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE fcr_iso_code=VALUES(fcr_iso_code),fcr_currency_name=VALUES(fcr_currency_name),fcr_decimal_places=VALUES(fcr_decimal_places)", currencyId, c.path("cur_iso_code").asText(), c.path("cur_currency_name").asText(null), c.path("cur_decimal_places").asInt());
                String penalty=c.path("premature_penalty_points").isMissingNode()||c.path("premature_penalty_points").isNull()?"1.0000":c.path("premature_penalty_points").asText();
                jdbc.update("INSERT INTO fd_product_currency_ref(product_id,currency_id,minimum_amount,maximum_amount,is_base_currency,source_version,premature_penalty_points) VALUES(?,?,?,?,?,?,?)",id,currencyId,new BigDecimal(c.path("pcc_minimum_amount").asText()),new BigDecimal(c.path("pcc_maximum_amount").asText()),c.path("pcc_is_base_currency").asBoolean(),version,penalty);
            }
            Iterator<JsonNode> bands=root.path("rateBands").elements();
            while(bands.hasNext()){
                JsonNode band=bands.next();
                Long currencyId=jdbc.query("SELECT fcr_currency_id FROM fd_currency_ref WHERE UPPER(fcr_iso_code)=UPPER(?)",rs->rs.next()?rs.getLong(1):null,band.path("cur_iso_code").asText());
                if(currencyId==null)throw new IllegalArgumentException("Rate band references an unknown currency");
                int minimum=band.has("minimumMonth")?band.path("minimumMonth").asInt():band.path("min_month").asInt();
                int maximum=band.has("maximumMonth")?band.path("maximumMonth").asInt():band.path("max_month").asInt();
                String effFrom = band.has("effectiveFrom") && !band.path("effectiveFrom").isNull() ? band.path("effectiveFrom").asText(null) : null;
                String effTo = band.has("effectiveTo") && !band.path("effectiveTo").isNull() ? band.path("effectiveTo").asText(null) : null;
                java.sql.Date fromDate = (effFrom == null || effFrom.isBlank()) ? null : java.sql.Date.valueOf(effFrom.substring(0, 10));
                java.sql.Date toDate = (effTo == null || effTo.isBlank()) ? null : java.sql.Date.valueOf(effTo.substring(0, 10));
                jdbc.update("INSERT INTO fd_product_rate_ref(product_id,currency_id,minimum_tenure_months,maximum_tenure_months,annual_rate,compounding_basis,payout_frequency,source_version,effective_from,effective_to) VALUES(?,?,?,?,?,?,?,?,?,?)",id,currencyId,minimum,maximum,new BigDecimal(band.path("ro_outcome_value").asText()),band.path("ro_compounding_basis").asText(null),band.path("ro_payout_frequency").asText(null),version,fromDate,toDate);
            }
            ack.acknowledge();
        } catch (Exception e) { log.error("Invalid product.catalog event; not acknowledging", e); throw new IllegalArgumentException("Could not apply product snapshot", e); }
    }
}
