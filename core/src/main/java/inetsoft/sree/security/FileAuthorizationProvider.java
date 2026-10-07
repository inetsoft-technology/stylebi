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
import java.util.concurrent.*;
import java.util.function.Function;
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
      if(storage != null && !storage.isClosed()) {
         return;
      }

      storage = KeyValueStorageManager.getInstance().getStorage(
         "defaultSecurityPermissions", new LoadPermissionsTask());

      isolatePermissionForOrg();
   }

   /**
    * Permission was isolated by organiztion to fix bugs like Bug #7091, this function is to
    * isolate permissions by organization for old storage.
    *
    * Each key is decided on its own, so keys that are already isolated are never split again and
    * running this again changes nothing (Bug #77832). A legacy key is removed only after all of its
    * per-organization copies were written, and a failure on one key leaves that key as it is and
    * never escapes init().
    */
   private void isolatePermissionForOrg() {
      Map<String, Permission> map = new HashMap<>();

      try {
         storage.stream().forEach(pair -> map.put(pair.getKey(), pair.getValue()));
      }
      catch(RuntimeException e) {
         LOG.error("Failed to read the permissions to isolate by organization", e);
         return;
      }

      Map<String, Boolean> knownOrgs = new HashMap<>();

      for(Map.Entry<String, Permission> entry : map.entrySet()) {
         try {
            isolatePermissionForOrg(entry.getKey(), entry.getValue(), knownOrgs);
         }
         catch(InterruptedException e) {
            // the remaining keys are kept as they are
            Thread.currentThread().interrupt();
            LOG.error("Interrupted while isolating the permissions by organization", e);
            break;
         }
         catch(Exception e) {
            LOG.error("Failed to isolate the permission {} by organization, it is kept as it is",
                      entry.getKey(), e);
         }
      }
   }

   private void isolatePermissionForOrg(String key, Permission permission,
                                        Map<String, Boolean> knownOrgs) throws Exception
   {
      if(permission == null) {
         return;
      }

      int delimiter = key.indexOf(":");

      if(delimiter < 0) {
         LOG.warn("Ignoring the permission {}, its key has no resource type", key);
         return;
      }

      ResourceType type = ResourceType.valueOf(key.substring(0, delimiter));
      String path = key.substring(delimiter + 1);
      Map<String, Permission> permissionMap = permission.splitPermissionForOrg();

      if(!isLegacyKey(path, permissionMap, knownOrgs)) {
         return;
      }

      // no meaningful scenario for setting permissions on a global role.
      permissionMap.remove("null");

      // keep the edited flag of each organization, also for an organization that was edited
      // without granting anyone, so it does not fall back to the parent's permission
      permission.getOrgEditedGrantAll().forEach((orgId, edited) -> {
         if(Boolean.TRUE.equals(edited) && !Tool.isEmptyString(orgId) && !"null".equals(orgId)) {
            permissionMap.computeIfAbsent(orgId, o -> new Permission())
               .updateGrantAllByOrg(orgId, true);
         }
      });

      for(Map.Entry<String, Permission> entry : permissionMap.entrySet()) {
         String target = getResourceKey(type, path, getResourceOrgID(entry.getKey()));

         // the permission already stored for the organization is the current one, a stale legacy
         // grant must not replace it
         if(storage.contains(target)) {
            continue;
         }

         storage.put(target, entry.getValue()).get(10L, TimeUnit.SECONDS);
      }

      storage.remove(key).get(10L, TimeUnit.SECONDS);
   }

   /**
    * Checks if a permission key has no organization part. The path of a legacy key may contain
    * ':' (schedule task ids are owner:name, cubes are ds::cube), so a key with an organization
    * part is legacy only if that part is not a known organization and some grantee belongs to
    * another organization. The keys of an organization the security provider doesn't know, like
    * SELF under LDAP or a deleted organization, are kept.
    */
   private boolean isLegacyKey(String path, Map<String, Permission> permissionMap,
                               Map<String, Boolean> knownOrgs)
   {
      int delimiter = path.indexOf(":");

      if(delimiter < 0) {
         return true;
      }

      String orgID = path.substring(0, delimiter);
      boolean otherOrg = permissionMap.keySet().stream()
         .anyMatch(o -> !"null".equals(o) && !o.equals(orgID));

      return otherOrg && !knownOrgs.computeIfAbsent(orgID, o ->
         SecurityEngine.getSecurity().getSecurityProvider().getOrganization(o) != null);
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
            storage.put(getResourceKey(type, resource, orgID), perm).get(10L, TimeUnit.SECONDS);
         }
         catch(Exception e) {
            throw storageWriteFailure(type, resource, e);
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
            storage.put(getResourceKey(type, identityID.convertToKey(), orgID), perm)
               .get(10L, TimeUnit.SECONDS);
         }
         catch(Exception e) {
            throw storageWriteFailure(type, identityID, e);
         }
      }
   }

   @Override
   public void removePermission(ResourceType type, String resource, String orgID) {
      init();
      orgID = getResourceOrgID(orgID);

      try {
         storage.remove(getResourceKey(type, resource, orgID)).get(10L, TimeUnit.SECONDS);
      }
      catch(Exception e) {
         throw storageWriteFailure(type, resource, e);
      }
   }

   @Override
   public void removePermission(ResourceType type, IdentityID identityID, String orgID) {
      init();
      orgID = getResourceOrgID(orgID);

      try {
         storage.remove(getResourceKey(type, identityID.convertToKey(), orgID)).get(10L, TimeUnit.SECONDS);
      }
      catch(Exception e) {
         throw storageWriteFailure(type, identityID, e);
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

         // best-effort per item: one failed remove must not stop the rest, or the org delete
         // cleanup that follows this call
         try {
            removePermission(type, path, resourceOrgID);
         }
         catch(RuntimeException e) {
            LOG.error("Failed to remove the permission of {} {} while cleaning organization {}, " +
                      "it may still be stored", type, path, orgId, e);
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

      // to-do, org changed ?

      List<KeyValuePair<Permission>> list = storage.stream().collect(Collectors.toList());

      // The identity record is already gone when this listener runs, so the cleanup is
      // best-effort per entry: a failed entry must not stop the remaining entries from being
      // updated (Bug #77799). This method must not throw either: AuthenticationChain.changeDelegate
      // has no catch, and a throw would skip SecurityEngine.fireAuthenticationChange (which logs out
      // the sessions of a removed or renamed organization).
      //
      // The caller holds the authentication provider's lock. After the first put that times out
      // (a hung backend), the remaining puts are still submitted but are not waited on, so a hung
      // backend costs at most one put timeout, as before. Puts that are slow but complete within
      // the timeout are still waited on one by one. Keys whose put timed out or was not waited on
      // are reported as not confirmed, because such a put may still land.
      List<String> failedKeys = new ArrayList<>();
      List<String> unconfirmedKeys = new ArrayList<>();
      Exception firstFailure = null;
      boolean timedOut = false;
      boolean interrupted = false;

      for(KeyValuePair<Permission> pair : list) {
         try {
            Permission perm = pair.getValue();
            boolean changed = false;

            for(ResourceAction action : ResourceAction.values()) {
               Set<Permission.PermissionIdentity> identities = perm.getGrants(action, type, null);

               if(identities.removeIf(pi -> Tool.equals(pi.getName(), oldID.name) &&
                                            Tool.equals(pi.getOrganizationID(), oldID.orgID)))
               {
                  if(!removed) {
                     boolean alreadyGranted = identities.stream().anyMatch(
                        pi -> Tool.equals(pi.getName(), newID.name) &&
                              Tool.equals(pi.getOrganizationID(), newID.orgID));

                     if(!alreadyGranted) {
                        identities.add(new Permission.PermissionIdentity(newID.name, newID.orgID));
                     }
                  }

                  perm.setGrants(action, type, identities);
                  changed = true;
               }
            }

            if(changed) {
               Future<Permission> future = storage.put(pair.getKey(), perm);

               if(timedOut) {
                  unconfirmedKeys.add(pair.getKey());
               }
               else {
                  future.get(10L, TimeUnit.SECONDS);
               }
            }
         }
         catch(TimeoutException e) {
            timedOut = true;
            unconfirmedKeys.add(pair.getKey());
            firstFailure = firstFailure == null ? e : firstFailure;
         }
         catch(InterruptedException e) {
            // Future.get cleared the interrupt flag, so the remaining entries can still be
            // updated. The flag is restored after the loop.
            interrupted = true;
            failedKeys.add(pair.getKey());
            firstFailure = firstFailure == null ? e : firstFailure;
         }
         catch(Exception e) {
            failedKeys.add(pair.getKey());
            firstFailure = firstFailure == null ? e : firstFailure;
         }
      }

      if(!failedKeys.isEmpty() || !unconfirmedKeys.isEmpty()) {
         LOG.error(
            "Failed to update the permissions after identity {} (type {}) was {}; these " +
            "permission entries may not have been updated: failed={}, not confirmed={}",
            oldID, type, removed ? "removed" : "renamed to " + newID, failedKeys, unconfirmedKeys,
            firstFailure);
      }

      // Restore the interrupt for the caller. Note that this listener is still inside
      // AuthenticationChain.changeDelegate, so the next listener (SecurityEngine's
      // fireAuthenticationChange, which logs out the sessions of a removed organization) then runs
      // on an interrupted thread, and an interruptible call there may fail.
      if(interrupted) {
         Thread.currentThread().interrupt();
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

   /**
    * Logs a failed permission storage write and returns the exception to throw. A write that timed
    * out may still complete, so the message says the permission may not have been saved.
    */
   private static RuntimeException storageWriteFailure(ResourceType type, Object resource,
                                                       Exception cause)
   {
      if(cause instanceof InterruptedException) {
         Thread.currentThread().interrupt();
      }

      String message = "The permission of " + type + " " + resource + " may not have been saved";
      LOG.error(message, cause);
      return new MessageException(message, cause);
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

         // the schedule time ranges need their own permission, sharing the dashboard one would
         // put the time range grants on DASHBOARD * and the dashboard grants on the time ranges
         perm = new Permission();
         perm.setOrgEditedGrantAll(edited);

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
