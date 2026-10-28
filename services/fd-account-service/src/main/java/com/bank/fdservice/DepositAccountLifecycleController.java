package com.bank.fdservice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.Map;

@RestController @RequestMapping("/fd/v1/staff/savings-accounts")
@PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
public class DepositAccountLifecycleController {
    private final JdbcTemplate jdbc;
    public DepositAccountLifecycleController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @PostMapping("/{id}/status") @Transactional
    public StatusView change(Authentication actor,@PathVariable long id,@Valid @RequestBody StatusRequest request){
        Map<String,Object> row=jdbc.query("SELECT sa_status,sa_balance FROM savings_accounts WHERE sa_account_id=? FOR UPDATE",r->r.next()?Map.of("status",r.getString(1),"balance",r.getBigDecimal(2)):null,id);
        if(row==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Deposit account not found");
        String from=(String)row.get("status"),to=switch(request.action()){case FREEZE->"INACTIVE";case UNFREEZE->"ACTIVE";case CLOSE->"CLOSED";};
        if(request.action()==Action.FREEZE&&!"ACTIVE".equals(from)||request.action()==Action.UNFREEZE&&!"INACTIVE".equals(from)||request.action()==Action.CLOSE&&!java.util.List.of("ACTIVE","INACTIVE").contains(from))throw new ResponseStatusException(HttpStatus.CONFLICT,"Deposit account cannot make that status transition");
        if(request.action()==Action.CLOSE){
            if(((BigDecimal)row.get("balance")).compareTo(BigDecimal.ZERO)!=0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Account must have a zero balance before it can be closed");
            if(count("SELECT COUNT(*) FROM fd_accounts WHERE fa_funding_savings_account_id=? AND fa_status IN ('PENDING','ACTIVE')",id)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Account is referenced by a pending or active fixed deposit");
            if(count("SELECT COUNT(*) FROM loan_applications WHERE la_destination_savings_id=? AND la_status IN ('PENDING_REVIEW','APPROVED')",id)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Account is referenced by a pending loan application");
            if(count("SELECT COUNT(*) FROM forex_requests WHERE fr_status='REQUESTED' AND (fr_source_savings_account_id=? OR fr_destination_savings_account_id=?)",id,id)>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Account is referenced by a pending forex request");
        }
        jdbc.update("UPDATE savings_accounts SET sa_status=?,sa_row_version=sa_row_version+1 WHERE sa_account_id=?",to,id);
        jdbc.update("INSERT INTO savings_account_status_audit(sasa_account_id,sasa_from_status,sasa_to_status,sasa_reason,sasa_actor_username,sasa_created_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))",id,from,to,request.reason().trim(),actor.getName());
        return new StatusView(id,from,to,request.reason().trim());
    }
    private int count(String sql,Object...args){Integer n=jdbc.queryForObject(sql,Integer.class,args);return n==null?0:n;}
    public enum Action { FREEZE, UNFREEZE, CLOSE }
    public record StatusRequest(@NotNull Action action,@NotBlank @Size(min=5,max=140) String reason){}
    public record StatusView(long accountId,String fromStatus,String status,String reason){}
}
