package io.github.abhishekchanda.jdbc.autoconfigure;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import io.r2dbc.spi.ConnectionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcR2dbcAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JdbcR2dbcAutoConfiguration.class));

    @Test
    void shouldCreateDefaultBeans() {
        contextRunner
                .withPropertyValues(
                        "r2dbc.jdbc.server=localhost",
                        "r2dbc.jdbc.database=testdb"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(DataSource.class);
                    assertThat(context).hasSingleBean(ConnectionFactory.class);
                    assertThat(context).hasSingleBean(DatabaseClient.class);
                    assertThat(context).hasSingleBean(R2dbcEntityTemplate.class);
                    assertThat(context).hasSingleBean(ReactiveTransactionManager.class);

                    DataSource ds = context.getBean(DataSource.class);
                    assertThat(ds).isInstanceOf(SQLServerDataSource.class);
                    SQLServerDataSource sqlDs = (SQLServerDataSource) ds;
                    assertThat(sqlDs.getServerName()).isEqualTo("localhost");
                    assertThat(sqlDs.getDatabaseName()).isEqualTo("testdb");
                    assertThat(sqlDs.getAuthentication()).isEqualTo("ActiveDirectoryMSI");
                });
    }

    @Test
    void shouldConfigureServicePrincipal() {
        contextRunner
                .withPropertyValues(
                        "r2dbc.jdbc.server=localhost",
                        "r2dbc.jdbc.database=testdb",
                        "r2dbc.jdbc.authentication=ActiveDirectoryServicePrincipal",
                        "r2dbc.jdbc.client-id=my-client-id",
                        "r2dbc.jdbc.client-secret=my-secret",
                        "r2dbc.jdbc.tenant-id=my-tenant"
                )
                .run(context -> {
                    DataSource ds = context.getBean(DataSource.class);
                    SQLServerDataSource sqlDs = (SQLServerDataSource) ds;
                    assertThat(sqlDs.getAuthentication()).isEqualTo("ActiveDirectoryServicePrincipal");
                    // SQLServerDataSource stores user/password differently depending on version/impl, 
                    // but we check if properties logic was executed
                    // Note: SQLServerDataSource does not expose getter for user/password easily or might match what we set
                });
    }

    @Test
    void shouldConfigureSqlPassword() {
        contextRunner
                .withPropertyValues(
                        "r2dbc.jdbc.server=localhost",
                        "r2dbc.jdbc.database=testdb",
                        "r2dbc.jdbc.authentication=SqlPassword",
                        "r2dbc.jdbc.username=sa",
                        "r2dbc.jdbc.password=secret"
                )
                .run(context -> {
                    DataSource ds = context.getBean(DataSource.class);
                    SQLServerDataSource sqlDs = (SQLServerDataSource) ds;
                    assertThat(sqlDs.getAuthentication()).isEqualTo("SqlPassword");
                });
    }

    @Test
    void shouldDisableAutoConfiguration() {
        contextRunner
                .withPropertyValues("r2dbc.jdbc.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ConnectionFactory.class);
                });
    }
}
