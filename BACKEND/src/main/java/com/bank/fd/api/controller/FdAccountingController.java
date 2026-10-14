package com.bank.fd.api.controller;
import com.bank.fd.api.dto.JournalResponse;
import com.bank.fd.api.dto.SavingsAccountResponse;
import com.bank.fd.application.*;
import com.bank.fd.repository.*;
import com.bank.common.exception.ResourceNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@RestController @RequestMapping("/fd/v1/accounts") @PreAuthorize("isAuthenticated()")
public class FdAccountingController {
    private final FdAccountRepository accounts; private final FdJournalRepository journals;
    private final FdCurrencyReferenceRepository currencies; private final FdAccessPolicy access;
    private final SavingsAccountRepository savings;
    public FdAccountingController(FdAccountRepository accounts,FdJournalRepository journals,
            FdCurrencyReferenceRepository currencies,FdAccessPolicy access,SavingsAccountRepository savings){
        this.accounts=accounts;this.journals=journals;this.currencies=currencies;this.access=access;this.savings=savings;
    }
    @GetMapping("/{id}/funding-savings") @Transactional(readOnly=true)
    public SavingsAccountResponse fundingSavings(@PathVariable Long id,Authentication auth) {
        var fd=accounts.findById(id).orElseThrow(() -> new ResourceNotFoundException("FD account not found"));
        access.requireOwned(auth.getName(),fd,auth);
        if(fd.getFundingSavingsAccountId()==null) throw new ResourceNotFoundException("Original savings account is not recorded");
        var source=savings.findById(fd.getFundingSavingsAccountId()).orElseThrow(() -> new ResourceNotFoundException("Original savings account not found"));
        String code=currencies.findById(source.getCurrencyId()).orElseThrow(() -> new ResourceNotFoundException("Currency not found")).getIsoCode();
        return SavingsAccountResponse.from(source,code);
    }
    @GetMapping("/{id}/journals") @Transactional(readOnly=true)
    public List<JournalResponse> list(@PathVariable Long id,Authentication auth) {
        var fd=accounts.findById(id).orElseThrow(() -> new ResourceNotFoundException("FD account not found"));
        access.requireOwned(auth.getName(),fd,auth);
        String code=currencies.findById(fd.getCurrencyId()).orElseThrow(() -> new ResourceNotFoundException("Currency not found")).getIsoCode();
        return journals.findByFdAccountIdOrderByPostedAtAscIdAsc(id).stream().map(j -> JournalResponse.from(j,code)).toList();
    }
}
