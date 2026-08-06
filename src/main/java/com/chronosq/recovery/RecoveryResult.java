package com.chronosq.recovery;

public record RecoveryResult(

        int retriedJobCount,

        int deadLetteredJobCount

) {

    public RecoveryResult {
        if (retriedJobCount < 0) {
            throw new IllegalArgumentException(
                    "retriedJobCount must not be negative"
            );
        }

        if (deadLetteredJobCount < 0) {
            throw new IllegalArgumentException(
                    "deadLetteredJobCount must not be negative"
            );
        }
    }

    public int totalRecoveredJobCount() {
        return retriedJobCount
                + deadLetteredJobCount;
    }

    public static RecoveryResult empty() {
        return new RecoveryResult(
                0,
                0
        );
    }
}