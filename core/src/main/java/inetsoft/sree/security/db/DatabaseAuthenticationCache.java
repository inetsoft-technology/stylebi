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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.sree.security.IdentityID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

class DatabaseAuthenticationCache implements AutoCloseable {
   public DatabaseAuthenticationCache(DatabaseAuthenticationProvider provider) {
      this.provider = provider;
   }

   private boolean initialize() {
      if(provider.getProviderName() != null && initialized.compareAndSet(false, true)) {
         Cluster cluster = Cluster.getInstance();
         String prefix = "DatabaseSecurity:" + provider.getProviderName();
         this.service = cluster.getSingletonService(
            prefix, DatabaseAuthenticationCacheService.class,
            () -> new DatabaseAuthenticationCacheService(provider.getProviderName()));
         service.connect();
         this.lists = cluster.getReplicatedMap(prefix + ".lists");
         this.orgNames = cluster.getReplicatedMap(prefix + ".orgNames");
         this.orgMembers = cluster.getReplicatedMap(prefix + ".orgMembers");
         this.groupUsers = cluster.getReplicatedMap(prefix + ".groupUsers");
         this.userRoles = cluster.getReplicatedMap(prefix + ".userRoles");
         this.userEmails = cluster.getReplicatedMap(prefix + ".userEmails");
      }

      // can happen when creating data source and connection properties are set.
      return provider.getProviderName() != null;
   }

   @Override
   public void close() throws Exception {
      if(service != null) {
         service.disconnect();
      }
   }

   public void load() {
      if(initialize()) {
         service.load();
      }
   }

   public void refresh() {
      if(initialize()) {
         service.refresh();
      }
   }

   @SuppressWarnings("unchecked")
   public IdentityID[] getUsers() {
      if(!initialize()) {
         return new IdentityID[0];
      }

      Set<IdentityID> users = lists.get(USER_LIST);

      if(users == null) {
         return new IdentityID[0];
      }

      return users.toArray(new IdentityID[0]);
   }

   @SuppressWarnings("unchecked")
   public String[] getOrganizations() {
      if(!initialize()) {
         return new String[0];
      }

      Set<String> organizations = lists.get(ORG_LIST);

      if(organizations == null) {
         return new String[0];
      }

      return organizations.toArray(new String[0]);
   }

   public String getOrganizationName(String orgID) {
      if(!initialize()) {
         return null;
      }

      return orgNames.get(orgID);
   }

   public String[] getOrganizationMembers(String orgID) {
      if(!initialize()) {
         return null;
      }

      return orgMembers.get(orgID);
   }

   public IdentityID[] getUsers(IdentityID groupIdentity) {
      if(!initialize()) {
         return new IdentityID[0];
      }

      return groupUsers.computeIfAbsent(groupIdentity, k ->
         new IdentityArray(provider.getDao().getUsers(k).result())).getValue();
   }

   @SuppressWarnings("unchecked")
   public IdentityID[] getRoles() {
      if(!initialize()) {
         return new IdentityID[0];
      }

      Set<IdentityID> roles = lists.get(ROLE_LIST);

      if(roles == null) {
         return new IdentityID[0];
      }

      return roles.toArray(new IdentityID[0]);
   }

   public IdentityID[] getRoles(IdentityID userId) {
      if(!initialize()) {
         return new IdentityID[0];
      }

      return userRoles.computeIfAbsent(userId, k ->
         new IdentityArray(provider.getDao().getRoles(k).result())).getValue();
   }

   @SuppressWarnings("unchecked")
   public IdentityID[] getGroups() {
      if(!initialize()) {
         return new IdentityID[0];
      }

      Set<IdentityID> groups = lists.get(GROUP_LIST);

      if(groups == null) {
         return new IdentityID[0];
      }

      return groups.toArray(new IdentityID[0]);
   }

   public String[] getEmails(IdentityID userIdentity) {
      if(!initialize()) {
         return new String[0];
      }

      return userEmails.computeIfAbsent(userIdentity, k -> provider.getDao().getEmails(k).result());
   }

   public boolean isLoading() {
      return initialize() && service.isLoading();
   }

   public boolean isInitialized() {
      return initialize() && service.isInitialized();
   }

   public long getAge() {
      return initialize() ? service.getAge() : 0L;
   }

   private final DatabaseAuthenticationProvider provider;
   private DatabaseAuthenticationCacheService service;
   private final AtomicBoolean initialized = new AtomicBoolean(false);
   @SuppressWarnings("rawtypes")
   private DistributedMap<String, Set> lists;
   private DistributedMap<String, String> orgNames;
   private DistributedMap<String, String[]> orgMembers;
   private DistributedMap<IdentityID, IdentityArray> groupUsers;
   private DistributedMap<IdentityID, IdentityArray> userRoles;
   private DistributedMap<IdentityID, String[]> userEmails;

   private static final Logger LOG = LoggerFactory.getLogger(DatabaseAuthenticationCache.class);
   private static final String ORG_LIST = "orgs";
   private static final String USER_LIST = "users";
   private static final String GROUP_LIST = "groups";
   private static final String ROLE_LIST = "roles";
}
