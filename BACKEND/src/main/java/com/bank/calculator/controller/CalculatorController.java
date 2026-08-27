package com.bank.calculator.controller;

import com.bank.calculator.dto.EstimationRequest;
import com.bank.calculator.dto.EstimationResponse;
import com.bank.calculator.service.FdCalculatorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/fd", "/api/v1/fd-calculator", "/api/v1/calculator"})
@CrossOrigin(origins = "*")
public class CalculatorController {

    private final FdCalculatorService calculatorService;

    public CalculatorController(FdCalculatorService calculatorService) {
        this.calculatorService = calculatorService;
    }

    @PostMapping("/calculate")
    public ResponseEntity<EstimationResponse> calculateMaturity(@Valid @RequestBody EstimationRequest request) {
        EstimationResponse response = calculatorService.calculateMaturityAmount(request);
        return ResponseEntity.ok(response);
    }
}