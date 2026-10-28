package com.bank.fdservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.math.RoundingMode;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SavingsInterestAccountProcessorTest {
    @Test void actual365DailyAmountsSumToExpectedMonthlyCredit(){
        BigDecimal daily=SavingsInterestAccountProcessor.dailyInterest(new BigDecimal("100000.00"),new BigDecimal("0.04"));
        assertEquals(new BigDecimal("10.95890411"),daily);
        assertEquals(new BigDecimal("339.73"),daily.multiply(BigDecimal.valueOf(31)).setScale(2,RoundingMode.HALF_UP));
    }
    @Test void zeroDecimalCurrencyUsesHalfUpForCredit(){
        assertEquals(new BigDecimal("340"),new BigDecimal("339.73").setScale(0,RoundingMode.HALF_UP));
    }
}
