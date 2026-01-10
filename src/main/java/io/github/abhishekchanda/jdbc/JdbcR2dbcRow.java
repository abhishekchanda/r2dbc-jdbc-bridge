package io.github.abhishekchanda.jdbc;

import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.lang.NonNull;

import java.sql.ResultSet;
import java.sql.SQLException;

public class JdbcR2dbcRow implements Row {

    private final ResultSet resultSet;

    public JdbcR2dbcRow(ResultSet resultSet) {
        this.resultSet = resultSet;
    }

    @Override
    public <T> T get(int index, @NonNull Class<T> type) {
        try {
            Object value = resultSet.getObject(index + 1); // JDBC is 1-indexed
            if (value == null) {
                return null;
            }
            return type.cast(value);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get value at index " + index, e);
        }
    }

    @Override
    public <T> T get(@NonNull String name, @NonNull Class<T> type) {
        try {
            Object value = resultSet.getObject(name);
            if (value == null) {
                return null;
            }
            return type.cast(value);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get value for column " + name, e);
        }
    }

    @Override
    @NonNull
    public RowMetadata getMetadata() {
        try {
            return new JdbcR2dbcRowMetadata(resultSet.getMetaData());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get metadata", e);
        }
    }
}
