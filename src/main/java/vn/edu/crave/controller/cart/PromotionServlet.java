package vn.edu.crave.controller.cart;

import vn.edu.crave.model.viewmodel.PromotionValidationResultDto;
import vn.edu.crave.model.form.ValidatePromotionRequest;
import vn.edu.crave.exception.AccountException;
import vn.edu.crave.security.SessionAuth;
import vn.edu.crave.model.service.PromotionService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(name = "PromotionServlet", urlPatterns = {"/promotions/apply", "/promotions/remove"})
public class PromotionServlet extends HttpServlet {
    private final PromotionService promotionService;

    public PromotionServlet() {
        this(new PromotionService());
    }

    public PromotionServlet(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        String returnTo = "/checkout".equals(request.getParameter("returnTo")) ? "/checkout" : "/cart";
        try {
            String customerId = SessionAuth.requireCustomer(request).id();
            if ("/promotions/remove".equals(request.getServletPath())) {
                request.getSession().removeAttribute("selectedPromotionId");
                request.getSession().removeAttribute("selectedPromotionCode");
            } else {
                ValidatePromotionRequest form = new ValidatePromotionRequest();
                form.setCode(request.getParameter("code"));
                PromotionValidationResultDto result = promotionService.validatePromotion(form, customerId);
                if (result.isValid()) {
                    request.getSession().setAttribute("selectedPromotionId", result.getPromotion().getPromotionId());
                    request.getSession().setAttribute("selectedPromotionCode", result.getPromotion().getCode());
                } else {
                    request.getSession().setAttribute("voucherErrorMessage", result.getMessage());
                }
            }
        } catch (AccountException exception) {
            if (exception.getStatusCode() == HttpServletResponse.SC_UNAUTHORIZED) {
                response.sendRedirect(request.getContextPath() + "/auth/login?returnTo=" + returnTo);
            } else {
                response.sendError(exception.getStatusCode(), exception.getMessage());
            }
            return;
        }
        response.sendRedirect(request.getContextPath() + returnTo);
    }
}