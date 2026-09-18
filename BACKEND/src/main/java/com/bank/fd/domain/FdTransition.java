package com.bank.fd.domain;

import com.bank.fd.entity.FdStatus;

public record FdTransition(FdStatus from, FdStatus to) {
}
