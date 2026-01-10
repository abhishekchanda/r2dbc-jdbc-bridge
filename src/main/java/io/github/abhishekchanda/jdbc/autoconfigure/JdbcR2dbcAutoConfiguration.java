package io.github.abhishekchanda.jdbc.autoconfigure;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import io.github.abhishekchanda.jdbc.JdbcR2dbcConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

@AutoConfiguration
@ConditionalOnClass({ConnectionFactory.class, SQLServerDataSource.class})
@ConditionalOnProperty(prefix = "r2dbc.jdbc", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JdbcR2dbcProperties.class)
@EnableR2dbcRepositories
public class JdbcR2dbcAutoConfiguration {

    private final JdbcR2dbcProperties properties;

    public JdbcR2dbcAutoConfiguration(JdbcR2dbcProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public DataSource dataSource() {
        SQLServerDataSource ds = new SQLServerDataSource();

        // Basic connection settings
        ds.setServerName(properties.getServer());
        ds.setDatabaseName(properties.getDatabase());

        // Encryption
        if (properties.isEncrypt()) {
            ds.setEncrypt("true");
        } else {
            ds.setEncrypt("false");
        }

        ds.setTrustServerCertificate(properties.isTrustServerCertificate());
        ds.setLoginTimeout(properties.getConnectionTimeout());

        // Authentication
        ds.setAuthentication(properties.getAuthentication());

        // Service Principal specific settings
        if ("ActiveDirectoryServicePrincipal".equalsIgnoreCase(properties.getAuthentication())) {
            if (StringUtils.hasText(properties.getClientId()) &&
                    StringUtils.hasText(properties.getClientSecret())) {
                ds.setUser(properties.getClientId() + "@" + properties.getTenantId());
                ds.setPassword(properties.getClientSecret());
            }
        } else if ("SqlPassword".equalsIgnoreCase(properties.getAuthentication())) {
            // Traditional username/password
            if (StringUtils.hasText(properties.getUsername())) {
                ds.setUser(properties.getUsername());
                ds.setPassword(properties.getPassword());
            }
        }

        return ds;
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public ConnectionFactory connectionFactory(DataSource dataSource) {
        return new JdbcR2dbcConnectionFactory(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public org.springframework.r2dbc.core.DatabaseClient databaseClient(ConnectionFactory connectionFactory) {
        return org.springframework.r2dbc.core.DatabaseClient.create(connectionFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public R2dbcEntityTemplate r2dbcEntityTemplate(ConnectionFactory connectionFactory) {
        return new R2dbcEntityTemplate(connectionFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public ReactiveTransactionManager transactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }
}
