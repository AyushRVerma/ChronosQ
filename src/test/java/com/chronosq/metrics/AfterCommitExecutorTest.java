package com.chronosq.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AfterCommitExecutorTest {

    private final AfterCommitExecutor executor =
            new AfterCommitExecutor();

    @AfterEach
    void cleanUpTransactionState() {
        if (TransactionSynchronizationManager
                .isSynchronizationActive()) {

            TransactionSynchronizationManager
                    .clearSynchronization();
        }

        TransactionSynchronizationManager
                .setActualTransactionActive(false);
    }

    @Test
    void shouldExecuteImmediatelyWithoutTransaction() {
        AtomicBoolean executed =
                new AtomicBoolean(false);

        executor.execute(
                () -> executed.set(true)
        );

        assertThat(executed.get())
                .isTrue();
    }

    @Test
    void shouldWaitUntilTransactionCommits() {
        beginTransaction();

        AtomicBoolean executed =
                new AtomicBoolean(false);

        executor.execute(
                () -> executed.set(true)
        );

        assertThat(executed.get())
                .isFalse();

        TransactionSynchronizationManager
                .getSynchronizations()
                .forEach(
                        TransactionSynchronization::afterCommit
                );

        assertThat(executed.get())
                .isTrue();
    }

    @Test
    void shouldNotExecuteAfterRollback() {
        beginTransaction();

        AtomicBoolean executed =
                new AtomicBoolean(false);

        executor.execute(
                () -> executed.set(true)
        );

        TransactionSynchronizationManager
                .getSynchronizations()
                .forEach(synchronization ->
                        synchronization.afterCompletion(
                                TransactionSynchronization
                                        .STATUS_ROLLED_BACK
                        )
                );

        assertThat(executed.get())
                .isFalse();
    }

    private void beginTransaction() {
        TransactionSynchronizationManager
                .setActualTransactionActive(true);

        TransactionSynchronizationManager
                .initSynchronization();
    }
}