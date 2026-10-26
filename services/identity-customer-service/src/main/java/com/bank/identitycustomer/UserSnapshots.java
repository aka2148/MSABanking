package com.bank.identitycustomer;

import java.util.Map;
import java.util.UUID;

final class UserSnapshots {
    static void writeUserSnapshot(org.springframework.jdbc.core.JdbcTemplate jdbc, long userId) {
        jdbc.update("UPDATE users SET usr_event_version=usr_event_version+1 WHERE usr_user_id=?", userId);
        publishCurrentUserSnapshot(jdbc, userId);
    }

    static void publishInitialUserSnapshot(org.springframework.jdbc.core.JdbcTemplate jdbc, long userId) {
        jdbc.update("UPDATE users SET usr_event_version=1 WHERE usr_user_id=? AND usr_event_version=0", userId);
        publishCurrentUserSnapshot(jdbc, userId);
    }

    static void publishCurrentUserSnapshot(org.springframework.jdbc.core.JdbcTemplate jdbc, long userId) {
        Map<String,Object> row=jdbc.queryForMap("SELECT u.usr_user_uuid,u.usr_status,u.usr_event_version,c.cus_customer_number,d.cud_full_name,cred.ucr_username,c.cus_kyc_status,c.cus_risk_category FROM users u JOIN customers c ON c.cus_user_id=u.usr_user_id LEFT JOIN customer_details d ON d.cud_customer_id=c.cus_customer_id JOIN user_credentials cred ON cred.ucr_user_id=u.usr_user_id WHERE u.usr_user_id=?", userId);
        try {
            Map<String,Object> user=new java.util.LinkedHashMap<>();
            user.put("userId",userId); user.put("userUuid",row.get("usr_user_uuid")); user.put("username",row.get("ucr_username"));
            user.put("customerNumber",row.get("cus_customer_number")); user.put("fullName",row.get("cud_full_name")); user.put("status",row.get("usr_status"));
            user.put("kycStatus",row.get("cus_kyc_status"));
            user.put("riskCategory",row.get("cus_risk_category"));
            Map<String,Object> event=Map.of("eventId",UUID.randomUUID().toString(),"eventType","USER_SNAPSHOT","schemaVersion",1,"occurredAt",java.time.Instant.now().toString(),"aggregateId",row.get("usr_user_uuid"),"sourceVersion",row.get("usr_event_version"),"user",user);
            String payload=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(event);
            jdbc.update("INSERT INTO outbox_event(event_key,event_type,topic,source_version,payload) VALUES(?,?,?,?,CAST(? AS JSON))",row.get("usr_user_uuid"),"USER_SNAPSHOT","user.snapshot",row.get("usr_event_version"),payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Unable to serialize user snapshot",e); }
    }

}
