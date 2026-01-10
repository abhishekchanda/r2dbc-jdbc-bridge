package io.github.abhishekchanda.jdbc;

import io.r2dbc.spi.*;
import org.reactivestreams.Publisher;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.r2dbc.spi.IsolationLevel.*;

public class JdbcR2dbcConnection implements Connection {

    private final java.sql.Connection jdbcConnection;
    private final Scheduler scheduler;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public JdbcR2dbcConnection(java.sql.Connection jdbcConnection, Scheduler scheduler) {
        this.jdbcConnection = jdbcConnection;
        this.scheduler = scheduler;
    }

    @Override
    @NonNull
    public Publisher<Void> beginTransaction() {
        return Mono.fromRunnable(() -> {
            try {
                jdbcConnection.setAutoCommit(false);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to begin transaction", e);
            }
        }).subscribeOn(scheduler).then();
    }

    @Override
    @NonNull
    public Publisher<Void> beginTransaction(@NonNull TransactionDefinition definition) {
        return Mono.fromRunnable(() -> {
            try {
                jdbcConnection.setAutoCommit(false);

                // Apply isolation level if specified
                if (definition.getAttribute(TransactionDefinition.ISOLATION_LEVEL) instanceof IsolationLevel) {
                    IsolationLevel isolationLevel = (IsolationLevel) definition.getAttribute(TransactionDefinition.ISOLATION_LEVEL);
                    jdbcConnection.setTransactionIsolation(mapIsolationLevel(isolationLevel));
                }

                // Apply read-only if specified
                Boolean readOnly = (Boolean) definition.getAttribute(TransactionDefinition.READ_ONLY);
                if (readOnly != null) {
                    jdbcConnection.setReadOnly(readOnly);
                }

                // Note: JDBC doesn't support transaction names directly
                // Lock wait timeout would need database-specific SQL

            } catch (SQLException e) {
                throw new RuntimeException("Failed to begin transaction with definition", e);
            }
        }).subscribeOn(scheduler).then();
    }

    @Override
    @NonNull
    public Publisher<Void> commitTransaction() {
        return Mono.fromRunnable(() -> {
            try {
                jdbcConnection.commit();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to commit transaction", e);
            }
        }).subscribeOn(scheduler).then();
    }

    @Override
    @NonNull
    public Publisher<Void> rollbackTransaction() {
        return Mono.fromRunnable(() -> {
            try {
                jdbcConnection.rollback();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to rollback transaction", e);
            }
        }).subscribeOn(scheduler).then();
    }

    @Override
    @NonNull
    public Publisher<Void> rollbackTransactionToSavepoint(@NonNull String name) {
        return Mono.fromRunnable(() -> {
            try {
                java.sql.Savepoint savepoint = jdbcConnection.setSavepoint(name);
                jdbcConnection.rollback(savepoint);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to rollback to savepoint: " + name, e);
            }
        }).subscribeOn(scheduler).then();
    }

    @Override
    @NonNull
    public Publisher<Void> releaseSavepoint(@NonNull String name) {
        return Mono.fromRunnable(() -> {
            try {
                // JDBC doesn't have a direct "release savepoint" - savepoints are released on commit/rollback
                // This is a no-op for most JDBC drivers
            } catch (Exception e) {
                throw new RuntimeException("Failed to release savepoint: " + name, e);
            }
        }).subscribeOn(scheduler).then();
    }

    @Override
    @NonNull
    public Publisher<Void> setLockWaitTimeout(@NonNull Duration timeout) {
        // Database-specific implementation would be needed
        // For SQL Server, you might use: SET LOCK_TIMEOUT
        return Mono.empty();
    }

    @Override
    @NonNull
    public Publisher<Void> setStatementTimeout(@NonNull Duration timeout) {
        // Statement timeout is set per-statement in JDBC, not per-connection
        // This is a no-op for the connection level
        // Individual statements would need to call PreparedStatement.setQueryTimeout()
        return Mono.empty();
    }

    @Override
    @NonNull
    public Publisher<Void> close() {
        return Mono.fromRunnable(() -> {
            if (closed.compareAndSet(false, true)) {
                try {
                    jdbcConnection.close();
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to close connection", e);
                }
            }
        }).subscribeOn(scheduler).then();
    }

