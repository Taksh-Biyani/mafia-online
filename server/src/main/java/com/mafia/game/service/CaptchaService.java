package com.mafia.game.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Verifies Cloudflare Turnstile tokens server-side.
 * The client receives a token from the Turnstile widget (Cloudflare JS handles the challenge),
 * then sends it here; we verify it with Cloudflare's siteverify API.
 */
@Service
public class CaptchaService {

    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final String secret;
    private final HttpClient httpClient;

    public CaptchaService(@Value("${turnstile.secret}") String secret) {
        this.secret = secret;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Verifies a Turnstile token. Optionally includes the client IP for stronger validation.
     * Returns false if the token is missing, invalid, or the Cloudflare API call fails.
     */
    public boolean verify(String token, String remoteIp) {
        if (token == null || token.isBlank()) return false;
        try {
            String body = "secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8)
                        + "&response=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
            if (remoteIp != null && !remoteIp.isBlank()) {
                body += "&remoteip=" + URLEncoder.encode(remoteIp, StandardCharsets.UTF_8);
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(VERIFY_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body().contains("\"success\":true");
        } catch (Exception e) {
            return false;
        }
    }
}
