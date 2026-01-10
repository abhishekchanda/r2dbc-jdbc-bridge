package io.github.abhishekchanda.jdbc;

import io.r2dbc.spi.Result;
import io.r2dbc.spi.Statement;
import org.reactivestreams.Publisher;
import org.springframework.lang.NonNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JdbcR2dbcStatement implements Statement {

    private static final Pattern NAMED_PARAM_PATTERN = Pattern.compile("[@:]([a-zA-Z0-9_]+)");

    private final java.sql.Connection connection;
    private final String originalSql;
    private final String jdbcSql;
    private final List<String> parameterNames;
    private final Scheduler scheduler;
    private final List<Binding> bindings = new ArrayList<>();
    private Binding currentBinding;
    private final Map<String, List<Integer>> parameterPositions;

    public JdbcR2dbcStatement(java.sql.Connection connection, String sql, Scheduler scheduler) {
        this.connection = connection;
        this.originalSql = sql;
        this.scheduler = scheduler;
        this.currentBinding = new Binding();

        ParseResult parseResult = parseNamedParameters(sql);
        this.jdbcSql = parseResult.jdbcSql;
        this.parameterNames = parseResult.parameterNames;
        this.parameterPositions = parseResult.parameterPositions;
    }

    @Override
    public Statement add() {
        bindings.add(currentBinding);
        currentBinding = new Binding();
        return this;
    }

    @Override
    public Statement bind(int index, Object value) {
        currentBinding.addPositional(index, value);
        return this;
    }

    @Override
    public Statement bind(String name, Object value) {
        currentBinding.addNamed(name, value);
        return this;
    }

    @Override
    public Statement bindNull(int index, Class<?> type) {
        currentBinding.addPositional(index, null);
        return this;
    }

    @Override
    public Statement bindNull(String name, Class<?> type) {
        currentBinding.addNamed(name, null);
        return this;
    }

    @Override
    @NonNull
    public Publisher<? extends Result> execute() {
        // Add current binding if not empty
        if (!currentBinding.isEmpty()) {
            bindings.add(currentBinding);
        }

        // If no bindings, add empty one
        if (bindings.isEmpty()) {
            bindings.add(new Binding());
        }

        return Flux.fromIterable(bindings)
                .flatMap(binding -> Mono.fromCallable(() -> {
                    PreparedStatement stmt = null;
                    try {
                        stmt = connection.prepareStatement(jdbcSql);
                        binding.bind(stmt, parameterNames, parameterPositions);

                        if (stmt.execute()) {
                            ResultSet rs = stmt.getResultSet();
                            return new JdbcR2dbcResult(rs, stmt, scheduler);
                        } else {
                            int updateCount = stmt.getUpdateCount();
                            return new JdbcR2dbcResult(updateCount, stmt, scheduler);
                        }
                    } catch (SQLException e) {
                        if (stmt != null) {
                            try {
                                stmt.close();
                            } catch (SQLException ex) {
                                // Ignore
                            }
                        }
                        throw new RuntimeException("Failed to execute statement", e);
                    }
                }).subscribeOn(scheduler));
    }

    @Override
    @NonNull
    public Statement returnGeneratedValues(@NonNull String... columns) {
        return this;
    }

    @Override
    @NonNull
    public Statement fetchSize(int rows) {
        return this;
    }

    private static ParseResult parseNamedParameters(String sql) {
        List<String> parameterNames = new ArrayList<>();
        Map<String, List<Integer>> parameterPositions = new HashMap<>();
        Matcher matcher = NAMED_PARAM_PATTERN.matcher(sql);

        StringBuffer sb = new StringBuffer();
        int position = 0;
        while (matcher.find()) {
            String paramName = matcher.group(1);
            parameterNames.add(paramName);

            // Track all positions where this parameter appears
            parameterPositions.computeIfAbsent(paramName, k -> new ArrayList<>()).add(position);
            position++;

            matcher.appendReplacement(sb, "?");
        }
        matcher.appendTail(sb);

        return new ParseResult(sb.toString(), parameterNames, parameterPositions);
    }

    private static class ParseResult {
        final String jdbcSql;
        final List<String> parameterNames;
        final Map<String, List<Integer>> parameterPositions;

        ParseResult(String jdbcSql, List<String> parameterNames, Map<String, List<Integer>> parameterPositions) {
            this.jdbcSql = jdbcSql;
            this.parameterNames = parameterNames;
            this.parameterPositions = parameterPositions;
        }
    }

    private static class Binding {
        private final Map<Integer, Object> positionalValues = new HashMap<>();
        private final Map<String, Object> namedValues = new HashMap<>();

        void addPositional(int index, Object value) {
            positionalValues.put(index, value);
        }

        void addNamed(String name, Object value) {
            namedValues.put(name, value);
        }

        boolean isEmpty() {
            return positionalValues.isEmpty() && namedValues.isEmpty();
        }

        void bind(PreparedStatement stmt, List<String> parameterNames, Map<String, List<Integer>> parameterPositions) throws SQLException {
            if (!namedValues.isEmpty()) {
                // For each unique parameter name, bind to all its positions
                for (Map.Entry<String, Object> entry : namedValues.entrySet()) {
                    String paramName = entry.getKey();
                    Object value = entry.getValue();

                    List<Integer> positions = parameterPositions.get(paramName);
                    if (positions != null) {
                        for (Integer position : positions) {
                            stmt.setObject(position + 1, value); // JDBC is 1-indexed
                        }
                    }
                }
            } else {
                // Use positional parameters directly
                for (Map.Entry<Integer, Object> entry : positionalValues.entrySet()) {
                    stmt.setObject(entry.getKey() + 1, entry.getValue()); // JDBC is 1-indexed
                }
            }
        }
    }
}
