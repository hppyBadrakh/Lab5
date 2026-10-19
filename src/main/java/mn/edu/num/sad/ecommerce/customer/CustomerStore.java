package mn.edu.num.sad.ecommerce.customer;

import java.util.List;
import java.util.Optional;

public interface CustomerStore {

    Optional<Customer> findById(String id);

    List<Customer> findAll();
}