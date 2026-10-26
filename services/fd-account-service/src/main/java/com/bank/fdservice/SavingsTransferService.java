package com.bank.fdservice;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

@Service
public class SavingsTransferService {
    private final JdbcTemplate jdbc;
    private final PostingService postings;
    private final MpinProofVerifier mpin;

    public SavingsTransferService(JdbcTemplate jdbc, PostingService postings, MpinProofVerifier mpin) {
        this.jdbc = jdbc;
        this.postings = postings;
        this.mpin=mpin;
    }

    public TransferResponse replayIfPresent(long actorId, String key, String hash, long fromAccountId) {
        Map<String, Object> row = jdbc.query("SELECT foi_request_hash,foi_status,foi_response_reference FROM fd_operation_idempotency WHERE foi_operation_scope='SAVINGS_TRANSFER' AND foi_actor_key=? AND foi_idempotency_key=?",
                rs -> rs.next() ? Map.of("hash", rs.getString(1), "status", rs.getString(2), "reference", rs.getString(3) == null ? "" : rs.getString(3)) : null,
                Long.toString(actorId), key);
        if (row == null) return null;
        return replayRow(row, hash, fromAccountId);
    }

    @Transactional
    public TransferResponse transfer(long actorId, String actorUsername, String key, String hash,
                                     SavingsTransferController.TransferRequest request,String mpinJti) {
        CustomerProductAccess.requireVerified(actorId);
        try {
            jdbc.update("INSERT INTO fd_operation_idempotency(foi_operation_scope,foi_actor_key,foi_idempotency_key,foi_request_hash,foi_status) VALUES('SAVINGS_TRANSFER',?,?,?,'PENDING')",
                    Long.toString(actorId), key, hash);
        } catch (DuplicateKeyException e) {
            Map<String, Object> row = jdbc.query("SELECT foi_request_hash,foi_status,foi_response_reference FROM fd_operation_idempotency WHERE foi_operation_scope='SAVINGS_TRANSFER' AND foi_actor_key=? AND foi_idempotency_key=?",
                    rs -> rs.next() ? Map.of("hash", rs.getString(1), "status", rs.getString(2), "reference", rs.getString(3) == null ? "" : rs.getString(3)) : null,
                    Long.toString(actorId), key);
            if (row != null) return replayRow(row, hash, request.fromAccountId());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transfer is already in progress");
        }
        mpin.consume(mpinJti,actorId,"TRANSFER");

        Map<String, Object> source = jdbc.query("SELECT sa_currency_id,sa_status FROM savings_accounts WHERE sa_account_id=? AND sa_holder_id=?",
                rs -> rs.next() ? Map.of("currency", rs.getLong(1), "status", rs.getString(2)) : null,
                request.fromAccountId(), actorId);
        if (source == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Savings account not found");
        if (!"ACTIVE".equals(source.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "Source savings account is not active");
        String targetNumber = request.toAccountNumber().trim();
        Map<String, Object> target = jdbc.query("SELECT sa_account_id,sa_currency_id,sa_status FROM savings_accounts WHERE sa_account_number=?",
                rs -> rs.next() ? Map.of("id", rs.getLong(1), "currency", rs.getLong(2), "status", rs.getString(3)) : null, targetNumber);
        if (target == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Destination savings account not found");
        long targetId = ((Number) target.get("id")).longValue();
        if (targetId == request.fromAccountId()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source and destination savings accounts must differ");
        long currencyId = ((Number) source.get("currency")).longValue();
        if (((Number) target.get("currency")).longValue() != currencyId) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transfer accounts must use the same currency");
        if (!"ACTIVE".equals(target.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "Destination savings account is not active");
        int scale = jdbc.queryForObject("SELECT fcr_decimal_places FROM fd_currency_ref WHERE fcr_currency_id=?", Integer.class, currencyId);
        BigDecimal amount;
        try { amount = request.amount().setScale(scale, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount exceeds the currency decimal scale"); }
        if (amount.signum() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive");

        String reference = "TRF-" + UUID.randomUUID();
        postings.postSavingsTransfer(request.fromAccountId(), targetId, currencyId, amount, reference,
                request.remark() == null || request.remark().isBlank() ? "Savings transfer" : request.remark().trim(), actorUsername);
        jdbc.update("UPDATE fd_operation_idempotency SET foi_status='COMPLETED',foi_aggregate_id=?,foi_response_reference=?,foi_completed_at=UTC_TIMESTAMP(6) WHERE foi_operation_scope='SAVINGS_TRANSFER' AND foi_actor_key=? AND foi_idempotency_key=?",
                request.fromAccountId(), reference, Long.toString(actorId), key);
        return response(reference, request.fromAccountId());
    }

    private TransferResponse replayRow(Map<String, Object> row, String hash, long fromAccountId) {
        if (!hash.equals(row.get("hash"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key was already used for a different transfer");
        if (!"COMPLETED".equals(row.get("status")) || row.get("reference").toString().isBlank())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transfer is already in progress");
        return response(row.get("reference").toString(), fromAccountId);
    }

    private TransferResponse response(String reference, long fromAccountId) {
        Map<String, Object> debit = jdbc.query("SELECT st_amount,st_currency_id,st_balance_after,st_posted_at FROM savings_transactions WHERE st_reference_id=? AND st_account_id=? AND st_entry_type='DEBIT'",
                rs -> rs.next() ? Map.of("amount", rs.getBigDecimal(1), "currency", rs.getLong(2), "balance", rs.getBigDecimal(3), "at", rs.getTimestamp(4).toInstant().toString()) : null, reference, fromAccountId);
        Long toId = jdbc.query("SELECT st_account_id FROM savings_transactions WHERE st_reference_id=? AND st_entry_type='CREDIT'",
                rs -> rs.next() ? rs.getLong(1) : null, reference);
        if (debit == null || toId == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Completed transfer history is unavailable");
        String destination = jdbc.queryForObject("SELECT sa_account_number FROM savings_accounts WHERE sa_account_id=?", String.class, toId);
        return new TransferResponse(reference, fromAccountId, mask(destination), (BigDecimal) debit.get("amount"),
                jdbc.queryForObject("SELECT fcr_iso_code FROM fd_currency_ref WHERE fcr_currency_id=?", String.class, debit.get("currency")),
                (BigDecimal) debit.get("balance"), debit.get("at").toString());
    }
    private static String mask(String value) { return value.length() <= 4 ? "••••" : "••••" + value.substring(value.length() - 4); }

    public record TransferResponse(String reference, long fromAccountId, String toAccountNumber,
                                   BigDecimal amount, String currency, BigDecimal fromBalanceAfter, String postedAt) {}
}
