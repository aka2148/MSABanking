package com.bank.fd.domain;

import com.bank.fd.entity.FdStatus;
import com.bank.fd.exception.InvalidFdTransitionException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FdLifecycleStateMachineTest {

    private final FdLifecycleStateMachine stateMachine = new FdLifecycleStateMachine();

    @Test
    void acceptsOnlyTheDocumentedLifecycleTransitions() {
        assertTransition(FdStatus.PENDING, FdLifecycleCommand.FUND, FdStatus.ACTIVE);
        assertTransition(FdStatus.ACTIVE, FdLifecycleCommand.MATURE, FdStatus.MATURED);
        assertTransition(FdStatus.MATURED, FdLifecycleCommand.CLOSE, FdStatus.CLOSED);
        assertTransition(FdStatus.ACTIVE, FdLifecycleCommand.PREMATURE_CLOSE, FdStatus.PREMATURELY_CLOSED);
    }

    @Test
    void rejectsEveryUnlistedStatusAndCommandPair() {
        Map<FdStatus, Map<FdLifecycleCommand, FdStatus>> validTransitions = Map.of(
                FdStatus.PENDING, Map.of(FdLifecycleCommand.FUND, FdStatus.ACTIVE),
                FdStatus.ACTIVE, Map.of(
                        FdLifecycleCommand.MATURE, FdStatus.MATURED,
                        FdLifecycleCommand.PREMATURE_CLOSE, FdStatus.PREMATURELY_CLOSED),
                FdStatus.MATURED, Map.of(FdLifecycleCommand.CLOSE, FdStatus.CLOSED)
        );

        for (FdStatus status : FdStatus.values()) {
            for (FdLifecycleCommand command : FdLifecycleCommand.values()) {
                if (validTransitions.getOrDefault(status, Map.of()).containsKey(command)) {
                    continue;
                }

                InvalidFdTransitionException exception = assertThrows(
                        InvalidFdTransitionException.class,
                        () -> stateMachine.transition(status, command));
                assertTrue(exception.getMessage().contains(status.name()));
                assertTrue(exception.getMessage().contains(command.name()));
            }
        }
    }

    @Test
    void rejectsNullStatusAndCommand() {
        assertThrows(InvalidFdTransitionException.class,
                () -> stateMachine.transition(null, FdLifecycleCommand.FUND));
        assertThrows(InvalidFdTransitionException.class,
                () -> stateMachine.transition(FdStatus.PENDING, null));
    }

    private void assertTransition(FdStatus from, FdLifecycleCommand command, FdStatus to) {
        FdTransition transition = stateMachine.transition(from, command);

        assertEquals(from, transition.from());
        assertEquals(to, transition.to());
    }
}
