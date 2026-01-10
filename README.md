# R2DBC JDBC Bridge

A Spring Boot library that bridges traditional JDBC drivers to R2DBC interfaces, enabling reactive programming with databases that lack native R2DBC drivers. Specifically designed to support Azure AD authentication (Managed Identity and Service Principal) for SQL Server.

[![Java 17](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.java.net/projects/jdk/17/)
[![Spring Boot 3.4.1](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## 🎯 Overview

This library solves a critical problem: **using Azure AD authentication with SQL Server in reactive Spring Boot applications**. The native `r2dbc-mssql` driver doesn't support Azure AD Service Principal authentication, forcing developers to choose between reactive programming and modern authentication methods.

### Key Features

- ✅ **Plug-and-Play** - Zero code changes required in your existing reactive application
- ✅ **Azure AD Support** - Full support for Managed Identity (MSI) and Service Principal authentication
- ✅ **Spring Boot Auto-Configuration** - Automatically configures when added as a dependency
- ✅ **Named Parameters** - Supports both `:paramName` (R2DBC) and `@paramName` (SQL Server) syntax
- ✅ **Duplicate Parameter Handling** - Correctly binds parameters that appear multiple times in SQL
- ✅ **Transaction Management** - Full reactive transaction support
- ✅ **Existing Code Compatible** - Works with `ReactiveCrudRepository`, `R2dbcEntityTemplate` and `DatabaseClient`
- ✅ **Type-Safe Configuration** - Configuration properties with IDE autocomplete

## 📋 Table of Contents

- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Authentication Methods](#authentication-methods)
- [Usage Examples](#usage-examples)
- [Building from Source](#building-from-source)
- [How It Works](#how-it-works)
- [Troubleshooting](#troubleshooting)
- [Performance Considerations](#performance-considerations)
- [Contributing](#contributing)
- [License](#license)

## 📦 Prerequisites

- Java 17 or higher
- Spring Boot 3.4.1 or higher
- Maven 3.6+ or Gradle 7+
- SQL Server database (Azure SQL Database or SQL Server 2016+)
- For Azure AD: Appropriate Azure AD configuration

## 🚀 Installation

### Maven

Add the dependency to your `pom.xml`:

```xml
<dependencies>
    <!-- R2DBC JDBC Bridge -->
    <dependency>
        <groupId>io.github.abhishekchanda</groupId>
        <artifactId>r2dbc-jdbc-bridge</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- SQL Server JDBC Driver -->
    <dependency>
        <groupId>com.microsoft.sqlserver</groupId>
        <artifactId>mssql-jdbc</artifactId>
        <version>13.2.1.jre11</version>
    </dependency>
    
    <!-- Microsoft Authentication Library (for Azure AD) -->
    <dependency>
        <groupId>com.microsoft.azure</groupId>
        <artifactId>msal4j</artifactId>
        <version>1.23.1</version>
    </dependency>
</dependencies>
```

### Gradle

Add to your `build.gradle`:

```gradle
dependencies {
    implementation 'io.github.abhishekchanda:r2dbc-jdbc-bridge:1.0.0'
    implementation 'com.microsoft.sqlserver:mssql-jdbc:13.2.1.jre11'
    implementation 'com.microsoft.azure:msal4j:1.23.1'
}
```

### Local Installation

If the library isn't published to Maven Central yet:

```bash
# Clone the repository
git clone https://github.com/abhishekchanda/r2dbc-jdbc-bridge.git
cd r2dbc-jdbc-bridge

# Install to local Maven repository
mvn clean install

# Then add the dependency to your project as shown above
```

## ⚡ Quick Start

### 1. Add the Dependency

Add the library to your project as shown in [Installation](#installation).

### 2. Configure Your Database

Add to `application.properties`:

```properties
# Database connection
r2dbc.jdbc.server=your-server.database.windows.net
r2dbc.jdbc.database=your-database

# Use Managed Identity (simplest for Azure)
r2dbc.jdbc.authentication=ActiveDirectoryMSI

# Disable default R2DBC auto-configuration
spring.autoconfigure.exclude=\
  org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration,\
  org.springframework.boot.autoconfigure.r2dbc.R2dbcDataAutoConfiguration
```

### 3. Use Your Existing Reactive Code

No code changes needed! Your existing repositories and services work automatically:

```java
@Repository
public interface UserRepository extends ReactiveCrudRepository<User, Long> {
    Flux<User> findByName(String name);
}

@Service
public class UserService {
    private final UserRepository userRepository;
    
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    public Mono<User> findUser(Long id) {
        return userRepository.findById(id);
    }
    
    @Transactional
    public Mono<User> createUser(User user) {
        return userRepository.save(user);
    }
}
```

That's it! 🎉

## ⚙️ Configuration

### Configuration Properties

All properties are prefixed with `r2dbc.jdbc`:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable/disable the bridge |
| `server` | String | - | SQL Server hostname (e.g., `server.database.windows.net`) |
| `database` | String | - | Database name |
| `authentication` | String | `ActiveDirectoryMSI` | Authentication method (see below) |
| `client-id` | String | - | Azure AD application (client) ID |
| `client-secret` | String | - | Azure AD client secret |
| `tenant-id` | String | - | Azure AD tenant ID |
| `username` | String | - | SQL username (for SQL authentication) |
| `password` | String | - | SQL password (for SQL authentication) |
| `encrypt` | boolean | `true` | Enable TLS encryption |
| `trust-server-certificate` | boolean | `false` | Trust server certificate without validation |
| `connection-timeout` | int | `30` | Connection timeout in seconds |
| `login-timeout` | int | `30` | Login timeout in seconds |

### Environment-Specific Configuration

#### Development (application-dev.properties)

```properties
# Use Service Principal with secrets from environment
r2dbc.jdbc.server=dev-server.database.windows.net
r2dbc.jdbc.database=dev-database
r2dbc.jdbc.authentication=ActiveDirectoryServicePrincipal
r2dbc.jdbc.client-id=${AZURE_CLIENT_ID}
r2dbc.jdbc.client-secret=${AZURE_CLIENT_SECRET}
r2dbc.jdbc.tenant-id=${AZURE_TENANT_ID}
```

#### Production (application-prod.properties)

```properties
# Use Managed Identity in production
r2dbc.jdbc.server=prod-server.database.windows.net
r2dbc.jdbc.database=prod-database
r2dbc.jdbc.authentication=ActiveDirectoryMSI
```

#### Local Development (application-local.properties)

```properties
# Use SQL authentication for local development
r2dbc.jdbc.server=localhost
r2dbc.jdbc.database=testdb
r2dbc.jdbc.authentication=SqlPassword
r2dbc.jdbc.username=sa
r2dbc.jdbc.password=YourStrong@Password
r2dbc.jdbc.trust-server-certificate=true
```

## 🔐 Authentication Methods

### 1. Managed Identity (MSI) - Recommended for Azure

Best for applications running in Azure (App Service, Container Apps, AKS, VMs).

```properties
r2dbc.jdbc.authentication=ActiveDirectoryMSI
```

**Setup:**
1. Enable Managed Identity on your Azure resource
2. Grant database permissions:
   ```sql
   CREATE USER [your-app-name] FROM EXTERNAL PROVIDER;
   ALTER ROLE db_datareader ADD MEMBER [your-app-name];
   ALTER ROLE db_datawriter ADD MEMBER [your-app-name];
   ```

### 2. Service Principal with Client Secret

Best for applications running outside Azure or for service-to-service authentication.

```properties
r2dbc.jdbc.authentication=ActiveDirectoryServicePrincipal
r2dbc.jdbc.client-id=your-client-id-guid
r2dbc.jdbc.client-secret=your-client-secret
r2dbc.jdbc.tenant-id=your-tenant-id-guid
```

**Setup:**
1. Create an App Registration in Azure AD
2. Create a client secret
3. Grant database permissions:
   ```sql
   CREATE USER [your-app-name] FROM EXTERNAL PROVIDER;
   ALTER ROLE db_datareader ADD MEMBER [your-app-name];
   ALTER ROLE db_datawriter ADD MEMBER [your-app-name];
   ```

### 3. SQL Server Authentication

Traditional username/password authentication.

```properties
r2dbc.jdbc.authentication=SqlPassword
r2dbc.jdbc.username=your-username
r2dbc.jdbc.password=your-password
```

### 4. Azure AD Password (Interactive)

Not recommended for production applications.

```properties
r2dbc.jdbc.authentication=ActiveDirectoryPassword
r2dbc.jdbc.username=user@domain.com
r2dbc.jdbc.password=user-password
```

## 💡 Usage Examples

### Basic CRUD Operations

```java
@Service
public class ProductService {
    
    private final ProductRepository repository;
    
    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }
    
    // Find by ID
    public Mono<Product> findById(Long id) {
        return repository.findById(id);
    }
    
    // Find all
    public Flux<Product> findAll() {
        return repository.findAll();
    }
    
    // Create
    @Transactional
    public Mono<Product> create(Product product) {
        return repository.save(product);
    }
    
    // Update
    @Transactional
    public Mono<Product> update(Long id, Product updates) {
        return repository.findById(id)
            .flatMap(existing -> {
                existing.setName(updates.getName());
                existing.setPrice(updates.getPrice());
                return repository.save(existing);
            });
    }
    
    // Delete
    @Transactional
    public Mono<Void> delete(Long id) {
        return repository.deleteById(id);
    }
}
```

### Custom Queries with R2dbcEntityTemplate

```java
@Service
public class UserService {
    
    private final R2dbcEntityTemplate template;
    
    public UserService(R2dbcEntityTemplate template) {
        this.template = template;
    }
    
    // Query with named parameters
    public Flux<User> findByAgeRange(int minAge, int maxAge) {
        String sql = "SELECT * FROM users WHERE age BETWEEN :minAge AND :maxAge";
        return template.getDatabaseClient()
            .sql(sql)
            .bind("minAge", minAge)
            .bind("maxAge", maxAge)
            .map((row, metadata) -> {
                User user = new User();
                user.setId(row.get("id", Long.class));
                user.setName(row.get("name", String.class));
                user.setAge(row.get("age", Integer.class));
                return user;
            })
            .all();
    }
    
    // Complex query with SQL Server parameters
    public Flux<Order> findOrdersByStatus(List<String> statuses) {
        String sql = """
            SELECT o.*, u.name as user_name
            FROM orders o
            JOIN users u ON o.user_id = u.id
            WHERE o.status IN (@P0_statuses)
            ORDER BY o.created_at DESC
            """;
        
        return template.getDatabaseClient()
            .sql(sql)
            .bind("P0_statuses", String.join(",", statuses))
            .map((row, metadata) -> {
                Order order = new Order();
                order.setId(row.get("id", Long.class));
                order.setStatus(row.get("status", String.class));
                order.setUserName(row.get("user_name", String.class));
                return order;
            })
            .all();
    }
}
```

### Transactions

```java
@Service
public class OrderService {
    
    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    
    @Transactional
    public Mono<Order> placeOrder(Order order) {
        return orderRepository.save(order)
            .flatMap(savedOrder -> 
                inventoryRepository.decrementStock(order.getProductId(), order.getQuantity())
                    .thenReturn(savedOrder)
            );
    }
    
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Mono<Void> transferFunds(Long fromAccount, Long toAccount, BigDecimal amount) {
        return accountRepository.findById(fromAccount)
            .flatMap(from -> {
                from.setBalance(from.getBalance().subtract(amount));
                return accountRepository.save(from);
            })
            .flatMap(saved -> accountRepository.findById(toAccount))
            .flatMap(to -> {
                to.setBalance(to.getBalance().add(amount));
                return accountRepository.save(to);
            })
            .then();
    }
}
```

### Repository Methods

```java
@Repository
public interface ProductRepository extends ReactiveCrudRepository<Product, Long> {
    
    // Derived query methods
    Flux<Product> findByName(String name);
    Flux<Product> findByPriceLessThan(BigDecimal price);
    Flux<Product> findByCategoryOrderByPriceDesc(String category);
    Mono<Long> countByCategory(String category);
    Mono<Boolean> existsByName(String name);
    
    // Custom query with @Query annotation
    @Query("SELECT * FROM products WHERE price BETWEEN :min AND :max")
    Flux<Product> findByPriceRange(@Param("min") BigDecimal min, @Param("max") BigDecimal max);
    
    // Native SQL with SQL Server parameters
    @Query("SELECT * FROM products WHERE category IN (@P0_categories)")
    Flux<Product> findByCategories(@Param("P0_categories") String categories);
}
```

## 🔨 Building from Source

### Prerequisites for Building

- JDK 17 or higher
- Maven 3.6+
- Git

### Build Steps

```bash
# Clone the repository
git clone https://github.com/abhishekchanda/r2dbc-jdbc-bridge.git
cd r2dbc-jdbc-bridge

# Build and run tests
mvn clean test

# Build without tests
mvn clean package -DskipTests

# Install to local Maven repository
mvn clean install

# Generate JavaDoc
mvn javadoc:javadoc

# Build with all artifacts (sources, javadoc)
mvn clean install -P release
```

### Project Structure

```
r2dbc-jdbc-bridge/
├── pom.xml
├── README.md
├── LICENSE
└── src/
    ├── main/
    │   ├── java/
    │   │   └── io/
    │   │       └── github/
    │   │           └── abhishekchanda/
    │   │               └── jdbc/
    │   │                   ├── JdbcR2dbcConnectionFactory.java
    │   │                   ├── JdbcR2dbcConnection.java
    │   │                   ├── JdbcR2dbcStatement.java
    │   │                   ├── JdbcR2dbcResult.java
    │   │                   ├── JdbcR2dbcRow.java
    │   │                   ├── JdbcR2dbcRowMetadata.java
    │   │                   └── autoconfigure/
    │   │                       ├── JdbcR2dbcAutoConfiguration.java
    │   │                       └── JdbcR2dbcProperties.java
    │   └── resources/
    │       └── META-INF/
    │           └── spring/
    │               └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── test/
        └── java/
            └── io/
                └── github/
                    └── abhishekchanda/
                        └── jdbc/
                            └── ... (test classes)
```

## 🔍 How It Works (T-SQL Execution)

The library acts as a **transparent bridge** between R2DBC and JDBC. Below is how different T-SQL operations are handled for SQL Server:

### 1. SELECT Queries
*   **Without Parameters:** The SQL is passed directly to a JDBC `PreparedStatement`. Results are lazily mapped from the `ResultSet` to a reactive `Flux<Row>`.
*   **With Parameters:** The library parses both R2DBC style (`:param`) and SQL Server style (`@param`) named parameters using regex. These are converted to JDBC positional placeholders (`?`). 
    *   *Input:* `SELECT * FROM Users WHERE id = :id`
    *   *JDBC Execution:* `SELECT * FROM Users WHERE id = ?`

### 2. DDL Operations (CREATE, ALTER, DROP)
Operations like `CREATE TABLE`, `ALTER TABLE`, or `DROP INDEX` are executed as standard SQL statements. The library executes the statement on a background worker thread and returns an update count (usually `0` for DDL) or completes the `Mono<Void>`.

### 3. Stored Procedures
*   **Creation/Modification:** `CREATE PROCEDURE` or `ALTER PROCEDURE` blocks are treated as single SQL statements and passed directly to the driver.
*   **Execution:** Procedures are executed using the `EXEC` syntax (e.g., `EXEC GetUser @Id = :id`). 
*   **Limitation & Workaround:** The library uses the `PreparedStatement` interface rather than `CallableStatement`. This means **OUTPUT parameters are not directly supported**.
    *   **Recommended Workaround:** Modify your stored procedure to return a result set instead of using output parameters.

    **Instead of this (Unsupported):**
    ```sql
    CREATE PROCEDURE GetUserCount @Count INT OUTPUT
    AS
    BEGIN
        SELECT @Count = COUNT(*) FROM Users
    END
    ```

    **Use this (Supported):**
    ```sql
    CREATE PROCEDURE GetUserCount
    AS
    BEGIN
        SELECT COUNT(*) AS UserCount FROM Users
    END
    ```

    **Java Usage:**
    ```java
    databaseClient.sql("EXEC GetUserCount")
        .map((row, metadata) -> row.get("UserCount", Integer.class))
        .one();
    ```

### 4. Threading Model
To ensure the Reactive Event Loop is never blocked:
1.  All JDBC calls are wrapped in `Mono.fromCallable` or `Flux.create`.
2.  Execution is offloaded to a `BoundedElastic` scheduler (named `jdbc-r2dbc`).
3.  The results are streamed back to the event loop once the blocking driver returns data.

## 🔍 How It Works (Architecture)

The library bridges JDBC and R2DBC by:

1. **Wrapping JDBC Connections** - Creates R2DBC `Connection` instances that wrap JDBC connections
2. **Reactive Scheduling** - Executes blocking JDBC operations on a bounded elastic scheduler
3. **Parameter Translation** - Converts R2DBC named parameters (`:name`) and SQL Server parameters (`@name`) to JDBC positional parameters (`?`)
4. **Result Mapping** - Streams JDBC `ResultSet` rows as reactive `Flux` emissions
5. **Transaction Management** - Maps R2DBC transaction calls to JDBC transaction operations

### Key Components

```
User Code (Reactive)
       ↓
Spring Data R2DBC
       ↓
R2DBC SPI Interface
       ↓
JdbcR2dbcConnectionFactory (Bridge)
       ↓
JDBC Driver (mssql-jdbc)
       ↓
SQL Server Database
```

### Thread Model

- **Event Loop Threads**: Handle reactive pipeline operations
- **Elastic Scheduler Threads**: Execute blocking JDBC operations
- **Bounded Pool**: Default 100 threads, 100,000 queue size, 60s TTL

### Performance Characteristics

While this bridge enables reactive programming with JDBC drivers, understand that:

- ✅ **Non-blocking at application level** - Event loop threads never block
- ✅ **Backpressure support** - Reactive streams handle backpressure correctly
- ⚠️ **Blocking at driver level** - JDBC operations still block (on elastic threads)
- ⚠️ **Thread overhead** - Context switching between event loop and elastic threads

For true non-blocking I/O, use a native R2DBC driver when available.

## 🐛 Troubleshooting

### Common Issues

#### 1. NoSuchMethodError: executorService()

**Error:**
```
NoSuchMethodError: 'com.microsoft.aad.msal4j.AbstractClientApplicationBase$Builder 
com.microsoft.aad.msal4j.ConfidentialClientApplication$Builder.executorService(...)'
```

**Solution:**
Add explicit MSAL4J dependency:
```xml
<dependency>
    <groupId>com.microsoft.azure</groupId>
    <artifactId>msal4j</artifactId>
    <version>1.17.2</version>
</dependency>
```

#### 2. Must declare the scalar variable "@P0_..."

**Error:**
```
SQLServerException: Must declare the scalar variable "@P0_groupTypeIds"
```

**Solution:**
This is fixed in version 1.0.0+. The library now correctly handles:
- Duplicate parameter names (e.g., `@P1_deptIds` appearing multiple times)
- Both R2DBC (`:param`) and SQL Server (`@param`) parameter syntax

#### 3. Named parameters not supported

**Error:**
```
UnsupportedOperationException: Named parameters not supported
```

**Solution:**
Upgrade to version 1.0.0+ which includes full named parameter support.

#### 4. Connection Refused / Timeout

**Symptoms:**
- Can't connect to database
- Timeout errors

**Checklist:**
- ✅ Verify server name (e.g., `server.database.windows.net`)
- ✅ Check firewall rules (Azure SQL firewall, NSG)
- ✅ Verify database exists
- ✅ Check authentication credentials
- ✅ Ensure Managed Identity is assigned and has permissions

#### 5. Authentication Failed

**For Managed Identity:**
```sql
-- Verify the user exists in the database
SELECT name, type_desc FROM sys.database_principals 
WHERE name = 'your-app-name';

-- Grant permissions
ALTER ROLE db_datareader ADD MEMBER [your-app-name];
ALTER ROLE db_datawriter ADD MEMBER [your-app-name];
```

**For Service Principal:**
- Verify client ID, secret, and tenant ID are correct
- Ensure app registration has appropriate API permissions
- Check that the service principal has database access

### Debugging

Enable debug logging:

```properties
# Application logging
logging.level.io.github.abhishekchanda.jdbc=DEBUG
logging.level.org.springframework.r2dbc=DEBUG
logging.level.org.springframework.data.r2dbc=DEBUG

# SQL Server JDBC driver logging
logging.level.com.microsoft.sqlserver.jdbc=DEBUG
```

### Getting Help

1. Check [GitHub Issues](https://github.com/abhishekchanda/r2dbc-jdbc-bridge/issues)
2. Review [Troubleshooting Guide](https://github.com/abhishekchanda/r2dbc-jdbc-bridge/wiki/Troubleshooting)
3. Post on [Stack Overflow](https://stackoverflow.com) with tags: `r2dbc`, `spring-data-r2dbc`, `azure-sql-database`
4. Contact maintainers

## ⚡ Performance Considerations

### Thread Pool Configuration

Adjust the elastic scheduler for your workload:

```java
@Configuration
public class CustomSchedulerConfig {
    
    @Bean
    public JdbcR2dbcConnectionFactory customConnectionFactory(DataSource dataSource) {
        return new JdbcR2dbcConnectionFactory(dataSource, 
            Schedulers.newBoundedElastic(
                200,      // thread cap (increase for high concurrency)
                200000,   // queue size
                "jdbc-r2dbc",
                120,      // TTL seconds
                true
            )
        );
    }
}
```

### Connection Pooling

Consider adding HikariCP for connection pooling:

```xml
<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
</dependency>
```

```java
@Bean
public DataSource dataSource(JdbcR2dbcProperties properties) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:sqlserver://" + properties.getServer() + 
                      ";databaseName=" + properties.getDatabase());
    config.setMaximumPoolSize(50);
    config.setMinimumIdle(10);
    config.setConnectionTimeout(30000);
    // ... other settings
    
    return new HikariDataSource(config);
}
```

### Monitoring

Monitor key metrics:
- Thread pool utilization
- Connection pool statistics
- Query execution times
- Backpressure signals

Use Spring Boot Actuator:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

### Development Setup

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass: `mvn test`
6. Commit your changes: `git commit -m 'Add amazing feature'`
7. Push to the branch: `git push origin feature/amazing-feature`
8. Open a Pull Request

### Code Style

- Follow standard Java conventions
- Use meaningful variable/method names
- Add JavaDoc for public APIs
- Write unit tests for new features
- Keep methods focused and concise

### Reporting Issues

When reporting issues, please include:
- Library version
- Spring Boot version
- Java version
- Database version
- Full stack trace
- Minimal reproducible example

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Spring Data R2DBC team for the reactive data access framework
- Microsoft for the SQL Server JDBC driver and MSAL4J
- R2DBC community for the reactive database connectivity specification

## 📞 Contact

- **Project Homepage**: https://github.com/abhishekchanda/r2dbc-jdbc-bridge
- **Issue Tracker**: https://github.com/abhishekchanda/r2dbc-jdbc-bridge/issues
- **Discussions**: https://github.com/abhishekchanda/r2dbc-jdbc-bridge/discussions

## 🗺️ Roadmap

- [ ] Support for additional JDBC drivers (PostgreSQL, MySQL, Oracle)
- [ ] Performance optimizations for result streaming
- [ ] Enhanced connection pooling integration
- [ ] Metrics and observability improvements
- [ ] Native GraalVM support
- [ ] Batch operation optimizations

---

**Made with ❤️ by the R2DBC JDBC Bridge Team**