package com.bank.identitycustomer;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
class MpinBindingGoldenVectorTest {
 @Test void kycReapplyVector(){assertEquals("KYC_REAPPLY|42|3",MpinService.binding("KYC_REAPPLY",java.util.Map.of("customerUserId",42,"cycleNumber",3)));}
 @Test void kycReapplyRejectsMissingField(){org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,()->MpinService.binding("KYC_REAPPLY",java.util.Map.of("customerUserId",42)));}
 @Test void withdrawalVectors(){assertEquals("WITHDRAWAL|12|12",MpinService.binding("WITHDRAWAL",java.util.Map.of("accountId",12,"amount",new java.math.BigDecimal("12.0"))));assertEquals("WITHDRAWAL|12|12.50",MpinService.binding("WITHDRAWAL",java.util.Map.of("accountId",12,"amount",new java.math.BigDecimal("12.50"))));}
}
