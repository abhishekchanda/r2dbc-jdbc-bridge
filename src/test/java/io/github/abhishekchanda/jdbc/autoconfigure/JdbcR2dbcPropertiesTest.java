package io.github.abhishekchanda.jdbc.autoconfigure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcR2dbcPropertiesTest {

    @Test
    void shouldGetAndSetProperties() {
        JdbcR2dbcProperties props = new JdbcR2dbcProperties();

        props.setEnabled(false);
        assertThat(props.isEnabled()).isFalse();

        props.setServer("myserver");
        assertThat(props.getServer()).isEqualTo("myserver");

        props.setDatabase("mydb");
        assertThat(props.getDatabase()).isEqualTo("mydb");

        props.setAuthentication("SqlPassword");
        assertThat(props.getAuthentication()).isEqualTo("SqlPassword");

        props.setClientId("client-id");
        assertThat(props.getClientId()).isEqualTo("client-id");

        props.setClientSecret("secret");
        assertThat(props.getClientSecret()).isEqualTo("secret");

        props.setTenantId("tenant");
        assertThat(props.getTenantId()).isEqualTo("tenant");

        props.setUsername("user");
        assertThat(props.getUsername()).isEqualTo("user");

        props.setPassword("pass");
        assertThat(props.getPassword()).isEqualTo("pass");

        props.setEncrypt(false);
        assertThat(props.isEncrypt()).isFalse();

        props.setTrustServerCertificate(true);
        assertThat(props.isTrustServerCertificate()).isTrue();

        props.setConnectionTimeout(60);
        assertThat(props.getConnectionTimeout()).isEqualTo(60);

        props.setLoginTimeout(60);
        assertThat(props.getLoginTimeout()).isEqualTo(60);
    }
}
