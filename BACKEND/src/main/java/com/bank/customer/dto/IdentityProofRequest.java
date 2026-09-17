package com.bank.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request body for POST /customers/{customerId}/identity-proofs.
 * proofTypeId maps to cip_proof_type FK → customer_classification_types.cct_type_id.
 */
public class IdentityProofRequest {
    /** ID of the ClassificationType that represents the document type (passport, PAN, Aadhaar etc.) */
    @NotNull private Integer proofTypeId;
    @NotBlank private String proofValue;
    private String maskedValue;
    private LocalDate expiryDate;
    private String documentReference;

    public Integer getProofTypeId() { return proofTypeId; }
    public void setProofTypeId(Integer v) { proofTypeId = v; }
    public String getProofValue() { return proofValue; }
    public void setProofValue(String v) { proofValue = v; }
    public String getMaskedValue() { return maskedValue; }
    public void setMaskedValue(String v) { maskedValue = v; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate v) { expiryDate = v; }
    public String getDocumentReference() { return documentReference; }
    public void setDocumentReference(String v) { documentReference = v; }
}
