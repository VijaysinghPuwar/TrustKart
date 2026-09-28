package com.vijaysinghpuwar.trustkart.support;

import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * A minimal browser for integration tests: keeps a cookie jar across requests (honouring deletions and the
 * cookie Path) and echoes the XSRF-TOKEN cookie in the X-XSRF-TOKEN header on writes, exactly like the SPA.
 */
public class Browser {

    private record StoredCookie(String value, String path) {}

    private final MockMvc mvc;
    private final Map<String, StoredCookie> jar = new LinkedHashMap<>();
    private String ip = "10." + RANDOM.nextInt(256) + "." + RANDOM.nextInt(256) + "." + (1 + RANDOM.nextInt(254));
    private static final java.util.Random RANDOM = new java.util.Random();

    public Browser(MockMvc mvc) {
        this.mvc = mvc;
    }

    public Browser from(String ipAddress) {
        this.ip = ipAddress;
        return this;
    }

    public ResultActions get(String url) throws Exception {
        return send(MockMvcRequestBuilders.get(url), false);
    }

    /** For URLs that are already percent-encoded (a String would be treated as a template and encoded again). */
    public ResultActions get(java.net.URI uri) throws Exception {
        return send(MockMvcRequestBuilders.get(uri), false);
    }

    public ResultActions post(String url, String json) throws Exception {
        return send(MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(json), true);
    }

    public ResultActions post(String url) throws Exception {
        return send(MockMvcRequestBuilders.post(url), true);
    }

    public ResultActions put(String url, String json) throws Exception {
        return send(MockMvcRequestBuilders.put(url).contentType(MediaType.APPLICATION_JSON).content(json), true);
    }

    public ResultActions patch(String url, String json) throws Exception {
        return send(MockMvcRequestBuilders.patch(url).contentType(MediaType.APPLICATION_JSON).content(json), true);
    }

    public ResultActions delete(String url) throws Exception {
        return send(MockMvcRequestBuilders.delete(url), true);
    }

    /** Sends a write with an extra header (e.g. Idempotency-Key). */
    public ResultActions post(String url, String json, String header, String value) throws Exception {
        return send(MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(json).header(header, value), true);
    }

    public String cookie(String name) {
        StoredCookie c = jar.get(name);
        return c == null ? null : c.value();
    }

    public void setCookie(String name, String value, String path) {
        jar.put(name, new StoredCookie(value, path));
    }

    public void forget(String name) {
        jar.remove(name);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, boolean write) throws Exception {
        if (write && cookie("XSRF-TOKEN") == null) {
            send(MockMvcRequestBuilders.get("/api/v1/auth/csrf"), false);
        }
        request.with(r -> {
            r.setRemoteAddr(ip);
            return r;
        });
        request.header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Chrome/140.0 Safari/537.36");
        String uri = request.buildRequest(new org.springframework.mock.web.MockServletContext()).getRequestURI();
        Cookie[] cookies = jar.entrySet().stream()
                .filter(e -> uri.startsWith(e.getValue().path()))
                .map(e -> new Cookie(e.getKey(), e.getValue().value()))
                .toArray(Cookie[]::new);
        if (cookies.length > 0) {
            request.cookie(cookies);
        }
        if (write && cookie("XSRF-TOKEN") != null) {
            request.header("X-XSRF-TOKEN", cookie("XSRF-TOKEN"));
        }
        ResultActions result = mvc.perform(request);
        absorb(result.andReturn().getResponse());
        return result;
    }

    private void absorb(MockHttpServletResponse response) {
        for (String header : response.getHeaders(HttpHeaders.SET_COOKIE)) {
            String[] parts = header.split(";");
            String[] nv = parts[0].split("=", 2);
            String name = nv[0].trim();
            String value = nv.length > 1 ? nv[1].trim() : "";
            String path = "/";
            boolean expired = false;
            for (int i = 1; i < parts.length; i++) {
                String attr = parts[i].trim();
                if (attr.regionMatches(true, 0, "Path=", 0, 5)) {
                    path = attr.substring(5);
                } else if (attr.regionMatches(true, 0, "Max-Age=", 0, 8) && attr.substring(8).equals("0")) {
                    expired = true;
                }
            }
            if (expired || value.isEmpty()) {
                jar.remove(name);
            } else {
                jar.put(name, new StoredCookie(value, path));
            }
        }
    }
}
