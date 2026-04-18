package com.sigcon.backend.general.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class XssFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String contentType = httpRequest.getContentType();

        if (contentType != null && contentType.contains("multipart/form-data")) {
            chain.doFilter(request, response);
            return;
        }

        XssRequestWrapper wrappedRequest = new XssRequestWrapper(httpRequest);
        chain.doFilter(wrappedRequest, response);
    }
}
