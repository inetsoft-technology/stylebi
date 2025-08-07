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
import inetsoft.sree.security.IdentityID;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

class DatabaseAuthenticationCache implements AutoCloseable {
   public DatabaseAuthenticationCache(DatabaseAuthenticationProvider provider) {
      this.provider = provider;
      this.cluster = Cluster.getInstance();
      prefix = "DatabaseSecurity:" + provider.getProviderName();
      this.lastLoad = cluster.getLong(prefix + ".lastLoad");
      this.lastFailure = cluster.getLong(prefix + ".lastFailure");
      this.loadCount = cluster.getLong(prefix + ".loadCount");
      this.lists = cluster.getReplicatedMap(prefix + ".lists");
      this.orgNames = cluster.getReplicatedMap(prefix + ".orgNames");
      this.orgMembers = cluster.getReplicatedMap(prefix + ".orgMembers");
      this.orgRoles = cluster.getReplicatedMap(prefix + ".orgRoles");
      this.groupUsers = cluster.getReplicatedMap(prefix + ".groupUsers");
      this.userRoles = cluster.getReplicatedMap(prefix + ".userRoles");
      this.userEmails = cluster.getReplicatedMap(prefix + ".userEmails");
      this.executor = Executors.newSingleThreadScheduledExecutor(
         r -> new Thread(r, "DatabaseAuthenticationCache"));
   }

   @Override
   public void close() throws Exception {
      executor.shutdown();
   }

   public void load() {
      refresh(false, true);
   }

   public void refresh() {
      refresh(true, false);
   }

