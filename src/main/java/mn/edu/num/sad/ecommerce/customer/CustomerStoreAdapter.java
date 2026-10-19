package mn.edu.num.sad.ecommerce.customer;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class CustomerStoreAdapter implements CustomerStore {

    private final LegacyCustomerStore legacyStore;

    public CustomerStoreAdapter(LegacyCustomerStore legacyStore) {
        this.legacyStore = Objects.requireNonNull(legacyStore);
    }

    @Override
    public Optional<Customer> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        ExternalCustomer raw = legacyStore.fetchCustomerById(id);

        return raw == null
                ? Optional.empty()
                : Optional.of(mapToDomain(raw));
    }

    @Override
    public List<Customer> findAll() {
        return legacyStore.fetchAllCustomers()
                .stream()
                .map(this::mapToDomain)
                .toList();
    }

    private Customer mapToDomain(ExternalCustomer external) {
        Optional<String> address =
                external.addr_blob() == null
                        || external.addr_blob().isBlank()
                        ? Optional.empty()
                        : Optional.of(external.addr_blob().trim());

        CustomerTier tier = external.vip_flag() == 1
                ? CustomerTier.VIP
                : CustomerTier.STANDARD;

        return new Customer(
                external.cust_id_num(),
                external.raw_full_name().trim(),
                external.contact_email()
                        .trim()
                        .toLowerCase(Locale.ROOT),
                address,
                tier
        );
    }
}