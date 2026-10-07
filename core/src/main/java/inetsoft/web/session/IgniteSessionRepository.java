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
import inetsoft.util.*;
import inetsoft.util.audit.SessionRecord;
import inetsoft.web.admin.server.NodeProtectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.*;
import org.springframework.util.Assert;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import javax.cache.Cache;
import javax.cache.CacheException;
import java.io.Serializable;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class IgniteSessionRepository
   implements FindByIndexNameSessionRepository<IgniteSessionRepository.IgniteSession>,
   MapChangeListener<String, MapSession>, InitializingBean, DisposableBean
{
   public IgniteSessionRepository(SecurityEngine securityEngine,
                                  AuthenticationService authenticationService,
                                  NodeProtectionService nodeProtectionService,
                                  Cluster cluster)
   {
      this.securityEngine = securityEngine;
      this.authenticationService = authenticationService;
      this.nodeProtectionService = nodeProtectionService;
      this.cluster = cluster;
   }

   @Override
   public void afterPropertiesSet() {
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
         withRetry(() -> this.sessions.put(session.getId(), session.getDelegate()));
      }
      else if(session.sessionIdChanged) {
         String oldId = session.originalId;
         // Note: remove + put are not wrapped in a distributed transaction, so there is a brief
         // window where neither the old nor the new key exists. On retry, remove is a no-op if
         // already gone and put sets the new key, making this safe to retry. (Bug #74131)
         withRetry(() -> {
            this.sessions.remove(oldId);
            this.sessions.put(session.getId(), session.getDelegate());
         });
         session.originalId = session.getId();
      }
      else if(session.hasChanges()) {
         Instant lastAccessedTime =
            session.lastAccessedTimeChanged ? session.getLastAccessedTime() : null;
         Duration maxInactiveInterval =
            session.maxInactiveIntervalChanged ? session.getMaxInactiveInterval() : null;

         withRetry(() -> this.sessions.invoke(
            session.getId(), new SessionUpdateEntryProcessor(),
            lastAccessedTime, maxInactiveInterval));
      }

      session.clearChangeFlags();
   }

   /**
    * Execute a session cache operation with retry on topology-change exceptions.
    *
    * <p>When an Ignite primary node leaves the cluster mid-transaction the commit is
    * rolled back with {@code IgniteTxRollbackCheckedException} (caused by
    * {@code ClusterTopologyCheckedException}).  After the topology stabilises Ignite
    * reassigns the partition, so retrying the same operation will succeed.  Session
    * saves and deletes are idempotent on retry, so retrying is safe.
    *
    * <p>3 retries × 200 ms = up to 600 ms overhead, which gives Ignite sufficient time
    * to stabilise the topology for session operations while keeping user-visible latency
    * low. (IgniteDistributedMap uses 5 retries for internal data operations where
    * higher retry counts are acceptable.)
    */
   private void withRetry(Runnable operation) {
      int attempts = 0;

      while(true) {
         try {
            operation.run();
            return;
         }
         catch(RuntimeException e) {
            if(!isTopologyException(e) || ++attempts > SAVE_MAX_RETRIES) {
               throw e;
            }

            LOG.warn("Session operation failed due to cluster topology change, retrying ({}/{})",
                     attempts, SAVE_MAX_RETRIES, e);

            try {
               Thread.sleep(SAVE_RETRY_DELAY_MS);
            }
            catch(InterruptedException ie) {
               Thread.currentThread().interrupt();
               throw new RuntimeException("Interrupted while retrying session operation", ie);
            }
         }
      }
   }

   /**
    * Returns {@code true} if the exception (or any cause in its chain) indicates that an
    * Ignite cluster topology change caused the operation to fail — i.e. the primary node
    * for the affected partition left the grid mid-transaction.
    *
    * <p>Class names are matched by string rather than {@code instanceof} to avoid a
    * hard compile-time dependency on Ignite internal classes
    * ({@code org.apache.ignite.internal.*}) that are not part of the public API and
    * may be relocated or renamed across major Ignite versions.
    */
   private static boolean isTopologyException(Throwable e) {
      for(Throwable t = e; t != null; t = t.getCause()) {
         String name = t.getClass().getName();

         if(name.contains("ClusterTopologyCheckedException") ||
            name.contains("ClusterTopologyException") ||
            name.contains("IgniteTxRollbackCheckedException"))
         {
            return true;
         }
      }

      return false;
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

   /**
    * Resolves a session read by a background scan of {@link #sessions} (an {@code iterator()}
    * entry) without renewing its Ignite access TTL, unlike {@link #findById}. Any {@code get}
    * through {@link #sessions} resets the TTL (see {@link PropertyAccessedExpiryPolicy}); iteration
    * does not. Only the request path ({@link #findById} for the requesting session) should keep a
    * session alive. A background reader that renewed every live session kept the TTL from firing
    * when an idle session timed out, so the session ended late (Bug #77886).
    *
    * <p>An expired session is still swept here. It is re-read through {@link #findById}, which
    * deletes it if it is still expired (so a session renewed by a concurrent request since the
    * iteration is not deleted). Returns {@code null} only on that expired path, when the session
    * is deleted or already gone. A live session is always returned as a wrapper of the iterated
    * value, even if another thread deletes it concurrently.
    */
   private IgniteSession findIteratedSession(MapSession session) {
      if(session.isExpired()) {
         return findById(session.getId());
      }

      return new IgniteSession(session, false);
   }

   @Override
   public void deleteById(String id) {
      MapSession session = this.sessions.get(id);

      if(session == null) {
         return;
      }

      IgniteSession igniteSession = new IgniteSession(session, false);
      withRetry(() -> this.sessions.remove(id));
      sendApplicationEvent(new SessionExpiredEvent(this.getClass().getName(), igniteSession));

      // deregister the principal explicitly, using the properly-wrapped IgniteSession we already
      // hold, rather than relying on entryRemoved()/entryExpired()'s own reaction to this same
      // removal (bug #76953, fix round 2). That reaction is dispatched asynchronously, on a
      // dedicated single-thread executor separate from this call's thread, so it is not
      // guaranteed to run after (or become a no-op because of) this call -- it is only that in
      // the overwhelming majority of executions, since the async dispatch takes many more hops
      // than this method's own next few synchronous lines. The two calls do race in practice;
      // logout()'s per-session claim keeps them from both passing its isActiveUser() gate and
      // writing a duplicate audit SessionRecord for one logical removal (bug #77300).
      logout(igniteSession, SessionRecord.LOGOFF_SESSION_TIMEOUT);
   }

   @Override
   public Map<String, IgniteSession> findByIndexNameAndIndexValue(String indexName,
                                                                  String indexValue)
   {
      if(!PRINCIPAL_NAME_INDEX_NAME.equals(indexName)) {
         return Map.of();
      }

      Map<String, IgniteSession> result = new HashMap<>();
      Iterator<Cache.Entry<String, MapSession>> iter = sessions.iterator();

      try {
         while(iter.hasNext()) {
            Cache.Entry<String, MapSession> session = iter.next();
            IgniteSession igniteSession = findIteratedSession(session.getValue());

            // could be out of sync due to session expiration, need to check for null
            if(igniteSession != null && isSessionForUser(igniteSession, indexValue)) {
               result.put(session.getValue().getId(), igniteSession);
            }
         }
      }
      finally {
         Tool.closeIterator(iter);
      }

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
      MapSession session = event.getOldValue();

      if(session != null) {
         if(session.isExpired()) {
            entryExpired(event);
         }
         else {
            LOG.debug("Session deleted with ID: {}", session.getId());

            // Publish the event using a properly-wrapped IgniteSession so its attributes (the
            // principal, LOGGED_OUT) resolve correctly for consumers like
            // SessionConnectionService -- the bare MapSession from the cache event never carries
            // attributes (they live only in the per-session DistributedMap, see IgniteSession's
            // getAttribute()/setAttribute()), so publishing it directly always produced an
            // event with a null principal (bug #77178). This wrapper is only used for the event
            // payload; it is deliberately NOT passed to the logout() call below -- see that
            // call's own comment and logout()'s javadoc for why this branch's logout() must stay
            // inert.
            IgniteSession igniteSession = new IgniteSession(session, false);
            sendApplicationEvent(new SessionDeletedEvent(this.getClass().getName(), igniteSession));
            logout(session, "");
            destroySessionAttributeMap(session.getId());
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
      MapSession session = event.getOldValue();
      LOG.debug("Session expired with ID: {}", session.getId());

      // Wrap the raw MapSession before the per-session attribute map is torn down below, so
      // logout() can actually resolve the principal -- passing the bare MapSession (as before fix
      // round 2) always resolves null (see logout()'s javadoc), silently skipping deregistration
      // for every genuine session expiry (bug #76953, fix round 2). This is safe here (unlike
      // "fixing" entryRemoved()'s "not expired" branch would be): by the time entryExpired() runs,
      // the session is already gone/expiring from the cache regardless of what logout() does, so
      // resolving the principal here only keeps SecurityEngine.users consistent with a removal
      // that has already happened -- it cannot itself force-remove an otherwise-active session.
      // Reuse the same wrapper for the published event (bug #77178) -- publishing it with the
      // raw `session` instead (as before) always produced an event with a null principal/
      // LOGGED_OUT attribute, for the same reason logout() needed the wrapper above.
      IgniteSession igniteSession = new IgniteSession(session, false);
      logout(igniteSession, SessionRecord.LOGOFF_SESSION_TIMEOUT);
      sendApplicationEvent(new SessionExpiredEvent(this.getClass().getName(), igniteSession));
      destroySessionAttributeMap(session.getId());
   }

   /**
    * Deregisters {@code session}'s principal, if still active. Only works when {@code session}
    * resolves attributes via the per-session DistributedMap (an {@link IgniteSession}) -- passing
    * the bare {@link MapSession} from a cache event (as {@link #entryRemoved}'s "not expired"
    * branch deliberately still does, below) is a no-op, because attribute reads/writes go only
    * through the per-session DistributedMap and never back to the cached MapSession itself.
    *
    * <p>{@link #invalidateSession}, {@link #deleteById}, and {@link #entryExpired} all now pass a
    * freshly-wrapped {@link IgniteSession} instead, so this correctly resolves the principal for
    * those three callers (fix round 2, bug #76953). This is safe for all three: by the time any of
    * them runs, the session is already being (or about to be) removed from the cache regardless of
    * what this method does, so deregistering the principal here only keeps
    * {@code SecurityEngine.users} consistent with a removal that is happening anyway -- it cannot
    * itself cause a still-active session to be force-removed.
    *
    * <p>Do NOT do the same for {@link #entryRemoved}'s "not expired" branch: that branch's own
    * {@code isExpired()} re-check is a per-node, possibly-stale/racy re-derivation of expiry against
    * a session that -- from that node's own disagreement -- may still be genuinely active elsewhere.
    * Making that branch's {@code logout()} call resolve a real principal would let one node's stale
    * view unilaterally force a cluster-wide logout of a still-active session -- the original
    * mechanism this bug is about. Leave it passing the bare {@code MapSession} (inert by
    * construction) unless that branch's classification logic is fixed first.
    */
   private void logout(Session session, String logoffReason) {
      Principal principal = session.getAttribute(RepletRepository.PRINCIPAL_COOKIE);

      if(principal instanceof SRPrincipal srp) {
         String sessionId = session.getId();

         // deleteById() and this node's own entryRemoved()/entryExpired() reaction to the same
         // removal run on different threads and both call this method; isActiveUser() is a
         // check-then-act gate, so without this claim both could pass it and write two LOGOFF
         // audit records from one node (bug #77300). A concurrent second caller skips; a later
         // one is stopped by isActiveUser(), since authenticationService.logout() deregisters the
         // principal synchronously before the claim is released. No lock is held while calling
         // authenticationService.logout().
         if(!loggingOutSessions.add(sessionId)) {
            return;
         }

         try {
            if(securityEngine.isActiveUser(principal)) {
               String remoteHost = srp.getUser().getIPAddress();
               authenticationService.logout(principal, remoteHost, logoffReason);
            }
         }
         finally {
            loggingOutSessions.remove(sessionId);
         }
      }
   }

   @Override
   public void entryUpdated(EntryEvent<String, MapSession> event) {
      // no-op
   }

   @EventListener(SessionLoggedOutEvent.class)
   public void loggedOut(SessionLoggedOutEvent event) {
      if(event.isInvalidateSession()) {
         Principal principal = event.getPrincipal();
         Iterator<Cache.Entry<String, MapSession>> iter = sessions.iterator();

         try {
            while(iter.hasNext()) {
               Cache.Entry<String, MapSession> entry = iter.next();
               IgniteSession igniteSession = findIteratedSession(entry.getValue());

               // could be out of sync due to session expiration, need to check for null
               if(igniteSession != null &&
                  principal.equals(igniteSession.getAttribute(RepletRepository.PRINCIPAL_COOKIE)))
               {
                  String sessionId = entry.getValue().getId();
                  withRetry(() -> this.sessions.remove(sessionId));
                  break;
               }
            }
         }
         finally {
            Tool.closeIterator(iter);
         }
      }
   }

   public List<SRPrincipal> getActiveSessions() {
      List<SRPrincipal> result = new ArrayList<>();
      Iterator<Cache.Entry<String, MapSession>> iter = sessions.iterator();

      try {
         while(iter.hasNext()) {
            Cache.Entry<String, MapSession> session = iter.next();
            IgniteSessionRepository.IgniteSession igniteSession =
               findIteratedSession(session.getValue());

            // could be out of sync due to session expiration, need to check for null
            if(igniteSession != null) {
               SRPrincipal principal = igniteSession.getAttribute(RepletRepository.PRINCIPAL_COOKIE);

               if(principal != null) {
                  result.add(principal);
               }
            }
         }
      }
      finally {
         Tool.closeIterator(iter);
      }

      return result;
   }

   public void updatePrincipalRolesAndGroups(IdentityID userID, IdentityID[] newRoles,
                                             String[] newGroups, AuthenticationProvider provider)
   {
      Iterator<Cache.Entry<String, MapSession>> iter = sessions.iterator();

      try {
         while(iter.hasNext()) {
            Cache.Entry<String, MapSession> entry = iter.next();
            IgniteSession igniteSession = findIteratedSession(entry.getValue());

            if(igniteSession != null) {
               updatePrincipalInSession(
                  igniteSession, RepletRepository.PRINCIPAL_COOKIE, userID, newRoles, newGroups,
                  provider);
               updatePrincipalInSession(
                  igniteSession, RepletRepository.EM_PRINCIPAL_COOKIE, userID, newRoles, newGroups,
                  provider);
            }
         }
      }
      finally {
         Tool.closeIterator(iter);
      }
   }

   private void updatePrincipalInSession(IgniteSession session, String cookieKey,
                                         IdentityID userID, IdentityID[] newRoles,
                                         String[] newGroups, AuthenticationProvider provider)
   {
      SRPrincipal principal = session.getAttribute(cookieKey);

      if(principal != null && Tool.equals(principal.getIdentityID(), userID)) {
         principal.setRoles(newRoles);
         principal.setGroups(newGroups);
         principal.updateRoles(provider);
         session.setAttribute(cookieKey, principal);
      }
   }

   public void invalidateSession(String id) {
      final IgniteSession session = this.findById(id);

      if(session != null) {
         withRetry(() -> this.sessions.remove(id));
         sendApplicationEvent(new SessionExpiredEvent(this.getClass().getName(), session));

         // deregister the principal explicitly -- this removal doesn't publish a
         // SessionLoggedOutEvent, and entryRemoved()'s own reaction to the cache removal is a
         // no-op here (see logout()'s javadoc), so nothing else would do it (bug #76953)
         logout(session, "");
      }
   }

   // Every 10 seconds: this pass is what ends an idle session (it deletes the sessions past their
   // timeout), so its period bounds how late the end can be. Every node runs it, but the nodes'
   // phases are arbitrary and can coincide, so the bound is one period, not a fraction of it
   // (Bug #77886: three nodes ran their 20 s passes within 5 s of each other).
   @Scheduled(fixedRate = 10000)
   public void checkSessions() {
      long currentTime = System.currentTimeMillis();
      long protectionExpirationTime = nodeProtectionService.getExpirationTime();
      long protectionRemainingTime = protectionExpirationTime - currentTime;
      boolean protectionExpiring = protectionRemainingTime > 0 &&
         protectionRemainingTime < PROTECTION_EXPIRATION_WARNING_TIME;

      Iterator<Cache.Entry<String, MapSession>> iter = sessions.iterator();

      try {
         while(iter.hasNext()) {
            Cache.Entry<String, MapSession> entry = iter.next();
            MapSession session = entry.getValue();
            Instant lastAccessedTime = session.getLastAccessedTime();
            Duration maxInactiveInterval = session.getMaxInactiveInterval();
            long sessionRemainingTime = lastAccessedTime.toEpochMilli() +
               maxInactiveInterval.toMillis() - currentTime;

            // an expired session is deleted now (findById() re-checks and deletes it) instead of
            // being left for Ignite's TTL; live sessions are read without renewing the TTL
            // (Bug #77886)
            if(sessionRemainingTime <= 0) {
               // one failed deletion must not skip the rest of the pass
               try {
                  findById(session.getId());
               }
               catch(RuntimeException e) {
                  LOG.warn("Failed to delete expired session {}", session.getId(), e);
               }
            }
            else {
               IgniteSession igniteSession = findIteratedSession(session);

               // could be out of sync due to session expiration, need to check for null
               if(igniteSession == null) {
                  continue;
               }

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
      finally {
         Tool.closeIterator(iter);
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
      // Single atomic get (not containsKey() + get()) -- the previous two-call sequence was a
      // TOCTOU: a concurrent destroySessionAttributeMap() could remove the entry between the
      // containsKey() check and the get(), spuriously falling through as if never cached
      // (Bug #77306). DistributedMap values are never stored as null, so a non-null get() result
      // is equivalent to "was present" without a second call.
      DistributedMap<String, Object> cached = SESSION_ATTRIBUTE_MAPS.get(sessionId);

      if(cached != null) {
         return cached;
      }

      Cluster cluster = Cluster.getInstance();

      if(cluster.mapExists(getSessionAttributeMapName(sessionId))) {
         DistributedMap<String, Object> map = cluster
            .getReplicatedMap(getSessionAttributeMapName(sessionId));

         if(cluster.getCache(DEFAULT_SESSION_MAP_NAME).containsKey(sessionId)) {
            // computeIfAbsent() makes the "not yet cached -> cache it" transition atomic per key,
            // closing the same-key TOCTOU between this cold path and a concurrent
            // destroySessionAttributeMap()/another getSessionAttributeMap() caching the map first
            // (Bug #77306). Only reached when the session is still known to exist -- preserve the
            // pre-existing behavior of NOT caching (and just returning) the freshly-fetched map
            // when the underlying session cache no longer contains this id.
            DistributedMap<String, Object> fetched = map;
            return SESSION_ATTRIBUTE_MAPS.computeIfAbsent(sessionId, id -> fetched);
         }

         return map;
      }

      return null;
   }

   private String sessionMapName = DEFAULT_SESSION_MAP_NAME;
   private FlushMode flushMode = FlushMode.ON_SAVE;
   private SaveMode saveMode = SaveMode.ON_SET_ATTRIBUTE;
   private Cache<String, MapSession> sessions;
   private final Cluster cluster;
   private SessionIdGenerator sessionIdGenerator = UuidSessionIdGenerator.getInstance();
   private final SecurityEngine securityEngine;
   private final AuthenticationService authenticationService;
   private final NodeProtectionService nodeProtectionService;
   private final Set<String> loggingOutSessions = ConcurrentHashMap.newKeySet();

   public static final String DEFAULT_SESSION_MAP_NAME = "spring.session.sessions";
   private static final Logger LOG = LoggerFactory.getLogger(IgniteSessionRepository.class);
   private static final int SAVE_MAX_RETRIES = 3;
   private static final long SAVE_RETRY_DELAY_MS = 200L;
   private static final int SESSION_EXPIRATION_WARNING_TIME = 90000; // 90 seconds, when to start warning the user about session expiring
   private static final int PROTECTION_EXPIRATION_WARNING_TIME = 600000; // 10 minutes, when to start warning the user about protection expiring
   private static final int PROTECTION_EXPIRATION_WARNING_INTERVAL = 120000; // 2 minutes, how often to warn about protection expiring
   private static final String EXPIRING_SOON_ATTR = IgniteSessionRepository.class.getName() + ".expiringSoon";
   private static final String LAST_PROTECTION_WARNING_TIME_ATTR = IgniteSessionRepository.class.getName() + ".lastProtectionWarningTime";
   // ConcurrentHashMap, not HashMap -- this map is shared by every HTTP session on the node and
   // mutated concurrently from at least three independent thread contexts (request threads via
   // createSessionAttributeMap()/getSessionAttributeMap()'s cold-path put, the single-threaded
   // Ignite cache-event listener executor via destroySessionAttributeMap(), and the
   // @Scheduled checkSessions() thread's cold-path put for every session
   // cluster-wide). Concurrent put/remove on a plain HashMap can corrupt its internal structure
   // badly enough to spuriously null out (or lose) an entirely unrelated key, with the damage
   // persisting for the life of the map -- this was the root cause of Bug #77306 (a sibling
   // session's own PRINCIPAL_COOKIE attribute intermittently reading back null, producing a
   // spurious HTTP 403 on an unrelated, still-active session).
   private static final Map<String, DistributedMap<String, Object>> SESSION_ATTRIBUTE_MAPS = new ConcurrentHashMap<>();
   private static final String SESSION_ATTRIBUTE_MAP = IgniteSessionRepository.class.getName() + ".sessionAttributeMap.";

   public final class IgniteSession implements Session {
      IgniteSession(MapSession cached, boolean isNew) {
         this.delegate = cached;
         this.isNew = isNew;
         this.originalId = cached.getId();
         DistributedMap<String, Object> map = getSessionAttributeMap(originalId);

         // could be out of sync due to session expiration, need to check for null (Bug #77306)
         if(map != null &&
            (this.isNew || (IgniteSessionRepository.this.saveMode == SaveMode.ALWAYS)))
         {
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
         DistributedMap<String, Object> oldMap = getSessionAttributeMap(oldSessionId);

         // could be out of sync due to session expiration, need to check for null (Bug #77306)
         if(oldMap != null) {
            for(Map.Entry<String, Object> entry : oldMap.entrySet()) {
               newMap.put(entry.getKey(), entry.getValue());
            }
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
         DistributedMap<String, Object> map = getSessionAttributeMap(originalId);

         // could be out of sync due to session expiration, need to check for null (Bug #77306)
         if(map == null) {
            return null;
         }

         return (T) map.get(getAttributeKey(attributeName));
      }

      @Override
      public Set<String> getAttributeNames() {
         DistributedMap<String, Object> map = getSessionAttributeMap(originalId);

         // could be out of sync due to session expiration, need to check for null (Bug #77306)
         if(map == null) {
            return Collections.emptySet();
         }

         return map.keySet().stream()
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

         DistributedMap<String, Object> map = getSessionAttributeMap(originalId);

         // could be out of sync due to session expiration, need to check for null (Bug #77306)
         if(map == null) {
            return;
         }

         if(attributeValue == null) {
            map.remove(getAttributeKey(attributeName));
         }
         else {
            map.put(getAttributeKey(attributeName), attributeValue);
         }
      }

      @Override
      public void removeAttribute(String attributeName) {
         DistributedMap<String, Object> map = getSessionAttributeMap(originalId);

         // could be out of sync due to session expiration, need to check for null (Bug #77306)
         if(map != null) {
            map.remove(getAttributeKey(attributeName));
         }
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
         ConfigurationContext.getContext().getSpringBean(Cluster.class).destroyReplicatedMap(name);
      }

      private final String name;
   }
}
