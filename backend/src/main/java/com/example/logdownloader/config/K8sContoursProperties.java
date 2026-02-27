package com.example.logdownloader.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "app.k8s")
public class K8sContoursProperties {

    private boolean skipTlsVerify = true;
    private Map<String, ContourConfig> contours = new HashMap<>();

    public boolean isSkipTlsVerify() {
        return skipTlsVerify;
    }

    public void setSkipTlsVerify(boolean skipTlsVerify) {
        this.skipTlsVerify = skipTlsVerify;
    }

    public Map<String, ContourConfig> getContours() {
        return contours;
    }

    public void setContours(Map<String, ContourConfig> contours) {
        this.contours = contours;
    }

    public static class ContourConfig {
        @NotBlank
        private String testsK8sUrl;
        @NotBlank
        private String testsK8sToken;
        @NotBlank
        private String masterK8sToken;

        public String getTestsK8sUrl() {
            return testsK8sUrl;
        }

        public void setTestsK8sUrl(String testsK8sUrl) {
            this.testsK8sUrl = testsK8sUrl;
        }

        public String getTestsK8sToken() {
            return testsK8sToken;
        }

        public void setTestsK8sToken(String testsK8sToken) {
            this.testsK8sToken = testsK8sToken;
        }

        public String getMasterK8sToken() {
            return masterK8sToken;
        }

        public void setMasterK8sToken(String masterK8sToken) {
            this.masterK8sToken = masterK8sToken;
        }
    }
}
