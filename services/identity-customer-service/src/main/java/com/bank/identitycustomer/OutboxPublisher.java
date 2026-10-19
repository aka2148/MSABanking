package com.bank.identitycustomer;

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
public class OutboxPublisher {
    private static final Logger log=LoggerFactory.getLogger(OutboxPublisher.class);
    private final JdbcTemplate jdbc; private final KafkaTemplate<String,String> kafka; private final ObjectMapper json;
    public OutboxPublisher(JdbcTemplate jdbc,KafkaTemplate<String,String> kafka,ObjectMapper json){this.jdbc=jdbc;this.kafka=kafka;this.json=json;}
    @Scheduled(fixedDelayString="${app.kafka.outbox-poll-ms:1000}")
    public void publishPending(){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,event_uuid,event_key,topic,payload FROM outbox_event WHERE is_published=0 ORDER BY id LIMIT 25");
        for(Map<String,Object> row:rows)try{
            Object value=row.get("payload");String body=value instanceof String text?text:json.writeValueAsString(value);
            kafka.send((String)row.get("topic"),(String)row.get("event_key"),body).get(10,TimeUnit.SECONDS);
            jdbc.update("UPDATE outbox_event SET is_published=1,published_at=UTC_TIMESTAMP(6) WHERE id=? AND is_published=0",row.get("id"));
        }catch(Exception e){log.warn("Outbox publish failed for event {}; it will be retried",row.get("event_uuid"),e);return;}
    }
}