    @Override
    @NonNull
    public Statement createStatement(@NonNull String sql) {
        return new JdbcR2dbcStatement(jdbcConnection, sql, scheduler);
    }

    @Override
    @NonNull
    public Batch createBatch() {
        throw new UnsupportedOperationException("Batch operations not yet implemented");
    }

    @Override
    @NonNull
    public Publisher<Void> createSavepoint(@NonNull String name) {
        return Mono.fromRunnable(() -> {
            try {
                jdbcConnection.setSavepoint(name);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to create savepoint: " + name, e);
            }
        }).subscribeOn(scheduler).then();
    }

    @Override
    @NonNull
    public Publisher<Void> setTransactionIsolationLevel(@NonNull IsolationLevel isolationLevel) {
        return Mono.fromRunnable(() -> {
            try {
                jdbcConnection.setTransactionIsolation(mapIsolationLevel(isolationLevel));
            } catch (SQLException e) {
                throw new RuntimeException("Failed to set isolation level", e);
            }
        }).subscribeOn(scheduler).then();
    }

    @Override
    @NonNull
    public Publisher<Boolean> validate(@NonNull ValidationDepth depth) {
        return Mono.fromCallable(() -> {
            try {
                return !jdbcConnection.isClosed() && jdbcConnection.isValid(5);
            } catch (SQLException e) {
                return false;
            }
        }).subscribeOn(scheduler);
    }

    @Override
    @NonNull
    public IsolationLevel getTransactionIsolationLevel() {
        try {
            int jdbcLevel = jdbcConnection.getTransactionIsolation();
            return mapFromJdbcIsolationLevel(jdbcLevel);
        } catch (SQLException e) {
            return IsolationLevel.READ_COMMITTED; // Default
        }
    }

    @Override
    public boolean isAutoCommit() {
        try {
            return jdbcConnection.getAutoCommit();
        } catch (SQLException e) {
            return true; // Default
        }
    }

    @Override
    @NonNull
    public Publisher<Void> setAutoCommit(boolean autoCommit) {
        return Mono.fromRunnable(() -> {
            try {
                jdbcConnection.setAutoCommit(autoCommit);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to set auto-commit", e);
            }
        }).subscribeOn(scheduler).then();
    }

    @Override
    @NonNull
    public io.r2dbc.spi.ConnectionMetadata getMetadata() {
        return new io.r2dbc.spi.ConnectionMetadata() {
            @Override
            @NonNull
            public String getDatabaseProductName() {
                try {
                    return jdbcConnection.getMetaData().getDatabaseProductName();
                } catch (SQLException e) {
                    return "Microsoft SQL Server";
                }
            }

            @Override
            @NonNull
            public String getDatabaseVersion() {
                try {
                    return jdbcConnection.getMetaData().getDatabaseProductVersion();
                } catch (SQLException e) {
                    return "Unknown";
                }
            }
        };
    }


    private int mapIsolationLevel(IsolationLevel isolationLevel) {
        if (isolationLevel.equals(READ_UNCOMMITTED)) {
            return java.sql.Connection.TRANSACTION_READ_UNCOMMITTED;
        } else if (isolationLevel.equals(READ_COMMITTED)) {
            return java.sql.Connection.TRANSACTION_READ_COMMITTED;
        } else if (isolationLevel.equals(REPEATABLE_READ)) {
            return java.sql.Connection.TRANSACTION_REPEATABLE_READ;
        } else if (isolationLevel.equals(SERIALIZABLE)) {
            return java.sql.Connection.TRANSACTION_SERIALIZABLE;
        }
        return java.sql.Connection.TRANSACTION_READ_COMMITTED;
    }

    private IsolationLevel mapFromJdbcIsolationLevel(int jdbcLevel) {
        return switch (jdbcLevel) {
            case java.sql.Connection.TRANSACTION_READ_UNCOMMITTED -> READ_UNCOMMITTED;
            case java.sql.Connection.TRANSACTION_READ_COMMITTED -> IsolationLevel.READ_COMMITTED;
            case java.sql.Connection.TRANSACTION_REPEATABLE_READ -> REPEATABLE_READ;
            case java.sql.Connection.TRANSACTION_SERIALIZABLE -> SERIALIZABLE;
            default -> IsolationLevel.READ_COMMITTED;
        };
    }
}
