package com.bank.fd.api;

import com.bank.fd.api.controller.FdV1ContractController;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the importable Postman demo collection agrees with the implemented
 * {@code /fd/v1} contract: every request maps to a controller-implemented
 * route, the demo-critical operations are covered, no success step points at
 * a still-501 placeholder, and no secrets are embedded.
 *
 * <p>Docker-free file/reflection test; runs in milliseconds.
 */
class PostmanCollectionConsistencyTest {

    private static final String COLLECTION_RELATIVE = "postman/fd-lifecycle.postman_collection.json";

    /** Record for one Postman request: HTTP method plus raw URL template. */
    private record CollectionRequest(String name, String method, String rawUrl) {
    }

    static Path locateCollection() {
        Path workingDir = Paths.get("").toAbsolutePath();
        List<Path> candidates = List.of(
                workingDir.resolve(COLLECTION_RELATIVE),
                workingDir.resolve("BACKEND").resolve(COLLECTION_RELATIVE),
                workingDir.getParent() == null ? null
                        : workingDir.getParent().resolve("BACKEND").resolve(COLLECTION_RELATIVE));
        for (Path candidate : candidates) {
            if (candidate != null && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("Postman collection not found; tried from " + workingDir);
    }

    static List<CollectionRequest> collectRequests(JsonNode node) {
        List<CollectionRequest> requests = new ArrayList<>();
        collectInto(node.path("item"), requests);
        return requests;
    }

    private static void collectInto(JsonNode items, List<CollectionRequest> sink) {
        if (!items.isArray()) {
            return;
        }
        for (JsonNode entry : items) {
            if (entry.has("request")) {
                JsonNode request = entry.get("request");
                String name = entry.path("name").asText("(unnamed)");
                String method;
                String rawUrl;
                if (request.isTextual()) {
                    method = "GET";
                    rawUrl = request.asText();
                } else {
                    method = request.path("method").asText("GET").toUpperCase();
                    JsonNode url = request.path("url");
                    rawUrl = url.isTextual() ? url.asText() : url.path("raw").asText("");
                }
                sink.add(new CollectionRequest(name, method, rawUrl));
            }
            if (entry.has("item")) {
                collectInto(entry.get("item"), sink);
            }
        }
    }

    /**
     * Normalizes a Postman URL template to a comparable route path:
     * strips the {{baseUrl}} variable (which already contains /fd/v1),
     * replaces remaining {{variables}} with "1", and drops query strings.
     */
    static String normalizeCollectionPath(String rawUrl) {
        String path = rawUrl.replace("{{baseUrl}}", "/fd/v1");
        path = path.replaceAll("\\{\\{[^}]+\\}\\}", "1");
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        return path.replaceAll("/+", "/");
    }

    /**
     * Builds the set of implemented routes ("METHOD /fd/v1/...") by
     * reflecting over mapping annotations on FdV1ContractController,
     * with path variables normalized to "1" for comparison.
     * Also returns the method-name lookup for operation coverage.
     */
    static Map<String, String> implementedRoutesByMethodName() {
        Map<String, String> routes = new HashMap<>();
        String prefix = FdV1ContractController.class.getAnnotation(
                org.springframework.web.bind.annotation.RequestMapping.class).value()[0];
        for (Method method : FdV1ContractController.class.getDeclaredMethods()) {
            for (Annotation annotation : method.getAnnotations()) {
                String[] paths = mappingPaths(annotation);
                if (paths == null) {
                    continue;
                }
                String httpMethod = mappingMethod(annotation);
                for (String path : paths) {
                    String full = (prefix + path).replaceAll("/+", "/")
                            .replaceAll("\\{[^}]+\\}", "1");
                    routes.put(httpMethod + " " + full, method.getName());
                }
            }
        }
        return routes;
    }

    private static String[] mappingPaths(Annotation annotation) {
        if (annotation instanceof GetMapping get) {
            return get.value().length == 0 ? get.path() : get.value();
        }
        if (annotation instanceof PostMapping post) {
            return post.value().length == 0 ? post.path() : post.value();
        }
        if (annotation instanceof PatchMapping patch) {
            return patch.value().length == 0 ? patch.path() : patch.value();
        }
        if (annotation instanceof DeleteMapping delete) {
            return delete.value().length == 0 ? delete.path() : delete.value();
        }
        return null;
    }

    private static String mappingMethod(Annotation annotation) {
        if (annotation instanceof GetMapping) {
            return "GET";
        }
        if (annotation instanceof PostMapping) {
            return "POST";
        }
        if (annotation instanceof PatchMapping) {
            return "PATCH";
        }
        return "DELETE";
    }

    @Test
    void collectionMatchesImplementedContract() throws Exception {
        Path collection = locateCollection();
        String raw = Files.readString(collection, StandardCharsets.UTF_8);
        JsonNode root = new ObjectMapper().readTree(raw);

        assertTrue(root.path("info").path("schema").asText().contains("collection/v2.1"),
                "Collection must declare the Postman collection v2.1 schema");

        List<CollectionRequest> requests = collectRequests(root);
        assertFalse(requests.isEmpty(), "Collection must contain at least one request");

        Map<String, String> implemented = implementedRoutesByMethodName();
        Set<String> implementedRoutes = new HashSet<>(implemented.keySet());

        Set<String> hitOperations = new HashSet<>();
        for (CollectionRequest request : requests) {
            assertFalse(request.rawUrl().isBlank(),
                    "Request '" + request.name() + "' must define a URL");
            String key = request.method() + " " + normalizeCollectionPath(request.rawUrl());
            assertTrue(implementedRoutes.contains(key),
                    "Collection request '" + request.name() + "' [" + request.method()
                            + " " + request.rawUrl() + "] has no implemented controller route");
            hitOperations.add(implemented.get(key));
        }

        Set<String> coveredOperations = hitOperations;
        for (String operation : List.of("listProducts", "createAccount", "fundAccount",
                "getAccount", "runAccrual", "matureAccount", "closeAccount",
                "requestPrematureClosure", "approvePrematureClosure", "listTransactions")) {
            assertTrue(coveredOperations.contains(operation),
                    "Demo-critical operation '" + operation + "' must be covered by the collection");
        }

        List<String> placeholderSegments = List.of("/payout", "/notice", "/statement",
                "/event", "/sync", "/ref/", "/holder", "/renew", "/activate",
                "maturity-instruction");
        for (CollectionRequest request : requests) {
            String lower = request.rawUrl().toLowerCase();
            for (String segment : placeholderSegments) {
                assertFalse(lower.contains(segment),
                        "Request '" + request.name() + "' targets still-501 placeholder path '"
                                + request.rawUrl() + "'");
            }
        }

        assertFalse(raw.contains("eyJ"),
                "Collection must not embed a real JWT (use the YOUR_JWT_HERE placeholder)");
        assertFalse(Pattern.compile("(?i)password").matcher(raw).find(),
                "Collection must not embed a password literal");
    }
}
