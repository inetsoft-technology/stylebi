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

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;
import org.springframework.web.socket.server.standard.TomcatRequestUpgradeStrategy;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers the AI assistant WebSocket reverse proxy handler.
 * Uses a uniquely-named {@code HandlerMapping} bean instead of {@code @EnableWebSocket}
 * to avoid the {@code webSocketHandlerMapping} bean-name conflict that arises when
 * {@code @EnableWebSocket} and {@code @EnableWebSocketMessageBroker} are both present in
 * the same application context.  The STOMP infrastructure (WebSocketConfig) wins that
 * name collision and silently discards any handlers registered through the
 * {@code @EnableWebSocket} path.
 *
 */
@Configuration
public class AssistantWebSocketProxyConfig {

   public AssistantWebSocketProxyConfig(AssistantWebSocketProxyHandler handler) {
      this.handler = handler;
   }

   @Bean
   public HandlerMapping assistantProxyWebSocketHandlerMapping() throws Exception {
      // Explicitly supply TomcatRequestUpgradeStrategy so the handshake handler is fully
      // initialised without relying on afterPropertiesSet() / auto-detection (which requires
      // being wired through the normal Spring WebSocket infrastructure).
      // TomcatRequestUpgradeStrategy is required here because this handler mapping is registered
      // outside the normal @EnableWebSocket infrastructure. This assumes Tomcat as the servlet
      // container, which is the only supported container for StyleBI.
      DefaultHandshakeHandler handshakeHandler =
         new DefaultHandshakeHandler(new TomcatRequestUpgradeStrategy());
      WebSocketHttpRequestHandler wsHandler = new WebSocketHttpRequestHandler(handler, handshakeHandler);

      Map<String, Object> urlMap = new LinkedHashMap<>();
      urlMap.put("/api/assistant/proxy", wsHandler);
      urlMap.put("/api/assistant/proxy/**", wsHandler);

      SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping() {
         @Override
         protected Object getHandlerInternal(HttpServletRequest request) throws Exception {
            // Only handle WebSocket upgrade requests; return null for plain HTTP so that
            // DispatcherServlet falls through to AssistantProxyController.
            String upgrade = request.getHeader("Upgrade");

            if(!"websocket".equalsIgnoreCase(upgrade)) {
               return null;
            }

            return super.getHandlerInternal(request);
         }
      };

      mapping.setUrlMap(urlMap);
      // Run before RequestMappingHandlerMapping (0) and STOMP handler mapping (1).
      // STOMP won't match /api/assistant/proxy, so order only matters for HTTP fall-through.
      mapping.setOrder(-2);
      mapping.setPatternParser(new PathPatternParser());

      return mapping;
   }

   private final AssistantWebSocketProxyHandler handler;
}
