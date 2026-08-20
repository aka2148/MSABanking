package com.bank.fd.service;

import com.bank.fd.repository.FdAccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountNumberGenerator {

    @Autowired
    private FdAccountRepository fdAccountRepository;

    @Transactional
    public synchronized String generateNextAccountNumber(String branchCode) {
        if (branchCode == null || branchCode.trim().isEmpty()) {
            branchCode = "000"; // default branch code
        }
        String prefix = "FD-BR" + branchCode.trim() + "-";
        String maxAccountNumber = fdAccountRepository.findMaxAccountNumberByPrefix(prefix);

        int nextSeq = 1;
        if (maxAccountNumber != null && maxAccountNumber.length() > prefix.length()) {
            try {
                String seqStr = maxAccountNumber.substring(prefix.length());
                nextSeq = Integer.parseInt(seqStr) + 1;
            } catch (NumberFormatException e) {
                // fallback to 1 if there's any parsing issue
            }
        }

        return prefix + String.format("%08d", nextSeq);
    }
}
