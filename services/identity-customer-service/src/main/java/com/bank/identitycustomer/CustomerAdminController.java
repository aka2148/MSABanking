package com.bank.identitycustomer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
public class CustomerAdminController {
    private final JdbcTemplate jdbc;
    public CustomerAdminController(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @GetMapping
    public CustomerPage list(@RequestParam(defaultValue="0") int page,
                                           @RequestParam(defaultValue="10") int size,
                                           @RequestParam(required=false) String status,
                                           @RequestParam(required=false) String risk,
                                           @RequestParam(required=false) String search) {
        int p=Math.max(page,0), s=Math.max(1,Math.min(size,100));
        StringBuilder where=new StringBuilder(" WHERE 1=1"); List<Object> args=new ArrayList<>();
        if(present(status)){where.append(" AND c.cus_kyc_status=?");args.add(status.trim());}
        if(present(risk)){where.append(" AND c.cus_risk_category=?");args.add(risk.trim());}
        if(present(search)){where.append(" AND (d.cud_full_name LIKE ? OR c.cus_customer_number LIKE ? OR u.ucr_username LIKE ?)");String q="%"+search.trim()+"%";args.add(q);args.add(q);args.add(q);}
        String from=" FROM customers c JOIN users x ON x.usr_user_id=c.cus_user_id LEFT JOIN user_credentials u ON u.ucr_user_id=x.usr_user_id LEFT JOIN customer_details d ON d.cud_customer_id=c.cus_customer_id";
        Long total=jdbc.queryForObject("SELECT COUNT(DISTINCT c.cus_customer_id)"+from+where,Long.class,args.toArray());
        List<Object> pageArgs=new ArrayList<>(args);pageArgs.add(s);pageArgs.add((long)p*s);
        List<CustomerResponse> content=jdbc.query("SELECT c.cus_customer_id,c.cus_customer_number,u.ucr_username,d.cud_full_name,c.cus_kyc_status,c.cus_risk_category,c.cus_legal_entity_type"+from+where+" ORDER BY c.cus_customer_id LIMIT ? OFFSET ?",(rs,n)->new CustomerResponse(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7)),pageArgs.toArray());
        long count=total==null?0:total;
        long pages=(count+s-1)/s;
        return new CustomerPage(content,count,pages,p,s,pages==0||p==0,pages==0||p>=pages-1);
    }

    @GetMapping("/search")
    public CustomerPage search(@RequestParam String q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) {
        return list(page,size,null,null,q);
    }

    @GetMapping("/{customerId}")
    public CustomerResponse get(@PathVariable long customerId) { return findCustomer(customerId); }

