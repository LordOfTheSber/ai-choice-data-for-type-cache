package com.example.cache.balancer.client;

import com.example.cache.balancer.config.MasterClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class MasterClientConfig {

    @Bean
    public RestClient masterRestClient(MasterClientProperties properties,
                                       RestClient.Builder builder) {
        return builder
            .build();
    }
}
