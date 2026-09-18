package com.bank.fd.domain;

import com.bank.fd.entity.FdStatus;
import com.bank.fd.exception.InvalidFdTransitionException;

public final class FdLifecycleStateMachine {

    public FdTransition transition(FdStatus currentStatus, FdLifecycleCommand command) {
        if (currentStatus == null || command == null) {
            throw new InvalidFdTransitionException(currentStatus, command);
        }

        FdStatus targetStatus = switch (currentStatus) {
            case PENDING -> command == FdLifecycleCommand.FUND ? FdStatus.ACTIVE : null;
            case ACTIVE -> switch (command) {
                case MATURE -> FdStatus.MATURED;
                case PREMATURE_CLOSE -> FdStatus.PREMATURELY_CLOSED;
                default -> null;
            };
            case MATURED -> command == FdLifecycleCommand.CLOSE ? FdStatus.CLOSED : null;
            case CLOSED, PREMATURELY_CLOSED -> null;
        };

        if (targetStatus == null) {
            throw new InvalidFdTransitionException(currentStatus, command);
        }

        return new FdTransition(currentStatus, targetStatus);
    }
}
