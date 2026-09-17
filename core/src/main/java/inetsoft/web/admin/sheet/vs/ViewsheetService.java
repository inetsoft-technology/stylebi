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
package inetsoft.web.admin.sheet.vs;

import inetsoft.report.internal.Util;
import inetsoft.sree.RepletEntry;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.AssetContent;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.VSSnapshot;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.RecycleUtils;
import inetsoft.web.admin.content.repository.RepletRegistryService;
import inetsoft.web.admin.sheet.OrganizationAccess;
import inetsoft.web.admin.sheet.Sheet;
import inetsoft.web.admin.sheet.SheetList;
import inetsoft.web.security.auth.MissingResourceException;
import inetsoft.web.security.auth.ResourceExistsException;
import inetsoft.web.security.auth.UnauthorizedAccessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.sql.Timestamp;
import java.util.Arrays;

/**
 * {@code ViewsheetService} implements the viewsheet management logic shared by the enterprise
 * Public API ({@code ViewsheetApiService}) and the admin-ai plugin. It always operates on the
 * caller's current organization -- there is no organization-switching concept here, that is an
 * enterprise-only capability layered on top by {@code ViewsheetApiService}.
 */
@Service
public class ViewsheetService {
   @Autowired
   public ViewsheetService(AssetRepository assetRepository,
                           RepletRegistryService repletRegistryService,
                           RepletRegistryManager repletRegistryManager)
   {
      this.assetRepository = assetRepository;
      this.repletRegistryService = repletRegistryService;
      this.repletRegistryManager = repletRegistryManager;
   }

   /**
    * Gets the list of available viewsheets in the caller's current organization.
    *
    * @param principal a principal that identifies the remote principal.
    *
    * @return The list of viewsheets.
    */
   public SheetList getViewsheets(Principal principal) throws Exception {
      String organizationid = OrganizationManager.getInstance().getCurrentOrgID(principal);
      SheetList list = new SheetList();
      IdentityID principalIdentityID =
         principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

      AssetEntry root = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, "/", null, organizationid);
      AssetEntry[] entries = assetRepository.getAllEntries(
         root, principal, ResourceAction.READ, new AssetEntry.Selector(AssetEntry.Type.VIEWSHEET));
      Arrays.stream(entries)
         .filter(entry -> !entry.getPath().startsWith(RecycleUtils.RECYCLE_BIN_FOLDER + "/"))
         .map(this::convertEntry)
         .forEach(list.getSheets()::add);

      // When security.exposedefaultorgtoall is enabled, also include the host org's global
      // viewsheets for users in non-host organizations (mirrors portal repository tree behavior).
      String hostOrgId = Organization.getDefaultOrganizationID();
      String principalOrgId = principal instanceof XPrincipal ? ((XPrincipal) principal).getOrgId() : null;

      if(SUtil.isDefaultVSGloballyVisible(principal) && !Tool.equals(principalOrgId, hostOrgId)) {
         AssetEntry hostRoot = new AssetEntry(
            AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, "/", null, hostOrgId);
         AssetEntry[] hostEntries = assetRepository.getAllEntries(
            hostRoot, principal, ResourceAction.READ, new AssetEntry.Selector(AssetEntry.Type.VIEWSHEET));
         Arrays.stream(hostEntries)
            .filter(entry -> !entry.getPath().startsWith(RecycleUtils.RECYCLE_BIN_FOLDER + "/"))
            .map(this::convertEntry)
            .forEach(list.getSheets()::add);
      }

      root = new AssetEntry(
         AssetRepository.USER_SCOPE, AssetEntry.Type.REPOSITORY_FOLDER, "/", principalIdentityID, organizationid);
      entries = assetRepository.getAllEntries(
         root, principal, ResourceAction.READ, new AssetEntry.Selector(AssetEntry.Type.VIEWSHEET));
      Arrays.stream(entries)
         .filter(entry -> !entry.getPath().startsWith(RecycleUtils.RECYCLE_BIN_FOLDER + "/"))
         .map(this::convertEntry)
         .forEach(list.getSheets()::add);

