package com.example.blockly_executor_service.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@Slf4j
@EnableConfigurationProperties
public class DataSourceConfig {

    @Value("${spring.datasource.write.url}")
    private String writeUrl;

    @Value("${spring.datasource.write.username}")
    private String writeUsername;

    @Value("${spring.datasource.write.password}")
    private String writePassword;

    @Value("${spring.datasource.read.url}")
    private String readUrl;

    @Value("${spring.datasource.read.username}")
    private String readUsername;

    @Value("${spring.datasource.read.password}")
    private String readPassword;

    // Primary DataSource - command (write)
    @Bean
    @Primary
    public DataSource writeDataSource() {
        log.info("Creating Write DataSource with URL: {}", writeUrl);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(writeUrl);
        config.setUsername(writeUsername);
        config.setPassword(writePassword);
        config.setDriverClassName("org.postgresql.Driver");

        // HikariCP settings
        config.setConnectionTimeout(30000);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setMaxLifetime(1800000);
        config.setIdleTimeout(300000);
        config.setPoolName("WritePool");
        config.setConnectionTestQuery("SELECT 1");
        config.setAutoCommit(true);

        HikariDataSource dataSource = new HikariDataSource(config);
        log.info("Write DataSource created: {}", dataSource.getPoolName());
        return dataSource;
    }

    // writeJdbcTemplate
    @Bean
    @Primary
    public JdbcTemplate writeJdbcTemplate(@Qualifier("writeDataSource") DataSource dataSource) {
        log.info("Creating Write JdbcTemplate");
        return new JdbcTemplate(dataSource);
    }

    // Secondary DataSource - query (read)
    @Bean
    public DataSource readDataSource() {
        log.info("Creating Read DataSource with URL: {}", readUrl);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(readUrl);
        config.setUsername(readUsername);
        config.setPassword(readPassword);
        config.setDriverClassName("org.postgresql.Driver");

        // HikariCP settings
        config.setConnectionTimeout(30000);
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setMaxLifetime(1800000);
        config.setIdleTimeout(300000);
        config.setPoolName("ReadPool");
        config.setConnectionTestQuery("SELECT 1");
        config.setAutoCommit(true);
        config.setReadOnly(true);

        HikariDataSource dataSource = new HikariDataSource(config);
        log.info("Read DataSource created: {}", dataSource.getPoolName());
        return dataSource;
    }

    // readJdbcTemplate
    @Bean
    public JdbcTemplate readJdbcTemplate(@Qualifier("readDataSource") DataSource dataSource) {
        log.info("Creating Read JdbcTemplate");
        return new JdbcTemplate(dataSource);
    }

    // EntityManagerFactory для JPA
    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("writeDataSource") DataSource dataSource) {
        log.info("Creating EntityManagerFactory with Write DataSource");
        return builder
                .dataSource(dataSource)
                .packages("com.example.blockly_executor_service.model")
                .persistenceUnit("default")
                .build();
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(
            @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory) {
        log.info("Creating Transaction Manager");
        return new JpaTransactionManager(entityManagerFactory);
    }

}
