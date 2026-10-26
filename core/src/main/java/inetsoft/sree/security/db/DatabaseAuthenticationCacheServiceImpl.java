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

package inetsoft.sree.security.db;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class DatabaseAuthenticationCacheServiceImpl implements DatabaseAuthenticationCacheService {
   public DatabaseAuthenticationCacheServiceImpl(String providerName) {
      this.providerName = providerName;
   }

   @Override
   public void cancel() {
      if(cancelled.compareAndSet(false, true)) {
         if(executor != null) {
            executor.shutdownNow();

            try {
               //noinspection ResultOfMethodCallIgnored
               executor.awaitTermination(15, TimeUnit.SECONDS);
            }
            catch(InterruptedException e) {
               LOG.warn("Interrupted while waiting for database authentication cache to shutdown", e);
            }
         }

      }
   }

   @Override
   public void init() throws Exception {
      cluster = Cluster.getInstance();
      prefix = "DatabaseSecurity:" + providerName;
      this.lists = cluster.getReplicatedMap(prefix + ".lists");
      this.orgNames = cluster.getReplicatedMap(prefix + ".orgNames");
      this.orgMembers = cluster.getReplicatedMap(prefix + ".orgMembers");
      this.orgRoles = cluster.getReplicatedMap(prefix + ".orgRoles");
      this.groupUsers = cluster.getReplicatedMap(prefix + ".groupUsers");
      this.userRoles = cluster.getReplicatedMap(prefix + ".userRoles");
      this.userEmails = cluster.getReplicatedMap(prefix + ".userEmails");
      this.cancelled = new AtomicBoolean(false);
      this.clientCount = cluster.getLong(prefix + ".clientCount");
      this.loadingCount = cluster.getLong(prefix + ".loadingCount");
      this.lastLoadTime = cluster.getLong(prefix + ".lastLoadTime");
      this.lastFailureTime = cluster.getLong(prefix + ".lastFailureTime");
      this.executor = Executors.newScheduledThreadPool(2, r ->
         new Thread(r, "DatabaseAuthenticationCache"));
      this.zeroSince = cluster.getLong(prefix + ".zeroSince");
      this.refreshSeq = cluster.getLong(prefix + ".refreshSeq");

      // Schedule periodic cleanup check
      executor.scheduleAtFixedRate(
         this::checkAndCleanup, CLEANUP_CHECK_INTERVAL_MS, CLEANUP_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
   }

   /**
    * Starts the periodic reload of this service instance. Ignite calls this once per deployed
    * instance, after {@link #init()}, both on the first deployment and on a failover redeployment.
    * It must not block and must not need the provider, which is not bound yet on a redeployed
    * instance.
    */
   @Override
   public void execute() {
      if(loadingCount.get() != 0L) {
         // A previous host died during a load and never released the load lock. Only one
         // instance of this service exists at a time, so nobody else holds it.
         LOG.info("Clearing the load lock left by a previous host of {}", prefix);
         loadingCount.set(0L);
      }

      // The first use of the cache does the initial load, so without a loaded cache the first
      // tick only has to catch a failed first load.
      long delay = lastLoadTime.get() != 0L ?
         getSafeReloadDelay() : Math.min(FAILURE_RETRY_DELAY_MS, getIdleDelay());
      scheduleReload(delay);
   }

   /**
    * One tick of the periodic reload. It always schedules the next tick unless the service was
    * cancelled, so an early return or an error never ends the reload chain.
    */
   void scheduledReload() {
      long delay = FAILURE_RETRY_DELAY_MS;

      try {
         if(clientCount.get() <= 0L) {
            // the provider was removed and waits for the cleanup, or no node uses it
            delay = getIdleDelay();
         }
         else {
            loadInternal(true);
            delay = getSafeReloadDelay();
         }
      }
      catch(Throwable e) {
         LOG.warn("Scheduled reload of the database authentication cache {} failed", prefix, e);
      }
      finally {
         if(!cancelled.get()) {
            scheduleReload(delay);
         }
      }
   }

   private void scheduleReload(long delay) {
      try {
         executor.schedule(this::scheduledReload, Math.max(MIN_RELOAD_DELAY_MS, delay),
                           TimeUnit.MILLISECONDS);
      }
      catch(RejectedExecutionException ignore) {
         // cancelled
      }
   }

   private long getSafeReloadDelay() {
      try {
         return Math.max(MIN_RELOAD_DELAY_MS, getNextReloadDelay());
      }
      catch(Throwable e) {
         LOG.debug("Failed to compute the next reload delay of {}", prefix, e);
         return FAILURE_RETRY_DELAY_MS;
      }
   }

   private static long getIdleDelay() {
      long interval = getCacheInterval();
      return interval > 0L ? interval : IDLE_DELAY_MS;
   }

   /**
    * Gets the reload interval. This is the same value as the provider's cache interval, read
    * here so that it is available on a host that has no provider bound yet.
    *
    * @return the interval in milliseconds, or a value <= 0 if the cache is disabled.
    */
   private static long getCacheInterval() {
      long interval = -1L;

      if("true".equals(SreeEnv.getProperty("security.cache"))) {
         try {
            interval = Long.parseLong(SreeEnv.getProperty("security.cache.interval"));
         }
         catch(Exception e) {
            LOG.debug("Invalid security cache interval", e);
         }
      }

      return interval;
   }

   public boolean isLoading() {
      return loadingCount.get() > 0;
   }

   public boolean isInitialized() {
      return lastLoadTime.get() != 0L;
   }

   public long getAge() {
      long loaded = lastLoadTime.get();
      return loaded == 0L ? 0L : System.currentTimeMillis() - loaded;
   }

   public void load() {
      executor.submit(() -> loadInternal(false));
   }

   public void refresh() {
      // a load that is running when this is called may have resolved an older provider, so it
      // sees the change and loads once more when it ends
      refreshSeq.incrementAndGet();
      executor.submit(() -> loadInternal(false));
   }

   public void connect() {
      provider = lookupProvider();
      clientCount.incrementAndGet();
      zeroSince.set(0L); // Clear the zero timestamp since we have an active client
   }

   public void disconnect() {
      // Only decrement the client count. Don't undeploy the service or destroy the cache
      // because other nodes in the cluster may still be using them. The service will be
      // cleaned up by the lazy cleanup task when no clients remain for the grace period.
      long newCount = clientCount.decrementAndGet();

      if(newCount == 0) {
         // Record when the count became zero for lazy cleanup
         zeroSince.set(System.currentTimeMillis());
      }
   }

   public void destroyService() {
      // Force client count to 0 and destroy all resources
      clientCount.set(0);
      zeroSince.set(0L);
      destroyCache();

      if(cluster != null) {
         cluster.undeploySingletonService(prefix);
      }
   }

   /**
    * Checks if the service should be cleaned up due to no active clients for the grace period.
    * This runs periodically as a background task.
    */
   private void checkAndCleanup() {
      if(cancelled.get()) {
         return;
      }

      try {
         long count = clientCount.get();
         long zeroTs = zeroSince.get();
         long now = System.currentTimeMillis();

         if(count <= 0 && zeroTs > 0 && (now - zeroTs) > CLEANUP_GRACE_PERIOD_MS) {
            LOG.info("Cleaning up unused database security service: {}", prefix);
            // Run on a separate thread to avoid deadlock: cancel() calls awaitTermination()
            // on this executor, but this task is still running on it waiting for
            // undeploySingletonService() to return — which triggers cancel() synchronously.
            Thread t = new Thread(this::destroyService, "DatabaseSecurityCleanup-" + prefix);
            t.setDaemon(true);
            t.start();
         }
      }
      catch(Exception e) {
         // Don't let exceptions kill the scheduled task
         LOG.debug("Error during cleanup check for {}", prefix, e);
      }
   }

   private void destroyCache() {
      if(cluster != null) {
         if(lists != null) {
            cluster.destroyMap(prefix + ".lists");
         }

         if(orgNames != null) {
            cluster.destroyMap(prefix + ".orgNames");
         }

         if(orgMembers != null) {
            cluster.destroyMap(prefix + ".orgMembers");
         }

         if(orgRoles != null) {
            cluster.destroyMap(prefix + ".orgRoles");
         }

         if(groupUsers != null) {
            cluster.destroyMap(prefix + ".groupUsers");
         }

         if(userRoles != null) {
            cluster.destroyMap(prefix + ".userRoles");
         }

         if(userEmails != null) {
            cluster.destroyMap(prefix + ".userEmails");
         }

         if(loadingCount != null) {
            cluster.destroyLong(prefix + ".loadingCount");
         }

         if(lastLoadTime != null) {
            cluster.destroyLong(prefix + ".lastLoadTime");
         }

         if(lastFailureTime != null) {
            cluster.destroyLong(prefix + ".lastFailureTime");
         }

         if(zeroSince != null) {
            cluster.destroyLong(prefix + ".zeroSince");
         }

         if(clientCount != null) {
            cluster.destroyLong(prefix + ".clientCount");
         }

         if(refreshSeq != null) {
            cluster.destroyLong(prefix + ".refreshSeq");
         }
      }
   }

   private void loadInternal(boolean scheduled) {
      if(cancelled.get()) {
         return;
      }

      if(scheduled && !isReloadRequired()) {
         return;
      }

      if(!loadingCount.compareAndSet(0, 1)) {
         return;
      }

      // read before the provider is resolved: a refresh requested after this point is run again
      long seqAtStart = refreshSeq.get();
      DatabaseAuthenticationProvider current = null;

      try {
         current = resolveProvider();

         if(current == null) {
            // not bound on this host yet and not in its chain: a failed attempt, retried later
            LOG.debug("Provider {} is not available on this host, the load is retried later",
                      providerName);
            lastFailureTime.set(System.currentTimeMillis());
            return;
         }

         AuthenticationDAO dao = current.getDao();
         QueryResult<IdentityID[]> newUsers = dao.getUsers();

         if(cancelled.get()) {
            return;
         }

         if(newUsers.failed()) {
            handleError(current);
            return;
         }

         QueryResult<IdentityID[]> newRoles = dao.getRoles();

         if(cancelled.get()) {
            return;
         }

         if(newRoles.failed()) {
            handleError(current);
            return;
         }

         QueryResult<IdentityID[]> newGroups = dao.getGroups();

         if(cancelled.get()) {
            return;
         }

         if(newGroups.failed()) {
            handleError(current);
            return;
         }

         QueryResult<String[]> newOrganizations = dao.getOrganizations();

         if(cancelled.get()) {
            return;
         }

         if(newOrganizations.failed()) {
            handleError(current);
            return;
         }

         QueryResult<Map<IdentityID, IdentityArray>> newUserRoles =
            dao.getUserRoles(Arrays.asList(newUsers.result()));

         if(cancelled.get()) {
            return;
         }

         if(newUserRoles.failed()) {
            handleError(current);
            return;
         }

         Map<String, String> newOrgNames = new HashMap<>();
         Map<String, String[]> newOrgMembers = new HashMap<>();
         Map<String, String[]> newOrgRoles = new HashMap<>();

         List<String> orgIDs = Arrays.asList(newOrganizations.result());

         for(String orgID : orgIDs) {
            String name = dao.getOrganizationName(orgID);

            if(cancelled.get()) {
               return;
            }

            newOrgNames.put(orgID, name);

            QueryResult<String[]> result;

            if((result = dao.getOrganizationMembers(orgID, orgIDs)).failed()) {
               handleError(current);
               return;
            }

            if(cancelled.get()) {
               return;
            }

            newOrgMembers.put(orgID, result.result());

            if((result = dao.getOrganizationRoles(orgID)).failed()) {
               handleError(current);
               return;
            }

            if(cancelled.get()) {
               return;
            }

            newOrgRoles.put(orgID, result.result());
         }

         try(DistributedTransaction tx = cluster.startTx()) {
            // Ignite doesn't allow the no-arg (clear-everything) removeAll() inside a
            // transaction; only the keyed overload is transaction-safe.
            lists.put(ORG_LIST, new TreeSet<>(Arrays.asList(newOrganizations.result())));
            lists.put(USER_LIST, new TreeSet<>(Arrays.asList(newUsers.result())));
            lists.put(GROUP_LIST, new TreeSet<>(Arrays.asList(newGroups.result())));
            lists.put(ROLE_LIST, new TreeSet<>(Arrays.asList(newRoles.result())));

            orgNames.removeAll(new HashSet<>(orgNames.keySet()));
            orgNames.putAll(newOrgNames);

            orgMembers.removeAll(new HashSet<>(orgMembers.keySet()));
            orgMembers.putAll(newOrgMembers);

            orgRoles.removeAll(new HashSet<>(orgRoles.keySet()));
            orgRoles.putAll(newOrgRoles);

            groupUsers.removeAll(new HashSet<>(groupUsers.keySet()));
            userRoles.removeAll(new HashSet<>(userRoles.keySet()));
            userRoles.putAll(newUserRoles.result());
            userEmails.removeAll(new HashSet<>(userEmails.keySet()));

            tx.commit();
            lastLoadTime.set(System.currentTimeMillis());
         }
      }
      catch(Exception ex) {
         LOG.warn("Failed to load database authentication cache for provider '{}'", providerName, ex);
         handleError(current);
      }
      finally {
         // the periodic reload is rescheduled by scheduledReload(), whatever happened here
         loadingCount.set(0);

         // A refresh that arrived while this load ran lost the load lock, and this load may have
         // used the configuration from before it: load once more.
         if(!cancelled.get() && refreshSeq.get() != seqAtStart) {
            try {
               executor.submit(() -> loadInternal(false));
            }
            catch(RejectedExecutionException ignore) {
               // cancelled
            }
         }
      }
   }

   private void handleError(DatabaseAuthenticationProvider current) {
      lastFailureTime.set(System.currentTimeMillis());

      if(current != null) {
         current.getConnectionProvider().resetConnection();
      }
   }

   /**
    * Resolves the provider to load with. The host's own chain is the current configuration, so
    * the provider is looked up there first. If it is not there, for example while the host's
    * chain swaps its provider instances, the last bound instance is used, which may be
    * {@code null} on a redeployed instance. A torn down instance, found in the chain or bound
    * before, is not available: loading with it would open a pool that nothing closes (Bug
    * #79352), so {@code null} is returned and the load is retried later.
    */
   private DatabaseAuthenticationProvider resolveProvider() {
      try {
         DatabaseAuthenticationProvider found = lookupProvider();

         if(found != null && found.isTornDown()) {
            LOG.debug("Provider {} found on this host is torn down", providerName);
            return null;
         }

         provider = found;
      }
      catch(Exception e) {
         LOG.debug("Provider {} not found on this host, using the last bound instance: {}",
                   providerName, e.getMessage());
      }

      DatabaseAuthenticationProvider result = provider;
      return result != null && result.isTornDown() ? null : result;
   }

   /**
    * Looks the provider up in the security chain of this node.
    *
    * @throws IllegalStateException if the provider is not in this node's chain.
    */
   DatabaseAuthenticationProvider lookupProvider() {
      return getProvider(providerName);
   }

   private static DatabaseAuthenticationProvider getProvider(String providerName) {
      // Without the engine's init lock or the chain's read lock: the callers may hold the
      // provider's cache locks, which the engine takes while it holds the init lock, and an EM
      // edit takes while it holds the chain's write lock, to tear the providers down.
      SecurityProvider root = SecurityEngine.getSecurity().peekSecurityProvider();

      if(root == null) {
         throw new IllegalStateException("Security not configured");
      }

      AuthenticationProvider rootAuthentication = root.getAuthenticationProvider();
      DatabaseAuthenticationProvider provider = null;

      if(rootAuthentication instanceof DatabaseAuthenticationProvider db &&
         Objects.equals(providerName, db.getProviderName()))
      {
         provider = db;
      }
      else if(rootAuthentication instanceof AuthenticationChain chain) {
         for(AuthenticationProvider child : chain.peekProviders()) {
            if(child instanceof DatabaseAuthenticationProvider db &&
               Objects.equals(providerName, db.getProviderName()))
            {
               provider = db;
               break;
            }
         }
      }

      if(provider == null) {
         throw new IllegalStateException(Tool.buildString("Security provider:", providerName, " not found"));
      }

      return provider;
   }

   private Instant getLastLoadTime() {
      long ts = lastLoadTime.get();

      if(ts == 0L) {
         return null;
      }

      return Instant.ofEpochMilli(ts);
   }

   private Instant getLastFailureTime() {
      long ts = lastFailureTime.get();

      if(ts == 0L) {
         return null;
      }

      return Instant.ofEpochMilli(ts);
   }

   private boolean isReloadRequired() {
      long interval = getCacheInterval();

      if(interval <= 0L) {
         // the cache was turned off, or the interval is invalid
         return false;
      }

      Instant loaded = getLastLoadTime();
      Instant failed = getLastFailureTime();

      if(failed != null && (loaded == null || failed.isAfter(loaded))) {
         // the same condition and time as the failure branch of getNextReloadDelay()
         return !Instant.now().isBefore(failed.plusMillis(FAILURE_RETRY_DELAY_MS));
      }

      return loaded == null || Instant.now().isAfter(loaded.plusMillis(interval));
   }

   private long getNextReloadDelay() {
      long interval = getCacheInterval();

      if(interval <= 0L) {
         return IDLE_DELAY_MS;
      }

      Instant loaded = getLastLoadTime();
      Instant failed = getLastFailureTime();

      if(failed != null && (loaded == null || failed.isAfter(loaded))) {
         return Math.max(MIN_RELOAD_DELAY_MS, Duration.between(
            Instant.now(), failed.plusMillis(FAILURE_RETRY_DELAY_MS)).toMillis());
      }

      if(loaded == null) {
         // shouldn't happen based on the sequence of calls, but just in case
         return interval;
      }

      long delay = interval - Duration.between(loaded, Instant.now()).toMillis();
      return Math.max(MIN_RELOAD_DELAY_MS, delay);
   }

   private final String providerName;

   private transient volatile DatabaseAuthenticationProvider provider;
   private transient Cluster cluster;
   private transient String prefix;

   @SuppressWarnings("rawtypes")
   private transient DistributedMap<String, Set> lists;
   private transient DistributedMap<String, String> orgNames;
   private transient DistributedMap<String, String[]> orgMembers;
   private transient DistributedMap<String, String[]> orgRoles;
   private transient DistributedMap<IdentityID, IdentityArray> groupUsers;
   private transient DistributedMap<IdentityID, IdentityArray> userRoles;
   private transient DistributedMap<IdentityID, String[]> userEmails;

   private transient DistributedLong lastLoadTime;
   private transient DistributedLong lastFailureTime;
   private transient DistributedLong loadingCount;
   private transient AtomicBoolean cancelled;
   private transient DistributedLong clientCount;
   private transient DistributedLong zeroSince;
   private transient DistributedLong refreshSeq;
   private transient ScheduledExecutorService executor;

   private static final Logger LOG =
      LoggerFactory.getLogger(DatabaseAuthenticationCacheServiceImpl.class);

   private static final String ORG_LIST = "orgs";
   private static final String USER_LIST = "users";
   private static final String GROUP_LIST = "groups";
   private static final String ROLE_LIST = "roles";

   // Grace period before cleaning up a service with no active clients (5 minutes)
   private static final long CLEANUP_GRACE_PERIOD_MS = 5 * 60 * 1000L;
   // How often to check for cleanup (5 minutes)
   private static final long CLEANUP_CHECK_INTERVAL_MS = 5 * 60 * 1000L;
   // The shortest delay between two ticks of the periodic reload, so that a tick that returns
   // early while a reload is overdue can't spin
   private static final long MIN_RELOAD_DELAY_MS = 1000L;
   // How long to wait before retrying a failed load (1 minute)
   private static final long FAILURE_RETRY_DELAY_MS = 60_000L;
   // The tick delay while the cache is disabled (1 hour, the default interval)
   private static final long IDLE_DELAY_MS = 3_600_000L;
}
