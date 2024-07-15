/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.security;

import inetsoft.sree.SreeEnv;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.session.web.socket.server.SessionRepositoryMessageInterceptor;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.messaging.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filter that rejects websocket upgrade requests if they will exceed the maximum number of
 * connections per client.
 */
public class WebSocketLimitFilter implements Filter, ApplicationListener<ApplicationEvent> {
   @Override
   public void init(FilterConfig filterConfig) throws ServletException {
      // NO-OP
   }

   @Override
   public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException
   {
      HttpServletRequest httpRequest = (HttpServletRequest) request;

      if("GET".equals(httpRequest.getMethod())) {
         String connection = httpRequest.getHeader("Connection");
         String upgrade = httpRequest.getHeader("Upgrade");

         if(connection != null && "upgrade".equalsIgnoreCase(connection) &&
            upgrade != null && "websocket".equalsIgnoreCase(upgrade))
         {
            int maxConnections = 20;
            String property = SreeEnv.getProperty("http.session.maxWebsocketPerUser");

            if(StringUtils.hasText(property)) {
               try {
                  maxConnections = Integer.parseInt(property);
               }
               catch(NumberFormatException e) {
                  LOG.error("Invalid value of http.session.maxWebsocketPerUser: {}", property, e);
               }
            }

            HttpSession session = httpRequest.getSession(false);

            if(session != null) {
               String httpSessionId = session.getId();
               Map<String, String> wsSessions = sessions.get(httpSessionId);

               if(wsSessions != null && wsSessions.size() >= maxConnections) {
                  ((HttpServletResponse) response).sendError(
                     HttpServletResponse.SC_BAD_REQUEST,
                     "Maximum number of connections per client exceeded");
                  return;
               }
            }
         }
      }

      chain.doFilter(request, response);
   }

   @Override
   public void destroy() {
      // NO-OP
   }

   @Override
   public void onApplicationEvent(ApplicationEvent event) {
      if(event instanceof SessionDestroyedEvent) {
         SessionDestroyedEvent e = (SessionDestroyedEvent) event;
         afterSessionClosed(e.getSessionId());
      }
      else if(event instanceof SessionConnectEvent) {
         SessionConnectEvent e = (SessionConnectEvent) event;
         String httpSessionId = getHttpSessionId(e);
         String wsSessionId = getWebSocketSessionId(e);
         afterConnectionEstablished(httpSessionId, wsSessionId);
      }
      else if(event instanceof SessionDisconnectEvent) {
         SessionDisconnectEvent e = (SessionDisconnectEvent) event;
         String httpSessionId = getHttpSessionId(e);
         afterConnectionClosed(httpSessionId, e.getSessionId());
      }
   }

   private void afterSessionClosed(String httpSessionId) {
      if(httpSessionId != null) {
         sessions.remove(httpSessionId);
      }
   }

   private void afterConnectionEstablished(String httpSessionId, String wsSessionId) {
      if(httpSessionId != null) {
         sessions.computeIfAbsent(httpSessionId, id -> new ConcurrentHashMap<>())
            .put(wsSessionId, wsSessionId);
      }
   }

   private void afterConnectionClosed(String httpSessionId, String wsSessionId) {
      if(httpSessionId != null && wsSessionId != null) {
         Map<String, String> wsSessions = sessions.get(httpSessionId);

         if(wsSessions != null) {
            wsSessions.remove(wsSessionId);

            if(wsSessions.isEmpty()) {
               sessions.remove(httpSessionId);
            }
         }
      }
   }

   private String getWebSocketSessionId(AbstractSubProtocolEvent event) {
      return SimpMessageHeaderAccessor.wrap(event.getMessage()).getSessionId();
   }

   private String getHttpSessionId(AbstractSubProtocolEvent event) {
      Map<String, Object> attributes =
         SimpMessageHeaderAccessor.wrap(event.getMessage()).getSessionAttributes();

      if(attributes == null) {
         return null;
      }

      return SessionRepositoryMessageInterceptor.getSessionId(attributes);
   }

   private final ConcurrentHashMap<String, Map<String, String>> sessions =
      new ConcurrentHashMap<>();
   private static final Logger LOG = LoggerFactory.getLogger(WebSocketLimitFilter.class);
}
