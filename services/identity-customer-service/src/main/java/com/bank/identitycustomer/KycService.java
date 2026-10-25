package com.bank.identitycustomer;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.*;

@Service
public class KycService {
    private static final List<String> COLUMNS = List.of("date_of_birth", "gender", "relationship", "father_or_spouse_name",
            "occupation", "country_of_origin", "proof_of_id_type", "proof_of_id_value", "id_issue_date");
    private final JdbcTemplate jdbc;
    public KycService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // Every operation locks the same customer first, including lazy creation and staff status races.
    private Map<String, Object> customer(String username) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT c.* FROM customers c JOIN user_credentials u ON u.ucr_user_id=c.cus_user_id WHERE u.ucr_username=? FOR UPDATE", username);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        return rows.get(0);
    }
    private long id(Map<String, Object> c) { return ((Number)c.get("cus_customer_id")).longValue(); }
    private Map<String, Object> profile(Map<String, Object> c) {
        long id = id(c);
        jdbc.update("INSERT IGNORE INTO customer_kyc_profile(customer_id) VALUES(?)", id);
        Map<String, Object> p = jdbc.queryForMap("SELECT " + String.join(",", COLUMNS)
                + ",customer_id,cycle_number,cycle_status,confirmed_fields,proof_number_masked,document_name,document_size,document_content_type FROM customer_kyc_profile WHERE customer_id=?", id);
        if ("PENDING".equals(c.get("cus_kyc_status")) && "COMPLETED".equals(p.get("cycle_status"))) {
            openCycle(id);
            p.put("cycle_number", ((Number)p.get("cycle_number")).intValue() + 1);
            p.put("cycle_status", "OPEN"); p.put("confirmed_fields", "");
        }
        return p;
    }
    private void openCycle(long id) {
        jdbc.update("UPDATE customer_kyc_profile SET cycle_number=cycle_number+1,cycle_status='OPEN',confirmed_fields='',updated_at=UTC_TIMESTAMP(6) WHERE customer_id=?", id);
    }
    private void editable(Map<String, Object> c, Map<String, Object> p) {
        if ("REJECTED".equals(c.get("cus_kyc_status"))) throw conflict("Please consult your bank officer about your rejected KYC.");
        if (!"PENDING".equals(c.get("cus_kyc_status")) || !"OPEN".equals(p.get("cycle_status")))
            throw conflict("Request re-KYC to update details.");
    }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }

    @Transactional
    public Map<String, Object> get(String username) {
        Map<String, Object> c = customer(username);
        return response(c, profile(c), username, Map.of());
    }

    @Transactional
    public Map<String, Object> save(String username, Map<String, Object> body) {
        Map<String, Object> c = customer(username), p = profile(c);
        editable(c, p);
        Map<String, String> values = values(p), errors = new LinkedHashMap<>();
        Set<String> confirmed = confirmed(p);
        for (String field : KycValidation.FIELDS) {
            if (!body.containsKey(field)) continue;
            Object raw = body.get(field);
            if (raw != null && !(raw instanceof String)) { errors.put(field, "Enter a valid value."); continue; }
            if (raw instanceof String text && text.codePoints().anyMatch(Character::isISOControl)) { errors.put(field, "Enter a valid value."); continue; }
            String value = raw == null || ((String)raw).isBlank() ? null : ((String)raw).trim();
            String error = KycValidation.error(field, value, values);
            if (error != null) { errors.put(field, error); continue; }
            values.put(field, value);
            if (value == null) confirmed.remove(field); else confirmed.add(field);
        }
        // A masked value is never accepted as an ID. This explicit flag confirms the server-held ID.
        if (Boolean.TRUE.equals(body.get("confirmSavedProof")) && !body.containsKey("proofOfIdValue")
                && KycValidation.validId(values.get("proofOfIdType"), values.get("proofOfIdValue"))) confirmed.add("proofOfIdValue");
        // Cross-field edits can invalidate retained values; never count or retain an invalid ID/date.
        for (String field : List.of("proofOfIdValue", "idIssueDate")) {
            if (values.get(field) != null && KycValidation.error(field, values.get(field), values) != null) {
                values.put(field, null); confirmed.remove(field);
            }
        }
        for (String field : KycValidation.FIELDS) {
            if (values.get(field) == null || KycValidation.error(field, values.get(field), values) != null) confirmed.remove(field);
        }
        String proof = values.get("proofOfIdValue");
        String masked = proof == null ? null : "****" + proof.substring(proof.length() - 4);
        List<Object> args = new ArrayList<>();
        for (String field : KycValidation.FIELDS) args.add(values.get(field));
        args.add(String.join(",", confirmed)); args.add(masked); args.add(id(c));
        jdbc.update("UPDATE customer_kyc_profile SET " + String.join("=?,", COLUMNS)
                + "=?,confirmed_fields=?,proof_number_masked=?,updated_at=UTC_TIMESTAMP(6) WHERE customer_id=?", args.toArray());
        return response(c, profile(c), username, errors);
    }

    @Transactional
    public Map<String, Object> submit(String username) {
        Map<String, Object> c = customer(username), p = profile(c);
        editable(c, p);
        if (confirmed(p).size() != KycValidation.FIELDS.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complete all 9 KYC fields before submitting.");
        Map<String, String> values = values(p);
        jdbc.update("UPDATE customers SET cus_kyc_status='VERIFIED' WHERE cus_customer_id=?", id(c));
        jdbc.update("UPDATE customer_kyc_profile SET cycle_status='COMPLETED' WHERE customer_id=?", id(c));
        jdbc.update("UPDATE customer_details SET cud_date_of_birth=?,cud_father_or_spouse_name=?,cud_occupation=?,cud_updated_date=UTC_TIMESTAMP(6) WHERE cud_customer_id=?",
                values.get("dateOfBirth"), values.get("fatherOrSpouseName"), values.get("occupation"), id(c));
        event(c, p, "AUTO_VERIFIED", null);
        UserSnapshots.writeUserSnapshot(jdbc, ((Number)c.get("cus_user_id")).longValue());
        c.put("cus_kyc_status", "VERIFIED");
        return response(c, profile(c), username, Map.of());
    }

    @Transactional
    public Map<String, Object> rekyc(String username, String rawRemarks) {
        Map<String, Object> c = customer(username);
        if (!"VERIFIED".equals(c.get("cus_kyc_status"))) throw conflict("Re-KYC can only be requested when KYC is verified.");
        String remarks = rawRemarks == null ? null : rawRemarks.trim();
        if (!KycValidation.safeText(remarks, 10, 500) || rawRemarks.codePoints().anyMatch(Character::isISOControl)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Enter remarks of 10–500 characters without markup or control characters.");
        profile(c); openCycle(id(c));
        jdbc.update("UPDATE customers SET cus_kyc_status='PENDING' WHERE cus_customer_id=?", id(c));
        c.put("cus_kyc_status", "PENDING");
        Map<String, Object> p = profile(c);
        event(c, p, "REKYC_REQUESTED", remarks);
        UserSnapshots.writeUserSnapshot(jdbc, ((Number)c.get("cus_user_id")).longValue());
        return response(c, p, username, Map.of());
    }
    private void event(Map<String, Object> c, Map<String, Object> p, String type, String remarks) {
        jdbc.update("INSERT INTO customer_kyc_events(customer_id,cycle_number,event_type,remarks) VALUES(?,?,?,?)", id(c), p.get("cycle_number"), type, remarks);
    }

    @Transactional
    public Map<String, Object> document(String username, MultipartFile file) {
        Map<String, Object> c = customer(username), p = profile(c);
        editable(c, p);
        if (file == null) {
            jdbc.update("UPDATE customer_kyc_profile SET document_name=NULL,document_content_type=NULL,document_size=NULL,document_bytes=NULL,updated_at=UTC_TIMESTAMP(6) WHERE customer_id=?", id(c));
        } else {
            if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a non-empty document up to 5 MB.");
            byte[] bytes;
            try { bytes = file.getBytes(); } catch (IOException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read document."); }
            String type = documentType(bytes);
            if (type == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a PDF, JPEG or PNG document.");
            String name = Optional.ofNullable(file.getOriginalFilename()).orElse("document").replace('\\', '/');
            name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}<>]", "_").trim();
            if (name.isBlank()) name = "document";
            if (name.length() > 255) name = name.substring(name.length() - 255);
            jdbc.update("UPDATE customer_kyc_profile SET document_name=?,document_content_type=?,document_size=?,document_bytes=?,updated_at=UTC_TIMESTAMP(6) WHERE customer_id=?", name, type, bytes.length, bytes, id(c));
        }
        return response(c, profile(c), username, Map.of());
    }
    static String documentType(byte[] bytes) {
        if (starts(bytes, new byte[]{0x25,0x50,0x44,0x46,0x2d})) return "application/pdf";
        if (starts(bytes, new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff})) return "image/jpeg";
        if (starts(bytes, new byte[]{(byte)0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a})) return "image/png";
        return null;
    }
    private static boolean starts(byte[] bytes, byte[] prefix) { return bytes.length >= prefix.length && Arrays.equals(Arrays.copyOf(bytes, prefix.length), prefix); }
    private Map<String, String> values(Map<String, Object> p) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < COLUMNS.size(); i++) values.put(KycValidation.FIELDS.get(i), Objects.toString(p.get(COLUMNS.get(i)), null));
        return values;
    }
    private Set<String> confirmed(Map<String, Object> p) {
        Set<String> fields = new LinkedHashSet<>(Arrays.asList(((String)p.get("confirmed_fields")).split(",")));
        fields.retainAll(KycValidation.FIELDS); return fields;
    }
    private Map<String, Object> response(Map<String, Object> c, Map<String, Object> p, String username, Map<String, String> errors) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, String> saved = values(p);
        Set<String> confirmed = confirmed(p);
        Map<String, Boolean> flags = new LinkedHashMap<>();
        for (String f : KycValidation.FIELDS) flags.put(f, confirmed.contains(f) && saved.get(f) != null && KycValidation.error(f, saved.get(f), saved) == null);
        result.put("kycStatus", c.get("cus_kyc_status")); result.put("cycleNumber", p.get("cycle_number")); result.put("cycleStatus", p.get("cycle_status"));
        result.put("completionPercent", flags.values().stream().filter(Boolean::booleanValue).count() * 100 / 9);
        result.put("completedFields", flags);
        saved.remove("proofOfIdValue"); result.put("values", saved); result.put("proofNumberMasked", p.get("proof_number_masked"));
        result.put("editingAllowed", "PENDING".equals(c.get("cus_kyc_status")) && "OPEN".equals(p.get("cycle_status")));
        result.put("fieldErrors", errors);
        result.put("document", p.get("document_name") == null ? null : Map.of("name", p.get("document_name"), "size", p.get("document_size"), "contentType", p.get("document_content_type")));
        result.put("verifiedAt", jdbc.query("SELECT created_at FROM customer_kyc_events WHERE customer_id=? AND event_type='AUTO_VERIFIED' ORDER BY id DESC LIMIT 1", rs -> rs.next() ? rs.getTimestamp(1).toInstant().toString() : null, id(c)));
        Map<String, Object> account = new LinkedHashMap<>();
        account.put("username", username); account.put("customerNumber", c.get("cus_customer_number"));
        account.put("fullName", jdbc.query("SELECT cud_full_name FROM customer_details WHERE cud_customer_id=? ORDER BY cud_detail_id LIMIT 1", rs -> rs.next() ? rs.getString(1) : null, id(c)));
        account.put("email", jdbc.query("SELECT cem_email_address FROM customer_emails WHERE cem_customer_id=? AND cem_is_primary=1 LIMIT 1", rs -> rs.next() ? rs.getString(1) : null, id(c)));
        account.put("mobile", jdbc.query("SELECT cph_phone_number FROM customer_phone_numbers WHERE cph_customer_id=? AND cph_is_primary=1 LIMIT 1", rs -> rs.next() ? rs.getString(1) : null, id(c)));
        result.put("accountDetails", account);
        return result;
    }
}
