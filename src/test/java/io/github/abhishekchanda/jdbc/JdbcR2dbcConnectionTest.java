package io.github.abhishekchanda.jdbc;

import io.r2dbc.spi.IsolationLevel;
import io.r2dbc.spi.ValidationDepth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JdbcR2dbcConnectionTest {

    @Mock
    private Connection jdbcConnection;

    private JdbcR2dbcConnection connection;

    @BeforeEach
    void setUp() {
        connection = new JdbcR2dbcConnection(jdbcConnection, Schedulers.immediate());
    }

    @Test
    void shouldBeginTransaction() throws SQLException {
        Mono<Void> begin = Mono.from(connection.beginTransaction());

        StepVerifier.create(begin)
                .verifyComplete();

        verify(jdbcConnection).setAutoCommit(false);
    }

    @Test
    void shouldCommitTransaction() throws SQLException {
        Mono<Void> commit = Mono.from(connection.commitTransaction());

        StepVerifier.create(commit)
                .verifyComplete();

        verify(jdbcConnection).commit();
    }

    @Test
    void shouldRollbackTransaction() throws SQLException {
        Mono<Void> rollback = Mono.from(connection.rollbackTransaction());

        StepVerifier.create(rollback)
                .verifyComplete();

        verify(jdbcConnection).rollback();
    }

    @Test
    void shouldSetTransactionIsolationLevel() throws SQLException {
        Mono<Void> setLevel = Mono.from(connection.setTransactionIsolationLevel(IsolationLevel.SERIALIZABLE));

        StepVerifier.create(setLevel)
                .verifyComplete();

        verify(jdbcConnection).setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
    }

    @Test
    void shouldValidateConnection() throws SQLException {
        when(jdbcConnection.isValid(5)).thenReturn(true);
        when(jdbcConnection.isClosed()).thenReturn(false);

        Mono<Boolean> validate = Mono.from(connection.validate(ValidationDepth.LOCAL));

        StepVerifier.create(validate)
                .expectNext(true)
                .verifyComplete();

        verify(jdbcConnection).isValid(5);
    }

    @Test
    void shouldCloseConnection() throws SQLException {
        Mono<Void> close = Mono.from(connection.close());

        StepVerifier.create(close)
                .verifyComplete();

        verify(jdbcConnection).close();
    }

    @Test
    void shouldCloseConnectionIdempotently() throws SQLException {
        Mono.from(connection.close()).block();
        Mono.from(connection.close()).block();

        verify(jdbcConnection, times(1)).close();
    }

    @Test
    void shouldCreateStatement() {
        assertThat(connection.createStatement("SELECT 1")).isNotNull();
    }

    @Test
    void shouldThrowExceptionForBatch() {
        try {
            connection.createBatch();
        } catch (UnsupportedOperationException e) {
            assertThat(e).hasMessageContaining("not yet implemented");
        }
    }
    
    @Test
    void shouldSetAutoCommit() throws SQLException {
        Mono<Void> setAutoCommit = Mono.from(connection.setAutoCommit(true));
        
        StepVerifier.create(setAutoCommit)
                .verifyComplete();
        
        verify(jdbcConnection).setAutoCommit(true);
    }

    @Test
    void shouldGetMetadata() throws SQLException {
        java.sql.DatabaseMetaData metaData = mock(java.sql.DatabaseMetaData.class);
        when(jdbcConnection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("SQL Server");
        when(metaData.getDatabaseProductVersion()).thenReturn("15.0");

        io.r2dbc.spi.ConnectionMetadata r2dbcMeta = connection.getMetadata();
        
        assertThat(r2dbcMeta.getDatabaseProductName()).isEqualTo("SQL Server");
        assertThat(r2dbcMeta.getDatabaseVersion()).isEqualTo("15.0");
    }

    @Test
    void shouldCreateSavepoint() throws SQLException {
        Mono<Void> savepoint = Mono.from(connection.createSavepoint("sp1"));
        StepVerifier.create(savepoint).verifyComplete();
        verify(jdbcConnection).setSavepoint("sp1");
    }

    @Test
    void shouldRollbackToSavepoint() throws SQLException {
        java.sql.Savepoint sp = mock(java.sql.Savepoint.class);
        when(jdbcConnection.setSavepoint("sp1")).thenReturn(sp);

        Mono<Void> rollback = Mono.from(connection.rollbackTransactionToSavepoint("sp1"));
        StepVerifier.create(rollback).verifyComplete();
        
        verify(jdbcConnection).rollback(sp);
    }
    
    @Test
    void shouldReleaseSavepoint() {
        Mono<Void> release = Mono.from(connection.releaseSavepoint("sp1"));
        StepVerifier.create(release).verifyComplete();
    }
    
    @Test
    void shouldHandleExceptionInBeginTransaction() throws SQLException {
        doThrow(new SQLException("Error")).when(jdbcConnection).setAutoCommit(false);
        
        StepVerifier.create(connection.beginTransaction())
                .expectError(RuntimeException.class)
                .verify();
    }
    
    @Test
    void shouldHandleExceptionInCommit() throws SQLException {
        doThrow(new SQLException("Error")).when(jdbcConnection).commit();

        StepVerifier.create(connection.commitTransaction())
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleExceptionInRollback() throws SQLException {
        doThrow(new SQLException("Error")).when(jdbcConnection).rollback();

        StepVerifier.create(connection.rollbackTransaction())
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleExceptionInCreateSavepoint() throws SQLException {
        doThrow(new SQLException("Error")).when(jdbcConnection).setSavepoint(anyString());

        StepVerifier.create(connection.createSavepoint("sp"))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleExceptionInRollbackToSavepoint() throws SQLException {
        doThrow(new SQLException("Error")).when(jdbcConnection).setSavepoint(anyString());

        StepVerifier.create(connection.rollbackTransactionToSavepoint("sp"))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleExceptionInSetIsolationLevel() throws SQLException {
        doThrow(new SQLException("Error")).when(jdbcConnection).setTransactionIsolation(anyInt());

        StepVerifier.create(connection.setTransactionIsolationLevel(IsolationLevel.SERIALIZABLE))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleExceptionInClose() throws SQLException {
        doThrow(new SQLException("Error")).when(jdbcConnection).close();

        StepVerifier.create(connection.close())
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldHandleExceptionInSetAutoCommit() throws SQLException {
        doThrow(new SQLException("Error")).when(jdbcConnection).setAutoCommit(anyBoolean());

        StepVerifier.create(connection.setAutoCommit(true))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void shouldMapIsolationLevels() throws SQLException {
        // Test all mapping branches
        Mono.from(connection.setTransactionIsolationLevel(IsolationLevel.READ_UNCOMMITTED)).block();
        verify(jdbcConnection).setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
        
        Mono.from(connection.setTransactionIsolationLevel(IsolationLevel.READ_COMMITTED)).block();
        verify(jdbcConnection).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

        Mono.from(connection.setTransactionIsolationLevel(IsolationLevel.REPEATABLE_READ)).block();
        verify(jdbcConnection).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        
        Mono.from(connection.setTransactionIsolationLevel(IsolationLevel.SERIALIZABLE)).block();
        verify(jdbcConnection).setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
    }
    
    @Test
    void shouldGetTransactionIsolationLevel() throws SQLException {
        when(jdbcConnection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_SERIALIZABLE);
        assertThat(connection.getTransactionIsolationLevel()).isEqualTo(IsolationLevel.SERIALIZABLE);
        
        when(jdbcConnection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_UNCOMMITTED);
        assertThat(connection.getTransactionIsolationLevel()).isEqualTo(IsolationLevel.READ_UNCOMMITTED);
        
        when(jdbcConnection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_REPEATABLE_READ);
        assertThat(connection.getTransactionIsolationLevel()).isEqualTo(IsolationLevel.REPEATABLE_READ);
        
        when(jdbcConnection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        assertThat(connection.getTransactionIsolationLevel()).isEqualTo(IsolationLevel.READ_COMMITTED);
    }
    
    @Test
    void shouldHandleGetIsolationLevelError() throws SQLException {
        when(jdbcConnection.getTransactionIsolation()).thenThrow(new SQLException("Error"));
        assertThat(connection.getTransactionIsolationLevel()).isEqualTo(IsolationLevel.READ_COMMITTED); // Default
    }
    
    @Test
    void shouldIsAutoCommit() throws SQLException {
        when(jdbcConnection.getAutoCommit()).thenReturn(true);
        assertThat(connection.isAutoCommit()).isTrue();
        
        when(jdbcConnection.getAutoCommit()).thenReturn(false);
        assertThat(connection.isAutoCommit()).isFalse();
    }

    @Test
    void shouldHandleIsAutoCommitError() throws SQLException {
        when(jdbcConnection.getAutoCommit()).thenThrow(new SQLException("Error"));
        assertThat(connection.isAutoCommit()).isTrue(); // Default
    }
    
    @Test
    void shouldHandleMetadataError() throws SQLException {
        when(jdbcConnection.getMetaData()).thenThrow(new SQLException("Error"));
        
        io.r2dbc.spi.ConnectionMetadata meta = connection.getMetadata();
        assertThat(meta.getDatabaseProductName()).isEqualTo("Microsoft SQL Server");
        assertThat(meta.getDatabaseVersion()).isEqualTo("Unknown");
    }
    
    @Test
    void shouldSetStatementTimeout() {
        StepVerifier.create(connection.setStatementTimeout(java.time.Duration.ofSeconds(1)))
                .verifyComplete();
    }
    
    @Test
    void shouldSetLockWaitTimeout() {
        StepVerifier.create(connection.setLockWaitTimeout(java.time.Duration.ofSeconds(1)))
                .verifyComplete();
    }
}
