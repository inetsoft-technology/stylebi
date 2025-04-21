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
package inetsoft.sree.security;

import inetsoft.sree.schedule.TimeRange;
import inetsoft.storage.*;
import inetsoft.util.SingletonManager;
import inetsoft.util.Tuple3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Authorization module that uses the file system to store the ACL.
 *
 * @author InetSoft Technology
 * @since  8.5
 */
public class FileAuthorizationProvider extends AbstractAuthorizationProvider {
   /**
    * Initializes this module.
    */
   private synchronized void init() {
      if(storage != null) {
         return;
      }

      storage = SingletonManager.getInstance(KeyValueStorage.class,
                                   "defaultSecurityPermissions",
                                   (Supplier<LoadPermissionsTask>) LoadPermissionsTask::new);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Permission getPermission(ResourceType type, String resource) {
      init();
      return storage.get(getResourceKey(type, resource));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Permission getPermission(ResourceType type, IdentityID resource) {
      init();
      return storage.get(getResourceKey(type, resource.convertToKey()));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public List<Tuple3<ResourceType, String, Permission>> getPermissions() {
      init();

      Function<KeyValuePair<Permission>, Tuple3<ResourceType, String, Permission>> mapper =
         pair -> {
            String key = pair.getKey();
            int delimiter = key.indexOf(":");

            ResourceType type = ResourceType.valueOf(key.substring(0, delimiter));
            String path = key.substring(delimiter + 1);

            return new Tuple3<>(type, path, pair.getValue());
         };

      return storage.stream().map(mapper).collect(Collectors.toList());
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setPermission(ResourceType type, String resource, Permission perm) {
      init();

      if(perm == null) {
         removePermission(type, resource);
      }
      else {
         try {
            storage.put(getResourceKey(type, resource), perm).get();
         }
         catch(Exception e) {
            LOG.error("Failed to set permission on {} {}", type, resource, e);
         }
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setPermission(ResourceType type, IdentityID identityID, Permission perm) {
      init();

      if(perm == null) {
         removePermission(type, identityID);
      }
      else {
         try {
            storage.put(getResourceKey(type, identityID.convertToKey()), perm).get();
         }
         catch(Exception e) {
            LOG.error("Failed to set permission on {} {}", type, identityID, e);
         }
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void removePermission(ResourceType type, String resource) {
      init();

      try {
         storage.remove(getResourceKey(type, resource)).get();
      }
      catch(Exception e) {
         LOG.error("Failed to remove permission from {} {}", type, resource, e);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void removePermission(ResourceType type, String resource, String orgID) {
      init();

      Permission perm = storage.get(getResourceKey(type, resource));

      for(ResourceAction action: ResourceAction.values()) {
         perm.cleanOrganizationFromPermission(action, orgID);
      }

      setPermission(type, resource, perm);
   }

   public void cleanOrganizationFromPermissions(String orgId) {
      for(Tuple3<ResourceType, String, Permission> permissionSet : getPermissions()) {
         for (ResourceAction action : ResourceAction.values()) {
            Permission permission = permissionSet.getThird();
            if(permission.isOrgInPerm(action, orgId)) {
               permission.cleanOrganizationFromPermission(action, orgId);
               permission.removeGrantAllByOrg(orgId);
               setPermission(permissionSet.getFirst(),permissionSet.getSecond(), permission);
            }
         }
      }
   }

   /**
    * Tear down the security provider.
    */
   @Override
   public void tearDown() {
      if(storage != null) {
         try {
            storage.close();
         }
         catch(Exception e) {
            LOG.warn("Failed to close permission storate", e);
         }

         storage = null;
      }
   }

   /**
    * Signals that a security object has been removed or renamed.
    *
    * @param event the object that describes the change event.
    */
   @Override
   public void authenticationChanged(AuthenticationChangeEvent event) {
      init();
      int type = event.getType();
      boolean removed = event.isRemoved();
      IdentityID oldID = event.getOldID();
      IdentityID newID = event.getNewID();

      List<KeyValuePair<Permission>> list = storage.stream().collect(Collectors.toList());

      try {
         for(KeyValuePair<Permission> pair : list) {
            Permission perm = pair.getValue();
            boolean changed = false;

            for(ResourceAction action : ResourceAction.values()) {
               Set<Permission.PermissionIdentity> identities = perm.getGrants(action, type);

               if(identities.remove(oldID) && !removed) {
                  identities.add(new Permission.PermissionIdentity(newID.name, newID.orgID));
                  perm.setGrants(action, type, identities);
                  changed = true;
               }
            }

            if(changed) {
               storage.put(pair.getKey(), perm).get();
            }
         }
      }
      catch(Exception e) {
         LOG.error("Failed to update permissions", e);
      }
   }

   private static String getResourceKey(ResourceType type, String path) {
         return type + ":" + path;
   }

   private KeyValueStorage<Permission> storage;
   private static final Logger LOG = LoggerFactory.getLogger(FileAuthorizationProvider.class);

   private static final class LoadPermissionsTask extends LoadKeyValueTask<Permission> {
      LoadPermissionsTask() {
         super("defaultSecurityPermissions");
      }

      @Override
      protected Class<Permission> initialize(Map<String, Permission> map) {
         addDefaultAdminPermissions(Organization.getDefaultOrganizationID(), map);
         addDefaultAdminPermissions(Organization.getSelfOrganizationID(), map);

         addDefaultRoleGrants(map);
         addDefaultPermissionForSelfOrg(map);

         return Permission.class;
      }

      private void addDefaultAdminPermissions(String orgId, Map<String, Permission> map) {
         Permission perm = new Permission();

         for(ResourceAction action : ResourceAction.values()) {
            Set<String> roles = new HashSet<>(Arrays.asList("Administrator","Organization Administrator"));
            perm.setRoleGrantsForOrg(action, roles, orgId);
         }

         map.put(getResourceKey(ResourceType.REPORT, "Built-in Admin Reports"), perm);
      }

      public static void addDefaultRoleGrants(Map<String, Permission> map) {
         Permission perm = new Permission();

         String defaultorgName = Organization.getDefaultOrganizationName();
         String defaultorgId = Organization.getDefaultOrganizationID();

         String selforgName = Organization.getSelfOrganizationName();
         String selforgId = Organization.getSelfOrganizationID();

         perm = new Permission();
         Map<String, Boolean> defedited = new HashMap<>();
         defedited.put(defaultorgId, true);

         String name = "Advanced";
         perm.setRoleGrantsForOrg(ResourceAction.ACCESS, Collections.singleton(name), defaultorgId);
         perm.setOrgEditedGrantAll(defedited);

         map.put(getResourceKey(ResourceType.SCHEDULER, "*"), perm);

         //self org permissions for remaining
         Map<String, Boolean> edited = new HashMap<>();
         edited.put(defaultorgId, true);
         edited.put(selforgId, true);

         perm = new Permission();

         name = "Designer";
         perm.setRoleGrantsForOrg(ResourceAction.ACCESS, Collections.singleton(name), defaultorgId);
         perm.setOrganizationGrantsForOrg(ResourceAction.ACCESS, Collections.singleton(selforgName), selforgId);
         perm.setOrgEditedGrantAll(edited);

         map.put(getResourceKey(ResourceType.COMPOSER, "*"), perm);
         map.put(getResourceKey(ResourceType.WORKSHEET, "*"), perm);
         map.put(getResourceKey(ResourceType.VIEWSHEET, "*"), perm);

         perm = new Permission();

         name = "Designer";
         perm.setRoleGrantsForOrg(ResourceAction.READ, Collections.singleton(name), defaultorgId);
         perm.setRoleGrantsForOrg(ResourceAction.WRITE, Collections.singleton(name), defaultorgId);
         perm.setOrganizationGrantsForOrg(ResourceAction.READ, Collections.singleton(selforgName), selforgId);
         perm.setOrganizationGrantsForOrg(ResourceAction.WRITE, Collections.singleton(selforgName), selforgId);
         perm.setOrgEditedGrantAll(edited);

         map.put(getResourceKey(ResourceType.DASHBOARD, "*"), perm);

         for(TimeRange range : TimeRange.getTimeRanges()) {
            map.put(getResourceKey(ResourceType.SCHEDULE_TIME_RANGE, range.getName()), perm);
         }

         perm = new Permission();

         name = "Advanced";
         perm.setRoleGrantsForOrg(ResourceAction.ACCESS, Collections.singleton(name), defaultorgId);
         perm.setOrgEditedGrantAll(edited);

         for(TimeRange range : TimeRange.getTimeRanges()) {
            map.put(getResourceKey(ResourceType.SCHEDULE_TIME_RANGE, range.getName()), perm);
         }
      }

      /**
       * Add the getting started required permission for the self org.
       */
      private static void addDefaultPermissionForSelfOrg(Map<String, Permission> map) {
         String selfOrganizationName = Organization.getSelfOrganizationName();
         String selfOrgId = Organization.getSelfOrganizationID();
         Permission perm = new Permission();
         perm.setOrganizationGrantsForOrg(ResourceAction.ACCESS,
            Collections.singleton(selfOrganizationName), selfOrgId);
         perm.updateGrantAllByOrg(selfOrgId, true);

         map.put(getResourceKey(ResourceType.CREATE_DATA_SOURCE, "*"), perm);
         map.put(getResourceKey(ResourceType.PORTAL_TAB, "Data"), perm);
         map.put(getResourceKey(ResourceType.PHYSICAL_TABLE, "*"), perm);
         map.put(getResourceKey(ResourceType.CROSS_JOIN, "*"), perm);
         map.put(getResourceKey(ResourceType.FREE_FORM_SQL, "*"), perm);

         perm = new Permission();
         perm.setOrganizationGrantsForOrg(ResourceAction.READ,
            Collections.singleton(selfOrganizationName), selfOrgId);
         perm.updateGrantAllByOrg(selfOrgId, true);

         map.put(getResourceKey(ResourceType.REPORT, "/"), perm);
         map.put(getResourceKey(ResourceType.ASSET, "/"), perm);
      }
   }
}
