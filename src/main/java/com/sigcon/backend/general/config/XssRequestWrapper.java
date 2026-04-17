package com.sigcon.backend.general.config;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;

public class XssRequestWrapper extends HttpServletRequestWrapper {

    private byte[] sanitizedBody;

    public XssRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getParameter(String name) {
        String value = super.getParameter(name);
        return sanitize(value);
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) return null;
        String[] sanitized = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            sanitized[i] = sanitize(values[i]);
        }
        return sanitized;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> original = super.getParameterMap();
        Map<String, String[]> sanitized = new HashMap<>();
        for (Map.Entry<String, String[]> entry : original.entrySet()) {
            String[] values = entry.getValue();
            String[] clean = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                clean[i] = sanitize(values[i]);
            }
            sanitized.put(entry.getKey(), clean);
        }
        return sanitized;
    }

    @Override
    public String getHeader(String name) {
        String value = super.getHeader(name);
        return sanitize(value);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (sanitizedBody == null) {
            sanitizedBody = sanitizeBody();
        }
        ByteArrayInputStream bais = new ByteArrayInputStream(sanitizedBody);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() { return bais.available() == 0; }
            @Override
            public boolean isReady() { return true; }
            @Override
            public void setReadListener(ReadListener listener) {}
            @Override
            public int read() { return bais.read(); }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    private byte[] sanitizeBody() throws IOException {
        String contentType = super.getContentType();
        if (contentType == null) {
            return super.getInputStream().readAllBytes();
        }

        byte[] rawBody = super.getInputStream().readAllBytes();

        if (contentType.contains("application/json")) {
            String body = new String(rawBody, StandardCharsets.UTF_8);
            String sanitized = sanitizeJsonStrings(body);
            return sanitized.getBytes(StandardCharsets.UTF_8);
        }

        return rawBody;
    }

    private String sanitizeJsonStrings(String json) {
        StringBuilder result = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        StringBuilder currentString = new StringBuilder();

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                if (inString) currentString.append(c);
                else result.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                if (inString) currentString.append(c);
                else result.append(c);
                continue;
            }

            if (c == '"') {
                if (inString) {
                    String sanitized = sanitize(currentString.toString());
                    result.append('"').append(escapeForJson(sanitized)).append('"');
                    currentString.setLength(0);
                    inString = false;
                } else {
                    inString = true;
                }
                continue;
            }

            if (inString) {
                currentString.append(c);
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    private String escapeForJson(String value) {
        if (value == null) return null;
        return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r")
                     .replace("\t", "\\t");
    }

    /** Patrones de XSS peligrosos a eliminar */
    private static final Pattern[] XSS_PATTERNS = {
        Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
        Pattern.compile("<script[^>]*>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("</script>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("javascript\\s*:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("on\\w+\\s*=", Pattern.CASE_INSENSITIVE),
        Pattern.compile("eval\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("expression\\s*\\(", Pattern.CASE_INSENSITIVE),
        Pattern.compile("vbscript\\s*:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<iframe[^>]*>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<object[^>]*>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<embed[^>]*>", Pattern.CASE_INSENSITIVE),
    };

    /**
     * Elimina patrones de XSS peligrosos sin alterar caracteres normales
     * como @, &, etc. Esto evita romper emails y otros datos legítimos.
     */
    private String sanitize(String value) {
        if (value == null) return null;
        String clean = value;
        for (Pattern pattern : XSS_PATTERNS) {
            clean = pattern.matcher(clean).replaceAll("");
        }
        return clean;
    }
}
