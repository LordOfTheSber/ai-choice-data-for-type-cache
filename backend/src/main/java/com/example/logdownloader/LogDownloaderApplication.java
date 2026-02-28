package com.example.logdownloader;

import com.example.logdownloader.config.K8sContoursProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(K8sContoursProperties.class)
public class LogDownloaderApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogDownloaderApplication.class, args);
    }
}
