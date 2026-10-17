package vn.edu.crave.model.service;

import vn.edu.crave.model.form.UpdateCartItemRequest;
import vn.edu.crave.model.entity.Cart;
import vn.edu.crave.model.entity.CartItem;
import vn.edu.crave.model.enums.ErrorCode;
import vn.edu.crave.exception.BadRequestException;
import vn.edu.crave.exception.ResourceNotFoundException;
import vn.edu.crave.model.dao.CartItemDAO;
import vn.edu.crave.model.dao.CartDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartCustomizationServiceTest {
    private final CartDAO carts = mock(CartDAO.class);
    private final CartItemDAO items = mock(CartItemDAO.class);
    private final CartService service = new CartService(carts, items);
    private final UpdateCartItemRequest request = new UpdateCartItemRequest("CI01", 2, " less spicy ");

    @BeforeEach
    void setup() {
        when(carts.findByCustomerId("KH01")).thenReturn(new Cart("GH01", (vn.edu.crave.model.entity.Customer) null));
        when(items.findById("CI01")).thenReturn(new CartItem("CI01", "GH01", "MA01", 1, "old"));
        when(items.isFoodAvailable("MA01")).thenReturn(true);
        when(items.getValidOptionIdsForFood(eq("MA01"), anyList())).thenAnswer(call -> call.getArgument(1));
        when(items.findItemsByCartId("GH01")).thenReturn(List.of());
    }

    @Test
    void optionWritesAreAtomicAndEmptySelectionClearsOptions() {
        for (List<String> selection : List.of(List.of("TC01"), List.<String>of())) {
            request.setOptionIds(selection);
            service.updateItem("KH01", request);
            verify(items).updateItemWithOptions("CI01", "GH01", 2, "less spicy", selection);
        }
        verify(items, never()).updateQuantity(anyString(), anyInt());
        verify(items, never()).updateNote(anyString(), anyString());
    }

    @Test
    void quantityOnlyWriteKeepsExistingOptionSelection() {
        service.updateItem("KH01", request);
        verify(items).updateQuantity("CI01", 2);
        verify(items).updateNote("CI01", "less spicy");
        verify(items, never()).updateItemWithOptions(anyString(), anyString(), anyInt(), any(), anyList());
    }

    @Test
    void invalidOrUnavailableOptionsNeverChangeQuantityOrNote() {
        request.setOptionIds(List.of("foreign"));
        when(items.getValidOptionIdsForFood("MA01", request.getOptionIds())).thenReturn(List.of());
        assertEquals(ErrorCode.INVALID_OPTION,
                assertThrows(BadRequestException.class, () -> service.updateItem("KH01", request)).getErrorCode());
        when(items.isFoodAvailable("MA01")).thenReturn(false);
        assertEquals(ErrorCode.FOOD_NOT_AVAILABLE,
                assertThrows(BadRequestException.class, () -> service.updateItem("KH01", request)).getErrorCode());
        verify(items, never()).updateQuantity(anyString(), anyInt());
        verify(items, never()).updateNote(anyString(), anyString());
        verify(items, never()).updateItemWithOptions(anyString(), anyString(), anyInt(), any(), anyList());
    }

    @Test
    void anotherCustomersItemCannotBeCustomized() {
        when(items.findById("CI01")).thenReturn(new CartItem("CI01", "OTHER", "MA01", 1, "old"));
        request.setOptionIds(List.of("TC01"));
        assertThrows(ResourceNotFoundException.class, () -> service.updateItem("KH01", request));
        verify(items, never()).updateItemWithOptions(anyString(), anyString(), anyInt(), any(), anyList());
    }
}
