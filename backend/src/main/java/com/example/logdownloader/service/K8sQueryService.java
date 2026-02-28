package com.example.logdownloader.service;

import com.example.logdownloader.config.K8sClientFactory;
import com.example.logdownloader.config.K8sContoursProperties;
import com.example.logdownloader.dto.WorkloadKind;
import com.example.logdownloader.util.LabelSelectorParser;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class K8sQueryService {

    private final K8sClientFactory factory;
    private final K8sContoursProperties props;

    public K8sQueryService(K8sClientFactory factory, K8sContoursProperties props) {
        this.factory = factory;
        this.props = props;
    }

    public List<String> contours() {
        return props.getContours().keySet().stream().sorted().toList();
    }

    public List<String> namespaces(String contour) {
        try (KubernetesClient client = factory.createClient(contour)) {
            return client.namespaces().list().getItems().stream().map(Namespace::getMetadata).map(m -> m.getName()).sorted().toList();
        }
    }

    public List<String> workloads(String contour, String namespace, WorkloadKind kind) {
        try (KubernetesClient client = factory.createClient(contour)) {
            return switch (kind) {
                case Deployment -> client.apps().deployments().inNamespace(namespace).list().getItems().stream()
                        .map(d -> d.getMetadata().getName()).sorted().toList();
                case StatefulSet -> client.apps().statefulSets().inNamespace(namespace).list().getItems().stream()
                        .map(s -> s.getMetadata().getName()).sorted().toList();
            };
        }
    }

    public List<String> pods(String contour, String namespace, String selector) {
        try (KubernetesClient client = factory.createClient(contour)) {
            if (StringUtils.hasText(selector)) {
                return client.pods()
                        .inNamespace(namespace)
                        .withLabels(LabelSelectorParser.parseEqualsSelector(selector))
                        .list()
                        .getItems()
                        .stream()
                        .map(Pod::getMetadata)
                        .map(m -> m.getName())
                        .sorted()
                        .toList();
            }
            return client.pods()
                    .inNamespace(namespace)
                    .list()
                    .getItems()
                    .stream()
                    .map(Pod::getMetadata)
                    .map(m -> m.getName())
                    .sorted()
                    .toList();
        }
    }

    public List<String> containers(String contour, String namespace, String pod) {
        try (KubernetesClient client = factory.createClient(contour)) {
            var item = client.pods().inNamespace(namespace).withName(pod).get();
            return extractAllContainerNames(item);
        }
    }

    public List<String> containersForPods(String contour, String namespace, List<String> pods, String selector) {
        try (KubernetesClient client = factory.createClient(contour)) {
            List<String> targetPods = (pods != null && !pods.isEmpty()) ? pods : pods(contour, namespace, selector);
            List<String> names = new ArrayList<>();
            for (String pod : targetPods) {
                var item = client.pods().inNamespace(namespace).withName(pod).get();
                names.addAll(extractAllContainerNames(item));
            }
            return names.stream().distinct().sorted().toList();
        }
    }

    private List<String> extractAllContainerNames(Pod item) {
        if (item == null || item.getSpec() == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        if (item.getSpec().getContainers() != null) {
            names.addAll(item.getSpec().getContainers().stream().map(c -> c.getName()).toList());
        }
        if (item.getSpec().getInitContainers() != null) {
            names.addAll(item.getSpec().getInitContainers().stream().map(c -> c.getName()).toList());
        }
        if (item.getSpec().getEphemeralContainers() != null) {
            names.addAll(item.getSpec().getEphemeralContainers().stream().map(c -> c.getName()).toList());
        }
        return names.stream().distinct().sorted().toList();
    }
}
