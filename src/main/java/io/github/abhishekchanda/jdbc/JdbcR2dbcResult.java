package io.github.abhishekchanda.jdbc;

import io.r2dbc.spi.Result;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.reactivestreams.Publisher;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.BiFunction;
import java.util.function.Predicate;

public class JdbcR2dbcResult implements Result {

    private final ResultSet resultSet;
    private final Integer updateCount;
    private final PreparedStatement statement;
    private final Scheduler scheduler;

    // Constructor for SELECT queries
    public JdbcR2dbcResult(ResultSet resultSet, PreparedStatement statement, Scheduler scheduler) {
        this.resultSet = resultSet;
        this.updateCount = null;
        this.statement = statement;
        this.scheduler = scheduler;
    }

    // Constructor for UPDATE/INSERT/DELETE queries
    public JdbcR2dbcResult(int updateCount, PreparedStatement statement, Scheduler scheduler) {
        this.resultSet = null;
        this.updateCount = updateCount;
        this.statement = statement;
        this.scheduler = scheduler;
    }

    @Override
    @NonNull
    public Publisher<Long> getRowsUpdated() {
        if (updateCount != null) {
            return Mono.just(updateCount.longValue())
                    .doFinally(signal -> closeStatement());
        }
        return Mono.just(0L)
                .doFinally(signal -> closeStatement());
    }

    @Override
    @NonNull
    public <T> Publisher<T> map(@NonNull BiFunction<Row, RowMetadata, ? extends T> mappingFunction) {
        if (resultSet == null) {
            return Flux.empty();
        }

        return Flux.<T>create(sink -> {
            try {
                while (resultSet.next()) {
                    JdbcR2dbcRow row = new JdbcR2dbcRow(resultSet);
                    JdbcR2dbcRowMetadata metadata = new JdbcR2dbcRowMetadata(resultSet.getMetaData());
                    T mapped = mappingFunction.apply(row, metadata);
                    sink.next(mapped);
                }
                sink.complete();
            } catch (SQLException e) {
                sink.error(new RuntimeException("Failed to map result", e));
            } finally {
                closeResources();
            }
        }).subscribeOn(scheduler);
    }

    @Override
    @NonNull
    public Result filter(@NonNull Predicate<Segment> filter) {
        // Return this for simple pass-through
        // In a full implementation, you might want to filter segments
        return this;
    }

    @Override
    @NonNull
    @SuppressWarnings("unchecked")
    public <T> Publisher<T> flatMap(@NonNull java.util.function.Function<Segment, ? extends Publisher<? extends T>> mappingFunction) {
        // Simple implementation that processes result as a single segment
        if (resultSet != null) {
            // Treat as RowSegment
            Segment rowSegment = new Segment() {
                public <T> Publisher<T> map(BiFunction<Row, RowMetadata, ? extends T> mappingFunction) {
                    return (Publisher<T>) Flux.<T>create(sink -> {
                        try {
                            while (resultSet.next()) {
                                JdbcR2dbcRow row = new JdbcR2dbcRow(resultSet);
                                JdbcR2dbcRowMetadata metadata = new JdbcR2dbcRowMetadata(resultSet.getMetaData());
                                T mapped = mappingFunction.apply(row, metadata);
                                sink.next(mapped);
                            }
                            sink.complete();
                        } catch (SQLException e) {
                            sink.error(new RuntimeException("Failed to read rows", e));
                        } finally {
                            closeResources();
                        }
                    }).subscribeOn(scheduler);
                }
            };
            return (Publisher<T>) mappingFunction.apply(rowSegment);
        } else if (updateCount != null) {
            // Treat as UpdateCount segment
            Segment updateSegment = new Segment() {
                public Publisher<Long> rowsUpdated() {
                    return Mono.just(updateCount.longValue())
                            .doFinally(signal -> closeStatement());
                }
            };
            return (Publisher<T>) mappingFunction.apply(updateSegment);
        }
        return Flux.empty();
    }

    private void closeResources() {
        try {
            if (resultSet != null && !resultSet.isClosed()) {
                resultSet.close();
            }
        } catch (SQLException e) {
            // Log but don't throw
        }
        closeStatement();
    }

    private void closeStatement() {
        try {
            if (statement != null && !statement.isClosed()) {
                statement.close();
            }
        } catch (SQLException e) {
            // Log but don't throw
        }
    }
}