   private void refresh(boolean force, boolean reschedule) {
      long next = -1;

      try {
         if(!Tool.equals(cluster.getServiceOwner(prefix), cluster.getLocalMember())) {
            next = cluster.submit(
               prefix, new RefreshCacheTask(provider.getProviderName(), force)).get();
         }
         else {
            next = new RefreshCacheTask(provider.getProviderName(), force).call();
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to refresh database authentication cache", e);
      }

      if(reschedule && next > 0) {
         executor.schedule(() -> refresh(false, true), next, TimeUnit.MILLISECONDS);
      }
   }

   long load(boolean force) {
      if(loadCount.incrementAndGet() > 1) {
         loadCount.decrementAndGet();
         return isFailedState() ? 60000L : provider.getCacheRefreshDelay();
      }

      try {
         if(!force && !isReloadRequired()) {
            return getNextReloadDelay();
         }

         AuthenticationDAO dao = provider.getDao();
         QueryResult<IdentityID[]> newUsers = dao.getUsers();

         if(newUsers.failed()) {
            handleError();
            return getNextReloadDelay();
         }

         QueryResult<IdentityID[]> newRoles = dao.getRoles();

         if(newRoles.failed()) {
            handleError();
            return getNextReloadDelay();
         }

         QueryResult<IdentityID[]> newGroups = dao.getGroups();

         if(newGroups.failed()) {
            handleError();
            return getNextReloadDelay();
         }

         QueryResult<String[]> newOrganizations = dao.getOrganizations();

         if(newOrganizations.failed()) {
            handleError();
            return getNextReloadDelay();
         }

         Map<String, String> newOrgNames = new HashMap<>();
         Map<String, String[]> newOrgMembers = new HashMap<>();
         Map<String, String[]> newOrgRoles = new HashMap<>();

         for(String orgID : newOrganizations.result()) {
            String name = dao.getOrganizationName(orgID);
            newOrgNames.put(orgID, name);

            QueryResult<String[]> result;

            if((result = dao.getOrganizationMembers(orgID)).failed()) {
               handleError();
               return getNextReloadDelay();
            }

            newOrgMembers.put(orgID, result.result());

            if((result = dao.getOrganizationRoles(orgID)).failed()) {
               handleError();
               return getNextReloadDelay();
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
            lastLoad.set(System.currentTimeMillis());
         }
      }
      finally {
         loadCount.decrementAndGet();
      }

      return getNextReloadDelay();
   }

   @SuppressWarnings("unchecked")
   public IdentityID[] getUsers() {
      Set<IdentityID> users = lists.get(USER_LIST);
      return users == null ? new IdentityID[0] : users.toArray(new IdentityID[0]);
   }

   @SuppressWarnings("unchecked")
   public String[] getOrganizations() {
      Set<String> organizations = lists.get(ORG_LIST);
      return organizations == null ? new String[0] : organizations.toArray(new String[0]);
   }

   public String getOrganizationName(String orgID) {
      return orgNames.get(orgID);
   }

   public String[] getOrganizationMembers(String orgID) {
      return orgMembers.get(orgID);
   }

   public IdentityID[] getUsers(IdentityID groupIdentity) {
      return groupUsers.computeIfAbsent(groupIdentity, k -> new IdentityArrayWrapper(provider.getDao().getUsers(k).result())).getValue();
   }

   @SuppressWarnings("unchecked")
   public IdentityID[] getRoles() {
      Set<IdentityID> roles = lists.get(ROLE_LIST);
      return roles == null ? new IdentityID[0] :roles.toArray(new IdentityID[0]);
   }

   public IdentityID[] getRoles(IdentityID userId) {
      return userRoles.computeIfAbsent(userId, k -> new IdentityArrayWrapper(provider.getDao().getRoles(k).result())).getValue();
   }

   @SuppressWarnings("unchecked")
   public IdentityID[] getGroups() {
      Set<IdentityID> groups = lists.get(GROUP_LIST);
      return groups == null ? new IdentityID[0] : groups.toArray(new IdentityID[0]);
   }

   public String[] getEmails(IdentityID userIdentity) {
      return userEmails.computeIfAbsent(userIdentity, k -> provider.getDao().getEmails(k).result());
   }

   public boolean isLoading() {
      return loadCount.get() > 0;
   }

   public boolean isInitialized() {
      return lastLoad.get() != 0;
   }

   public long getAge() {
      long loaded = lastLoad.get();
      return loaded == 0L ? 0L : System.currentTimeMillis() - loaded;
   }

   private Instant getLastLoadTime() {
      long ts = lastLoad.get();

      if(ts == 0L) {
         return null;
      }

      return Instant.ofEpochMilli(ts);
   }

   private Instant getLastFailureTime() {
      long ts = lastFailure.get();

      if(ts == 0L) {
         return null;
      }

      return Instant.ofEpochMilli(ts);
   }

   private boolean isFailedState() {
      Instant loaded = getLastLoadTime();
      Instant failed = getLastFailureTime();
      return failed != null && (loaded == null || failed.isAfter(loaded.plusSeconds(60L)));
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

   private void handleError() {
      lastFailure.set(System.currentTimeMillis());
      provider.getConnectionProvider().resetConnection();
   }

   private static class IdentityArrayWrapper {
      public IdentityArrayWrapper(IdentityID[] value) {
         this.value = value;
      }

      public IdentityID[] getValue() {
         return value;
      }

      private final IdentityID[] value;
   }

   private final DatabaseAuthenticationProvider provider;
   private final Cluster cluster;
   private final String prefix;
   private final DistributedLong lastLoad;
   private final DistributedLong lastFailure;
   private final DistributedLong loadCount;
   @SuppressWarnings("rawtypes")
   private final DistributedMap<String, Set> lists;
   private final DistributedMap<String, String> orgNames;
   private final DistributedMap<String, String[]> orgMembers;
   private final DistributedMap<String, String[]> orgRoles;
   private final DistributedMap<IdentityID, IdentityArrayWrapper> groupUsers;
   private final DistributedMap<IdentityID, IdentityArrayWrapper> userRoles;
   private final DistributedMap<IdentityID, String[]> userEmails;
   private final ScheduledExecutorService executor;

   private static final Logger LOG = LoggerFactory.getLogger(DatabaseAuthenticationCache.class);
   private static final String ORG_LIST = "orgs";
   private static final String USER_LIST = "users";
   private static final String GROUP_LIST = "groups";
   private static final String ROLE_LIST = "roles";
}
