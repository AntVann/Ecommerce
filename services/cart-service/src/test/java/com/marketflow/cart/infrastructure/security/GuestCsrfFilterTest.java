package com.marketflow.cart.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class GuestCsrfFilterTest {
    private final GuestCsrfFilter filter = new GuestCsrfFilter();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsAnonymousMutationWithoutDoubleSubmitToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/cart/items");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("GUEST_CSRF_INVALID_403");
    }

    @Test
    void acceptsMatchingTokenAndGuestIdentifier() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/cart/items");
        request.setCookies(
                new Cookie("MARKETFLOW_GUEST_CART", "guest"),
                new Cookie(GuestCsrfFilter.COOKIE, "csrf"));
        request.addHeader(GuestCsrfFilter.HEADER, "csrf");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
