package io.github.abhishekchanda.jdbc;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import javax.sql.DataSource;

public class JdbcR2dbcConnectionFactory implements ConnectionFactory {

    private static final Logger log = LoggerFactory.getLogger(JdbcR2dbcConnectionFactory.class);

    private final DataSource dataSource;
    private final Scheduler scheduler;

    public JdbcR2dbcConnectionFactory(DataSource dataSource) {
        this.dataSource = dataSource;
        // Bounded elastic scheduler for blocking JDBC operations
        this.scheduler = Schedulers.newBoundedElastic(
                100,      // thread cap
                100000,   // queue size
                "jdbc-r2dbc",
                60,       // TTL seconds
                true
        );
        log.info("JdbcR2dbcConnectionFactory initialized - using JDBC bridge for Service Principal auth");
    }

    @Override
    public Publisher<? extends Connection> create() {
        log.debug("Creating new R2DBC connection from JDBC");
        return Mono.fromCallable(() -> {
            java.sql.Connection jdbcConn = dataSource.getConnection();
            return new JdbcR2dbcConnection(jdbcConn, scheduler);
        }).subscribeOn(scheduler);
    }

    @Override
    public ConnectionFactoryMetadata getMetadata() {
        return () -> "Microsoft SQL Server";
    }
}
