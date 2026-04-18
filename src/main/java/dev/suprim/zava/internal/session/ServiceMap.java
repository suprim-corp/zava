package dev.suprim.zava.internal.session;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapping of Zalo service names to endpoint URLs.
 *
 * <p>Populated from the {@code zpw_service_map_v3} field in the login response.
 * Each service maps to one or more base URLs; the first is the primary endpoint.
 *
 * <p>Example services: {@code chat}, {@code group}, {@code friend}, {@code file},
 * {@code sticker}, {@code reaction}, {@code profile}, etc.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceMap {

    private final Map<String, List<String>> services = new LinkedHashMap<>();

    @JsonAnySetter
    public void addService(String name, List<String> urls) {
        services.put(name, urls != null ? urls : Collections.emptyList());
    }

    /**
     * Get the primary URL for a service.
     *
     * @param serviceName the service name (e.g. "chat", "group", "friend")
     * @return the first URL for the service
     * @throws IllegalArgumentException if the service is not found or has no URLs
     */
    public String getUrl(String serviceName) {
        List<String> urls = services.get(serviceName);
        if (urls == null || urls.isEmpty()) {
            throw new IllegalArgumentException("Service not found or has no URLs: " + serviceName);
        }
        return urls.get(0);
    }

    /**
     * Get all URLs for a service (for fallback/rotation).
     *
     * @param serviceName the service name
     * @return unmodifiable list of URLs, or empty list if service not found
     */
    public List<String> getUrls(String serviceName) {
        List<String> urls = services.get(serviceName);
        return urls != null ? Collections.unmodifiableList(urls) : Collections.emptyList();
    }

    /**
     * Check if a service exists in the map.
     */
    public boolean hasService(String serviceName) {
        return services.containsKey(serviceName);
    }

    /**
     * Get all service names.
     */
    public java.util.Set<String> getServiceNames() {
        return Collections.unmodifiableSet(services.keySet());
    }
}
