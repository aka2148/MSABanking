package com.bank.fdservice;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class FdCompoundingTest {
    @Test void datesStayAnchoredToTheOriginalDayIncludingLeapYears() {
        LocalDate start=LocalDate.of(2024,1,31);
        assertTrue(FdCompounding.capitalizationDate(start,LocalDate.of(2024,2,29),"MONTHLY"));
        assertTrue(FdCompounding.capitalizationDate(start,LocalDate.of(2024,3,31),"MONTHLY"));
        assertFalse(FdCompounding.capitalizationDate(start,LocalDate.of(2024,3,29),"MONTHLY"));
        assertTrue(FdCompounding.capitalizationDate(start,LocalDate.of(2024,4,30),"QUARTERLY"));
        assertFalse(FdCompounding.capitalizationDate(start,LocalDate.of(2024,2,29),"QUARTERLY"));
        assertTrue(FdCompounding.capitalizationDate(start,LocalDate.of(2025,1,31),"YEARLY"));
        assertTrue(FdCompounding.capitalizationDate(LocalDate.of(2024,2,29),LocalDate.of(2025,2,28),"YEARLY"));
    }
    @Test void allThreeBasesHaveKnownFinalBalances() {
        LocalDate start=LocalDate.of(2025,1,31);
        String[] bases={"MONTHLY","QUARTERLY","YEARLY"};
        String[] balances={"10616.77","10613.64","10600.00"};
        for(int i=0;i<bases.length;i++) assertEquals(new BigDecimal(balances[i]),FdCompounding.maturity(new BigDecimal("10000"),new BigDecimal("0.06"),start,start.plusYears(1),2,bases[i]));
    }
    @Test void quarterlyAndUnknownRemainBitForBitEqualToThePreviousAlgorithm() {
        for (LocalDate start : new LocalDate[]{LocalDate.of(2024,1,31),LocalDate.of(2025,2,28)}) {
            for (int scale : new int[]{0,2,3,8}) {
                LocalDate end=start.plusMonths(17);
                BigDecimal principal=new BigDecimal("10000"), rate=new BigDecimal("0.0625");
                BigDecimal balance=principal,pending=BigDecimal.ZERO.setScale(8,RoundingMode.HALF_UP);
                for(LocalDate day=start.plusDays(1);!day.isAfter(end);day=day.plusDays(1)) {
                    pending=pending.add(balance.multiply(rate).divide(BigDecimal.valueOf(365),12,RoundingMode.HALF_UP).setScale(8,RoundingMode.HALF_UP));
                    boolean boundary=false;
                    long elapsed=java.time.temporal.ChronoUnit.MONTHS.between(start,day);
                    for(long q=3;q<=elapsed+3;q+=3)if(start.plusMonths(q).equals(day))boundary=true;
                    if(day.equals(end)||boundary){balance=balance.add(pending.setScale(scale,RoundingMode.HALF_UP));pending=BigDecimal.ZERO.setScale(8,RoundingMode.HALF_UP);}
                }
                for(String basis:new String[]{"QUARTERLY",null,"UNKNOWN"}) assertEquals(balance.setScale(scale,RoundingMode.HALF_UP),FdCompounding.maturity(principal,rate,start,end,scale,basis));
            }
        }
    }
}
