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
package inetsoft.web.messaging;

import inetsoft.sree.RepletRepository;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import inetsoft.util.Tool;
import inetsoft.web.admin.server.NodeProtectionService;
import inetsoft.web.security.AbstractLogoutFilter;
import inetsoft.web.session.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.*;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.Principal;
import java.util.*;

@Component
public class SessionConnectionService {

   @Autowired
   public SessionConnectionService(IgniteSessionRepository sessionRepository,
                                   AuthenticationService authenticationService,
                                   ApplicationEventPublisher eventPublisher)
   {
      this.sessionRepository = sessionRepository;
      this.authenticationService = authenticationService;
      this.eventPublisher = eventPublisher;
   }

   @PostConstruct
   public void addSessionListener() {
      SecurityEngine.getSecurity().addAuthenticationChangeListener(authenticationChangeListener);
      cluster = Cluster.getInstance();
      cluster.addMessageListener(listener);
   }

   @PreDestroy
   public void removeSessionListener() {
      SecurityEngine.getSecurity().removeAuthenticationChangeListener(authenticationChangeListener);
   }

   public void webSocketConnected(WebSocketSession session) {
      cleanReferences();

      String wsSessionId = session.getId();
      eventPublisher.publishEvent(new WebsocketConnectionEvent(this, wsSessionId, true));
      String httpSessionId = (String) session.getAttributes().get("SPRING.SESSION.ID");

      synchronized(httpSessions) {
         httpSessions.computeIfAbsent(httpSessionId, id -> new HashSet<>()).add(wsSessionId);
         webSocketSessions.put(
            wsSessionId, new WebSocketSessionRef(httpSessionId, wsSessionId, session));
      }
   }

   public void webSocketDisconnected(WebSocketSession session) {
      cleanReferences();

      String wsSessionId = session.getId();
      eventPublisher.publishEvent(new WebsocketConnectionEvent(this, wsSessionId, false));
      String httpSessionId = (String) session.getAttributes().get("SPRING.SESSION.ID");

      synchronized(httpSessions) {
         Set<String> wsSessionIds = httpSessions.get(httpSessionId);

         if(wsSessionIds != null) {
            wsSessionIds.remove(wsSessionId);

            if(wsSessionIds.isEmpty()) {
               httpSessions.remove(httpSessionId);
            }

            webSocketSessions.remove(wsSessionId);
         }
      }
   }
   
   private void messageReceived(MessageEvent event) {
      Object message = event.getMessage();

      if(event.getMessage() instanceof SessionExpiredEvent sessionExpiredEvent) {
         onSessionExpiredEvent(sessionExpiredEvent);
      }
      else if(message instanceof PrincipalChangedEvent principalChangedEvent) {
         handlePrincipalChanged(principalChangedEvent);
      }
   }

   private void onSessionExpiredEvent(SessionExpiredEvent event) {
      cleanReferences();
      String httpSessionId = event.getSessionId();

      synchronized(httpSessions) {
         Set<String> wsSessionIds = httpSessions.remove(httpSessionId);

         if(wsSessionIds != null) {
            Principal principal = event.getPrincipalCookie();
            // only redirect if authenticated
            boolean authenticated =
               principal != null && !XPrincipal.ANONYMOUS.equals(principal.getName());
            // don't redirect if already coming from the logout filter
            boolean fromLogout = Boolean.TRUE.equals(event.getLoggedOutAttribute());

            // redirect to login page if not anonymous, otherwise, allow to reconnect
            CloseStatus status;

            if(!fromLogout && authenticated) {
               // redirect to the login page with session timeout message
               status = new CloseStatus(4002, "Session timeout");
            }
            else if(authenticated) {
               // redirect to the login page without the session timeout message
               status = new CloseStatus(4001, "Logged out");
            }
            else {
               // allow to reconnect if anonymous
               status = CloseStatus.NORMAL;
            }

            for(String wsSessionId : wsSessionIds) {
               WebSocketSessionRef ref = webSocketSessions.remove(wsSessionId);

               if(ref != null) {
                  WebSocketSession wsSession = ref.getSession();

                  if(wsSession != null) {
                     try {
                        wsSession.close(status);
                     }
                     catch(IOException e) {
                        LOG.warn("Failed to close websocket connection", e);
                     }
                  }
               }
            }
         }
      }
   }

