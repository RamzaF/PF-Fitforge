package com.fitforge.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tracks the Spring transaction completion outcome for the current thread so
 * post-commit media cleanup and rollback compensation never misclassify a
 * committed or uncertain transaction as rolled back.
 */
public final class TxCompletionTracker implements AutoCloseable {

    public enum State {
        NOT_STARTED,
        IN_PROGRESS,
        COMMITTED,
        ROLLED_BACK,
        UNKNOWN
    }

    private static final ThreadLocal<TxCompletionTracker> CURRENT = new ThreadLocal<>();

    private volatile State state = State.NOT_STARTED;

    private TxCompletionTracker() {
    }

    public static TxCompletionTracker begin() {
        TxCompletionTracker tracker = new TxCompletionTracker();
        CURRENT.set(tracker);
        return tracker;
    }

    public static void bindToCurrentTransaction() {
        TxCompletionTracker tracker = CURRENT.get();
        if (tracker == null) {
            return;
        }
        tracker.state = State.IN_PROGRESS;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    tracker.state = switch (status) {
                        case STATUS_COMMITTED -> State.COMMITTED;
                        case STATUS_ROLLED_BACK -> State.ROLLED_BACK;
                        default -> State.UNKNOWN;
                    };
                }
            });
        }
    }

    public State state() {
        return state;
    }

    @Override
    public void close() {
        CURRENT.remove();
    }
}
