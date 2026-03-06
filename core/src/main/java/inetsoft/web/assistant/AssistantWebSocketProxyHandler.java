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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import org.springframework.http.HttpHeaders;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * WebSocket proxy handler that bridges browser WebSocket connections to the AI assistant server.
 * Handles connections at {@code /api/assistant/proxy} and {@code /api/assistant/proxy/**},
 * forwarding them to the upstream assistant service via WebSocket.
 *
 * <p>Active only in proxy mode ({@code chat.app.internal.url} is configured).
 * {@code chat.app.internal.url} is the server-to-server base URL used to reach the assistant;
 * it is converted to a {@code ws://} or {@code wss://} URL for the upstream connection.
 * When the internal URL is not set the handler closes the browser connection immediately —
 * in direct mode the browser connects to the assistant WebSocket endpoint directly and this
 * handler is never involved.</p>
 *
 * <p>The assistant client establishes a WebSocket for real-time status events (e.g. tool-use
 * steps shown as "thinking" bubbles). Without this handler those connections would fail,
 * leaving the UI with no intermediate progress updates.</p>
 */
@Component
public class AssistantWebSocketProxyHandler extends AbstractWebSocketHandler {

   @Override
   public void afterConnectionEstablished(WebSocketSession browserSession) {
      try {
         afterConnectionEstablishedInternal(browserSession);
      }
      catch(Throwable t) {
         LOG.error("Unexpected error establishing assistant WebSocket proxy for session {}",
            browserSession.getId(), t);
         closeQuietly(browserSession, CloseStatus.SERVER_ERROR);
      }
   }

   private void afterConnectionEstablishedInternal(WebSocketSession browserSession) {
      // Proxy mode requires chat.app.internal.url. When only chat.app.server.url is set the
      // browser contacts the assistant directly and this WebSocket proxy is never reached.
      String internalBase = SreeEnv.getProperty(AIAssistantController.CHAT_APP_INTERNAL_URL);

      if(internalBase == null || internalBase.trim().isEmpty()) {
         closeQuietly(browserSession, CloseStatus.SERVER_ERROR);
         return;
      }

      URI browserUri = browserSession.getUri();
      String proxiedPath = extractProxiedPath(browserUri);
      String query = browserUri != null ? browserUri.getQuery() : null;
      String wsBase = toWsUrl(internalBase.trim());
      String upstreamUrl = wsBase + proxiedPath + (query != null ? "?" + query : "");

      // Forward the same set of headers as the HTTP proxy.
      WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
      HttpHeaders handshakeHeaders = browserSession.getHandshakeHeaders();

      for(String header : FORWARD_WS_HEADERS) {
         String value = handshakeHeaders.getFirst(header);

         if(value != null) {
            headers.set(header, value);
         }
      }

      WebSocketHandler upstreamHandler = new AbstractWebSocketHandler() {
         @Override
         protected void handleTextMessage(WebSocketSession upstream, TextMessage msg) throws Exception {
            if(browserSession.isOpen()) {
               synchronized(browserSession) {
                  browserSession.sendMessage(msg);
               }
            }
         }

         @Override
         protected void handleBinaryMessage(WebSocketSession upstream, BinaryMessage msg) throws Exception {
            if(browserSession.isOpen()) {
               synchronized(browserSession) {
                  browserSession.sendMessage(msg);
               }
            }
         }

         @Override
         public void afterConnectionClosed(WebSocketSession upstream, CloseStatus status) {
            upstreamSessions.remove(browserSession.getId());
            closeQuietly(browserSession, status);
         }

         @Override
         public void handleTransportError(WebSocketSession upstream, Throwable ex) {
            LOG.warn("Assistant WebSocket upstream transport error: {}", ex.getMessage());
            upstreamSessions.remove(browserSession.getId());
            closeQuietly(browserSession, CloseStatus.SERVER_ERROR);
         }
      };

      try {
         WebSocketSession upstreamSession = getWsClient()
            .execute(upstreamHandler, headers, URI.create(upstreamUrl))
            .get(10, TimeUnit.SECONDS);

         // Guard against the browser disconnecting while the upstream connection was
         // being established. If already closed, close the upstream immediately and skip
         // registration to prevent a permanently-leaked session in the map.
         if(browserSession.isOpen()) {
            upstreamSessions.put(browserSession.getId(), upstreamSession);
         }
         else {
            closeQuietly(upstreamSession, CloseStatus.NORMAL);
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to connect assistant WebSocket proxy to upstream {}: {}",
            upstreamUrl, e.getMessage());
         closeQuietly(browserSession, CloseStatus.SERVER_ERROR);
      }
   }

   @Override
   protected void handleTextMessage(WebSocketSession browserSession, TextMessage message) throws Exception {
      WebSocketSession upstream = upstreamSessions.get(browserSession.getId());

      if(upstream != null && upstream.isOpen()) {
         synchronized(upstream) {
            upstream.sendMessage(message);
         }
      }
   }

   @Override
   protected void handleBinaryMessage(WebSocketSession browserSession, BinaryMessage message) throws Exception {
      WebSocketSession upstream = upstreamSessions.get(browserSession.getId());

      if(upstream != null && upstream.isOpen()) {
         synchronized(upstream) {
            upstream.sendMessage(message);
         }
      }
   }

   @Override
   public void afterConnectionClosed(WebSocketSession browserSession, CloseStatus status) {
      WebSocketSession upstream = upstreamSessions.remove(browserSession.getId());

      if(upstream != null && upstream.isOpen()) {
         closeQuietly(upstream, status);
      }
   }

   @Override
   public void handleTransportError(WebSocketSession browserSession, Throwable ex) {
      LOG.warn("Assistant WebSocket browser transport error: {}", ex.getMessage());
      WebSocketSession upstream = upstreamSessions.remove(browserSession.getId());

      if(upstream != null && upstream.isOpen()) {
         closeQuietly(upstream, CloseStatus.SERVER_ERROR);
      }
   }

   private String extractProxiedPath(URI uri) {
      if(uri == null) {
         return "/";
      }

      String path = uri.getPath();
      String prefix = AIAssistantController.PROXY_PATH_PREFIX;
      int idx = path.indexOf(prefix);

      // Verify the match starts on a path-segment boundary (the char before the prefix is '/'
      // or the prefix is at position 0) and is followed by '/' or end-of-string.
      // This prevents a spurious match if the prefix string appears inside a path segment.
      if(idx >= 0 && (idx == 0 || path.charAt(idx - 1) == '/')) {
         String rest = path.substring(idx + prefix.length());

         if(rest.isEmpty() || rest.startsWith("/")) {
            return rest.isEmpty() ? "/" : rest;
         }
      }

      return "/";
   }

   private String toWsUrl(String httpUrl) {
      if(httpUrl.startsWith("https://")) {
         return "wss://" + httpUrl.substring("https://".length());
      }
      else if(httpUrl.startsWith("http://")) {
         return "ws://" + httpUrl.substring("http://".length());
      }

      return httpUrl;
   }

   private void closeQuietly(WebSocketSession session, CloseStatus status) {
      if(session.isOpen()) {
         try {
            session.close(status);
         }
         catch(IOException ignored) {
         }
      }
   }

   private StandardWebSocketClient buildWsClient() {
      StandardWebSocketClient client = new StandardWebSocketClient();

      // SSL verification is configurable via chat.app.server.ssl.verify.
      // Default: trust all, suitable for private-network deployments with self-signed certs
      // (matching the nginx "proxy_ssl_verify off" used in Docker Compose).
      // Set to "true" to use the JVM default trust store in production.
      // Changing this property requires a server restart.
      if(!AIAssistantController.isSslVerifyEnabled()) {
         try {
            SSLContext sslContext = SSLContextBuilder.create()
               .loadTrustMaterial(null, (chain, authType) -> true)
               .build();
            client.setUserProperties(Map.of("org.apache.tomcat.websocket.SSL_CONTEXT", sslContext));
         }
         catch(NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            LOG.warn("Could not configure trust-all SSL context for assistant WebSocket proxy", e);
         }
      }

      return client;
   }

   @PostConstruct
   private void startCleanup() {
      cleanup = Executors.newSingleThreadScheduledExecutor(r -> {
         Thread t = new Thread(r, "assistant-ws-proxy-cleanup");
         t.setDaemon(true);
         return t;
      });
      // Periodically evict entries whose upstream session closed without triggering
      // afterConnectionClosed (e.g. network failure, proxy timeout).
      cleanup.scheduleAtFixedRate(this::removeClosedSessions, 60, 60, TimeUnit.SECONDS);
   }

   @PreDestroy
   private void stopCleanup() {
      if(cleanup != null) {
         cleanup.shutdownNow();
      }
   }

   private void removeClosedSessions() {
      upstreamSessions.entrySet().removeIf(e -> !e.getValue().isOpen());
   }

   private StandardWebSocketClient getWsClient() {
      if(wsClient == null) {
         synchronized(this) {
            if(wsClient == null) {
               wsClient = buildWsClient();
            }
         }
      }

      return wsClient;
   }

   private volatile StandardWebSocketClient wsClient;
   private volatile ScheduledExecutorService cleanup;
   private final Map<String, WebSocketSession> upstreamSessions = new ConcurrentHashMap<>();
   private static final List<String> FORWARD_WS_HEADERS =
      List.of("Authorization", "x-client-id", "x-request-id");
   private static final Logger LOG = LoggerFactory.getLogger(AssistantWebSocketProxyHandler.class);
}
