package com.bank.productservice;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductTextRulesTest {
    @Test void generatedPrefixesAreSafeAndBounded() {
        assertEquals("HOME_LOAN",ProductTextRules.codePrefix("Home & Loan","VARIANT",32));
        assertEquals("VARIANT",ProductTextRules.codePrefix("123 !","VARIANT",32));
        assertEquals("HOME",ProductTextRules.codePrefix("123 Home","VARIANT",32));
        assertEquals(27,ProductTextRules.codePrefix("A".repeat(50),"PRODUCT",27).length());
    }
    @Test void codesHaveStrictLengthAndAlphabet() {
        assertTrue(ProductTextRules.validCode("FD_01"));
        for (String code : new String[]{"fd_01", "1FD", "FD", "FD-X", " FD_X", "A".repeat(33)}) assertFalse(ProductTextRules.validCode(code), code);
    }
    @Test void namesNormalizeSpacesAndRejectSymbolsOrNumbersOnly() {
        assertEquals("Home Loan", ProductTextRules.name("  Home  Loan  "));
        assertTrue(ProductTextRules.validName("  Home  Loan  ", 128));
        assertTrue(ProductTextRules.validName("Saver (A/B) & Co., Jr's - 2", 255));
        for (String name : new String[]{"12345", "Hi", "Loan <b>", "Loan!", "A".repeat(129)}) assertFalse(ProductTextRules.validName(name,128));
    }
    @Test void optionalDescriptionRejectsMarkupControlsAndBoundaryViolations() {
        assertTrue(ProductTextRules.validDescription(null));
        assertTrue(ProductTextRules.validDescription(""));
        assertTrue(ProductTextRules.validDescription("  Valid description  "));
        assertTrue(ProductTextRules.validDescription("A".repeat(1000)));
        for (String description : new String[]{"short", "          ", "1234567890", "Description <b>", "Description\nline", "A".repeat(1001)}) assertFalse(ProductTextRules.validDescription(description));
    }
}
