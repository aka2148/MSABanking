package com.bank.fdservice;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice
@org.springframework.core.annotation.Order(0)
public class MpinProofExceptionHandler {
    @ExceptionHandler(MpinProofVerifier.MpinProofException.class)
    public ResponseEntity<?> mpin(MpinProofVerifier.MpinProofException ex){return ResponseEntity.status(ex.status()).body(Map.of("code",ex.code(),"message","MPIN proof is required or invalid."));}
}
