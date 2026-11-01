// legacy/LegacyOrderEndpoint.java - THE ANTI-PATTERN
// Kept outside src/ so it is never compiled. It is the "before" picture for ADR-0005.
public class LegacyOrderEndpoint {
    public String handleHttpRequest(String action, Map<String, Object> payload) {
        if ("CREATE".equalsIgnoreCase(action)) {
            try {
                // Symptom 1: Raw payload parsing without schema validation
                String customerId = (String) payload.get("customerId");
                double subtotal = Double.parseDouble(payload.get("amount").toString());
                String tier = (String) payload.getOrDefault("tier", "STANDARD");

                // Symptom 2: Domain business logic hardcoded inside presentation controller
                double discount = ("VIP".equalsIgnoreCase(tier)) ? subtotal * 0.15 : 0.0;
                double finalTotal = (subtotal - discount) * 1.10; // Hardcoded 10% tax

                // Symptom 3: Direct infrastructure JDBC call inside controller
                Connection conn = DriverManager.getConnection("jdbc:h2:mem:test", "sa", "");
                PreparedStatement ps = conn.prepareStatement("INSERT INTO orders VALUES (?, ?, ?)");
                ps.setString(1, customerId); ps.setDouble(2, finalTotal); ps.setString(3, "PENDING");
                ps.executeUpdate();

                return "{\"status\":\"SUCCESS\",\"total\":" + finalTotal + "}";
            } catch (Exception e) {
                // Symptom 4: Returning 200 OK with error payload; masking HTTP failure semantics
                return "{\"status\":\"ERROR\",\"message\":\"" + e.getMessage() + "\"}";
            }
        }
        return "{\"status\":\"UNKNOWN_ACTION\"}";
    }
}
