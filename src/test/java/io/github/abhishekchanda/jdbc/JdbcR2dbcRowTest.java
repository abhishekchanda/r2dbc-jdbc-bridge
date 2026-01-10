package io.github.abhishekchanda.jdbc;

import io.r2dbc.spi.RowMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcR2dbcRowTest {

    @Mock
    private ResultSet resultSet;

    private JdbcR2dbcRow row;

    @BeforeEach
    void setUp() {
        row = new JdbcR2dbcRow(resultSet);
    }

    @Test
    void shouldGetValueByIndex() throws SQLException {
        when(resultSet.getObject(1)).thenReturn("value");
        
        String val = row.get(0, String.class);
        
        assertThat(val).isEqualTo("value");
    }

    @Test
    void shouldGetValueByName() throws SQLException {
        when(resultSet.getObject("col")).thenReturn(123);

        Integer val = row.get("col", Integer.class);

        assertThat(val).isEqualTo(123);
    }

    @Test
    void shouldReturnNullWhenValueIsNull() throws SQLException {
        when(resultSet.getObject(1)).thenReturn(null);

        String val = row.get(0, String.class);

        assertThat(val).isNull();
    }

    @Test
    void shouldGetMetadata() throws SQLException {
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        when(resultSet.getMetaData()).thenReturn(metaData);

        RowMetadata rm = row.getMetadata();

        assertThat(rm).isInstanceOf(JdbcR2dbcRowMetadata.class);
    }

    @Test
    void shouldHandleGetByIndexError() throws SQLException {
        when(resultSet.getObject(1)).thenThrow(new SQLException("Error"));

        assertThatThrownBy(() -> row.get(0, String.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to get value at index 0");
    }

    @Test
    void shouldHandleGetByNameError() throws SQLException {
        when(resultSet.getObject("col")).thenThrow(new SQLException("Error"));

        assertThatThrownBy(() -> row.get("col", String.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to get value for column col");
    }

    @Test
    void shouldHandleGetMetadataError() throws SQLException {
        when(resultSet.getMetaData()).thenThrow(new SQLException("Error"));

        assertThatThrownBy(() -> row.getMetadata())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to get metadata");
    }
}
