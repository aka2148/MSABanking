package com.bank.fd.exception;

import com.bank.common.exception.BankingException;
import com.bank.fd.domain.FdLifecycleCommand;
import com.bank.fd.entity.FdStatus;

public class InvalidFdTransitionException extends BankingException {

    public InvalidFdTransitionException(FdStatus currentStatus, FdLifecycleCommand command) {
        super("Invalid FD transition: current status='" + currentStatus
                + "', command='" + command + "'");
    }

    public InvalidFdTransitionException(String message) {
        super(message);
    }
}
