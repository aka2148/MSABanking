package com.bank.identitycustomer;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class KycValidationTest {
    @Test void validatesDatesAndEveryEnum() {
        for (String value : new String[]{"1899-12-31", "2999-01-01", "2000-02-30", "bad"})
            assertNotNull(KycValidation.error("dateOfBirth", value, Map.of()));
        assertNull(KycValidation.error("dateOfBirth", LocalDate.now(java.time.ZoneOffset.UTC).minusYears(17).toString(), Map.of()));
        assertNull(KycValidation.error("dateOfBirth", LocalDate.now(java.time.ZoneOffset.UTC).minusYears(18).toString(), Map.of()));
        for (String field : new String[]{"gender", "relationship", "occupation", "countryOfOrigin", "proofOfIdType"})
            assertNotNull(KycValidation.error(field, "INVALID", Map.of()));
        assertEquals(249, KycValidation.COUNTRIES.size());
        for (String issue : new String[]{"1999-12-31", "2000-01-01", "2999-01-01", "bad"})
            assertNotNull(KycValidation.error("idIssueDate", issue, Map.of("dateOfBirth", "2000-01-01")));
        assertNull(KycValidation.error("idIssueDate", "2000-01-02", Map.of("dateOfBirth", "2000-01-01")));
        assertEquals("Date of birth can't be in the future.",KycValidation.error("dateOfBirth",LocalDate.now(java.time.ZoneOffset.UTC).plusDays(1).toString(),Map.of()));
        assertEquals("ID issue date can't be in the future.",KycValidation.error("idIssueDate",LocalDate.now(java.time.ZoneOffset.UTC).plusDays(1).toString(),Map.of("dateOfBirth","2000-01-01")));
        assertNull(KycValidation.error("idIssueDate",LocalDate.now(java.time.ZoneOffset.UTC).toString(),Map.of("dateOfBirth","2000-01-01")));
        assertNull(KycValidation.error("occupation","UNEMPLOYED",Map.of()));
    }
    @Test void validatesAllProofFormatsAndSafeText() {
        Map<String,String> examples = Map.of("AADHAAR", "234567890123", "PAN", "ABCDE1234F", "PASSPORT", "A1234567", "DRIVING_LICENSE", "KA-12345678", "VOTER_ID", "ABC1234567");
        examples.forEach((type, value) -> { assertTrue(KycValidation.validId(type, value)); assertFalse(KycValidation.validId(type, "!" + value)); });
        assertFalse(KycValidation.validId("AADHAAR", "123456789012"));
        for (String name : new String[]{"A", "12", "<name>", "Na\nme", "x".repeat(101)})
            assertNotNull(KycValidation.error("fatherOrSpouseName", name, Map.of()));
        assertNull(KycValidation.error("fatherOrSpouseName", "李明", Map.of()));
        assertFalse(KycValidation.safeText("x".repeat(9), 10, 500));
        assertFalse(KycValidation.safeText("x".repeat(501), 10, 500));
    }
    @Test void recognizesMagicBytesOnly() {
        assertEquals("application/pdf", KycService.documentType(new byte[]{37,80,68,70,45}));
        assertEquals("image/jpeg", KycService.documentType(new byte[]{(byte)255,(byte)216,(byte)255}));
        assertEquals("image/png", KycService.documentType(new byte[]{(byte)137,80,78,71,13,10,26,10}));
        assertNull(KycService.documentType(new byte[]{37,80}));
        assertNull(KycService.documentType("fake.pdf".getBytes()));
    }
}
