package mn.edu.num.sad.ecommerce.customer;

import java.util.List;
import java.util.Objects;

public final class LegacyCustomerStore {

    private final List<ExternalCustomer> customers;

    public LegacyCustomerStore(List<ExternalCustomer> customers) {
        this.customers = List.copyOf(
                Objects.requireNonNull(customers)
        );
    }

    public ExternalCustomer fetchCustomerById(String id) {
        return customers.stream()
                .filter(customer ->
                        Objects.equals(customer.cust_id_num(), id))
                .findFirst()
                .orElse(null);
    }

    public List<ExternalCustomer> fetchAllCustomers() {
        return customers;
    }
}