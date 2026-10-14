package com.bank.fd.api.controller;
import com.bank.fd.application.*;
import com.bank.fd.api.dto.SavingsAccountResponse;
import com.bank.fd.repository.SavingsAccountRepository;
import com.bank.common.exception.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

/** Explicit demo cash only. This endpoint is absent unless local-demo is active. */
@RestController @Profile("local-demo") @RequestMapping("/fd/v1/savings-accounts")
@PreAuthorize("hasRole('CUSTOMER')")
public class SavingsDemoFundingController {
    private final SavingsAccountRepository accounts; private final FdActorResolver actors;
    private final FdAccountingService accounting; private final SavingsAccountService savings;
    public SavingsDemoFundingController(SavingsAccountRepository accounts,FdActorResolver actors,
            FdAccountingService accounting,SavingsAccountService savings){
        this.accounts=accounts;this.actors=actors;this.accounting=accounting;this.savings=savings;
    }
    @GetMapping("/demo-options")
    public java.util.Map<String,Boolean> options() { return java.util.Map.of("enabled",true); }
    public record DemoOpeningRequest(BigDecimal amount) { }
    @PostMapping("/{id}/demo-opening") @Transactional
    public SavingsAccountResponse provision(@PathVariable Long id,@RequestBody DemoOpeningRequest request,Authentication auth){
        var holder=actors.resolve(auth.getName());
        var account=accounts.findByIdForUpdate(id).filter(a -> a.getHolderId().equals(holder.getId()))
            .orElseThrow(() -> new ResourceNotFoundException("Savings account not found"));
        if(!"ACTIVE".equals(account.getStatus())) throw new InvalidInputException("Savings account must be active");
        accounting.provisionDemoOpening(account,request.amount());
        return savings.get(auth.getName(),id);
    }
}
