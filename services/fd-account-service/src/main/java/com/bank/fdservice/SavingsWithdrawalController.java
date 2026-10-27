package com.bank.fdservice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@RestController @RequestMapping("/api/v1/savings-accounts")
public class SavingsWithdrawalController {
 private final JdbcTemplateHolder holder; private final SavingsWithdrawalService service; private final MpinProofVerifier mpin;
 public SavingsWithdrawalController(org.springframework.jdbc.core.JdbcTemplate jdbc,SavingsWithdrawalService service,MpinProofVerifier mpin){this.holder=new JdbcTemplateHolder(jdbc);this.service=service;this.mpin=mpin;}
 @PostMapping("/withdrawals") @PreAuthorize("hasRole('CUSTOMER')")
 public SavingsWithdrawalService.WithdrawalResponse withdraw(Authentication actor,@RequestHeader("Idempotency-Key") @Size(max=255) String key,@RequestHeader(value="X-MPIN-Proof",required=false) String proof,@Valid @RequestBody WithdrawalRequest request){
  if(key==null||key.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Idempotency-Key is required"); long user=userId(actor);
  Integer owned=holder.jdbc.query("SELECT 1 FROM savings_accounts WHERE sa_account_id=? AND sa_holder_id=?",rs->rs.next()?1:null,request.fromAccountId(),user);
  if(owned==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Savings account not found"); CustomerProductAccess.requireVerified(user); validateRemark(request.remark());
  String hash=hash(request); SavingsWithdrawalService.WithdrawalResponse replay=service.replayIfPresent(user,key,hash,request.fromAccountId()); if(replay!=null)return replay;
  String jti=mpin.verify(actor,proof,"WITHDRAWAL",user,MpinProofVerifier.canonical("WITHDRAWAL",request.fromAccountId(),request.amount()));
  return service.withdraw(user,actor.getName(),key,hash,request,jti);
 }
 private long userId(Authentication a){Object d=a.getDetails() instanceof Map<?,?> m?m.get("userId"):null;if(!(d instanceof Number n))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Token does not contain a valid customer identity");return n.longValue();}
 static String hash(WithdrawalRequest r){String s=r.fromAccountId()+"|"+(r.amount()==null?"":r.amount().stripTrailingZeros().toPlainString())+"|"+(r.remark()==null?"":r.remark().trim());try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private static void validateRemark(String s){if(s!=null&&(s.length()>140||s.matches("(?s).*[<>\\p{Cntrl}].*")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Remark must be at most 140 characters and contain no markup or control characters");}
 public record WithdrawalRequest(@NotNull @Positive Long fromAccountId,@NotNull BigDecimal amount,@Size(max=140) String remark){}
 private record JdbcTemplateHolder(org.springframework.jdbc.core.JdbcTemplate jdbc){}
}
