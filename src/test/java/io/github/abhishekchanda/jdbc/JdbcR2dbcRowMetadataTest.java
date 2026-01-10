package io.github.abhishekchanda.jdbc;

import io.r2dbc.spi.ColumnMetadata;
import io.r2dbc.spi.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JdbcR2dbcRowMetadataTest {

    @Mock
    private ResultSetMetaData metaData;

    private JdbcR2dbcRowMetadata rowMetadata;

    @BeforeEach
    void setUp() {
        rowMetadata = new JdbcR2dbcRowMetadata(metaData);
    }

    @Test
    void shouldGetColumnMetadataByIndex() throws SQLException {
        when(metaData.getColumnName(1)).thenReturn("id");
        when(metaData.getColumnClassName(1)).thenReturn("java.lang.Long");
        when(metaData.getColumnTypeName(1)).thenReturn("BIGINT");

        ColumnMetadata cm = rowMetadata.getColumnMetadata(0); // 0-indexed in R2DBC

        assertThat(cm.getName()).isEqualTo("id");
        assertThat(cm.getType().getJavaType()).isEqualTo(Long.class);
        assertThat(cm.getType().getName()).isEqualTo("BIGINT");
    }

    @Test
    void shouldGetColumnMetadataByName() throws SQLException {
        when(metaData.getColumnCount()).thenReturn(2);
        when(metaData.getColumnName(1)).thenReturn("id");
        when(metaData.getColumnName(2)).thenReturn("name");
        when(metaData.getColumnClassName(2)).thenReturn("java.lang.String");
        when(metaData.getColumnTypeName(2)).thenReturn("VARCHAR");

        ColumnMetadata cm = rowMetadata.getColumnMetadata("NAME"); // Case insensitive

        assertThat(cm.getName()).isEqualTo("name");
        assertThat(cm.getType().getJavaType()).isEqualTo(String.class);
        assertThat(cm.getType().getName()).isEqualTo("VARCHAR");
    }

    @Test
    void shouldThrowExceptionIfColumnNotFound() throws SQLException {
        when(metaData.getColumnCount()).thenReturn(1);
        when(metaData.getColumnName(1)).thenReturn("id");

        assertThatThrownBy(() -> rowMetadata.getColumnMetadata("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Column not found");
    }

    @Test
    void shouldGetAllColumnMetadatas() throws SQLException {
        when(metaData.getColumnCount()).thenReturn(2);
        when(metaData.getColumnName(1)).thenReturn("id");
        when(metaData.getColumnName(2)).thenReturn("name");

        List<? extends ColumnMetadata> metadatas = rowMetadata.getColumnMetadatas();

        assertThat(metadatas).hasSize(2);
        assertThat(metadatas.get(0).getName()).isEqualTo("id");
        assertThat(metadatas.get(1).getName()).isEqualTo("name");
    }

    @Test
    void shouldCheckContains() throws SQLException {
        when(metaData.getColumnCount()).thenReturn(1);
        when(metaData.getColumnName(1)).thenReturn("id");

        assertThat(rowMetadata.contains("id")).isTrue();
        assertThat(rowMetadata.contains("ID")).isTrue();
        assertThat(rowMetadata.contains("missing")).isFalse();
    }
    
    @Test
    void shouldHandleTypeExceptions() throws SQLException {
        when(metaData.getColumnClassName(1)).thenThrow(new SQLException("Error"));
        
        ColumnMetadata cm = rowMetadata.getColumnMetadata(0);
        
        Type type = cm.getType();
        assertThat(type.getJavaType()).isEqualTo(Object.class);
        assertThat(type.getName()).isEqualTo("UNKNOWN");
    }

    @Test
    void shouldHandleClassNotFound() throws SQLException {
        when(metaData.getColumnClassName(1)).thenReturn("com.nonexistent.Class");
        when(metaData.getColumnTypeName(1)).thenThrow(new SQLException("Error"));

        ColumnMetadata cm = rowMetadata.getColumnMetadata(0);

        Type type = cm.getType();
        assertThat(type.getJavaType()).isEqualTo(Object.class);
        assertThat(type.getName()).isEqualTo("UNKNOWN");
    }
}
