package io.github.abhishekchanda.jdbc;

import io.r2dbc.spi.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JdbcR2dbcStatementTest {

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;
    
    @Mock
    private ResultSet resultSet;

    @BeforeEach
    void setUp() throws SQLException {
        // Default behavior for execute
        lenient().when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        lenient().when(preparedStatement.execute()).thenReturn(true);
        lenient().when(preparedStatement.getResultSet()).thenReturn(resultSet);
    }

    @Test
    void shouldParseR2dbcStyleParameters() throws SQLException {
        String sql = "SELECT * FROM users WHERE name = :name AND age > :age";
        JdbcR2dbcStatement statement = new JdbcR2dbcStatement(connection, sql, Schedulers.immediate());

        statement.bind("name", "John");
        statement.bind("age", 30);
        
        Flux<? extends Result> result = Flux.from(statement.execute());
        
        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sqlCaptor.capture());
        
        assertThat(sqlCaptor.getValue()).isEqualTo("SELECT * FROM users WHERE name = ? AND age > ?");

        verify(preparedStatement).setObject(1, "John");
        verify(preparedStatement).setObject(2, 30);
    }

    @Test
    void shouldParseSqlServerStyleParameters() throws SQLException {
        String sql = "SELECT * FROM users WHERE name = @name AND age > @age";
        JdbcR2dbcStatement statement = new JdbcR2dbcStatement(connection, sql, Schedulers.immediate());

        statement.bind("name", "Jane");
        statement.bind("age", 25);

        Flux<? extends Result> result = Flux.from(statement.execute());

        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sqlCaptor.capture());

        assertThat(sqlCaptor.getValue()).isEqualTo("SELECT * FROM users WHERE name = ? AND age > ?");

        verify(preparedStatement).setObject(1, "Jane");
        verify(preparedStatement).setObject(2, 25);
    }

    @Test
    void shouldHandleDuplicateParameters() throws SQLException {
        String sql = "SELECT * FROM users WHERE (name = :val OR nickname = :val)";
        JdbcR2dbcStatement statement = new JdbcR2dbcStatement(connection, sql, Schedulers.immediate());

        statement.bind("val", "test");

        Flux<? extends Result> result = Flux.from(statement.execute());

        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(sqlCaptor.capture());

        assertThat(sqlCaptor.getValue()).isEqualTo("SELECT * FROM users WHERE (name = ? OR nickname = ?)");

        // Both positional parameters should be set
        verify(preparedStatement).setObject(1, "test");
        verify(preparedStatement).setObject(2, "test");
    }

    @Test
    void shouldSupportAddBatch() throws SQLException {
        String sql = "INSERT INTO users (name) VALUES (:name)";
        JdbcR2dbcStatement statement = new JdbcR2dbcStatement(connection, sql, Schedulers.immediate());

        statement.bind("name", "User1").add();
        statement.bind("name", "User2").add();
        
        Flux<? extends Result> result = Flux.from(statement.execute());

        StepVerifier.create(result)
                .expectNextCount(2) 
                .verifyComplete();

        verify(connection, times(2)).prepareStatement(anyString());
        verify(preparedStatement).setObject(1, "User1");
        verify(preparedStatement).setObject(1, "User2");
    }

    @Test
    void shouldBindPositionalParameters() throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        JdbcR2dbcStatement statement = new JdbcR2dbcStatement(connection, sql, Schedulers.immediate());

        statement.bind(0, 100); // 0-indexed R2DBC -> 1-indexed JDBC

        Flux<? extends Result> result = Flux.from(statement.execute());
        StepVerifier.create(result).expectNextCount(1).verifyComplete();

        verify(preparedStatement).setObject(1, 100);
    }

    @Test
    void shouldBindNullsByName() throws SQLException {
        String sql = "UPDATE users SET name = :name WHERE id = :id";
        JdbcR2dbcStatement statement = new JdbcR2dbcStatement(connection, sql, Schedulers.immediate());

        statement.bindNull("name", String.class);
        statement.bindNull("id", Integer.class);

        Flux<? extends Result> result = Flux.from(statement.execute());
        StepVerifier.create(result).expectNextCount(1).verifyComplete();

        // :name is 1st param (index 1), :id is 2nd (index 2)
        verify(preparedStatement).setObject(1, null);
        verify(preparedStatement).setObject(2, null);
    }

    @Test
    void shouldBindNullsByPosition() throws SQLException {
        String sql = "UPDATE users SET name = ? WHERE id = ?";
        JdbcR2dbcStatement statement = new JdbcR2dbcStatement(connection, sql, Schedulers.immediate());

        statement.bindNull(0, String.class);
        statement.bindNull(1, Integer.class);

        Flux<? extends Result> result = Flux.from(statement.execute());
        StepVerifier.create(result).expectNextCount(1).verifyComplete();

        verify(preparedStatement).setObject(1, null);
        verify(preparedStatement).setObject(2, null);
    }
    
    @Test
    void shouldHandleExecuteFailure() throws SQLException {
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("Prepare failed"));
        
        JdbcR2dbcStatement statement = new JdbcR2dbcStatement(connection, "SELECT 1", Schedulers.immediate());
        statement.bind(0, 1);
        
        StepVerifier.create(statement.execute())
                .expectErrorMatches(t -> t.getMessage().contains("Failed to execute statement"))
                .verify();
    }
    
    @Test
    void shouldIgnoreNoOpMethods() {
        JdbcR2dbcStatement statement = new JdbcR2dbcStatement(connection, "SELECT 1", Schedulers.immediate());
        statement.fetchSize(100);
        statement.returnGeneratedValues("id");
        // Just verify no exception
    }
}