   private void handlePrincipalChanged(PrincipalChangedEvent event) {
      String httpSessionId = event.getSessionId();
      Set<String> wsSessionIds = httpSessions.get(httpSessionId);

      if(wsSessionIds != null) {
         for(String wsSessionId : wsSessionIds) {
            WebSocketSessionRef ref = webSocketSessions.get(wsSessionId);
            WebSocketSession session = ref == null ? null : ref.getSession();

            if(session != null) {
               SRPrincipal principal = event.getNewPrincipal();

               if((!event.isEm() || principal == null) &&
                  session.getAttributes().get(RepletRepository.PRINCIPAL_COOKIE) != null)
               {
                  session.getAttributes().put(RepletRepository.PRINCIPAL_COOKIE, principal);
               }

               if((event.isEm() || principal == null) &&
                  session.getAttributes().get(RepletRepository.EM_PRINCIPAL_COOKIE) != null)
               {
                  session.getAttributes().put(RepletRepository.EM_PRINCIPAL_COOKIE, principal);
               }

               XPrincipal user = (XPrincipal) session.getPrincipal();

               if(user != null && (event.isEm() && SUtil.isEMPrincipal(user) || !event.isEm() && !SUtil.isEMPrincipal(user))) {
                  user.copyContent(principal);
               }
            }
         }
      }
   }

   private void cleanReferences() {
      synchronized(httpSessions) {
         for(Iterator<WebSocketSessionRef> i = webSocketSessions.values().iterator(); i.hasNext();)
         {
            WebSocketSessionRef session = i.next();

            if(session.getSession() == null) {
               i.remove();
               Set<String> wsSessionIds = httpSessions.get(session.getHttpSessionId());

               if(wsSessionIds != null) {
                  wsSessionIds.remove(session.getWsSessionId());

                  if(wsSessionIds.isEmpty()) {
                     httpSessions.remove(session.getHttpSessionId());
                  }
               }
            }
         }
      }
   }

   private void authenticationChanged(AuthenticationChangeEvent event) {
      if(event.getType() == Identity.ORGANIZATION) {
         SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
         String oldOrgName = event.getOldID() == null ? null : event.getOldID().name;
         String newOrgName = event.getNewID() == null ? null : event.getNewID().name;
         String oldOrgId = event.getOldOrgID();
         String newOrgID = event.getNewOrgID();

         if(!Tool.equals(oldOrgName, newOrgName)) {
            logoutOutSessionUsersContainingOrg(oldOrgName, oldOrgId);
         }
         else if(!Tool.equals(oldOrgId, newOrgID)) {
            logoutOutSessionUsersContainingOrg(oldOrgName, oldOrgId);
         }
      }

   }

   private void logoutOutSessionUsersContainingOrg(String oldOrgName, String oldOrgId) {
      for(SRPrincipal principal : sessionRepository.getActiveSessions()) {
         if(principal != null && (Tool.equals(oldOrgName, principal.getIdentityID().orgID) ||
                                  Tool.equals(oldOrgId, principal.getOrgId()))) {
            Map<String, IgniteSessionRepository.IgniteSession> map =
               sessionRepository.findByIndexNameAndIndexValue(
               FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal.getName());

            for(Map.Entry<String, IgniteSessionRepository.IgniteSession> e : map.entrySet()) {
               SRPrincipal sessionPrincipal =
                  e.getValue().getAttribute(RepletRepository.PRINCIPAL_COOKIE);

               if(sessionPrincipal != null && Tool.equals(oldOrgName, sessionPrincipal.getIdentityID().orgID)) {
                  authenticationService.logout(sessionPrincipal, sessionPrincipal.getHost(), "", false);
               }
            }
         }
      }
   }

   private Cluster cluster;
   private final MessageListener listener = this::messageReceived;
   private final AuthenticationService authenticationService;
   private final IgniteSessionRepository sessionRepository;
   private final ApplicationEventPublisher eventPublisher;
   private final Map<String, WebSocketSessionRef> webSocketSessions = new HashMap<>();
   private final Map<String, Set<String>> httpSessions = new HashMap<>();
   private final AuthenticationChangeListener authenticationChangeListener = this::authenticationChanged;
   private static final Logger LOG = LoggerFactory.getLogger(SessionConnectionService.class);

   private static final class WebSocketSessionRef {
      public WebSocketSessionRef(String httpSessionId, String wsSessionId, WebSocketSession session)
      {
         this.httpSessionId = httpSessionId;
         this.wsSessionId = wsSessionId;
         this.session = new WeakReference<>(session);
      }

      public String getHttpSessionId() {
         return httpSessionId;
      }

      public String getWsSessionId() {
         return wsSessionId;
      }

      public WebSocketSession getSession() {
         return session.get();
      }

      private final String httpSessionId;
      private final String wsSessionId;
      private final WeakReference<WebSocketSession> session;
   }
}
