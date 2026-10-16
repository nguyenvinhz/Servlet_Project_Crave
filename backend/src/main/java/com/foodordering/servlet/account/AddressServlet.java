package com.foodordering.servlet.account;

import com.foodordering.bean.AddressBean;
import com.foodordering.bean.AddressWriteForm;
import com.foodordering.exception.AccountException;
import com.foodordering.security.SessionAuth;
import com.foodordering.service.AccountService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@WebServlet(name = "AddressServlet", urlPatterns = "/customer/addresses")
public class AddressServlet extends AccountServlet {
    private final AccountService accountService;

    public AddressServlet() { this(new AccountService()); }
    public AddressServlet(AccountService accountService) { this.accountService = accountService; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            String customerId = SessionAuth.requireCustomer(request).id();
            String edit = request.getParameter("edit");
            if (edit != null && !edit.isBlank()) request.setAttribute("editing", fields(accountService.getAddress(customerId, id(edit))));
            loadAddresses(request, customerId);
        } catch (Exception exception) { if (error(request, response, exception)) return; }
        page(request, response, "Địa chỉ giao hàng", "customer/addresses");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        String customerId;
        try { customerId = SessionAuth.requireCustomer(request).id(); }
        catch (Exception exception) { error(request, response, exception); return; }
        String action = request.getParameter("action");
        try {
            String message;
            switch (action == null ? "save" : action) {
                case "delete" -> {
                    accountService.deleteAddress(customerId, id(request.getParameter("id")));
                    message = "Đã xóa địa chỉ.";
                }
                case "default" -> {
                    AddressBean address = accountService.getAddress(customerId, id(request.getParameter("id")));
                    accountService.updateAddress(customerId, address.id(),
                            new AddressWriteForm(address.addressLine(), address.note(), true));
                    message = "Đã chọn địa chỉ mặc định.";
                }
                case "save" -> {
                    AddressWriteForm form = new AddressWriteForm(request.getParameter("addressLine"),
                            request.getParameter("note"), "true".equals(request.getParameter("isDefault")));
                    String addressId = request.getParameter("id");
                    if (addressId == null || addressId.isBlank()) {
                        accountService.createAddress(customerId, form);
                        message = "Đã thêm địa chỉ.";
                    } else {
                        accountService.updateAddress(customerId, id(addressId), form);
                        message = "Đã cập nhật địa chỉ.";
                    }
                }
                default -> throw new AccountException("INVALID_REQUEST", "Thao tác địa chỉ không hợp lệ.", 400);
            }
            redirect(request, response, "/customer/addresses", message);
            return;
        } catch (Exception exception) {
            if (error(request, response, exception)) return;
            if (action == null || "save".equals(action)) {
                request.setAttribute("submitted", true);
                request.setAttribute("editingId", request.getParameter("id"));
            }
        }
        try { loadAddresses(request, customerId); }
        catch (Exception exception) { if (error(request, response, exception)) return; }
        page(request, response, "Địa chỉ giao hàng", "customer/addresses");
    }

    private void loadAddresses(HttpServletRequest request, String customerId) {
        request.setAttribute("addresses", accountService.listAddresses(customerId).stream().map(AddressServlet::fields).toList());
    }

    private static Map<String, Object> fields(AddressBean address) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", address.id());
        fields.put("addressLine", address.addressLine());
        fields.put("note", address.note());
        fields.put("defaultAddress", address.isDefault());
        return fields;
    }

    private static String id(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,10}")) {
            throw new AccountException("INVALID_REQUEST", "Mã địa chỉ không hợp lệ.", 400);
        }
        return value;
    }
}
