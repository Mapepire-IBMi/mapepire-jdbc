package io.github.mapepire_ibmi;

import java.sql.SQLException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

final class SqlExceptions {
    private SqlExceptions() {
    }

    /**
     * Converts an exception raised while waiting on a CompletableFuture into a
     * SQLException, preserving the sqlState/error of the underlying failure
     * instead of discarding it behind a generic ExecutionException/
     * CompletionException wrapper.
     */
    static SQLException toSqlException(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null
                && (cause instanceof ExecutionException || cause instanceof CompletionException)) {
            cause = cause.getCause();
        }
        return cause instanceof SQLException ? (SQLException) cause : new SQLException(cause);
    }
}