      return list;
   }

   /**
    * A minimal alias/description pair for a viewsheet.
    */
   public record Metadata(String alias, String description) {
   }

   /**
    * Reads a viewsheet's current alias/description.
    */
   public Metadata getViewsheetMetadata(String id, Principal user) throws Exception {
      AssetEntry entry = getAssetEntry(id, user);
      AbstractSheet vs = assetRepository.getSheet(entry, user, false, AssetContent.ALL);
      String description = "";

      if(vs instanceof Viewsheet) {
         description = ((Viewsheet) vs).getViewsheetInfo().getDescription();
      }
      else if(vs instanceof VSSnapshot) {
         description = ((VSSnapshot) vs).getSnapshotDescription();
      }

      return new Metadata(entry.getAlias(), description);
   }

   /**
    * Updates a viewsheet's alias/description without renaming it.
    */
   public void updateMetadata(String id, String alias, String description, Principal principal)
      throws Exception
   {
      repletRegistryService.updateSheet(id, null, null, alias, description, principal);
   }

   /**
    * Renames a viewsheet.
    */
   public void renameViewsheet(String id, String path, boolean global, IdentityID owner,
                               Principal user) throws Exception
   {
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord record = new ActionRecord(SUtil.getUserName(user), ActionRecord.ACTION_NAME_RENAME, "",
         ActionRecord.OBJECT_TYPE_DASHBOARD, actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE, "");

      try {
         AssetEntry oldEntry = getAssetEntry(id, user);
         AssetEntry newEntry = new AssetEntry(
            global ? AssetRepository.GLOBAL_SCOPE : AssetRepository.USER_SCOPE,
            AssetEntry.Type.VIEWSHEET, path, global ? null : owner);

         //handle site admin in different organization, ensure same org for asset rename
         newEntry.setOrgID(oldEntry.getOrgID());
         record.setObjectName(oldEntry.getName());
         record.setActionError("Target Entry: " + newEntry.getDescription());

         assetRepository.changeSheet(oldEntry, newEntry, user, true, true, false);
         record.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
      }
      catch(UnauthorizedAccessException | MissingResourceException e) {
         record.setActionError("Failed to rename viewsheet: " + id);
         throw e;
      }
      // Bug #76299: changeSheet0() rejects a rename whose destination parent folder does not
      // exist with MessageException/FOLDER_REQUIRED (added by the Bug #76194 fix). That is a
      // client-correctable condition, so report it as a 404 instead of letting the catch-all
      // below wrap it into an opaque 500.
      catch(MessageException e) {
         record.setActionError("Failed to rename viewsheet: " + id);

         if("FOLDER_REQUIRED".equals(e.getKeywords())) {
            throw new MissingResourceException(e.getMessage(), e);
         }

         throw new RuntimeException("Failed to rename viewsheet: " + id, e);
      }
      catch(Exception e) {
         record.setActionError("Failed to rename viewsheet: " + id);
         throw new RuntimeException("Failed to rename viewsheet: " + id, e);
      }
      finally {
         Audit.getInstance().auditAction(record, user);
      }
   }

   /**
    * Deletes a viewsheet.
    */
   public void deleteViewsheet(String id, Principal user) throws Exception {
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord record = new ActionRecord(SUtil.getUserName(user), ActionRecord.ACTION_NAME_DELETE, "",
         ActionRecord.OBJECT_TYPE_DASHBOARD, actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE, "");

      try {
         AssetEntry entry = getAssetEntry(id, user);
         record.setObjectName(entry.getName());
         assetRepository.removeSheet(entry, user, true);
         record.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
      }
      catch(UnauthorizedAccessException | MissingResourceException e) {
         record.setActionError("Failed to delete viewsheet: " + id);
         throw e;
      }
      catch(Exception e) {
         record.setActionError("Failed to delete viewsheet: " + id);
         throw new RuntimeException("Failed to delete viewsheet: " + id, e);
      }
      finally {
         Audit.getInstance().auditAction(record, user);
      }
   }

   public void addFolder(String parentFolder, String folderName, IdentityID owner,
                         Principal principal) throws Exception
   {
      addFolder(parentFolder, folderName, null, null, owner, principal);
   }

   public void addFolder(String parentFolder, String folderName, String alias, String description,
                         IdentityID owner, Principal principal) throws Exception
   {
      String parentPath;

      if(StringUtils.isEmpty(parentFolder) || "/".equals(parentFolder)) {
         parentPath = "";
      }
      else if(parentFolder.endsWith("/")) {
         parentPath = parentFolder;
      }
      else {
         parentPath = parentFolder + "/";
      }

      if(owner != null && !parentPath.startsWith(Tool.MY_DASHBOARD + "/")) {
         parentPath = Tool.MY_DASHBOARD + "/" + parentPath;
      }

      ActionRecord record = SUtil.getActionRecord(
         principal, ActionRecord.ACTION_NAME_CREATE, parentPath, ActionRecord.OBJECT_TYPE_FOLDER);

      try {
         if(owner != null) {
            OrganizationAccess.check(owner.getOrgID(), principal);
         }

         try {
            repletRegistryService.checkPermission(parentPath, ResourceType.REPORT, ResourceAction.WRITE, principal);
         }
         catch(MessageException ignore) {
            throw new UnauthorizedAccessException(parentPath);
         }

         String fullPath = parentPath + folderName;

         if(repletRegistryService.isDuplicatedName(fullPath, null, owner)) {
            throw new ResourceExistsException(fullPath);
         }

         repletRegistryService.addRepositoryFolder(fullPath, alias, description, owner);

         record.setObjectName(Util.getObjectFullPath(
            RepletEntry.DASHBOARD_FOLDER, fullPath, principal, owner));
      }
      catch(Exception e) {
         record.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         record.setActionError(e.getMessage());
         throw e;
      }
      finally {
         Audit.getInstance().auditAction(record, principal);
      }
   }

   public void removeFolder(String path, IdentityID owner, Principal principal) throws Exception {
      ActionRecord record = SUtil.getActionRecord(
         principal, ActionRecord.ACTION_NAME_DELETE, path, ActionRecord.OBJECT_TYPE_FOLDER);

      try {
         if(owner != null) {
            OrganizationAccess.check(owner.getOrgID(), principal);
         }

         RepletRegistry registry = repletRegistryManager.getRegistry(owner);

         if(StringUtils.isEmpty(path) || "/".equals(path)) {
            throw new IllegalArgumentException("Cannot delete root folder");
         }

         if(path.startsWith("/")) {
            path = path.substring(1);
         }

         try {
            repletRegistryService.checkPermission(path, ResourceType.REPORT, ResourceAction.DELETE, principal);
         }
         catch(MessageException ignore) {
            throw new UnauthorizedAccessException(path);
         }

         if(!registry.isFolder(path)) {
            throw new MissingResourceException(path);
         }

         registry.removeFolder(path);
      }
      catch(Exception e) {
         record.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         record.setActionError(e.getMessage());
         throw e;
      }
      finally {
         Audit.getInstance().auditAction(record, principal);
      }
   }

   public void renameFolder(String oldPath, String newPath, IdentityID owner, Principal principal)
      throws Exception
   {
      ActionRecord record = SUtil.getActionRecord(
         principal, ActionRecord.ACTION_NAME_RENAME, oldPath, ActionRecord.OBJECT_TYPE_FOLDER);

      try {
         if(owner != null) {
            OrganizationAccess.check(owner.getOrgID(), principal);
         }

         RepletRegistry registry = repletRegistryManager.getRegistry(owner);

         if(StringUtils.isEmpty(oldPath) || "/".equals(oldPath)) {
            throw new IllegalArgumentException("Cannot rename root folder");
         }

         if(oldPath.startsWith("/")) {
            oldPath = oldPath.substring(1);
         }

         if(StringUtils.isEmpty(newPath) || "/".equals(newPath)) {
            throw new IllegalArgumentException("Cannot overwrite root folder");
         }

         if(newPath.startsWith("/")) {
            newPath = newPath.substring(1);
         }

         try {
            repletRegistryService.checkPermission(
               oldPath, ResourceType.REPORT, ResourceAction.DELETE, principal);
         }
         catch(MessageException ignore) {
            throw new UnauthorizedAccessException(oldPath);
         }

         if(!registry.isFolder(oldPath)) {
            throw new MissingResourceException(oldPath);
         }

         int oldParentIdx = oldPath.lastIndexOf('/');
         String oldParentPath = oldParentIdx < 0 ? "/" : oldPath.substring(0, oldParentIdx);
         int newParentIdx = newPath.lastIndexOf('/');
         String newParentPath = newParentIdx < 0 ? "/" : newPath.substring(0, newParentIdx);

         if(!Tool.equals(oldParentPath, newParentPath)) {
            try {
               if(!repletRegistryService.checkPermission(
                  newParentPath, ResourceType.REPORT, ResourceAction.WRITE, principal))
               {
                  throw new UnauthorizedAccessException(newParentPath);
               }
            }
            catch(MessageException ignore) {
               throw new UnauthorizedAccessException(newParentPath);
            }
         }

         if(SUtil.isDuplicatedRepositoryPath(newPath, owner)) {
            throw new ResourceExistsException(newPath);
         }

         String result = registry.changeFolder(oldPath, newPath, principal);

         if(!"true".equals(result)) {
            throw new Exception(result);
         }
      }
      catch(Exception e) {
         record.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         record.setActionError(e.getMessage());
         throw e;
      }
      finally {
         Audit.getInstance().auditAction(record, principal);
      }
   }

   private Sheet convertEntry(AssetEntry entry) {
      return new Sheet(
         entry.toIdentifier(), entry.getPath(), entry.toView(),
         entry.getScope() == AssetRepository.GLOBAL_SCOPE, entry.getUser(), null);
   }

   private AssetEntry getAssetEntry(String identifier, Principal user) throws Exception {
      AssetEntry entry = AssetEntry.createAssetEntry(identifier);
      AssetEntry resolved = assetRepository.getAssetEntry(entry);

      if(resolved == null) {
         throw new MissingResourceException(entry.toIdentifier());
      }

      checkPermission(entry, user, ResourceAction.READ);
      entry.setAlias(resolved.getAlias());
      entry.setProperty("description", resolved.getProperty("description"));
      return entry;
   }

   private void checkPermission(AssetEntry entry, Principal user, ResourceAction action)
      throws Exception
   {
      try {
         assetRepository.checkAssetPermission(user, entry, action);
      }
      catch(Exception e) {
         throw new UnauthorizedAccessException(entry.toIdentifier());
      }
   }

   private final AssetRepository assetRepository;
   private final RepletRegistryService repletRegistryService;
   private final RepletRegistryManager repletRegistryManager;
}
