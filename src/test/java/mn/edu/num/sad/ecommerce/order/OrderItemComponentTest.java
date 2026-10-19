package mn.edu.num.sad.ecommerce.order;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderItemComponentTest {

    @Test
    void singleItemCalculatesItsTotalPrice() {
        OrderItemComponent item = new SingleItem(
                "Keyboard",
                2,
                new BigDecimal("25.00")
        );

        assertEquals(new BigDecimal("50.00"), item.price());
        assertEquals("Keyboard x2 = 50.00", item.render());
    }

    @Test
    void bundleSumsItsChildren() {
        ItemBundle bundle = new ItemBundle("Accessories")
                .add(new SingleItem(
                        "Mouse", 1, new BigDecimal("15.00")))
                .add(new SingleItem(
                        "Keyboard", 1, new BigDecimal("40.00")));

        assertEquals(new BigDecimal("55.00"), bundle.price());
        assertEquals(
                "Accessories [Mouse x1 = 15.00, "
                        + "Keyboard x1 = 40.00] = 55.00",
                bundle.render()
        );
    }

    @Test
    void nestedBundlesCalculateRecursively() {
        ItemBundle accessories = new ItemBundle("Accessories")
                .add(new SingleItem(
                        "Mouse", 1, new BigDecimal("15.00")));

        ItemBundle workstation = new ItemBundle("Workstation")
                .add(new SingleItem(
                        "Monitor", 1, new BigDecimal("150.00")))
                .add(accessories);

        assertEquals(new BigDecimal("165.00"), workstation.price());
        assertTrue(workstation.render().contains("Accessories"));
        assertTrue(workstation.render().contains("Mouse x1 = 15.00"));
    }

    @Test
    void mixedTreeCalculatesLeafAndBundlePrices() {
        ItemBundle order = new ItemBundle("Order")
                .add(new SingleItem(
                        "Laptop", 1, new BigDecimal("900.00")))
                .add(new ItemBundle("Extras")
                        .add(new SingleItem(
                                "Mouse", 2, new BigDecimal("10.00")))
                        .add(new SingleItem(
                                "Bag", 1, new BigDecimal("30.00"))));

        assertEquals(new BigDecimal("950.00"), order.price());
        assertTrue(order.render().startsWith("Order ["));
        assertTrue(order.render().contains("Extras ["));
    }
}