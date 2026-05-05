package com.sigcon.backend.platform.dashboard.infrastructure;

import com.sigcon.backend.platform.dashboard.domain.service.RequestMetricsRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * HU-PA-PLAT-06 E3: interceptor que captura latencia y status de cada request HTTP
 * y los registra en {@link RequestMetricsRegistry}.
 */
@Component
@RequiredArgsConstructor
public class RequestMetricsInterceptor implements HandlerInterceptor {

    private static final String START_ATTR = "_metrics_start";
    private final RequestMetricsRegistry registry;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                              @NonNull HttpServletResponse response,
                              @NonNull Object handler) {
        request.setAttribute(START_ATTR, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                 @NonNull HttpServletResponse response,
                                 @NonNull Object handler,
                                 Exception ex) {
        Object startObj = request.getAttribute(START_ATTR);
        if (!(startObj instanceof Long start)) return;
        long durationMs = (System.nanoTime() - start) / 1_000_000L;
        String endpoint = resolveEndpoint(request);
        registry.record(endpoint, durationMs, response.getStatus());
    }

    /**
     * Devuelve el patron de URL si Spring lo ha matcheado (con placeholders {id}),
     * sino devuelve la URI cruda. El patron es preferible para no fragmentar
     * los buckets por cada id distinto.
     */
    private String resolveEndpoint(HttpServletRequest req) {
        Object pattern = req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String path = pattern != null ? pattern.toString() : req.getRequestURI();
        return req.getMethod() + " " + path;
    }
}
