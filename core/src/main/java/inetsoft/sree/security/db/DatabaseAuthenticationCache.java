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
import org.apache.ignite.IgniteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class DatabaseAuthenticationCache implements AutoCloseable {
   public DatabaseAuthenticationCache(DatabaseAuthenticationProvider provider) {
      this.provider = provider;
   }

   private boolean initialize() {
      if(service != null) {
         return true;
      }

      if(provider.getProviderName() == null) {
         return false;
      }

      serviceLock.lock();

      try {
         if(service == null) {
            try {
               Cluster cluster = Cluster.getInstance();
               String prefix = "DatabaseSecurity:" + provider.getProviderName();
               this.service = cluster.getSingletonService(
                  prefix, DatabaseAuthenticationCacheService.class,
                  () -> new DatabaseAuthenticationCacheServiceImpl(provider.getProviderName()));
               service.connect();
               this.lists = cluster.getReplicatedMap(prefix + ".lists");
               this.orgNames = cluster.getReplicatedMap(prefix + ".orgNames");
               this.orgMembers = cluster.getReplicatedMap(prefix + ".orgMembers");
               this.groupUsers = cluster.getReplicatedMap(prefix + ".groupUsers");
               this.userRoles = cluster.getReplicatedMap(prefix + ".userRoles");
               this.userEmails = cluster.getReplicatedMap(prefix + ".userEmails");
            }
            catch(Exception ex) {
               service = null;
               LOG.error("Failed to initialize service for provider: " + provider.getProviderName(), ex);
               return false;
            }
         }
      }
      finally {
         serviceLock.unlock();
      }

      return true;
   }

   @Override
   public void close() throws Exception {
      serviceLock.lock();

      try {
         if(service != null) {
            try {
               service.disconnect();
            }
            catch(IgniteException e) {
               // Service may have been undeployed by another node, ignore
               LOG.debug("Error disconnecting from service, may have been undeployed", e);
            }
            finally {
               service = null;
            }
         }
      }
      finally {
         serviceLock.unlock();
      }
   }

   public void load() {
      if(!initialize()) {
         return;
      }

      try {
         service.load();
      }
      catch(IgniteException e) {
         if(isServiceNotFoundError(e)) {
            resetService();
            if(initialize()) {
               service.load();
            }
         }
         else {
            throw e;
         }
      }
   }

   public void refresh() {
      if(!initialize()) {
         return;
      }

      try {
         service.refresh();
      }
      catch(IgniteException e) {
         if(isServiceNotFoundError(e)) {
            resetService();
            if(initialize()) {
               service.refresh();
            }
         }
         else {
            throw e;
         }
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

   public Map<IdentityID, IdentityID[]> getAllUserRoles() {
      if(!initialize()) {
         return Map.of();
      }

      Map<IdentityID, IdentityID[]> result = new HashMap<IdentityID, IdentityID[]>();

      for(Map.Entry<IdentityID, IdentityArray> entry : userRoles.entrySet()) {
         result.put(entry.getKey(), entry.getValue().getValue());
      }

      return result;
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
      if(!initialize()) {
         return false;
      }

      try {
         return service.isLoading();
      }
      catch(IgniteException e) {
         if(isServiceNotFoundError(e)) {
            resetService();
            return initialize() && service.isLoading();
         }
         throw e;
      }
   }

   public boolean isInitialized() {
      if(!initialize()) {
         return false;
      }

      try {
         return service.isInitialized();
      }
      catch(IgniteException e) {
         if(isServiceNotFoundError(e)) {
            resetService();
            return initialize() && service.isInitialized();
         }
         throw e;
      }
   }

   public long getAge() {
      if(!initialize()) {
         return 0L;
      }

      try {
         return service.getAge();
      }
      catch(IgniteException e) {
         if(isServiceNotFoundError(e)) {
            resetService();
            return initialize() ? service.getAge() : 0L;
         }
         throw e;
      }
   }

   /**
    * Checks if the exception indicates that the Ignite service was not found.
    */
   private boolean isServiceNotFoundError(IgniteException e) {
      String message = e.getMessage();
      return message != null && message.contains("Failed to find deployed service");
   }

   /**
    * Resets the service reference so it can be reinitialized on next access.
    * This is used to recover from cases where the service was unexpectedly undeployed.
    */
   private void resetService() {
      serviceLock.lock();

      try {
         LOG.warn("Service not found, resetting for reinitialization: DatabaseSecurity:{}",
            provider.getProviderName());
         service = null;
         lists = null;
         orgNames = null;
         orgMembers = null;
         groupUsers = null;
         userRoles = null;
         userEmails = null;
      }
      finally {
         serviceLock.unlock();
      }
   }

   private final DatabaseAuthenticationProvider provider;
   private volatile DatabaseAuthenticationCacheService service;
   private final Lock serviceLock = new ReentrantLock();
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