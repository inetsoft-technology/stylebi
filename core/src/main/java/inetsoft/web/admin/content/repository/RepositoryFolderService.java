/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.content.repository;

import inetsoft.report.internal.Util;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.RecycleBin;
import inetsoft.web.RecycleUtils;
import inetsoft.web.admin.content.repository.model.SetRepositoryFolderTableModel;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.admin.security.ResourcePermissionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RepositoryFolderService {
   @Autowired
   public RepositoryFolderService(ResourcePermissionService permissionService,
                                  ContentRepositoryTreeService treeService) {
      this.permissionService = permissionService;
      this.treeService = treeService;
   }

   public RepositoryFolderSettingsModel getSettings(String path, boolean isWorksheetFolder,
                                                    IdentityID owner, Principal principal)
      throws Exception
   {
      owner = owner != null && owner.name.length() > 0 ? owner : null;
      RepletRegistry registry = RepletRegistry.getRegistry(owner);
      String folderName = "/".equals(path) ? "/" : registryManager.getName(path);
      int idx = path.lastIndexOf(folderName);
      String parentFolder = idx == 0 ? "/" : path.substring(0, idx - 1);
      boolean editable = !("Built-in Admin Reports".equals(folderName) && "/".equals(parentFolder));

      List<String> fnsList = Arrays.stream(registry.getFolders(path, false))
         .filter(fn -> hasPermission(fn, ResourceType.REPORT, ResourceAction.READ, principal))
         .collect(Collectors.toList());

      if(!(registry.isFolder(path) || isWorksheetFolder)) {
         throw new MessageException(catalog.getString("em.repository.missingResource", path));
      }

      RepositoryFolderSettingsModel.Builder builder = RepositoryFolderSettingsModel.builder()
         .folderName(folderName)
         .alias(registry.getFolderAlias(path))
         .parentFolder(parentFolder)
         .parentFolderLabel(SUtil.localize(parentFolder, principal))
         .description(registry.getFolderDescription(path))
         .folders(fnsList)
         .editable(editable);

      boolean securityTabEnabled = !Tool.MY_DASHBOARD.equals(path) && owner == null &&
            (registry.isFolder(path) || isWorksheetFolder);
      final ResourcePermissionModel tableModel;

      if(!securityTabEnabled) {
         tableModel = null;
      }
      else {
         ResourceType rtype;
         EnumSet<ResourceAction> ractions;

         if(isWorksheetFolder) {
            rtype = ResourceType.ASSET;
            ractions = ResourcePermissionService.ADMIN_ACTIONS;
         }
         else {
            rtype = ResourceType.REPORT;
            ractions = ResourcePermissionService.ADMIN_SHARE_ACTIONS;
         }

         tableModel = permissionService.getTableModel(path, rtype, ractions, principal);
      }

      return builder.permissionTableModel(tableModel).build();
   }

   public RepositoryFolderSettingsModel applySettings(IdentityID owner,
                                                      SetRepositoryFolderSettingsModel model,
                                                      Principal principal)
      throws Exception
   {
      boolean isWSFolder = model.isWSFolder();
      String oldPath = model.oldPath();
      String newPath = SUtil.removeControlChars(model.newPath());
      int type = RepositoryEntry.REPOSITORY | RepositoryEntry.FOLDER;
      ActionRecord actionRecord = SUtil.getActionRecord(principal,
         ActionRecord.ACTION_NAME_EDIT, newPath, ActionRecord.OBJECT_TYPE_FOLDER);

      if(!Tool.equals(oldPath, newPath)) {
         actionRecord.setObjectType(ActionRecord.ACTION_NAME_RENAME);
      }

      if(isWSFolder) {
         oldPath = registryManager.splitWorksheetPath(oldPath, false);
         type = RepositoryEntry.WORKSHEET_FOLDER;
      }
      else {
         if(!oldPath.equals(newPath) && registryManager.isDuplicatedName(newPath, oldPath, owner)) {
            throw new MessageException(catalog.getString("em.viewsheet.duplicateName"));
         }
      }

      ResourceType resourceType = ResourceType.REPORT;

      if(isWSFolder) {
         if(SUtil.isMyReport(oldPath) && owner != null) {
            resourceType = ResourceType.MY_DASHBOARDS;
         }
         else {
            resourceType = ResourceType.ASSET;
         }
      }

      registryManager.checkPermission(oldPath, resourceType, ResourceAction.WRITE, principal);
      final boolean replace = model.replace();
      IdentityID userTo = getUserFromFolder(newPath);

      if(userTo == null) {
         userTo = owner;
      }

      oldPath = getFolderPath(oldPath);
      newPath = getFolderPath(newPath);

      if(userTo != null && !newPath.startsWith(Tool.MY_DASHBOARD)) {
         newPath = Tool.MY_DASHBOARD + newPath.substring(newPath.contains("/") ?
                                                         newPath.indexOf("/") : 0);
      }

      String description = model.description();
      String alias = model.alias();
      String newRuleType = model.newRuleType();
      String ruleDate = model.ruleDate();
      String fullPath = Util.getObjectFullPath(type, model.oldPath(), principal, owner);

      if(newRuleType != null) {
         if("DateArchiveRule".equals(newRuleType)) {
            newRuleType = "date";
         }
         else if("AgeArchiveRule".equals(newRuleType)) {
            newRuleType = "age";
         }
         else if("VersionArchiveRule".equals(newRuleType)) {
            newRuleType = "version";
         }
      }

      //ws folder setProperty only support rename folder
      if(isWSFolder) {
         oldPath = registryManager.splitWorksheetPath(oldPath, owner != null);
         newPath = registryManager.splitWorksheetPath(newPath, owner != null);
         oldPath = registryManager.splitMyReportPath(oldPath);
         newPath = registryManager.splitMyReportPath(newPath);

         if(!Tool.equals(oldPath, newPath)) {
            registryManager.checkPermission(oldPath, resourceType, ResourceAction.DELETE,
                                            principal);
         }

         if(!registryManager.changeWorksheetFolder(oldPath, newPath, owner, principal)) {
            LOG.warn(Catalog.getCatalog().getString("em.viewsheet.duplicateName"));
            throw new MessageException(Catalog.getCatalog().getString("Duplicate Name"));
         }

         if(model.permissionTableModel() != null && model.permissionTableModel().changed()) {
            permissionService.setResourcePermissions(newPath, ResourceType.ASSET, fullPath,
                                                     model.permissionTableModel(), principal);
         }
         else {
            permissionService.updateResourcePermissions(oldPath, newPath, resourceType);
         }

         return getSettings(newPath, true, owner, principal);
      }

      try {
         registryManager.updateRepositoryFolder(oldPath, owner, newPath, userTo, alias, description,
                                                replace, newRuleType, ruleDate, principal);

         if(model.permissionTableModel() != null && model.permissionTableModel().changed()) {
            permissionService.setResourcePermissions(newPath, ResourceType.REPORT, fullPath,
                                                     model.permissionTableModel(), principal);
         }
      }
      catch(RepletRegistryManager.DuplicateNameException e) {
         LOG.warn(Catalog.getCatalog().getString("em.viewsheet.duplicateName"));
      }
      catch(RepletRegistryManager.RenameFailedException e) {
         LOG.warn(e.getMessage());
      }
      finally {
         if(actionRecord != null) {
            Audit.getInstance().auditAction(actionRecord, principal);
         }
      }

      return getSettings(newPath, false, owner, principal);
   }

   public ConnectionStatus deleteRepositoryFolderSettings(IdentityID owner, boolean force,
                                              SetRepositoryFolderTableModel tableModel,
                                              Principal principal)
      throws Exception
   {
      if(tableModel.table() == null) {
         return null;
      }

      owner = owner != null && owner.name.length() > 0 ? owner : null;
      RecycleBin recycleBin = RecycleBin.getRecycleBin();
      RepletRegistry registry = RepletRegistry.getRegistry(owner);
      boolean ismy = owner != null;
      List<String> errors = new ArrayList<>();
      StringBuilder confirmMessage = new StringBuilder();
      boolean needConfirm = false;

      for(String replet : tableModel.table()) {
         try {
            if(replet != null) {
               boolean isFolder = replet.startsWith(catalog.getString("Folder"));
               replet = (ismy ? Tool.MY_DASHBOARD : "") +
                  replet.substring(ismy ? replet.indexOf("/") : replet.indexOf(": ") + 2);

               if(isFolder) {
                  String worksheets = "Worksheets";
                  int index = replet.indexOf(worksheets);

                  if(index >= 0) {
                     String folderPath = replet.substring(index + worksheets.length() + 1);

                     if(RecycleUtils.isInRecycleBin(folderPath)) {
                        registryManager.removeWorksheetFolder(folderPath, owner, force, principal);
                        recycleBin.removeEntry(folderPath);
                     }
                     else {
                        String wsFolderPath = owner != null ?
                           treeService.getUnscopedPath(folderPath) : folderPath;
                        RecycleUtils.moveAssetFolderToRecycleBin(wsFolderPath, owner, principal, recycleBin, false);
                     }
                  }
                  else {
                     for(String folderPath : registry.getAllFolders()) {
                        if(replet.equals(folderPath)) {
                           checkPermission(folderPath, ResourceType.REPORT, ResourceAction.READ, principal);

                           if(RecycleUtils.isInRecycleBin(folderPath)) {
                              if(!registry.removeFolder(folderPath)) {
                                 errors.add(folderPath);
                              }
                           }
                           else {
                              RecycleUtils.moveRepositoryFolderToRecycleBin(folderPath, folderPath, owner, principal, recycleBin);
                           }

                           break;
                        }
                     }
                  }
               }
            }
         }
         catch(ConfirmException confirmException) {
            confirmMessage.append(confirmException.getMessage()).append(" ");
         }
         catch(MessageException msgException) {
            throw msgException;
         }
      }

      registry.save();

      if(errors.size() > 0) {
         String[] errs = errors.toArray(new String[0]);
         LOG.error(Catalog.getCatalog().getString("em.registry.deleteRepletsError", arrayToString(errs, ",")));
      }

      if(!confirmMessage.toString().isEmpty()) {
         return new ConnectionStatus(confirmMessage.toString());
      }

      return null;
   }

   /**
    * Get folder type.
    */
   private int getFolderType(String folder, boolean isWorksheetFolder) {
      int type = isWorksheetFolder ? RepositoryEntry.WORKSHEET_FOLDER : RepositoryEntry.FOLDER;

      if(folder == null) {
         return type;
      }

      if(folder.equals(RepositoryEntry.REPOSITORY_FOLDER)) {
         folder = "/";
      }
      else if(folder.indexOf(RepositoryEntry.REPOSITORY_FOLDER) == 0) {
         folder = folder.substring(
            RepositoryEntry.REPOSITORY_FOLDER.length() + 1);
      }

      return type;
   }

   private boolean hasPermission(String resource, ResourceType prefix, ResourceAction access,
                                 Principal principal)
   {
      return registryManager.hasPermission(resource, prefix, access, principal);
   }

   public boolean checkPermission(String resource, ResourceType prefix,
                                  ResourceAction access, Principal principal)
   {
      return registryManager.checkPermission(resource, prefix, access,
                                             principal);
   }

   /**
    * If the folder contain user name, get user name from path.
    */
   private IdentityID getUserFromFolder(String folder) {
      if(folder.startsWith("(") && folder.indexOf(")") > 0) {
         return IdentityID.getIdentityIDFromKey(folder.substring(1, folder.indexOf(")")));
      }

      return null;
   }

   /**
    * If the folder contain user name, get the right folder path.
    */
   private String getFolderPath(String folder) {
      if(folder.startsWith("(") && folder.indexOf(")") > 0) {
         return folder.substring(folder.indexOf(")") + 1);
      }

      return folder;
   }

   /**
    * Convert an array to string.
    */
   public String arrayToString(String[] arr, String delim) {
      if(arr == null || arr.length == 0) {
         return null;
      }

      StringBuilder buffer = new StringBuilder();

      for(String item : arr) {
         if(buffer.length() > 0) {
            buffer.append(delim);
         }

         buffer.append(item);
      }

      return buffer.toString();
   }

   private final RepletRegistryManager registryManager = new RepletRegistryManager();
   private final ResourcePermissionService permissionService;
   private final ContentRepositoryTreeService treeService;
   private final Catalog catalog = Catalog.getCatalog();
   private static final Logger LOG = LoggerFactory.getLogger(RepositoryFolderService.class);
}
