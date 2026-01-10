package io.github.abhishekchanda.jdbc;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JdbcR2dbcResultTest {

    @Mock
    private ResultSet resultSet;

    @Mock
    private PreparedStatement statement;

    @Mock
    private ResultSetMetaData metaData;

    @BeforeEach
    void setUp() throws SQLException {
        lenient().when(resultSet.getMetaData()).thenReturn(metaData);
    }

    @Test
    void shouldMapRows() throws SQLException {
        // Mock ResultSet behavior
        when(resultSet.next()).thenReturn(true, true, false); // 2 rows
        when(resultSet.getObject(1)).thenReturn("Row1", "Row2"); // 1-indexed

        JdbcR2dbcResult result = new JdbcR2dbcResult(resultSet, statement, Schedulers.immediate());

        Flux<String> mapped = Flux.from(result.map((row, meta) -> row.get(0, String.class)));

        StepVerifier.create(mapped)
                .expectNext("Row1")
                .expectNext("Row2")
                .verifyComplete();
        
        verify(resultSet, times(3)).next();
        verify(resultSet).close();
        verify(statement).close();
    }

    @Test
    void shouldHandleUpdateCount() throws SQLException {
        JdbcR2dbcResult result = new JdbcR2dbcResult(5, statement, Schedulers.immediate());

        Mono<Long> updateCount = Mono.from(result.getRowsUpdated());

        StepVerifier.create(updateCount)
                .expectNext(5L)
                .verifyComplete();

        verify(statement).close();
    }

    @Test
    void shouldHandleMapError() throws SQLException {
        when(resultSet.next()).thenThrow(new SQLException("Read failed"));

        JdbcR2dbcResult result = new JdbcR2dbcResult(resultSet, statement, Schedulers.immediate());

        Flux<Object> mapped = Flux.from(result.map((row, meta) -> row.get(0, String.class)));

        StepVerifier.create(mapped)
                .expectErrorMatches(t -> t.getMessage().contains("Failed to map result"))
                .verify();

        // Resources should still be closed
        verify(resultSet).close();
        verify(statement).close();
    }

    @Test
    void shouldFlatMapUpdateCount() {
        JdbcR2dbcResult result = new JdbcR2dbcResult(10, statement, Schedulers.immediate());

        Mono<Long> output = Mono.from(result.flatMap(segment -> {
            try {
                // Use reflection to call rowsUpdated on the anonymous class
                java.lang.reflect.Method method = segment.getClass().getMethod("rowsUpdated");
                return (org.reactivestreams.Publisher<Long>) method.invoke(segment);
            } catch (Exception e) {
                return Mono.error(e);
            }
        }));

        StepVerifier.create(output)
                .expectNext(10L)
                .verifyComplete();
    }

    @Test
    void shouldFlatMapRows() throws SQLException {
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getObject(1)).thenReturn("Data");
        
        JdbcR2dbcResult result = new JdbcR2dbcResult(resultSet, statement, Schedulers.immediate());
        
        Mono<String> output = Mono.from(result.flatMap(segment -> {
            try {
                // Use reflection to call map on the anonymous class
                java.lang.reflect.Method method = segment.getClass().getMethod("map", java.util.function.BiFunction.class);
                return (org.reactivestreams.Publisher<String>) method.invoke(segment, 
                    (java.util.function.BiFunction<Row, RowMetadata, String>) (row, meta) -> row.get(0, String.class));
            } catch (Exception e) {
                return Mono.error(e);
            }
        }));
        
        StepVerifier.create(output)
                .expectNext("Data")
                .verifyComplete();
    }

    @Test
    void shouldFilter() {
        JdbcR2dbcResult result = new JdbcR2dbcResult(resultSet, statement, Schedulers.immediate());
        assertThat(result.filter(s -> true)).isEqualTo(result);
    }
}
