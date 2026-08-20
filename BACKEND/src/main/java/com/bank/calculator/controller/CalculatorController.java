package com.bank.calculator.controller;

import com.bank.calculator.dto.EstimationRequest;
import com.bank.calculator.dto.EstimationResponse;
import com.bank.calculator.service.FdCalculatorService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calculator")
public class CalculatorController {

    @Autowired
    private FdCalculatorService fdCalculatorService;

    @PostMapping("/estimate")
    public ResponseEntity<EstimationResponse> estimateInterest(@Valid @RequestBody EstimationRequest request) {
        EstimationResponse response = fdCalculatorService.calculateEstimation(request);
        return ResponseEntity.ok(response);
    }
}
