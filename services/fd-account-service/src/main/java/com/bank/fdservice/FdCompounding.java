package com.bank.fdservice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

final class FdCompounding {
    private FdCompounding() {}
    static int months(String basis) {
        if ("MONTHLY".equalsIgnoreCase(basis)) return 1;
        if ("YEARLY".equalsIgnoreCase(basis)) return 12;
        return 3;
    }
    static String basis(String value) { return switch(months(value)) { case 1 -> "MONTHLY"; case 12 -> "YEARLY"; default -> "QUARTERLY"; }; }
    static boolean capitalizationDate(LocalDate start, LocalDate date, String basis) {
        int period=months(basis);
        long elapsed=ChronoUnit.MONTHS.between(start,date);
        for (long month=period;month<=elapsed+period;month+=period) if (start.plusMonths(month).equals(date)) return true;
        return false;
    }
    static BigDecimal maturity(BigDecimal principal,BigDecimal rate,LocalDate start,LocalDate end,int scale,String basis) {
        BigDecimal balance=principal,period=BigDecimal.ZERO.setScale(8,RoundingMode.HALF_UP);
        for(LocalDate d=start.plusDays(1);!d.isAfter(end);d=d.plusDays(1)) {
            period=period.add(balance.multiply(rate).divide(BigDecimal.valueOf(365),12,RoundingMode.HALF_UP).setScale(8,RoundingMode.HALF_UP));
            if(d.equals(end)||capitalizationDate(start,d,basis)) {
                balance=balance.add(period.setScale(scale,RoundingMode.HALF_UP));
                period=BigDecimal.ZERO.setScale(8,RoundingMode.HALF_UP);
            }
        }
        return balance.setScale(scale,RoundingMode.HALF_UP);
    }
}
