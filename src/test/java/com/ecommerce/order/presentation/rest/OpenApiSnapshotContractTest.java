package com.ecommerce.order.presentation.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OpenAPI sanity checks on the live document from {@code /v3/api-docs}, and a drift check against the
 * committed snapshot {@code docs/openapi/rest-endpoints.json}.
 * <p>
 * After an intended API change, refresh the snapshot and commit it:
 * {@code ./mvnw test -Dtest=OpenApiSnapshotContractTest -Dopenapi.snapshot.update=true}
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiSnapshotContractTest {

    private static final Path SNAPSHOT = Path.of("docs", "openapi", "rest-endpoints.json");
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Autowired
    private MockMvc mockMvc;

    private JsonNode live;

    @BeforeEach
    void fetchLiveDocument() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        live = JSON.readTree(body);
    }

    @Test
    @DisplayName("The document is OpenAPI 3 and describes all five operations on noun URIs")
    void shouldDescribeEveryOperation() {
        assertThat(live.path("openapi").asText()).startsWith("3.");
        assertThat(live.path("info").path("title").asText()).isEqualTo("Order Service API");

        JsonNode paths = live.path("paths");
        assertThat(paths.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "/api/v1/orders",
                "/api/v1/orders/{orderId}",
                "/api/v1/orders/{orderId}/payment",
                "/api/v1/orders/{orderId}/cancellation",
                "/api/v1/customers/{customerId}/orders");
        assertThat(paths.path("/api/v1/orders").has("post")).isTrue();
        assertThat(paths.path("/api/v1/orders/{orderId}").has("get")).isTrue();
        assertThat(paths.path("/api/v1/orders/{orderId}/payment").has("put")).isTrue();
        assertThat(paths.path("/api/v1/orders/{orderId}/cancellation").has("put")).isTrue();
        assertThat(paths.path("/api/v1/customers/{customerId}/orders").has("get")).isTrue();
    }

    @Test
    @DisplayName("Every operation documents its status codes, and errors are problem+json")
    void shouldDocumentStatusCodes() {
        Map<String, List<String>> expected = Map.of(
                "/api/v1/orders|post", List.of("201", "400", "429"),
                "/api/v1/orders/{orderId}|get", List.of("200", "404"),
                "/api/v1/customers/{customerId}/orders|get", List.of("200", "400"),
                "/api/v1/orders/{orderId}/payment|put", List.of("202", "204", "402", "404", "409"),
                "/api/v1/orders/{orderId}/cancellation|put", List.of("204", "404", "409"));

        expected.forEach((operation, codes) -> {
            String[] key = operation.split("\\|");
            JsonNode responses = live.path("paths").path(key[0]).path(key[1]).path("responses");
            assertThat(responses.fieldNames()).toIterable().as(operation).containsExactlyInAnyOrderElementsOf(codes);
            for (String code : codes) {
                if (code.startsWith("4")) {
                    assertThat(responses.path(code).path("content").path("application/problem+json")
                            .path("schema").path("$ref").asText())
                            .as(operation + " " + code)
                            .isEqualTo("#/components/schemas/ProblemDetail");
                }
            }
        });

        JsonNode created = live.path("paths").path("/api/v1/orders").path("post").path("responses").path("201");
        assertThat(created.path("headers").has("Location")).isTrue();
        assertThat(created.path("content").path("application/json").path("schema").path("$ref").asText())
                .isEqualTo("#/components/schemas/OrderResponse");
    }

    @Test
    @DisplayName("Schemas carry the validation constraints and examples a client needs")
    void shouldPublishSchemaConstraints() {
        JsonNode schemas = live.path("components").path("schemas");
        JsonNode item = schemas.path("OrderItemRequest").path("properties");

        assertThat(item.path("sku").path("pattern").asText()).isEqualTo("[A-Z0-9]{3,12}-[A-Z0-9]{2,6}");
        assertThat(item.path("sku").path("example").asText()).isEqualTo("ELEC-1001");
        assertThat(item.path("currency").path("pattern").asText()).isEqualTo("[A-Z]{3}");
        assertThat(item.path("quantity").path("maximum").asInt()).isEqualTo(999);
        assertThat(schemas.path("CreateOrderRequest").path("required")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("customerId", "shippingAddress", "items");
        assertThat(schemas.path("OrderResponse").path("properties").path("status").path("enum"))
                .extracting(JsonNode::asText)
                .containsExactly("DRAFT", "PLACED", "PAYMENT_PENDING", "PAID", "CANCELLED");
    }

    @Test
    @DisplayName("The committed snapshot docs/openapi/rest-endpoints.json matches the live contract")
    void shouldMatchCommittedSnapshot() throws Exception {
        if (Boolean.getBoolean("openapi.snapshot.update")) {
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, canonical(live) + "\n", StandardCharsets.UTF_8);
        }

        assertThat(SNAPSHOT).as("snapshot file; create it with -Dopenapi.snapshot.update=true").exists();
        JsonNode committed = JSON.readTree(Files.readString(SNAPSHOT, StandardCharsets.UTF_8));

        assertThat(canonical(committed))
                .as("REST contract changed. If intended, rerun with -Dopenapi.snapshot.update=true and commit the snapshot.")
                .isEqualTo(canonical(live));
    }

    /** Sorted keys and fixed indentation, so the comparison ignores key order and line endings. */
    private static String canonical(JsonNode node) throws Exception {
        Object tree = JSON.treeToValue(node, Object.class);
        assertThat(node).isInstanceOf(ObjectNode.class);
        return JSON.writeValueAsString(tree).replace("\r\n", "\n");
    }
}
