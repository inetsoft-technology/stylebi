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

public class DatabaseAuthenticationCacheServiceImp implements DatabaseAuthenticationCacheService {
   public DatabaseAuthenticationCacheServiceImp(String providerName) {
      this.providerName = providerName;
   }

   @Override
   public void cancel() {
      cancelled = true;

      if (executor != null) {
         executor.shutdownNow();

         try {
            //noinspection ResultOfMethodCallIgnored
            executor.awaitTermination(15, TimeUnit.SECONDS);
         }
         catch(InterruptedException e) {
            LOG.warn("Interrupted while waiting for database authentication cache to shutdown", e);
         }
      }

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
      }
   }

   @Override
   public void init() throws Exception {
      provider = getProvider(providerName);
      cluster = Cluster.getInstance();
      prefix = "DatabaseSecurity:" + providerName;
      this.lists = cluster.getReplicatedMap(prefix + ".lists");
      this.orgNames = cluster.getReplicatedMap(prefix + ".orgNames");
      this.orgMembers = cluster.getReplicatedMap(prefix + ".orgMembers");
      this.orgRoles = cluster.getReplicatedMap(prefix + ".orgRoles");
      this.groupUsers = cluster.getReplicatedMap(prefix + ".groupUsers");
      this.userRoles = cluster.getReplicatedMap(prefix + ".userRoles");
      this.userEmails = cluster.getReplicatedMap(prefix + ".userEmails");
      this.cancelled = false;
      this.clientCount = cluster.getLong(prefix + ".clientCount");
      this.loadingCount = cluster.getLong(prefix + ".loadingCount");
      this.lastLoadTime = cluster.getLong(prefix + ".lastLoadTime");
      this.lastFailureTime = cluster.getLong(prefix + ".lastFailureTime");
      this.executor = Executors.newScheduledThreadPool(2, r ->
         new Thread(r, "DatabaseAuthenticationCache"));
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
      executor.submit(() -> loadInternal(false));
   }

   public void connect() {
      clientCount.incrementAndGet();
   }

   public void disconnect() {
      if(clientCount.decrementAndGet() == 0) {
         if(cluster != null) {
            cluster.undeploySingletonService(prefix);
         }
      }
   }

   private void loadInternal(boolean scheduled) {
      if(cancelled) {
         return;
      }

      if(scheduled && !isReloadRequired()) {
         return;
      }

      if(!loadingCount.compareAndSet(0, 1)) {
         return;
      }

      try {
         AuthenticationDAO dao = provider.getDao();
         QueryResult<IdentityID[]> newUsers = dao.getUsers();

         if(cancelled) {
            return;
         }

         if(newUsers.failed()) {
            handleError();
            return;
         }

         QueryResult<IdentityID[]> newRoles = dao.getRoles();

         if(cancelled) {
            return;
         }

         if(newRoles.failed()) {
            handleError();
            return;
         }

         QueryResult<IdentityID[]> newGroups = dao.getGroups();

         if(cancelled) {
            return;
         }

         if(newGroups.failed()) {
            handleError();
            return;
         }

         QueryResult<String[]> newOrganizations = dao.getOrganizations();

         if(cancelled) {
            return;
         }

         if(newOrganizations.failed()) {
            handleError();
            return;
         }

         Map<String, String> newOrgNames = new HashMap<>();
         Map<String, String[]> newOrgMembers = new HashMap<>();
         Map<String, String[]> newOrgRoles = new HashMap<>();

         for(String orgID : newOrganizations.result()) {
            String name = dao.getOrganizationName(orgID);

            if(cancelled) {
               return;
            }

            newOrgNames.put(orgID, name);

            QueryResult<String[]> result;

            if((result = dao.getOrganizationMembers(orgID)).failed()) {
               handleError();
               return;
            }

            if(cancelled) {
               return;
            }

            newOrgMembers.put(orgID, result.result());

            if((result = dao.getOrganizationRoles(orgID)).failed()) {
               handleError();
               return;
            }

            if(cancelled) {
               return;
            }

            newOrgRoles.put(orgID, result.result());
         }

         try(DistributedTransaction tx = cluster.startTx()) {
            lists.removeAll();
            lists.put(ORG_LIST, new TreeSet<>(Arrays.asList(newOrganizations.result())));
            lists.put(USER_LIST, new TreeSet<>(Arrays.asList(newUsers.result())));
            lists.put(GROUP_LIST, new TreeSet<>(Arrays.asList(newGroups.result())));
            lists.put(ROLE_LIST, new TreeSet<>(Arrays.asList(newRoles.result())));

            orgNames.removeAll();
            orgNames.putAll(newOrgNames);

            orgMembers.removeAll();
            orgMembers.putAll(newOrgMembers);

            orgRoles.removeAll();
            orgRoles.putAll(newOrgRoles);

            groupUsers.removeAll();
            userRoles.removeAll();
            userEmails.removeAll();

            tx.commit();
            lastLoadTime.set(System.currentTimeMillis());
         }
      }
      finally {
         loadingCount.set(0);

         if(scheduled) {
            executor.schedule(() -> loadInternal(true), getNextReloadDelay(), TimeUnit.MILLISECONDS);
         }
      }
   }

   private void handleError() {
      lastFailureTime.set(System.currentTimeMillis());
      provider.getConnectionProvider().resetConnection();
   }

   private static DatabaseAuthenticationProvider getProvider(String providerName) {
      SecurityProvider root = SecurityEngine.getSecurity().getSecurityProvider();

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
         for(AuthenticationProvider child : chain.getProviders()) {
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
      Instant loaded = getLastLoadTime();
      Instant failed = getLastFailureTime();

      if(failed != null && (loaded == null || failed.isAfter(loaded.plusSeconds(60L)))) {
         return true;
      }

      return loaded == null ||
         Instant.now().isAfter(loaded.plusMillis(provider.getCacheRefreshDelay()));
   }

   private long getNextReloadDelay() {
      Instant loaded = getLastLoadTime();
      Instant failed = getLastFailureTime();

      if(failed != null && (loaded == null || failed.isAfter(loaded))) {
         return failed.plusMillis(60L).toEpochMilli();
      }

      long interval = provider.getCacheRefreshDelay();

      if(loaded == null) {
         // shouldn't happen based on the sequence of calls, but just in case
         return interval;
      }

      long delay = interval - Duration.between(loaded, Instant.now()).toMillis();

      if(delay <= 0) {
         return 1L;
      }

      return delay;
   }

   private final String providerName;

   private transient DatabaseAuthenticationProvider provider;
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
   private transient boolean cancelled;
   private transient DistributedLong clientCount;
   private transient ScheduledExecutorService executor;

   private static final Logger LOG =
      LoggerFactory.getLogger(DatabaseAuthenticationCacheServiceImp.class);

   private static final String ORG_LIST = "orgs";
   private static final String USER_LIST = "users";
   private static final String GROUP_LIST = "groups";
   private static final String ROLE_LIST = "roles";
}
