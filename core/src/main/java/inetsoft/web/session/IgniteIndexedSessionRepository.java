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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.*;
import org.springframework.session.events.*;
import org.springframework.util.Assert;

import javax.cache.Cache;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class IgniteIndexedSessionRepository
   implements FindByIndexNameSessionRepository<IgniteIndexedSessionRepository.IgniteSession>,
   MapChangeListener<String, MapSession>, SessionListener, InitializingBean, DisposableBean
{
   public IgniteIndexedSessionRepository(SecurityEngine securityEngine,
                                         AuthenticationService authenticationService,
                                         NodeProtectionService nodeProtectionService)
   {
      this.securityEngine = securityEngine;
      this.authenticationService = authenticationService;
      this.nodeProtectionService = nodeProtectionService;
   }

   @Override
   public void afterPropertiesSet() throws Exception {
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

   public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
      Objects.requireNonNull(applicationEventPublisher, "ApplicationEventPublisher cannot be null");
      this.eventPublisher = applicationEventPublisher;
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

   @SuppressWarnings("ClassEscapesDefinedScope")
   @Override
   public IgniteSession createSession() {
      MapSession cached = new MapSession(this.sessionIdGenerator);
      javax.cache.expiry.Duration expiry = PropertyAccessedExpiryPolicy.getExpiryFromProperty();
      cached.setMaxInactiveInterval(
         Duration.ofSeconds(expiry.getTimeUnit().toSeconds(expiry.getDurationAmount())));
      IgniteSession session = new IgniteSession(cached, true);
      session.flushImmediateIfNecessary();
      return session;
   }

   @SuppressWarnings("ClassEscapesDefinedScope")
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
         Map<String, Object> delta = session.delta.isEmpty() ? null : session.delta;

         boolean principalChanged = false;
         SRPrincipal oldPrincipal = null;
         SRPrincipal newPrincipal = null;

         if(delta != null && delta.containsKey(RepletRepository.PRINCIPAL_COOKIE)) {
            principalChanged = true;
            oldPrincipal = session.getDelegate().getAttribute(RepletRepository.PRINCIPAL_COOKIE);
            newPrincipal = (SRPrincipal) delta.get(RepletRepository.PRINCIPAL_COOKIE);
         }

         this.sessions.invoke(
            session.getId(), new SessionUpdateEntryProcessor(),
            lastAccessedTime, maxInactiveInterval, delta);

         if(principalChanged) {
            eventPublisher.publishEvent(
               new PrincipalChangedEvent(this, oldPrincipal, newPrincipal, session));
         }
      }

      session.clearChangeFlags();
   }

   @SuppressWarnings("ClassEscapesDefinedScope")
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
      this.sessions.remove(id);
   }

   @SuppressWarnings("ClassEscapesDefinedScope")
   @Override
   public Map<String, IgniteSession> findByIndexNameAndIndexValue(String indexName,
                                                                  String indexValue)
   {
      if(!PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
         return Map.of();
      }

      Map<String, IgniteSession> result = new HashMap<>();

      sessions.iterator().forEachRemaining(session -> {
         if(isSessionForUser(session.getValue(), indexValue)) {
            result.put(session.getValue().getId(), new IgniteSession(session.getValue(), false));
         }
      });

      return result;
   }

   private boolean isSessionForUser(MapSession session, String userName) {
      final Object user = session.getAttribute(RepletRepository.PRINCIPAL_COOKIE);
      return user instanceof SRPrincipal && userName.equals(((SRPrincipal) user).getName());
   }

   @Override
   public void entryAdded(EntryEvent<String, MapSession> event) {
      MapSession session = event.getValue();

      if(session.getId().equals(session.getOriginalId())) {
         LOG.debug("Session created with ID: {}", session.getId());
         this.eventPublisher.publishEvent(new SessionCreatedEvent(this, session));
      }
   }

   @Override
   public void entryEvicted(EntryEvent<String, MapSession> event) {
      entryExpired(event);
   }

   @Override
   public void entryRemoved(EntryEvent<String, MapSession> event) {
      MapSession session = event.getValue();

      if(session != null) {
         LOG.debug("Session deleted with ID: {}", session.getId());
         this.eventPublisher.publishEvent(new SessionDeletedEvent(this, session));
         logout(session, "");
      }
   }

   @Override
   public void entryExpired(EntryEvent<String, MapSession> event) {
      LOG.debug("Session expired with ID: {}", event.getOldValue().getId());
      logout(event.getOldValue(), SessionRecord.LOGOFF_SESSION_TIMEOUT);
      this.eventPublisher.publishEvent(new SessionExpiredEvent(this, event.getOldValue()));
   }

   private void logout(Session session, String logoffReason) {
      Principal principal = session.getAttribute(RepletRepository.PRINCIPAL_COOKIE);

      if(principal instanceof SRPrincipal srp) {
         if(securityEngine.isActiveUser(principal)) {
            String remoteHost = srp.getUser().getIPAddress();
            authenticationService.logout(principal, remoteHost, logoffReason);
         }

         this.eventPublisher.publishEvent(new PrincipalChangedEvent(this, srp, null, session));
      }
   }

   @Override
   public void entryUpdated(EntryEvent<String, MapSession> event) {
      // no-op
   }

   @Override
   public void loggedIn(SessionEvent event) {
      // no-op
   }

   @Override
   public void loggedOut(SessionEvent event) {
      if(event.isInvalidateSession()) {
         Principal principal = event.getPrincipal();

         for(Iterator<Cache.Entry<String, MapSession>> i = sessions.iterator(); i.hasNext(); ) {
            Cache.Entry<String, MapSession> entry = i.next();

            if(principal.equals(entry.getValue().getAttribute(RepletRepository.PRINCIPAL_COOKIE))) {
               i.remove();
               break;
            }
         }
      }
   }

   public List<SRPrincipal> getActiveSessions() {
      List<SRPrincipal> result = new ArrayList<>();
      sessions.iterator().forEachRemaining(session -> {
         SRPrincipal principal = session.getValue().getAttribute(RepletRepository.PRINCIPAL_COOKIE);

         if(principal != null) {
            result.add(principal);
         }
      });

      return result;
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
            if(protectionExpiring) {
               Long lastProtectionWarningTime = session.getAttribute(LAST_PROTECTION_WARNING_TIME_ATTR);

               if(lastProtectionWarningTime == null ||
                  (currentTime - lastProtectionWarningTime) >= PROTECTION_EXPIRATION_WARNING_INTERVAL)
               {
                  session.setAttribute(LAST_PROTECTION_WARNING_TIME_ATTR, currentTime);
                  eventPublisher.publishEvent(new SessionExpiringSoonEvent(
                     this, new IgniteSession(session, false), protectionRemainingTime, true,
                     true));
               }
            }
            // if protection is no longer expiring then close the dialogs
            // this can happen if some other node was terminated first instead
            // and the protection on this node was extended
            else if(protectionExpirationTime == 0 &&
               session.getAttribute(LAST_PROTECTION_WARNING_TIME_ATTR) != null)
            {
               session.removeAttribute(LAST_PROTECTION_WARNING_TIME_ATTR);
               eventPublisher.publishEvent(new SessionExpiringSoonEvent(
               this, new IgniteSession(session, false), protectionRemainingTime, false,
               true));
            }

            // warn the user if remaining time is less than EXPIRATION_WARNING_TIME
            if(sessionRemainingTime <= SESSION_EXPIRATION_WARNING_TIME) {
               session.setAttribute(EXPIRING_SOON_ATTR, true);
               eventPublisher.publishEvent(new SessionExpiringSoonEvent(
                  this, new IgniteSession(session, false), protectionRemainingTime, true,
                  false));
            }
            else if(Boolean.TRUE.equals(session.getAttribute(EXPIRING_SOON_ATTR))) {
               session.removeAttribute(EXPIRING_SOON_ATTR);
               eventPublisher.publishEvent(new SessionExpiringSoonEvent(
                  this, new IgniteSession(session, false), protectionRemainingTime, false,
                  false));
            }
         }
      }
   }

   private ApplicationEventPublisher eventPublisher = e -> {};
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
   private static final Logger LOG = LoggerFactory.getLogger(IgniteIndexedSessionRepository.class);
   private static final int SESSION_EXPIRATION_WARNING_TIME = 90000; // 90 seconds, when to start warning the user about session expiring
   private static final int PROTECTION_EXPIRATION_WARNING_TIME = 600000; // 10 minutes, when to start warning the user about protection expiring
   private static final int PROTECTION_EXPIRATION_WARNING_INTERVAL = 120000; // 2 minutes, how often to warn about protection expiring
   private static final String EXPIRING_SOON_ATTR = IgniteIndexedSessionRepository.class.getName() + ".expiringSoon";
   private static final String LAST_PROTECTION_WARNING_TIME_ATTR = IgniteIndexedSessionRepository.class.getName() + ".lastProtectionWarningTime";

   final class IgniteSession implements Session {

      IgniteSession(MapSession cached, boolean isNew) {
         this.delegate = cached;
         this.isNew = isNew;
         this.originalId = cached.getId();

         if(this.isNew || (IgniteIndexedSessionRepository.this.saveMode == SaveMode.ALWAYS)) {
            getAttributeNames()
               .forEach(n -> this.delta.put(n, cached.getAttribute(n)));
         }
      }

      @Override
      public String getId() {
         return this.delegate.getId();
      }

      @Override
      public String changeSessionId() {
         String newSessionId = IgniteIndexedSessionRepository.this.sessionIdGenerator.generate();
         this.delegate.setId(newSessionId);
         this.sessionIdChanged = true;
         return newSessionId;
      }

      @Override
      public <T> T getAttribute(String attributeName) {
         T attributeValue = this.delegate.getAttribute(attributeName);

         if(attributeValue != null &&
            IgniteIndexedSessionRepository.this.saveMode.equals(SaveMode.ON_GET_ATTRIBUTE))
         {
            this.delta.put(attributeName, attributeValue);
         }

         return attributeValue;
      }

      @Override
      public Set<String> getAttributeNames() {
         return this.delegate.getAttributeNames();
      }

      @Override
      public void setAttribute(String attributeName, Object attributeValue) {
         this.delegate.setAttribute(attributeName, attributeValue);
         this.delta.put(attributeName, attributeValue);
         flushImmediateIfNecessary();
      }

      @Override
      public void removeAttribute(String attributeName) {
         setAttribute(attributeName, null);
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
         return (this.lastAccessedTimeChanged || this.maxInactiveIntervalChanged ||
            !this.delta.isEmpty());
      }

      void clearChangeFlags() {
         this.isNew = false;
         this.lastAccessedTimeChanged = false;
         this.sessionIdChanged = false;
         this.maxInactiveIntervalChanged = false;
         this.delta.clear();
      }

      private void flushImmediateIfNecessary() {
         if(IgniteIndexedSessionRepository.this.flushMode == FlushMode.IMMEDIATE) {
            IgniteIndexedSessionRepository.this.save(this);
         }
      }

      private final MapSession delegate;
      private boolean isNew;
      private boolean sessionIdChanged;
      private boolean lastAccessedTimeChanged;
      private boolean maxInactiveIntervalChanged;
      private String originalId;
      private final Map<String, Object> delta = new HashMap<>();
   }
}
