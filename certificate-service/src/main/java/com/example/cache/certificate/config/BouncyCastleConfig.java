package com.example.cache.certificate.config;

import java.security.Security;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class BouncyCastleConfig {

    public BouncyCastleConfig() {
        registerProvider();
    }

    private void registerProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) != null) {
            return;
        }
        Security.addProvider(new BouncyCastleProvider());
        log.info("Registered BouncyCastle security provider");
    }
}
