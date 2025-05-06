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

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.TimeRange;
import inetsoft.storage.*;
import inetsoft.util.*;
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

      isolatePermissionForOrg();
   }

   /**
    * Permission was isolated by organiztion to fix bugs like Bug #7091, this function is to
    * isolate permissions by organization for old storage.
    */
   private void isolatePermissionForOrg() {
      if(!needIsolate()) {
         return;
      }

      Map<String, Permission> map = new HashMap<>();
      storage.stream().forEach(pair -> map.put(pair.getKey(), pair.getValue()));
      storage.removeAll(storage.keys().collect(Collectors.toSet()));

      for(Map.Entry<String, Permission> entry : map.entrySet()) {
         String key = entry.getKey();
         int delimiter = key.indexOf(":");
         ResourceType type = ResourceType.valueOf(key.substring(0, delimiter));
         String path = key.substring(delimiter + 1);
         Permission permission = entry.getValue();

         if(permission == null) {
            continue;
         }

         Map<String, Permission> permissionMap = permission.splitPermissionForOrg();

         permissionMap.forEach((orgId, orgPermission) -> {
            // no meaningful scenario for setting permissions on a global role.
            if("null".equals(orgId)) {
               return;
            }

            setPermission(type, path, orgPermission, orgId);
         });
      }
   }

   private boolean needIsolate() {
      String key = storage.keys().findFirst().orElse(null);

      if(key == null) {
         return false;
      }

      String[] arr = key.split(":");

      if(arr.length < 3) {
         return true;
      }

      String orgID = arr[1];

      return SecurityEngine.getSecurity().getSecurityProvider().getOrganization(orgID) == null;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Permission getPermission(ResourceType type, String resource, String orgID) {
      init();
      return storage.get(getResourceKey(type, resource, orgID));
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Permission getPermission(ResourceType type, IdentityID resource, String orgID) {
      init();
      return storage.get(getResourceKey(type, resource.convertToKey(), getResourceOrgID(orgID)));
   }


   /**
    * {@inheritDoc}
    */
   @Override
   public List<Tuple4<ResourceType, String, String, Permission>> getPermissions() {
      init();

      Function<KeyValuePair<Permission>, Tuple4<ResourceType, String, String, Permission>> mapper =
         pair -> {
            String key = pair.getKey();
            int delimiter = key.indexOf(":");

            ResourceType type = ResourceType.valueOf(key.substring(0, delimiter));
            String path = key.substring(delimiter + 1);
            delimiter = path.indexOf(":");
            String orgID = null;

            if(delimiter != -1) {
               orgID = path.substring(0, delimiter);
               path = path.substring(delimiter + 1);
            }

            return new Tuple4<>(type, orgID, path, pair.getValue());
         };

      return storage.stream().map(mapper).collect(Collectors.toList());
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setPermission(ResourceType type, String resource, Permission perm, String orgID) {
      init();
      orgID = getResourceOrgID(orgID);

      if(perm == null) {
         removePermission(type, resource, orgID);
      }
      else {
         try {
            storage.put(getResourceKey(type, resource, orgID), perm).get();
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
   public void setPermission(ResourceType type, IdentityID identityID, Permission perm, String orgID) {
      init();
      orgID = getResourceOrgID(orgID);

      if(perm == null) {
         removePermission(type, identityID, orgID);
      }
      else {
         try {
            storage.put(getResourceKey(type, identityID.convertToKey(), orgID), perm).get();
         }
         catch(Exception e) {
            LOG.error("Failed to set permission on {} {}", type, identityID, e);
         }
      }
   }

   @Override
   public void removePermission(ResourceType type, String resource, String orgID) {
      init();
      orgID = getResourceOrgID(orgID);

      try {
         storage.remove(getResourceKey(type, resource, orgID)).get();
      }
      catch(Exception e) {
         LOG.error("Failed to remove permission from {} {}", type, resource, e);
      }
   }

   public void cleanOrganizationFromPermissions(String orgId) {
      for(Tuple4<ResourceType, String, String, Permission> permissionSet : getPermissions()) {
         String resourceOrgID = permissionSet.getSecond();

         if(resourceOrgID != null && !Tool.equals(resourceOrgID, orgId)) {
            continue;
         }

         ResourceType type = permissionSet.getFirst();
         String path = permissionSet.getThird();

         removePermission(type, path, resourceOrgID);
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

      // to-do, org changed ?

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
      return getResourceKey(type, path, null);
   }

   private static String getResourceKey(ResourceType type, String path, String orgID) {
      orgID = orgID != null ? orgID : SUtil.isMultiTenant() ?
         OrganizationManager.getInstance().getCurrentOrgID() : Organization.getDefaultOrganizationID();
      return type + ":" + orgID + ":" + path;
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

         addDefaultRoleGrants(Organization.getDefaultOrganizationID(), map);
         addDefaultRoleGrants(Organization.getSelfOrganizationID(), map);

         addDefaultPermissionForSelfOrg(map);

         return Permission.class;
      }

      private void addDefaultAdminPermissions(String orgId, Map<String, Permission> map) {
         Permission perm = new Permission();

         for(ResourceAction action : ResourceAction.values()) {
            Set<String> roles = new HashSet<>(Arrays.asList("Administrator","Organization Administrator"));
            perm.setRoleGrantsForOrg(action, roles, orgId);
         }

         map.put(getResourceKey(ResourceType.REPORT, "Built-in Admin Reports", orgId), perm);
      }

      public static void addDefaultRoleGrants(String orgID, Map<String, Permission> map) {
         String selforgName = Organization.getSelfOrganizationName();
         Permission perm = null;

         if(Organization.getDefaultOrganizationID().equals(orgID)) {
            perm = new Permission();
            Map<String, Boolean> defedited = new HashMap<>();
            defedited.put(orgID, true);
            perm.setOrgEditedGrantAll(defedited);
            perm.setRoleGrantsForOrg(ResourceAction.ACCESS, Collections.singleton("Advanced"), orgID);
            map.put(getResourceKey(ResourceType.SCHEDULER, "*", orgID), perm);
         }

         Map<String, Boolean> edited = new HashMap<>();
         edited.put(orgID, true);

         perm = new Permission();
         perm.setOrgEditedGrantAll(edited);

         if(Organization.getDefaultOrganizationID().equals(orgID)) {
            perm.setRoleGrantsForOrg(ResourceAction.ACCESS, Collections.singleton("Designer"), orgID);
         }
         else if(Organization.getSelfOrganizationID().equals(orgID)) {
            perm.setOrganizationGrantsForOrg(ResourceAction.ACCESS,
                                             Collections.singleton(selforgName), orgID);
         }

         map.put(getResourceKey(ResourceType.COMPOSER, "*", orgID), perm);
         map.put(getResourceKey(ResourceType.WORKSHEET, "*", orgID), perm);
         map.put(getResourceKey(ResourceType.VIEWSHEET, "*", orgID), perm);


         perm = new Permission();
         perm.setOrgEditedGrantAll(edited);

         if(Organization.getDefaultOrganizationID().equals(orgID)) {
            String name = "Designer";
            perm.setRoleGrantsForOrg(ResourceAction.READ, Collections.singleton(name), orgID);
            perm.setRoleGrantsForOrg(ResourceAction.WRITE, Collections.singleton(name), orgID);
         }
         else if(Organization.getSelfOrganizationID().equals(orgID)) {
            perm.setOrganizationGrantsForOrg(ResourceAction.READ, Collections.singleton(selforgName), orgID);
            perm.setOrganizationGrantsForOrg(ResourceAction.WRITE, Collections.singleton(selforgName), orgID);
         }

         map.put(getResourceKey(ResourceType.DASHBOARD, "*", orgID), perm);

         if(Organization.getDefaultOrganizationID().equals(orgID)) {
            perm.setRoleGrantsForOrg(ResourceAction.ACCESS, Collections.singleton("Advanced"), orgID);
         }

         for(TimeRange range : TimeRange.getTimeRanges()) {
            map.put(getResourceKey(ResourceType.SCHEDULE_TIME_RANGE, range.getName(), orgID), perm);
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

         map.put(getResourceKey(ResourceType.CREATE_DATA_SOURCE, "*", selfOrgId), perm);
         map.put(getResourceKey(ResourceType.PORTAL_TAB, "Data", selfOrgId), perm);
         map.put(getResourceKey(ResourceType.PHYSICAL_TABLE, "*", selfOrgId), perm);
         map.put(getResourceKey(ResourceType.CROSS_JOIN, "*", selfOrgId), perm);
         map.put(getResourceKey(ResourceType.FREE_FORM_SQL, "*", selfOrgId), perm);

         perm = new Permission();
         perm.setOrganizationGrantsForOrg(ResourceAction.READ,
            Collections.singleton(selfOrganizationName), selfOrgId);
         perm.updateGrantAllByOrg(selfOrgId, true);

         map.put(getResourceKey(ResourceType.REPORT, "/", selfOrgId), perm);
         map.put(getResourceKey(ResourceType.ASSET, "/", selfOrgId), perm);
      }
   }
}
