package com.bank.customer.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;

/** Maps public.customer_identity_proofs. cip_proof_type is a FK to customer_classification_types. */
@Entity
@Table(name = "customer_identity_proofs", schema = "public")
public class IdentityProof {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "cip_proof_id") private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cip_customer_id", nullable = false)
    @JdbcTypeCode(SqlTypes.INTEGER)
    private CustomerProfile customer;

    /** FK to customer_classification_types — the type/category of the identity document. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "cip_proof_type", nullable = false)
    private ClassificationType proofType;

    @Column(name = "cip_proof_value", nullable = false) private String proofValue;
    @Column(name = "cip_proof_value_masked") private String maskedValue;
    @Column(name = "cip_expiry_date") private LocalDate expiryDate;
    @Column(name = "cip_verification_status", nullable = false) private String verificationStatus = "PENDING";
    @Column(name = "cip_document_ref") private String documentReference;

    public Integer getId() { return id; }
    public CustomerProfile getCustomer() { return customer; }
    public void setCustomer(CustomerProfile v) { customer = v; }
    public ClassificationType getProofType() { return proofType; }
    public void setProofType(ClassificationType v) { proofType = v; }
    public String getProofValue() { return proofValue; }
    public void setProofValue(String v) { proofValue = v; }
    public String getMaskedValue() { return maskedValue; }
    public void setMaskedValue(String v) { maskedValue = v; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate v) { expiryDate = v; }
    public String getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(String v) { verificationStatus = v; }
    public String getDocumentReference() { return documentReference; }
    public void setDocumentReference(String v) { documentReference = v; }
}