package com.chronosq.metrics;

import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;


//You would use this in a service where you want to ensure a database transaction
// (like saving a new order) is completely finalized before triggering a
// secondary action (like sending an email).
@Component
public class AfterCommitExecutor {

    public void execute(
            Runnable action
    ) {
        Objects.requireNonNull(
                action,
                "action must not be null"
        );

        boolean transactionActive =
                TransactionSynchronizationManager
                        .isActualTransactionActive();

        boolean synchronizationActive =
                TransactionSynchronizationManager
                        .isSynchronizationActive();

        if (transactionActive && synchronizationActive) {
            TransactionSynchronizationManager
                    .registerSynchronization(
                            new TransactionSynchronization() {

                                @Override
                                public void afterCommit() {
                                    action.run();
                                }
                            }
                    );

            return;
        }

        action.run();
    }
}

//This class ensures that certain actions are only performed after a database transaction has successfully committed.
// It is annotated as a Spring @Component.
// It contains a single execute method that accepts a Runnable action.
// It uses Spring's TransactionSynchronizationManager to check if a transaction and
// synchronization are currently active.
// If a transaction is active, it registers a TransactionSynchronization to run the action
// specifically in the afterCommit() phase.
// If no transaction is active, it safely defaults to executing the Runnable immediately.