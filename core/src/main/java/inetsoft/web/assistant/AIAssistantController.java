/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package inetsoft.web.assistant;

import inetsoft.sree.SreeEnv;
import inetsoft.web.viewsheet.service.LinkUriArgumentResolver;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.net.ssl.*;
import java.net.URI;
import java.net.http.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@RestController
public class AIAssistantController {

   @PostConstruct
   public void createHealthClient() {
      HttpClient.Builder builder = HttpClient.newBuilder()
         .connectTimeout(Duration.ofSeconds(3));

      if(!isSslVerifyEnabled()) {
         LOG.warn("SSL certificate verification is disabled for AI assistant health check " +
                     "connections (chat.app.server.ssl.verify=false). Set to true in production when " +
                     "the assistant server uses a CA-signed certificate.");

         try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] {
               new X509TrustManager() {
                  public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                  public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                  public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
               }
            }, null);
            SSLParameters sslParameters = new SSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("");
            builder.sslContext(sslContext).sslParameters(sslParameters);
         }
         catch(NoSuchAlgorithmException | KeyManagementException e) {
            LOG.warn("Could not configure trust-all SSL context for AI assistant health check", e);
         }
      }

      healthClient = builder.build();
   }

   @PreDestroy
   public void closeHealthClient() {
      if(healthClient != null) {
         healthClient.close();
      }
   }

   /**
    * Returns the base URL that the browser should use for all AI assistant API calls.
    *
    * <ul>
    *   <li><b>Proxy mode</b> ({@code chat.app.internal.url} is set): returns
    *       {@code {styleBIUrl}/api/assistant/proxy}. All browser traffic is routed through
    *       StyleBI's reverse proxy; the assistant server needs no public network access.</li>
    *   <li><b>Direct mode</b> (only {@code chat.app.server.url} is set): returns that URL
    *       directly. The browser contacts the assistant server without going through StyleBI.</li>
    *   <li><b>Not configured</b>: returns 204 No Content; the AI assistant is inactive.</li>
    * </ul>
    */
   @GetMapping("/api/assistant/get-chat-app-server-url")
   public ResponseEntity<String> getChatAppServerUrl(HttpServletRequest request) {
      // Proxy mode: chat.app.internal.url is the server-to-server upstream URL.
      String internalUrl = SreeEnv.getProperty(CHAT_APP_INTERNAL_URL);

      if(internalUrl != null && !internalUrl.trim().isEmpty()) {
         String styleBIUrl = LinkUriArgumentResolver.getLinkUri(request);

         // Guard against Host-header injection: only return a URL with a known-safe scheme.
         if(!styleBIUrl.startsWith("http://") && !styleBIUrl.startsWith("https://")) {
            return ResponseEntity.noContent().build();
         }

         if(styleBIUrl.endsWith("/")) {
            styleBIUrl = styleBIUrl.substring(0, styleBIUrl.length() - 1);
         }

         return ResponseEntity.ok(styleBIUrl + PROXY_PATH_PREFIX);
      }

      // Direct mode: chat.app.server.url is the browser-facing assistant URL (legacy).
      String serverUrl = SreeEnv.getProperty(CHAT_APP_SERVER_URL);

      if(serverUrl != null && !serverUrl.trim().isEmpty()) {
         return ResponseEntity.ok(serverUrl.trim());
      }

      // Neither URL is configured; AI assistant is not active.
      return ResponseEntity.noContent().build();
   }

   @GetMapping("/api/assistant/ai-assistant-visible")
   public boolean isAiAssistantVisible() {
      if(!"true".equalsIgnoreCase(SreeEnv.getProperty(AI_ASSISTANT_VISIBLE, "false"))) {
         return false;
      }

      // Visible only when at least one assistant URL is configured (proxy or direct mode).
      String internalUrl = SreeEnv.getProperty(CHAT_APP_INTERNAL_URL);
      String serverUrl = SreeEnv.getProperty(CHAT_APP_SERVER_URL);
      return (internalUrl != null && !internalUrl.trim().isEmpty())
         || (serverUrl != null && !serverUrl.trim().isEmpty());
   }

   /**
    * Returns the full StyleBI server URL.
    * This URL is used as the JWT issuer for SSO tokens and should be passed
    * to external applications (like chat-app) to enable them to verify tokens
    * by fetching the JWKS from ${styleBIUrl}/sso/jwks.
    */
   @GetMapping("/api/assistant/get-stylebi-url")
   public ResponseEntity<String> getStyleBIUrl(HttpServletRequest request) {
      String url = LinkUriArgumentResolver.getLinkUri(request);

      // Guard against Host-header injection: only return a URL with a known-safe scheme.
      if(url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) {
         return ResponseEntity.noContent().build();
      }

      // Remove trailing slash for consistency
      if(url.endsWith("/")) {
         url = url.substring(0, url.length() - 1);
      }

      return ResponseEntity.ok(url);
   }

   /**
    * Returns {@code true} if SSL certificate verification is enabled for server-to-server
    * connections to the assistant (both HTTP and WebSocket proxies).
    * Reads {@code chat.app.server.ssl.verify}; defaults to {@code false} so that private-network
    * deployments with self-signed certificates work out of the box.
    * Changing this property requires a server restart.
    */
   public static boolean isSslVerifyEnabled() {
      return "true".equalsIgnoreCase(SreeEnv.getProperty("chat.app.server.ssl.verify", "false"));
   }

   /**
    * Checks whether the AI assistant server is currently reachable.
    *
    * <p>Always returns HTTP 200 with a boolean body ({@code true} = online, {@code false} =
    * unreachable). Using a non-error status code avoids triggering any application-level HTTP
    * interceptors that treat 4xx/5xx as fatal errors.
    *
    * <p>Returns 204 No Content when the assistant is not configured at all.
    * Uses a 3-second connect and read timeout. Non-blocking: the servlet thread is released
    * immediately while the upstream check runs on the HttpClient's own thread pool.
    */
   @GetMapping("/api/assistant/health")
   public CompletableFuture<ResponseEntity<Boolean>> checkAssistantHealth() {
      String upstreamBase = resolveUpstreamBase();

      if(upstreamBase == null) {
         return CompletableFuture.completedFuture(ResponseEntity.noContent().build());
      }

      String url = upstreamBase.endsWith("/")
         ? upstreamBase + "health"
         : upstreamBase + "/health";

      HttpRequest req = HttpRequest.newBuilder()
         .uri(URI.create(url))
         .timeout(Duration.ofSeconds(3))
         .method("HEAD", HttpRequest.BodyPublishers.noBody())
         .build();

      return healthClient.sendAsync(req, HttpResponse.BodyHandlers.discarding())
         .thenApply(r -> ResponseEntity.ok(r.statusCode() < 500))
         .exceptionally(e -> {
            LOG.debug("AI assistant health check failed for {}: {}", url, e.getMessage());
            return ResponseEntity.ok(false);
         });
   }

   private String resolveUpstreamBase() {
      String internalUrl = SreeEnv.getProperty(CHAT_APP_INTERNAL_URL);

      if(internalUrl != null && !internalUrl.trim().isEmpty()) {
         return internalUrl.trim();
      }

      String serverUrl = SreeEnv.getProperty(CHAT_APP_SERVER_URL);

      if(serverUrl != null && !serverUrl.trim().isEmpty()) {
         return serverUrl.trim();
      }

      return null;
   }

   private HttpClient healthClient;

   public static final String CHAT_APP_SERVER_URL = "chat.app.server.url";
   public static final String CHAT_APP_INTERNAL_URL = "chat.app.internal.url";
   public static final String PROXY_PATH_PREFIX = "/api/assistant/proxy";
   public static final String AI_ASSISTANT_VISIBLE = "ai.assistant.visible";
   private static final Logger LOG = LoggerFactory.getLogger(AIAssistantController.class);
}
