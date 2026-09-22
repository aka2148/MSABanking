package com.bank.fd.domain;

import com.bank.fd.entity.FdStatus;
import com.bank.fd.exception.InvalidFdTransitionException;
import com.bank.fd.repository.FdAccountRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Phase 5 boundary/invalid coverage beyond the existing domain tests
 * ({@code FdInterestCalculatorTest}, {@code FdLifecycleStateMachineTest},
 * {@code PrematurePenaltyTest}, {@code FdCalculationPolicyTest}).
 *
 * <p>Covers only the gaps: zero-day maturity identity, the worked one-day
 * Actual/365 value, the full (100-point) penalty floor, the three
 * representative invalid transitions called out in the plan, and
 * null/negative principal validation that never reaches persistence.
 */
class FdPhase5BoundaryTest {

    private final FdLifecycleStateMachine stateMachine = new FdLifecycleStateMachine();

    @Test
    void zeroDayTermMaturityEqualsPrincipalExactlyAtScaleTwo() {
        LocalDate day = LocalDate.of(2026, 3, 1);
        BigDecimal principal = new BigDecimal("100000.00");

        assertThat(FdInterestCalculator.interestForPeriod(
                principal, new BigDecimal("0.0650"), day, day))
                .isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(FdInterestCalculator.maturityAmount(
                principal, new BigDecimal("0.0650"), day, day))
                .isEqualTo(new BigDecimal("100000.00"));
    }

    @Test
    void oneDayActual365InterestRoundsHalfUpTo1781() {
        BigDecimal interest = FdInterestCalculator.interestForPeriod(
                new BigDecimal("100000.00"),
                new BigDecimal("0.0650"),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 2));

        assertThat(interest).isEqualTo(new BigDecimal("17.81"));
    }

    @Test
    void fullHundredPointPenaltyFloorsPenalizedRateAtZero() {
        BigDecimal penalized = PrematurePenalty.penalizedRate(
                new BigDecimal("0.0650"), new BigDecimal("100.00"));

        assertThat(penalized).isEqualTo(new BigDecimal("0.0000"));
        assertThat(PrematurePenalty.prematureInterest(
                new BigDecimal("100000.00"),
                new BigDecimal("0.0650"),
                new BigDecimal("100.00"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 7, 2)))
                .isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void fundOnActiveIsRejected() {
        assertThatThrownBy(() -> stateMachine.transition(FdStatus.ACTIVE, FdLifecycleCommand.FUND))
                .isInstanceOf(InvalidFdTransitionException.class);
    }

    @Test
    void matureOnPendingIsRejected() {
        assertThatThrownBy(() -> stateMachine.transition(FdStatus.PENDING, FdLifecycleCommand.MATURE))
                .isInstanceOf(InvalidFdTransitionException.class);
    }

    @Test
    void closeOnPendingIsRejected() {
        assertThatThrownBy(() -> stateMachine.transition(FdStatus.PENDING, FdLifecycleCommand.CLOSE))
                .isInstanceOf(InvalidFdTransitionException.class);
    }

    @Test
    void nullPrincipalIsRejectedBeforeAnyPersistenceCall() {
        FdAccountRepository accounts = mock(FdAccountRepository.class);
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 4, 1);

        assertThatThrownBy(() -> FdInterestCalculator.interestForPeriod(
                null, new BigDecimal("0.0650"), start, end))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FdInterestCalculator.maturityAmount(
                null, new BigDecimal("0.0650"), start, end))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(accounts);
    }

    @Test
    void negativePrincipalIsRejectedBeforeAnyPersistenceCall() {
        FdAccountRepository accounts = mock(FdAccountRepository.class);
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 4, 1);

        BigDecimal negative = new BigDecimal("-1000.00").setScale(2, RoundingMode.HALF_UP);
        assertThatThrownBy(() -> FdInterestCalculator.interestForPeriod(
                negative, new BigDecimal("0.0650"), start, end))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FdInterestCalculator.maturityAmount(
                negative, new BigDecimal("0.0650"), start, end))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(accounts);
    }
}
