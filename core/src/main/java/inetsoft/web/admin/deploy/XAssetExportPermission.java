/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.admin.deploy;

import inetsoft.sree.RepletEngine;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.XPrincipal;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.util.dep.*;
import inetsoft.web.admin.content.repository.RepositoryOwnerOrgCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;

/**
 * Bug #77922, #77923, #77924, decides who may get the content of an asset out of the server
 * through the repository export or a schedule backup.
 * <p>
 * The owner is taken from the stored object that the asset's writer reads, never from a
 * client-supplied owner. Most owned assets are read from the storage of {@link XAsset#getUser()},
 * so that owner is checked. A schedule task asset is written from the task stored under its id
 * and an auto-save asset from the auto-save file of the user named in its file name, both ignore
 * {@code getUser()}, so their owner is resolved from the task or the file name.
 * <p>
 * Static, with no Spring injection, so that the scheduler can use it. Permissions are checked
 * with {@link SecurityEngine#getSecurity()}, the same bean {@code DeployService} is given.
 */
public final class XAssetExportPermission {
   private XAssetExportPermission() {
   }

   /**
    * Checks if a principal may get the content of an asset.
    *
    * @param asset        the asset that is written, built the way its writer builds it.
    * @param resourcePath the path of the ASSET resource checked for a selected asset without a
    *                     security resource.
    * @param selected     {@code true} for an asset the caller picked, a global asset then needs
    *                     ADMIN permission; {@code false} for a dependent asset, a global
    *                     dependent is not checked.
    * @param principal    the principal, the caller or the owner of a schedule task.
    *
    * @return {@code true} if permitted.
    */
   public static boolean isPermitted(XAsset asset, String resourcePath, boolean selected,
                                     Principal principal)
   {
      SecurityEngine security = SecurityEngine.getSecurity();

      if(!security.isSecurityEnabled()) {
         return true;
      }

      if(principal == null || asset == null) {
         return false;
      }

      try {
         if(asset instanceof VSAutoSaveAsset || asset instanceof WSAutoSaveAsset) {
            return isAutoSavePermitted(asset.getPath(), principal);
         }

         if(asset instanceof ScheduleTaskAsset) {
            return isTaskPermitted(asset.getPath(), principal);
         }

         Resource resource = asset.getSecurityResource();
         IdentityID owner = asset.getUser();

         if(resource == null) {
            return !selected || security.checkPermission(
               principal, ResourceType.ASSET, resourcePath, ResourceAction.ADMIN);
         }
         else if(owner != null && !XAsset.NULL.equals(owner.name)) {
            return isOwnerPermitted(owner, principal);
         }
         // an asset with the __NULL__ owner is built with the owner's organization
         else if(owner != null && !isOwnerOrgPermitted(owner, principal)) {
            return false;
         }
         else {
            return !selected || security.checkPermission(
               principal, resource.getType(), resource.getPath(), ResourceAction.ADMIN);
         }
      }
      catch(SecurityException e) {
         LOG.warn("Failed to check the permission on {} for {}, refusing it", asset, principal, e);
         return false;
      }
   }

   /**
    * Checks if a principal may get the content of an asset owned by a user. The owner must be in
    * the principal's organization, or the principal is a site admin, and the principal must be
    * the owner or have ADMIN permission on the owner.
    *
    * @param owner     the owner of the asset.
    * @param principal the principal.
    *
    * @return {@code true} if permitted.
    */
   public static boolean isOwnerPermitted(IdentityID owner, Principal principal) {
      if(owner == null || principal == null || !isOwnerOrgPermitted(owner, principal)) {
         return false;
      }

      try {
         return principal.getName().equals(owner.convertToKey()) ||
            SecurityEngine.getSecurity().checkPermission(
               principal, ResourceType.SECURITY_USER, owner, ResourceAction.ADMIN);
      }
      catch(SecurityException e) {
         LOG.warn("Failed to check the permission on user {} for {}, refusing it", owner, principal,
                  e);
         return false;
      }
   }

   private static boolean isOwnerOrgPermitted(IdentityID owner, Principal principal) {
      try {
         RepositoryOwnerOrgCheck.checkOwnerOrg(owner, principal);
         return true;
      }
      catch(MessageException e) {
         return false;
      }
   }

   /**
    * Checks if a principal may get the content of an auto-save file. The auto-save file is read
    * from {@code recycle/} + the file name with the user of its third field moved to the current
    * organization (VSAutoSaveAsset/WSAutoSaveAsset.writeContent), so the owner is resolved from
    * the name in the same way.
    * <p>
    * Bug #77947, also used by the EM endpoints that delete, restore or get the time of a named
    * auto-save file.
    *
    * @param path      the name of the auto-save file, without the {@code recycle/} prefix.
    * @param principal the principal.
    *
    * @return {@code true} if permitted.
    */
   public static boolean isAutoSavePermitted(String path, Principal principal) {
      if(!SecurityEngine.getSecurity().isSecurityEnabled()) {
         return true;
      }

      if(principal == null) {
         return false;
      }

      String name = SUtil.addAutoSaveOrganization(SUtil.trimAutoSaveOrganization(path));
      String[] fields = name == null ? new String[0] : Tool.split(name, '^');

      if(fields.length < 4) {
         return false;
      }

      IdentityID owner = IdentityID.getIdentityIDFromKey(fields[2]);

      // a file saved without a user, only the administrators that the repository tree lists the
      // organization's auto-save files for
      if(owner == null || Tool.isEmptyString(owner.name) || "_NULL_".equals(owner.name) ||
         XAsset.NULL.equals(owner.name) || XPrincipal.ANONYMOUS.equals(owner.name))
      {
         OrganizationManager manager = OrganizationManager.getInstance();
         return manager.isSiteAdmin(principal) || manager.isOrgAdmin(principal);
      }

      return isOwnerPermitted(owner, principal);
   }

   /**
    * The task is written from the task stored under the id in the current organization, an
    * internal task id is always resolved in the host organization
    * (ScheduleTaskAsset.writeContent), so the stored task is checked.
    */
   private static boolean isTaskPermitted(String path, Principal principal) {
      if(path == null) {
         return false;
      }

      String taskId = path.substring(path.lastIndexOf('/') + 1);
      ScheduleTask task = ScheduleManager.getScheduleManager().getScheduleTask(taskId);

      if(task == null) {
         return false;
      }

      // the internal tasks of the host organization are not listed for anybody, and the internal
      // task permission of RepletEngine.hasTaskPermission() is a resource that an organization
      // admin may grant in an own organization
      if(ScheduleManager.isInternalTask(taskId) || ScheduleManager.isInternalTask(task.getTaskId()) ||
         task.getOwner() == null)
      {
         return OrganizationManager.getInstance().isSiteAdmin(principal);
      }

      RepletEngine engine = SUtil.getRepletEngine(SUtil.getRepletRepository());

      if(engine == null) {
         LOG.warn("Replet engine is not available, refusing the export of task {}", taskId);
         return false;
      }

      return engine.hasTaskPermission(task, principal);
   }

   private static final Logger LOG = LoggerFactory.getLogger(XAssetExportPermission.class);
}
