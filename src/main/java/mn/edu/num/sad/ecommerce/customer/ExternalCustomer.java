package mn.edu.num.sad.ecommerce.customer;

public record ExternalCustomer(
        String cust_id_num,
        String raw_full_name,
        String contact_email,
        String addr_blob,
        int vip_flag
) {
}