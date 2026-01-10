package io.github.abhishekchanda.jdbc;

import io.r2dbc.spi.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcR2dbcConnectionFactoryTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private java.sql.Connection jdbcConnection;

    private JdbcR2dbcConnectionFactory connectionFactory;

    @BeforeEach
    void setUp() {
        connectionFactory = new JdbcR2dbcConnectionFactory(dataSource);
    }

    @Test
    void shouldCreateConnection() throws SQLException {
        when(dataSource.getConnection()).thenReturn(jdbcConnection);

        Mono<? extends Connection> connectionMono = Mono.from(connectionFactory.create());

        StepVerifier.create(connectionMono)
                .assertNext(connection -> {
                    assertThat(connection).isInstanceOf(JdbcR2dbcConnection.class);
                })
                .verifyComplete();

        verify(dataSource).getConnection();
    }

    @Test
    void shouldHandleConnectionError() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

        Mono<? extends Connection> connectionMono = Mono.from(connectionFactory.create());

        StepVerifier.create(connectionMono)
                .expectErrorMatches(throwable -> 
                    throwable instanceof SQLException && 
                    throwable.getMessage().equals("Connection failed")
                ) // Mono.fromCallable propagates the exception directly
                .verify();

        verify(dataSource).getConnection();
    }
}
