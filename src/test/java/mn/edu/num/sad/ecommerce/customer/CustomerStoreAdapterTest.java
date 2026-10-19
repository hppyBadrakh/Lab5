package mn.edu.num.sad.ecommerce.customer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CustomerStoreAdapterTest {

    @Test
    void shouldReturnEmptyListWhenLegacyStoreIsEmpty() {
        CustomerStore store =
                new CustomerStoreAdapter(
                        new LegacyCustomerStore(List.of())
                );

        assertTrue(store.findAll().isEmpty());
    }

    @Test
    void shouldReturnEmptyOptionalWhenRecordNotFound() {
        CustomerStore store =
                new CustomerStoreAdapter(
                        new LegacyCustomerStore(List.of())
                );

        assertEquals(
                Optional.empty(),
                store.findById("MISSING")
        );
    }

    @Test
    void shouldMapLegacyFieldsCorrectlyToDomainCustomer() {
        ExternalCustomer external = new ExternalCustomer(
                "C-1",
                " Bob ",
                "BOB@TEST.COM",
                "Main St",
                1
        );

        Customer customer =
                new CustomerStoreAdapter(
                        new LegacyCustomerStore(List.of(external))
                ).findById("C-1").orElseThrow();

        assertEquals("C-1", customer.id());
        assertEquals("Bob", customer.fullName());
        assertEquals("bob@test.com", customer.email());
        assertEquals(
                Optional.of("Main St"),
                customer.address()
        );
        assertEquals(CustomerTier.VIP, customer.tier());
    }
}