    @GetMapping("/by-user/{userId}")
    public CustomerResponse byUser(@PathVariable long userId) {
        List<Long> ids=jdbc.queryForList("SELECT cus_customer_id FROM customers WHERE cus_user_id=?",Long.class,userId);
        if(ids.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Customer not found for user");
        return findCustomer(ids.get(0));
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional
    public CustomerResponse create(@Valid @RequestBody CreateCustomer request) {
        if(jdbc.queryForList("SELECT usr_user_id FROM users WHERE usr_user_id=?",Long.class,request.userId).isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found");
        if(!jdbc.queryForList("SELECT cus_customer_id FROM customers WHERE cus_user_id=?",Long.class,request.userId).isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"User already has a customer profile");
        String number=present(request.customerNumber)?request.customerNumber.trim():"CUS-"+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase();
        jdbc.update("INSERT INTO customers(cus_user_id,cus_customer_number,cus_legal_entity_type,cus_kyc_status,cus_risk_category) VALUES(?,?,?,?,?)",request.userId,number,request.legalEntityType,present(request.kycStatus)?request.kycStatus:"PENDING",request.riskCategory);
        long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        String name=present(request.fullName)?request.fullName.trim():username(request.userId);
        jdbc.update("INSERT INTO customer_details(cud_customer_id,cud_full_name) VALUES(?,?)",id,name);
        UserSnapshots.writeUserSnapshot(jdbc, request.userId);
        return findCustomer(id);
    }

    @PutMapping("/{customerId}")
    @Transactional public CustomerResponse update(@PathVariable long customerId,@RequestBody UpdateCustomer request) {
        findCustomer(customerId);
        if(request.legalEntityType!=null) jdbc.update("UPDATE customers SET cus_legal_entity_type=? WHERE cus_customer_id=?",request.legalEntityType,customerId);
        if(request.kycStatus!=null) jdbc.update("UPDATE customers SET cus_kyc_status=? WHERE cus_customer_id=?",request.kycStatus,customerId);
        if(request.riskCategory!=null) jdbc.update("UPDATE customers SET cus_risk_category=? WHERE cus_customer_id=?",request.riskCategory,customerId);
        if(request.fullName!=null) {
            List<Long> detailIds=jdbc.queryForList("SELECT cud_detail_id FROM customer_details WHERE cud_customer_id=? ORDER BY cud_detail_id LIMIT 1",Long.class,customerId);
            if(detailIds.isEmpty()) jdbc.update("INSERT INTO customer_details(cud_customer_id,cud_full_name) VALUES(?,?)",customerId,request.fullName);
            else jdbc.update("UPDATE customer_details SET cud_full_name=? WHERE cud_detail_id=?",request.fullName,detailIds.get(0));
        }
        UserSnapshots.writeUserSnapshot(jdbc, jdbc.queryForObject("SELECT cus_user_id FROM customers WHERE cus_customer_id=?", Long.class, customerId));
        return findCustomer(customerId);
    }

    @PatchMapping("/{customerId}/status")
    @Transactional
    public CustomerResponse updateStatus(@PathVariable long customerId,@RequestBody Map<String,String> body) {
        String status=body.get("status"),risk=body.get("riskCategory");
        if(status!=null) jdbc.update("UPDATE customers SET cus_kyc_status=? WHERE cus_customer_id=?",status,customerId);
        if(risk!=null) jdbc.update("UPDATE customers SET cus_risk_category=? WHERE cus_customer_id=?",risk,customerId);
        UserSnapshots.writeUserSnapshot(jdbc, jdbc.queryForObject("SELECT cus_user_id FROM customers WHERE cus_customer_id=?", Long.class, customerId));
        return findCustomer(customerId);
    }

    private CustomerResponse findCustomer(long id) {
        return jdbc.query("SELECT c.cus_customer_id,c.cus_customer_number,u.ucr_username,d.cud_full_name,c.cus_kyc_status,c.cus_risk_category,c.cus_legal_entity_type FROM customers c LEFT JOIN user_credentials u ON u.ucr_user_id=c.cus_user_id LEFT JOIN customer_details d ON d.cud_customer_id=c.cus_customer_id WHERE c.cus_customer_id=? ORDER BY d.cud_detail_id LIMIT 1",rs->{if(!rs.next())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Customer not found");return new CustomerResponse(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7));},id);
    }
    private String username(long userId) { return jdbc.query("SELECT ucr_username FROM user_credentials WHERE ucr_user_id=?",rs->rs.next()?rs.getString(1):"Customer",userId); }
    private boolean present(String value) { return value!=null&&!value.isBlank(); }

    public record CreateCustomer(@NotNull Long userId,String customerNumber,String legalEntityType,String kycStatus,String riskCategory,String fullName) {}
    public record UpdateCustomer(String legalEntityType,String kycStatus,String riskCategory,String fullName) {}
    public record CustomerResponse(Long id,String customerNumber,String username,String fullName,String kycStatus,String riskCategory,String legalEntityType) {}
    public record CustomerPage(List<CustomerResponse> content,long totalElements,long totalPages,int number,int size,boolean first,boolean last) {
        public int getNumberOfElements(){return content.size();}
        public boolean isEmpty(){return content.isEmpty();}
    }
}
