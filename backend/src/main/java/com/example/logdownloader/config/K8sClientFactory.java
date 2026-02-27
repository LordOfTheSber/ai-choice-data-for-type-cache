package com.example.logdownloader.config;

import com.example.logdownloader.error.ApiException;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class K8sClientFactory {

    private final K8sContoursProperties properties;

    public K8sClientFactory(K8sContoursProperties properties) {
        this.properties = properties;
    }

    public KubernetesClient createClient(String contour) {
        return createByContour(contour, false);
    }

    public KubernetesClient createClientForMaster(String contour) {
        return createByContour(contour, true);
    }

    public KubernetesClient createFallbackClient() {
        return new DefaultKubernetesClient();
    }

    private KubernetesClient createByContour(String contour, boolean master) {
        if (!StringUtils.hasText(contour)) {
            return createFallbackClient();
        }
        var contourConfig = properties.getContours().get(contour);
        if (contourConfig == null) {
            throw new ApiException(404, "Unknown contour: " + contour);
        }
        String token = master ? contourConfig.getMasterK8sToken() : contourConfig.getTestsK8sToken();
        Config config = new ConfigBuilder()
                .withMasterUrl(contourConfig.getTestsK8sUrl())
                .withOauthToken(token)
                .withTrustCerts(properties.isSkipTlsVerify())
                .withDisableHostnameVerification(properties.isSkipTlsVerify())
                .build();
        return new DefaultKubernetesClient(config);
    }
}
