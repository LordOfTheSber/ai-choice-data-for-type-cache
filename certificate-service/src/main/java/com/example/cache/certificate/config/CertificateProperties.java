package com.example.cache.certificate.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "certificate")
public class CertificateProperties {

    @NotNull
    private Path caDirectory;

    @NotBlank
    private String caCertificateFile;

    @NotBlank
    private String caPrivateKeyFile;

    @NotBlank
    private String caPrivateKeyPassword;

    @NotNull
    private Duration issuedCertificateTtl;

    @NotEmpty
    private Set<String> trustedServiceIds = new LinkedHashSet<>();

    @Min(2048)
    private int caKeySize = 3072;

    @Min(2048)
    private int issuedKeySize = 2048;

    @NotBlank
    private String signatureAlgorithm = "SHA256withRSA";

    public char[] getCaPrivateKeyPasswordChars() {
        return caPrivateKeyPassword.toCharArray();
    }

    public Path caCertificatePath() {
        return caDirectory.resolve(caCertificateFile);
    }

    public Path caPrivateKeyPath() {
        return caDirectory.resolve(caPrivateKeyFile);
    }
}
