package com.bank.customer.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JoinFormula;
import java.time.LocalDate;

/** Maps public.customer_identity_proofs. cip_proof_type is a FK to customer_classification_types. */
@Entity
@Table(name = "customer_identity_proofs", schema = "public")
public class IdentityProof {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "cip_proof_id") private Integer id;

    @Column(name = "cip_customer_id", nullable = false)
    private Integer customerForeignKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinFormula(value = "cast(cip_customer_id as bigint)", referencedColumnName = "cus_customer_id")
    private CustomerProfile customer;

    @Column(name = "cip_proof_type", nullable = false)
    private Integer proofTypeForeignKey;

    /** FK to customer_classification_types — the type/category of the identity document. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinFormula(value = "cast(cip_proof_type as bigint)", referencedColumnName = "cct_type_id")
    private ClassificationType proofType;

    @Column(name = "cip_proof_value", nullable = false) private String proofValue;
    @Column(name = "cip_proof_value_masked") private String maskedValue;
    @Column(name = "cip_expiry_date") private LocalDate expiryDate;
    @Column(name = "cip_verification_status", nullable = false) private String verificationStatus = "PENDING";
    @Column(name = "cip_document_ref") private String documentReference;

    public Integer getId() { return id; }
    public CustomerProfile getCustomer() { return customer; }
    public void setCustomer(CustomerProfile v) {
        customer = v;
        customerForeignKey = v == null || v.getId() == null ? null : Math.toIntExact(v.getId());
    }
    public ClassificationType getProofType() { return proofType; }
    public void setProofType(ClassificationType v) {
        proofType = v;
        proofTypeForeignKey = v == null || v.getId() == null ? null : Math.toIntExact(v.getId());
    }
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