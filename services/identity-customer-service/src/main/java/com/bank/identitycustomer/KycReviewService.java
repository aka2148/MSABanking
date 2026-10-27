package com.bank.identitycustomer;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class KycReviewService {
    private final JdbcTemplate jdbc;
    private final KycService kyc;
    public KycReviewService(JdbcTemplate jdbc,KycService kyc){this.jdbc=jdbc;this.kyc=kyc;}
    private static final String FROM=" FROM customers c JOIN customer_kyc_profile p ON p.customer_id=c.cus_customer_id JOIN user_credentials u ON u.ucr_user_id=c.cus_user_id LEFT JOIN customer_details n ON n.cud_customer_id=c.cus_customer_id JOIN customer_kyc_events s ON s.customer_id=p.customer_id AND s.cycle_number=p.cycle_number AND s.event_type='KYC_SUBMITTED' LEFT JOIN customer_kyc_events d ON d.customer_id=p.customer_id AND d.cycle_number=p.cycle_number AND d.event_type IN ('STAFF_VERIFIED','STAFF_REJECTED')";
    private static final String STATE="CASE WHEN d.event_type='STAFF_VERIFIED' THEN 'APPROVED' WHEN d.event_type='STAFF_REJECTED' THEN 'REJECTED' WHEN c.cus_kyc_status='PENDING' THEN 'AWAITING_REVIEW' ELSE 'UNAVAILABLE' END";
    @Transactional(readOnly=true)
    public Page queue(String status,String search,int page,int size){
        if(!Set.of("AWAITING_REVIEW","APPROVED","REJECTED","ALL").contains(status))throw bad("Unknown KYC queue status.");
        int number=Math.max(0,page),limit=Math.max(1,Math.min(100,size));
        String where=" WHERE "+STATE+"<> 'UNAVAILABLE'";
        List<Object> args=new ArrayList<>();
        if(!"ALL".equals(status)){where+=" AND "+STATE+"=?";args.add(status);}
        if(search!=null&&!search.isBlank()){
            where+=" AND (n.cud_full_name LIKE ? OR c.cus_customer_number LIKE ? OR u.ucr_username LIKE ?)";
            String q="%"+search.trim()+"%";args.add(q);args.add(q);args.add(q);
        }
        long count=jdbc.queryForObject("SELECT COUNT(*)"+FROM+where,Long.class,args.toArray());
        args.add(limit);args.add((long)number*limit);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT c.cus_customer_id AS customerId,c.cus_customer_number AS customerNumber,n.cud_full_name AS fullName,s.created_at AS submittedAt,p.cycle_number AS cycleNumber,"+STATE+" AS state,p.proof_of_id_type AS proofType,CONCAT(CASE WHEN p.proof_of_id_type='AADHAAR' THEN 'XXXX XXXX ' ELSE '******' END,RIGHT(p.proof_of_id_value,4)) AS proofNumberMasked,(p.document_bytes IS NOT NULL) AS hasDocument,d.reviewer_username AS decidedBy,d.created_at AS decidedAt,d.remarks AS reason"+FROM+where+" ORDER BY CASE WHEN "+STATE+"='AWAITING_REVIEW' THEN 0 ELSE 1 END,s.created_at,c.cus_customer_id LIMIT ? OFFSET ?",args.toArray());
        return new Page(rows,count,number,limit);
    }
    @Transactional
    public Map<String,Object> detail(long id,String staff){
        Map<String,Object> p=profile(id);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("customerId",id);result.put("cycleNumber",p.get("cycle_number"));
        Map<String,Object> account=jdbc.queryForMap("SELECT c.cus_customer_number AS customerNumber,u.ucr_username AS username,n.cud_full_name AS fullName,(SELECT cem_email_address FROM customer_emails WHERE cem_customer_id=c.cus_customer_id AND cem_is_primary=1 LIMIT 1) AS email,(SELECT cph_phone_number FROM customer_phone_numbers WHERE cph_customer_id=c.cus_customer_id AND cph_is_primary=1 LIMIT 1) AS mobile FROM customers c JOIN user_credentials u ON u.ucr_user_id=c.cus_user_id LEFT JOIN customer_details n ON n.cud_customer_id=c.cus_customer_id WHERE c.cus_customer_id=? LIMIT 1",id);
        account.put("email",maskEmail((String)account.get("email")));account.put("mobile",maskTail((String)account.get("mobile")));
        result.put("account",account);
        Map<String,Object> personal=new LinkedHashMap<>();
        for(String field:List.of("date_of_birth","gender","relationship","father_or_spouse_name","occupation","country_of_origin"))personal.put(field,p.get(field));
        result.put("personal",personal);
        Map<String,Object> proof=new LinkedHashMap<>();
        proof.put("type",p.get("proof_of_id_type"));proof.put("numberMasked",maskId((String)p.get("proof_of_id_type"),(String)p.get("proof_of_id_value")));proof.put("issueDate",p.get("id_issue_date"));
        result.put("proof",proof);
        result.put("document",p.get("document_name")==null?null:Map.of("name",p.get("document_name"),"size",p.get("document_size"),"contentType",p.get("document_content_type")));
        List<Map<String,Object>> history=jdbc.queryForList("SELECT event_type AS eventType,remarks,reviewer_username AS reviewer,created_at AS createdAt FROM customer_kyc_events WHERE customer_id=? AND cycle_number=? ORDER BY id",id,p.get("cycle_number"));
        result.put("history",history);
        String state=jdbc.query("SELECT "+STATE+FROM+" WHERE c.cus_customer_id=?",rs->rs.next()?rs.getString(1):"UNAVAILABLE",id);
        result.put("state",state);
        // Free text (including filenames) must not accidentally disclose the stored full ID.
        redact(result,(String)p.get("proof_of_id_value"),Objects.toString(proof.get("numberMasked"),"[redacted]"));
        access(id,p,staff,"VIEW_DETAIL");return result;
    }
    @Transactional
    public Document document(long id,String staff){
        Map<String,Object> p=profile(id);
        byte[] bytes=jdbc.query("SELECT document_bytes FROM customer_kyc_profile WHERE customer_id=?",rs->rs.next()?rs.getBytes(1):null,id);
        if(bytes==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No supporting document is available.");
        access(id,p,staff,"VIEW_DOCUMENT");return new Document(bytes,(String)p.get("document_content_type"));
    }
    @Transactional
    public void decide(long id,String staff,String decision,String reason){
        if(!Set.of("APPROVE","REJECT").contains(Objects.toString(decision,"")))throw bad("Choose APPROVE or REJECT.");
        String note=reason==null?null:reason.trim();
        boolean control = reason != null && reason.codePoints().anyMatch(Character::isISOControl);
        if(("REJECT".equals(decision)||note!=null&&!note.isBlank())&&(!KycValidation.safeText(note,10,500)||control))throw bad("Enter a decision reason of 10-500 characters without markup or control characters.");
        kyc.reviewByStaff(id,"APPROVE".equals(decision)?"VERIFIED":"REJECTED",staff,note);
        access(id,profile(id),staff,"DECISION");
    }
    private Map<String,Object> profile(long id){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT customer_id,cycle_number,date_of_birth,gender,relationship,father_or_spouse_name,occupation,country_of_origin,proof_of_id_type,proof_of_id_value,id_issue_date,document_name,document_size,document_content_type FROM customer_kyc_profile WHERE customer_id=?",id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"KYC application not found.");return rows.get(0);
    }
    private void access(long id,Map<String,Object> p,String staff,String action){jdbc.update("INSERT INTO customer_kyc_access_log(customer_id,cycle_number,staff_username,action) VALUES(?,?,?,?)",id,p.get("cycle_number"),staff,action);}
    static String maskId(String type,String value){if(value==null)return null;return ("AADHAAR".equals(type)?"XXXX XXXX ":"******")+value.substring(Math.max(0,value.length()-4));}
    static String maskTail(String value){return value==null?null:"******"+value.substring(Math.max(0,value.length()-4));}
    static String maskEmail(String value){if(value==null)return null;int at=value.indexOf('@');return at<0?"******":value.substring(0,Math.min(2,Math.max(0,at-1)))+"*****"+value.substring(at);}
    @SuppressWarnings("unchecked")
    private static void redact(Object node,String full,String masked){
        if(full==null||full.isEmpty())return;
        if(node instanceof Map<?,?> map){Map<String,Object> values=(Map<String,Object>)map;values.replaceAll((k,v)->{if(v instanceof String s)return s.replace(full,masked);redact(v,full,masked);return v;});}
        else if(node instanceof List<?> list)list.forEach(v->redact(v,full,masked));
    }
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    public record Page(List<Map<String,Object>> content,long totalElements,int number,int size){}
    public record Document(byte[] bytes,String contentType){}
}
