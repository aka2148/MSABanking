package com.bank.fdservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@EnableScheduling
@ConditionalOnProperty(name="app.kafka.enabled",havingValue="true")
public class FdEventPublisher {
    private static final Logger log=LoggerFactory.getLogger(FdEventPublisher.class);
    private final JdbcTemplate jdbc;private final KafkaTemplate<String,String> kafka;private final ObjectMapper json;
    public FdEventPublisher(JdbcTemplate jdbc,KafkaTemplate<String,String> kafka,ObjectMapper json){this.jdbc=jdbc;this.kafka=kafka;this.json=json;}
    @Scheduled(fixedDelayString="${app.kafka.outbox-poll-ms:1000}")
    public void publishPending(){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT fae_event_id,fae_uuid,fae_account_id,fae_event_type,fae_payload,fae_aggregate_version FROM fd_account_events WHERE fae_is_published=0 ORDER BY fae_event_id LIMIT 25");
        for(Map<String,Object> row:rows)try{
            Object payload=row.get("fae_payload");String body=payload instanceof String text?text:json.writeValueAsString(payload);
            Map<String,Object> envelope=Map.of("eventId",row.get("fae_uuid"),"eventType",row.get("fae_event_type"),"schemaVersion",1,"aggregateId",row.get("fae_account_id").toString(),"aggregateVersion",row.get("fae_aggregate_version"),"payload",json.readTree(body));
            kafka.send("fd.lifecycle",row.get("fae_account_id").toString(),json.writeValueAsString(envelope)).get(10,TimeUnit.SECONDS);
            jdbc.update("UPDATE fd_account_events SET fae_is_published=1,fae_published_at=UTC_TIMESTAMP(6) WHERE fae_event_id=? AND fae_is_published=0",row.get("fae_event_id"));
        }catch(Exception e){log.warn("FD event publish failed for {}; it will be retried",row.get("fae_uuid"),e);return;}
    }
}
