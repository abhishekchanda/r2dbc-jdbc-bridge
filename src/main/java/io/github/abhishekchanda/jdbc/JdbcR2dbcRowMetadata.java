package io.github.abhishekchanda.jdbc;

import io.r2dbc.spi.ColumnMetadata;
import io.r2dbc.spi.RowMetadata;
import io.r2dbc.spi.Type;
import org.springframework.lang.NonNull;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JdbcR2dbcRowMetadata implements RowMetadata {

    private final ResultSetMetaData metaData;

    public JdbcR2dbcRowMetadata(ResultSetMetaData metaData) {
        this.metaData = metaData;
    }

    @Override
    @NonNull
    public ColumnMetadata getColumnMetadata(int index) {
        return new JdbcR2dbcColumnMetadata(metaData, index + 1); // JDBC is 1-indexed
    }

    @Override
    @NonNull
    public ColumnMetadata getColumnMetadata(@NonNull String name) {
        try {
            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                if (metaData.getColumnName(i).equalsIgnoreCase(name)) {
                    return new JdbcR2dbcColumnMetadata(metaData, i);
                }
            }
            throw new IllegalArgumentException("Column not found: " + name);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get column metadata", e);
        }
    }

    @Override
    @NonNull
    public List<? extends ColumnMetadata> getColumnMetadatas() {
        try {
            int columnCount = metaData.getColumnCount();
            List<ColumnMetadata> metadatas = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                metadatas.add(new JdbcR2dbcColumnMetadata(metaData, i));
            }
            return metadatas;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get column metadatas", e);
        }
    }

    @Override
    public boolean contains(@NonNull String columnName) {
        try {
            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                if (metaData.getColumnName(i).equalsIgnoreCase(columnName)) {
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            return false;
        }
    }

    private static class JdbcR2dbcColumnMetadata implements ColumnMetadata {
        private final ResultSetMetaData metaData;
        private final int columnIndex;

        JdbcR2dbcColumnMetadata(ResultSetMetaData metaData, int columnIndex) {
            this.metaData = metaData;
            this.columnIndex = columnIndex;
        }

        @Override
        @NonNull
        public String getName() {
            try {
                return metaData.getColumnName(columnIndex);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get column name", e);
            }
        }

        @Override
        @NonNull
        public Type getType() {
            try {
                final String typeName = metaData.getColumnClassName(columnIndex);
                final int sqlType = metaData.getColumnType(columnIndex);

                return new Type() {
                    @Override
                    @NonNull
                    public Class<?> getJavaType() {
                        try {
                            return Class.forName(typeName);
                        } catch (ClassNotFoundException e) {
                            return Object.class;
                        }
                    }

                    @Override
                    @NonNull
                    public String getName() {
                        try {
                            return metaData.getColumnTypeName(columnIndex);
                        } catch (SQLException e) {
                            return "UNKNOWN";
                        }
                    }
                };
            } catch (SQLException e) {
                return new Type() {
                    @Override
                    @NonNull
                    public Class<?> getJavaType() {
                        return Object.class;
                    }

                    @Override
                    @NonNull
                    public String getName() {
                        return "UNKNOWN";
                    }
                };
            }
        }
    }
}
