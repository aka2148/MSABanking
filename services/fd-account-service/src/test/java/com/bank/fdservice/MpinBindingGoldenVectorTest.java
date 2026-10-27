package com.bank.fdservice;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
class MpinBindingGoldenVectorTest {
 @Test void kycReapplyVector(){assertEquals("KYC_REAPPLY|42|3",MpinProofVerifier.canonical("KYC_REAPPLY",42,3));}
 @Test void withdrawalVectors(){assertEquals("WITHDRAWAL|12|12",MpinProofVerifier.canonical("WITHDRAWAL",12,new java.math.BigDecimal("12.0")));assertEquals("WITHDRAWAL|12|12.50",MpinProofVerifier.canonical("WITHDRAWAL",12,new java.math.BigDecimal("12.50")));}
}
