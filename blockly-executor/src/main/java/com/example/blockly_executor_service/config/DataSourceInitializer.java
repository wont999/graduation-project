package com.example.blockly_executor_service.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataSourceInitializer implements ApplicationRunner {

    @Qualifier("readDataSource")
    private final DataSource readDataSource;

    @Override
    public void run(ApplicationArguments args) {
        try {
            readDataSource.getConnection().close();
            log.info("Read DataSource initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize read DataSource", e);
            throw new RuntimeException("Failed to initialize read DataSource", e);
        }
    }
}
