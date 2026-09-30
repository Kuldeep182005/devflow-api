package com.devflow.api.approval.domain;

public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED;

    public boolean isTerminal() {
        return this != PENDING;
    }

    public boolean canTransitionTo(ApprovalStatus next) {
        if (next == null) {
            return false;
        }
        if (isTerminal()) {
            return false;
        }
        return next == APPROVED || next == REJECTED || next == CANCELLED;
    }
}
