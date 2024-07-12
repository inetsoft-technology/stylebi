/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web;

import com.github.benmanes.caffeine.cache.*;
import inetsoft.sree.RepletRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.util.Tool;
import inetsoft.util.audit.SessionRecord;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpSessionBindingListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.*;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;

import javax.xml.xpath.*;
import java.io.*;
import java.security.Principal;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A {@link SessionRepository} backed by a {@link java.util.Map} and that uses a
 * {@link MapSession}. By default a {@link java.util.concurrent.ConcurrentHashMap} is
 * used, but a custom {@link java.util.Map} can be injected to use distributed maps
 * provided by NoSQL stores like Redis and Hazelcast.
 * <p>
 * <p>
 * The implementation does NOT support firing {@link SessionDeletedEvent} or
 * {@link SessionExpiredEvent}.
 * </p>
 *
 * @author Rob Winch
 * @since 1.0
 */
public class MapSessionRepository implements SessionRepository<MapSession>,
   FindByIndexNameSessionRepository<MapSession>, RemovalListener<String, MapSession>,
   SessionListener
{
   /**
    * Creates an instance backed by a {@link java.util.concurrent.ConcurrentHashMap}.
    */
   public MapSessionRepository(ServletContext servletContext,
                               SecurityEngine securityEngine,
                               AuthenticationService authenticationService)
   {
      this.servletContext = servletContext;
      this.securityEngine = securityEngine;
      this.authenticationService = authenticationService;
      this.defaultMaxInactiveInterval = getSessionTimeout();
      authenticationService.addSessionListener(this);

      this.sessions = Caffeine.newBuilder()
         .expireAfterAccess(this.defaultMaxInactiveInterval, TimeUnit.SECONDS)
         .maximumSize(getMaxActiveSessions())
         .removalListener(this)
         .build();
   }

   @PreDestroy
   public void shutdown() {
      authenticationService.removeSessionListener(this);
   }

   @Override
   public void save(MapSession session) {
      this.sessions.put(session.getId(), session);

      if(session.modifiedAttributes.containsKey(RepletRepository.PRINCIPAL_COOKIE)) {
         Object[] values = session.modifiedAttributes.get(RepletRepository.PRINCIPAL_COOKIE);
         firePrincipalChange(session, (SRPrincipal) values[0], (SRPrincipal) values[1]);
      }

      session.modifiedAttributes.clear();
   }

   @Override
   public MapSession findById(String id) {
      Session saved = this.sessions.getIfPresent(id);

      if(saved == null) {
         return null;
      }

      if(saved.isExpired()) {
         sessionExpired(saved.getId());
         return null;
      }

      return new MapSession(saved);
   }

   public void invalidate(String id) {
      sessionExpired(id);
   }

   private void sessionExpired(String id) {
      final Session session = this.sessions.getIfPresent(id);

      if(session != null) {
         this.sessions.invalidate(id);
         logout(session, "");
      }
   }

   @Override
   public void deleteById(String id) {
      this.sessions.invalidate(id);
   }

   @Override
   public MapSession createSession() {
      MapSession result = new MapSession(servletContext);
      result.setMaxInactiveInterval(
         Duration.of(this.defaultMaxInactiveInterval, ChronoUnit.SECONDS));
      return result;
   }

   /**
    * Iterate over sessions and remove expired sessions.
    */
   @Scheduled(fixedRate = 180000L)
   public void evictExpiredSessions() {
      this.sessions.cleanUp();
   }

   /**
    * Find a Map of the session id to the {@link Session} of all sessions that contain
    * the session attribute with the name
    * {@link FindByIndexNameSessionRepository#PRINCIPAL_NAME_INDEX_NAME} and the value of
    * the specified principal name.
    *
    * @param indexName  the name if the index (i.e.
    *                   {@link FindByIndexNameSessionRepository#PRINCIPAL_NAME_INDEX_NAME})
    * @param indexValue the value of the index to search for.
    *
    * @return a Map (never null) of the session id to the {@link Session} of all sessions
    * that contain the session specified index name and the value of the specified index
    * name. If no results are found, an empty Map is returned.
    */
   @Override
   public Map<String, MapSession> findByIndexNameAndIndexValue(String indexName,
                                                                    String indexValue)
   {
      if(!PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
         return Collections.emptyMap();
      }

      return sessions.asMap().values().stream()
         .filter(session -> isSessionForUser(session, indexValue))
         .collect(Collectors.toMap(Session::getId, Function.identity()));
   }

   @Override
   public void onRemoval(String key, MapSession value, RemovalCause cause) {
      if(cause.wasEvicted()) {
         logout(value, cause == RemovalCause.EXPIRED ? SessionRecord.LOGOFF_SESSION_TIMEOUT : "");
      }

      if(cause != RemovalCause.REPLACED) {
         fireSessionExpired(value);

         // HttpSessionBindingListener should be notified of removal when the session is invalidated
         for(String attributeName : value.getAttributeNames()) {
            Object attributeValue = value.getAttribute(attributeName);

            if(attributeValue instanceof HttpSessionBindingListener) {
               value.removeAttribute(attributeName);
            }
         }

         SRPrincipal principal = value.getAttribute(RepletRepository.PRINCIPAL_COOKIE);

         if(principal != null) {
            firePrincipalChange(value, principal, null);
         }
      }
   }

   @Override
   public void loggedIn(SessionEvent event) {
      // no-op
   }

   @Override
   public void loggedOut(SessionEvent event) {
      if(event.isInvalidateSession()) {
         Principal principal = event.getPrincipal();
         sessions.asMap().entrySet().stream()
            .filter(e -> principal.equals(e.getValue().getAttribute(RepletRepository.PRINCIPAL_COOKIE)))
            .map(Map.Entry::getKey)
            .findFirst()
            .ifPresent(sessions::invalidate);
      }
   }

   /**
    * Gets the list of principals with active sessions.
    *
    * @return the principal list.
    */
   public List<SRPrincipal> getActiveSessions() {
      return sessions.asMap().values().stream()
         .map(session -> session.getAttribute(RepletRepository.PRINCIPAL_COOKIE))
         .filter(Objects::nonNull)
         .map(SRPrincipal.class::cast)
         .collect(Collectors.toList());
   }

   private boolean isSessionForUser(Session session, String userName) {
      final Object user = session.getAttribute(RepletRepository.PRINCIPAL_COOKIE);
      return user instanceof SRPrincipal && userName.equals(((SRPrincipal) user).getName());
   }

   private void logout(Session session, String logoffReason) {
      final Principal principal = session.getAttribute(RepletRepository.PRINCIPAL_COOKIE);

      if(principal instanceof SRPrincipal && securityEngine.isActiveUser(principal)) {
         final String remoteHost = ((SRPrincipal) principal).getUser().getIPAddress();
         authenticationService.logout(principal, remoteHost, logoffReason);
      }
   }

   /**
    * Attempt to read webapp.session-config.session-timeout from WEB-INF/web.xml.
    * If reading this property fails for any reason, returns null.
    *
    * @return the timeout in seconds.
    */
   private int getSessionTimeout() {
      final String webXmlPath = this.servletContext.getRealPath("/WEB-INF/web.xml");

      if(webXmlPath != null) {
         final File webXmlFile = new File(webXmlPath);

         if(webXmlFile.exists()) {
            final XPath xpath = XPathFactory.newInstance().newXPath();

            try(InputStream input = new FileInputStream(webXmlFile)) {
               final Document document = Tool.parseXML(input);
               final String xmlPath = "/web-app/session-config/session-timeout/text()";
               final Double timeout =
                  (Double) xpath.evaluate(xmlPath, document, XPathConstants.NUMBER);

               if(timeout != null && !Double.isNaN(timeout)) {
                  int value = (int) TimeUnit.MINUTES.toSeconds(timeout.longValue());
                  SreeEnv.setProperty("http.session.timeout", Integer.toString(value));
                  return value;
               }
            }
            catch(Exception e) {
               LOG.error("Failed to read session-timeout from web.xml", e);
            }
         }
      }

      String property = SreeEnv.getProperty("http.session.timeout");

      if(StringUtils.hasText(property)) {
         try {
            return Integer.parseInt(property);
         }
         catch(NumberFormatException e) {
            LOG.error("Invalid value for http.session.timeout: {}", property, e);
         }
      }

      property = Integer.toString(MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS);
      SreeEnv.setProperty("http.session.timeout", property);
      return MapSession.DEFAULT_MAX_INACTIVE_INTERVAL_SECONDS;
   }

   private int getMaxActiveSessions() {
      String property = SreeEnv.getProperty("http.session.maxActive");

      if(StringUtils.hasText(property)) {
         try {
            return Integer.parseInt(property);
         }
         catch(NumberFormatException e) {
            LOG.error("Invalid value for http.session.maxActive: {}", property, e);
         }
      }

      return 10000;
   }

   private void firePrincipalChange(Session session, SRPrincipal oldValue,
                                    SRPrincipal newValue)
   {
      PrincipalChangeEvent event = new PrincipalChangeEvent(this, oldValue, newValue, session);
      Set<PrincipalChangeListener> principalListeners;

      synchronized(this.principalListeners) {
         principalListeners = new HashSet<>(this.principalListeners);
      }

      for(PrincipalChangeListener listener : principalListeners) {
         listener.principalChanged(event);
      }
   }

   public void addPrincipalChangeListener(PrincipalChangeListener l) {
      synchronized(principalListeners) {
         principalListeners.add(l);
      }
   }

   public void removePrincipalChangeListener(PrincipalChangeListener l) {
      synchronized(principalListeners) {
         principalListeners.remove(l);
      }
   }

   private void fireSessionExpired(Session session) {
      SessionExpirationEvent event = new SessionExpirationEvent(this, session);
      Set<SessionExpirationListener> sessionListeners;

      synchronized(this.sessionListeners) {
         sessionListeners = new HashSet<>(this.sessionListeners);
      }

      for(SessionExpirationListener l : sessionListeners) {
         l.sessionExpired(event);
      }
   }

   public void addSessionExpirationListener(SessionExpirationListener l) {
      synchronized(sessionListeners) {
         sessionListeners.add(l);
      }
   }

   public void removeSessionExpirationListener(SessionExpirationListener l) {
      synchronized(sessionListeners) {
         sessionListeners.remove(l);
      }
   }

   /**
    * If non-null, this value is used to override
    * {@link Session#setMaxInactiveInterval(Duration)}.
    */
   private int defaultMaxInactiveInterval;

   private final Cache<String, MapSession> sessions;
   private final ServletContext servletContext;
   private final SecurityEngine securityEngine;
   private final AuthenticationService authenticationService;
   private final Set<PrincipalChangeListener> principalListeners = new HashSet<>();
   private final Set<SessionExpirationListener> sessionListeners = new HashSet<>();
   private static final Logger LOG = LoggerFactory.getLogger(MapSessionRepository.class);

   public interface PrincipalChangeListener extends EventListener {
      void principalChanged(PrincipalChangeEvent event);
   }

   public static final class PrincipalChangeEvent extends EventObject {
      public PrincipalChangeEvent(Object source, SRPrincipal oldValue, SRPrincipal newValue,
                                  Session session)
      {
         super(source);
         this.oldValue = oldValue;
         this.newValue = newValue;
         this.session = session;
      }

      public SRPrincipal getOldValue() {
         return oldValue;
      }

      public SRPrincipal getNewValue() {
         return newValue;
      }

      public Session getSession() {
         return session;
      }

      private final SRPrincipal oldValue;
      private final SRPrincipal newValue;
      private final Session session;
   }

   public interface SessionExpirationListener extends EventListener {
      void sessionExpired(SessionExpirationEvent event);
   }

   public static final class SessionExpirationEvent extends EventObject {
      public SessionExpirationEvent(Object source, Session session) {
         super(source);
         this.session = session;
      }


      public Session getSession() {
         return session;
      }

      private final Session session;
   }
}
