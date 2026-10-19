package mn.edu.num.sad.ecommerce.customer;

import java.util.Optional;

public record Customer(
        String id,
        String fullName,
        String email,
        Optional<String> address,
        CustomerTier tier
) {
}