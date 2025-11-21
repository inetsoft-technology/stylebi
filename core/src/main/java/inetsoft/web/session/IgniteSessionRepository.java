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

package inetsoft.web.session;

import inetsoft.sree.RepletRepository;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.*;
import inetsoft.util.audit.SessionRecord;
import inetsoft.web.admin.server.NodeProtectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.*;
import org.springframework.util.Assert;

import javax.cache.Cache;
import java.io.Serializable;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class IgniteSessionRepository
   implements FindByIndexNameSessionRepository<IgniteSessionRepository.IgniteSession>,
   MapChangeListener<String, MapSession>, SessionListener, InitializingBean, DisposableBean
{
   public IgniteSessionRepository(SecurityEngine securityEngine,
                                  AuthenticationService authenticationService,
                                  NodeProtectionService nodeProtectionService)
   {
      this.securityEngine = securityEngine;
      this.authenticationService = authenticationService;
      this.nodeProtectionService = nodeProtectionService;
   }

   @Override
   public void afterPropertiesSet() {
      authenticationService.addSessionListener(this);
      this.cluster = Cluster.getInstance();
      this.sessions = cluster.getCache(
         this.sessionMapName, true, new PropertyAccessedExpiryPolicy());
      this.cluster.addReplicatedMapListener(this.sessionMapName, this);
   }

   @Override
   public void destroy() throws Exception {
      this.cluster.removeReplicatedMapListener(this.sessionMapName, this);
   }

   public void setSessionMapName(String sessionMapName) {
      Assert.hasText(sessionMapName, "Map name must not be empty");
      this.sessionMapName = sessionMapName;
   }

   public void setFlushMode(FlushMode flushMode) {
      Objects.requireNonNull(flushMode, "FlushMode cannot be null");
      this.flushMode = flushMode;
   }

   public void setSaveMode(SaveMode saveMode) {
      Objects.requireNonNull(saveMode, "SaveMode cannot be null");
      this.saveMode = saveMode;
   }

   public void setSessionIdGenerator(SessionIdGenerator sessionIdGenerator) {
      Objects.requireNonNull(sessionIdGenerator, "SessionIdGenerator cannot be null");
      this.sessionIdGenerator = sessionIdGenerator;
   }

   @Override
   public IgniteSession createSession() {
      MapSession cached = new MapSession(this.sessionIdGenerator);
      javax.cache.expiry.Duration expiry = PropertyAccessedExpiryPolicy.getExpiryFromProperty();
      cached.setMaxInactiveInterval(
         Duration.ofSeconds(expiry.getTimeUnit().toSeconds(expiry.getDurationAmount())));
      createSessionAttributeMap(cached.getId());
      IgniteSession session = new IgniteSession(cached, true);
      session.flushImmediateIfNecessary();
      return session;
   }

   @Override
   public void save(IgniteSession session) {
      if(session.isNew) {
         this.sessions.put(session.getId(), session.getDelegate());
      }
      else if(session.sessionIdChanged) {
         this.sessions.remove(session.originalId);
         session.originalId = session.getId();
         this.sessions.put(session.getId(), session.getDelegate());
      }
      else if(session.hasChanges()) {
         Instant lastAccessedTime =
            session.lastAccessedTimeChanged ? session.getLastAccessedTime() : null;
         Duration maxInactiveInterval =
            session.maxInactiveIntervalChanged ? session.getMaxInactiveInterval() : null;

         this.sessions.invoke(
            session.getId(), new SessionUpdateEntryProcessor(),
            lastAccessedTime, maxInactiveInterval);
      }

      session.clearChangeFlags();
   }

   @Override
   public IgniteSession findById(String id) {
      MapSession saved = this.sessions.get(id);

      if(saved == null) {
         return null;
      }

      if(saved.isExpired()) {
         deleteById(saved.getId());
         return null;
      }

      return new IgniteSession(saved, false);
   }

   @Override
   public void deleteById(String id) {
      MapSession session = this.sessions.get(id);
      IgniteSession igniteSession = new IgniteSession(session, false);
      this.sessions.remove(id);
      sendApplicationEvent(new SessionExpiredEvent(this.getClass().getName(), igniteSession));
   }

   @Override
   public Map<String, IgniteSession> findByIndexNameAndIndexValue(String indexName,
                                                                  String indexValue)
   {
      if(!PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
         return Map.of();
      }

      Map<String, IgniteSession> result = new HashMap<>();

      sessions.iterator().forEachRemaining(session -> {
         IgniteSession igniteSession = findById(session.getValue().getId());

         if(isSessionForUser(igniteSession, indexValue)) {
            result.put(session.getValue().getId(), igniteSession);
         }
      });

      return result;
   }

   private boolean isSessionForUser(IgniteSession session, String userName) {
      final Object user = session.getAttribute(RepletRepository.PRINCIPAL_COOKIE);
      return user instanceof SRPrincipal && userName.equals(((SRPrincipal) user).getName());
   }

   @Override
   public void entryAdded(EntryEvent<String, MapSession> event) {
      MapSession session = event.getValue();

      if(session.getId().equals(session.getOriginalId())) {
         LOG.debug("Session created with ID: {}", session.getId());
         sendApplicationEvent(new SessionCreatedEvent(this.getClass().getName(), session));
      }
   }

   @Override
   public void entryRemoved(EntryEvent<String, MapSession> event) {
      MapSession session = event.getValue();

      if(session != null) {
         if(session.isExpired()) {
            entryExpired(event);
         }
         else {
            LOG.debug("Session deleted with ID: {}", session.getId());
            sendApplicationEvent(new SessionDeletedEvent(this.getClass().getName(), session));
            logout(session, "");
            destroySessionAttributeMap(event.getOldValue().getId());
         }
      }
   }

   private void sendApplicationEvent(ApplicationEvent event) {
      try {
         cluster.sendMessage(event);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to send application event", e);
      }
   }

   @Override
   public void entryExpired(EntryEvent<String, MapSession> event) {
      LOG.debug("Session expired with ID: {}", event.getOldValue().getId());
      logout(event.getOldValue(), SessionRecord.LOGOFF_SESSION_TIMEOUT);
      sendApplicationEvent(new SessionExpiredEvent(this.getClass().getName(), event.getOldValue()));
      destroySessionAttributeMap(event.getOldValue().getId());
   }

   private void logout(Session session, String logoffReason) {
      Principal principal = session.getAttribute(RepletRepository.PRINCIPAL_COOKIE);

      if(principal instanceof SRPrincipal srp) {
         if(securityEngine.isActiveUser(principal)) {
            String remoteHost = srp.getUser().getIPAddress();
            authenticationService.logout(principal, remoteHost, logoffReason);
         }
      }
   }

   @Override
   public void entryUpdated(EntryEvent<String, MapSession> event) {
      // no-op
   }

   @Override
   public void loggedIn(inetsoft.sree.security.SessionEvent event) {
      // no-op
   }

   @Override
   public void loggedOut(inetsoft.sree.security.SessionEvent event) {
      if(event.isInvalidateSession()) {
         Principal principal = event.getPrincipal();

         for(Iterator<Cache.Entry<String, MapSession>> i = sessions.iterator(); i.hasNext(); ) {
            Cache.Entry<String, MapSession> entry = i.next();
            IgniteSession igniteSession = findById(entry.getValue().getId());

            if(principal.equals(igniteSession.getAttribute(RepletRepository.PRINCIPAL_COOKIE))) {
               i.remove();
               break;
            }
         }
      }
   }

   public List<SRPrincipal> getActiveSessions() {
      List<SRPrincipal> result = new ArrayList<>();
      sessions.iterator().forEachRemaining(session -> {
         IgniteSessionRepository.IgniteSession igniteSession = findById(session.getValue().getId());

         // could be out of sync due to session expiration, need to check for null
         if(igniteSession != null) {
            SRPrincipal principal = igniteSession.getAttribute(RepletRepository.PRINCIPAL_COOKIE);

            if(principal != null) {
               result.add(principal);
            }
         }
      });

      return result;
   }

   public void invalidateSession(String id) {
      final IgniteSession session = this.findById(id);

      if(session != null) {
         this.sessions.remove(id);
         sendApplicationEvent(new SessionExpiredEvent(this.getClass().getName(), session));
      }
   }

   @Scheduled(fixedRate = 20000) // Check every 20 seconds
   public void checkSessions() {
      long currentTime = System.currentTimeMillis();
      long protectionExpirationTime = nodeProtectionService.getExpirationTime();
      long protectionRemainingTime = protectionExpirationTime - currentTime;
      boolean protectionExpiring = protectionRemainingTime > 0 &&
         protectionRemainingTime < PROTECTION_EXPIRATION_WARNING_TIME;

      for(Cache.Entry<String, MapSession> entry : sessions) {
         MapSession session = entry.getValue();
         Instant lastAccessedTime = session.getLastAccessedTime();
         Duration maxInactiveInterval = session.getMaxInactiveInterval();
         long sessionRemainingTime = lastAccessedTime.toEpochMilli() +
            maxInactiveInterval.toMillis() - currentTime;

         // only check sessions that haven't expired already
         if(sessionRemainingTime > 0) {
            IgniteSession igniteSession = findById(session.getId());

            if(protectionExpiring) {
               Long lastProtectionWarningTime = igniteSession.getAttribute(LAST_PROTECTION_WARNING_TIME_ATTR);

               if(lastProtectionWarningTime == null ||
                  (currentTime - lastProtectionWarningTime) >= PROTECTION_EXPIRATION_WARNING_INTERVAL)
               {
                  igniteSession.setAttribute(LAST_PROTECTION_WARNING_TIME_ATTR, currentTime);
                  sendApplicationEvent(new SessionExpiringSoonEvent(
                     this.getClass().getName(), igniteSession, protectionRemainingTime, true,
                     true));
               }
            }
            // if protection is no longer expiring then close the dialogs
            // this can happen if some other node was terminated first instead
            // and the protection on this node was extended
            else if(protectionExpirationTime == 0 &&
               igniteSession.getAttribute(LAST_PROTECTION_WARNING_TIME_ATTR) != null)
            {
               igniteSession.removeAttribute(LAST_PROTECTION_WARNING_TIME_ATTR);
               sendApplicationEvent(new SessionExpiringSoonEvent(
                  this.getClass().getName(), igniteSession, protectionRemainingTime, false,
                  true));
            }

            // warn the user if remaining time is less than EXPIRATION_WARNING_TIME
            if(sessionRemainingTime <= SESSION_EXPIRATION_WARNING_TIME) {
               igniteSession.setAttribute(EXPIRING_SOON_ATTR, true);
               sendApplicationEvent(new SessionExpiringSoonEvent(
                  this.getClass().getName(), igniteSession, sessionRemainingTime, true,
                  false));
            }
            else if(Boolean.TRUE.equals(igniteSession.getAttribute(EXPIRING_SOON_ATTR))) {
               igniteSession.removeAttribute(EXPIRING_SOON_ATTR);
               sendApplicationEvent(new SessionExpiringSoonEvent(
                  this.getClass().getName(), igniteSession, sessionRemainingTime, false,
                  false));
            }
         }
      }
   }

   private static String getSessionAttributeMapName(String sessionId) {
      return SESSION_ATTRIBUTE_MAP + sessionId;
   }

   private static DistributedMap<String, Object> createSessionAttributeMap(String sessionId) {
      DistributedMap<String, Object> map = Cluster.getInstance()
         .getReplicatedMap(getSessionAttributeMapName(sessionId));
      SESSION_ATTRIBUTE_MAPS.put(sessionId, map);
      return map;
   }

   private static void destroySessionAttributeMap(String sessionId) {
      DistributedMap<String, Object> map = SESSION_ATTRIBUTE_MAPS.remove(sessionId);

      if(map != null) {
         Cluster.getInstance().getScheduledExecutor()
            .scheduleWithId("destroy-map-" + sessionId,
                            new DestroyMapTask(getSessionAttributeMapName(sessionId)),
                            10, TimeUnit.MINUTES);
      }
   }

   public static DistributedMap<String, Object> getSessionAttributeMap(String sessionId) {
      if(SESSION_ATTRIBUTE_MAPS.containsKey(sessionId)) {
         return SESSION_ATTRIBUTE_MAPS.get(sessionId);
      }

      Cluster cluster = Cluster.getInstance();

      if(cluster.mapExists(getSessionAttributeMapName(sessionId))) {
         DistributedMap<String, Object> map = cluster
            .getReplicatedMap(getSessionAttributeMapName(sessionId));

         if(cluster.getCache(DEFAULT_SESSION_MAP_NAME).containsKey(sessionId)) {
            SESSION_ATTRIBUTE_MAPS.put(sessionId, map);
         }

         return map;
      }

      return null;
   }

   private String sessionMapName = DEFAULT_SESSION_MAP_NAME;
   private FlushMode flushMode = FlushMode.ON_SAVE;
   private SaveMode saveMode = SaveMode.ON_SET_ATTRIBUTE;
   private Cache<String, MapSession> sessions;
   private Cluster cluster;
   private SessionIdGenerator sessionIdGenerator = UuidSessionIdGenerator.getInstance();
   private final SecurityEngine securityEngine;
   private final AuthenticationService authenticationService;
   private final NodeProtectionService nodeProtectionService;

   public static final String DEFAULT_SESSION_MAP_NAME = "spring.session.sessions";
   private static final Logger LOG = LoggerFactory.getLogger(IgniteSessionRepository.class);
   private static final int SESSION_EXPIRATION_WARNING_TIME = 90000; // 90 seconds, when to start warning the user about session expiring
   private static final int PROTECTION_EXPIRATION_WARNING_TIME = 600000; // 10 minutes, when to start warning the user about protection expiring
   private static final int PROTECTION_EXPIRATION_WARNING_INTERVAL = 120000; // 2 minutes, how often to warn about protection expiring
   private static final String EXPIRING_SOON_ATTR = IgniteSessionRepository.class.getName() + ".expiringSoon";
   private static final String LAST_PROTECTION_WARNING_TIME_ATTR = IgniteSessionRepository.class.getName() + ".lastProtectionWarningTime";
   private static final Map<String, DistributedMap<String, Object>> SESSION_ATTRIBUTE_MAPS = new HashMap<>();
   private static final String SESSION_ATTRIBUTE_MAP = IgniteSessionRepository.class.getName() + ".sessionAttributeMap.";

   public final class IgniteSession implements Session {
      IgniteSession(MapSession cached, boolean isNew) {
         this.delegate = cached;
         this.isNew = isNew;
         this.originalId = cached.getId();
         DistributedMap<String, Object> map = getSessionAttributeMap(originalId);

         if(this.isNew || (IgniteSessionRepository.this.saveMode == SaveMode.ALWAYS)) {
            this.delegate.getAttributeNames()
               .forEach(n -> map.put(getAttributeKey(n), cached.getAttribute(n)));
         }
      }

      @Override
      public String getId() {
         return this.delegate.getId();
      }

      @Override
      public String changeSessionId() {
         String oldSessionId = this.originalId;
         String newSessionId = IgniteSessionRepository.this.sessionIdGenerator.generate();
         this.delegate.setId(newSessionId);
         this.sessionIdChanged = true;

         DistributedMap<String, Object> newMap = getSessionAttributeMap(newSessionId);
         Set<Map.Entry<String, Object>> oldEntries = getSessionAttributeMap(oldSessionId).entrySet();

         for(Map.Entry<String, Object> entry : oldEntries) {
            newMap.put(entry.getKey(), entry.getValue());
         }

         Principal principal = getAttribute(RepletRepository.PRINCIPAL_COOKIE);

         if(principal instanceof DestinationUserNameProviderPrincipal) {
            ((DestinationUserNameProviderPrincipal) principal).setHttpSessionId(newSessionId);
            setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
         }

         principal = getAttribute(RepletRepository.EM_PRINCIPAL_COOKIE);

         if(principal instanceof DestinationUserNameProviderPrincipal) {
            ((DestinationUserNameProviderPrincipal) principal).setHttpSessionId(newSessionId);
            setAttribute(RepletRepository.EM_PRINCIPAL_COOKIE, principal);
         }

         return newSessionId;
      }

      @Override
      public <T> T getAttribute(String attributeName) {
         return (T) getSessionAttributeMap(originalId).get(getAttributeKey(attributeName));
      }

      @Override
      public Set<String> getAttributeNames() {
         return getSessionAttributeMap(originalId).keySet().stream()
            .filter(key -> key != null && key.startsWith(ATTR_PREFIX))
            .map(key -> key.substring(ATTR_PREFIX.length()))
            .collect(Collectors.toSet());
      }

      @Override
      public void setAttribute(String attributeName, Object attributeValue) {
         if(attributeValue instanceof DestinationUserNameProviderPrincipal &&
            (RepletRepository.PRINCIPAL_COOKIE.equals(attributeName) ||
               RepletRepository.EM_PRINCIPAL_COOKIE.equals(attributeName)))
         {
            ((DestinationUserNameProviderPrincipal) attributeValue).setHttpSessionId(originalId);
         }

         if(attributeValue == null) {
            getSessionAttributeMap(originalId).remove(getAttributeKey(attributeName));
         }
         else {
            getSessionAttributeMap(originalId).put(getAttributeKey(attributeName), attributeValue);
         }
      }

      @Override
      public void removeAttribute(String attributeName) {
         getSessionAttributeMap(originalId).remove(getAttributeKey(attributeName));
      }

      @Override
      public Instant getCreationTime() {
         return this.delegate.getCreationTime();
      }

      @Override
      public void setLastAccessedTime(Instant lastAccessedTime) {
         this.delegate.setLastAccessedTime(lastAccessedTime);
         this.lastAccessedTimeChanged = true;
         flushImmediateIfNecessary();
      }

      @Override
      public Instant getLastAccessedTime() {
         return this.delegate.getLastAccessedTime();
      }

      @Override
      public void setMaxInactiveInterval(Duration interval) {
         this.delegate.setMaxInactiveInterval(interval);
         this.maxInactiveIntervalChanged = true;
         flushImmediateIfNecessary();
      }

      @Override
      public Duration getMaxInactiveInterval() {
         return this.delegate.getMaxInactiveInterval();
      }

      @Override
      public boolean isExpired() {
         return this.delegate.isExpired();
      }

      MapSession getDelegate() {
         return this.delegate;
      }

      boolean hasChanges() {
         return (this.lastAccessedTimeChanged || this.maxInactiveIntervalChanged);
      }

      void clearChangeFlags() {
         this.isNew = false;
         this.lastAccessedTimeChanged = false;
         this.sessionIdChanged = false;
         this.maxInactiveIntervalChanged = false;
      }

      private void flushImmediateIfNecessary() {
         if(IgniteSessionRepository.this.flushMode == FlushMode.IMMEDIATE) {
            IgniteSessionRepository.this.save(this);
         }
      }

      private String getAttributeKey(String attributeName) {
         return ATTR_PREFIX + attributeName;
      }

      private final MapSession delegate;
      private boolean isNew;
      private boolean sessionIdChanged;
      private boolean lastAccessedTimeChanged;
      private boolean maxInactiveIntervalChanged;
      private String originalId;
      private static final String ATTR_PREFIX = "IgniteSession.ATTR.";
   }

   private final static class DestroyMapTask implements Runnable, Serializable {
      public DestroyMapTask(String name) {
         this.name = name;
      }

      @Override
      public void run() {
         Cluster.getInstance().destroyReplicatedMap(name);
      }

      private final String name;
   }
}
