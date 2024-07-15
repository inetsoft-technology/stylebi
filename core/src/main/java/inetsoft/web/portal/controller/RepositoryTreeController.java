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
package inetsoft.web.portal.controller;

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.internal.Util;
import inetsoft.sree.*;
import inetsoft.sree.internal.AnalyticEngine;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.RecycleBin;
import inetsoft.web.RecycleUtils;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.portal.model.*;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.rmi.RemoteException;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller that provides a REST endpoint used to get the contents of the
 * repository tree.
 *
 * @since 12.3
 */
@RestController
public class RepositoryTreeController {
   /**
    * Creates a new instance of <tt>RepositoryTreeController</tt>.
    *
    * @param analyticRepository the analytic repository.
    */
   @Autowired
   public RepositoryTreeController(AnalyticRepository analyticRepository,
      RepositoryEntryModelFactoryService repositoryEntryModelFactoryService,
      RepositoryTreeService repositoryTreeService)
   {
      this.analyticRepository = analyticRepository;
      this.repositoryEntryModelFactoryService = repositoryEntryModelFactoryService;
      this.repositoryTreeService = repositoryTreeService;
   }

   /**
    * Gets the specified repository folder.
    *
    * @param path the path to the folder.
    *
    * @return the repository folder.
    */
   @RequestMapping(
      value = "/api/portal/tree",
      method = RequestMethod.GET
   )
   public TreeNodeModel getFolder(
      @RequestParam(value = "path", defaultValue = "/") String path,
      @RequestParam(value = "permission", defaultValue = "READ") String permission,
      @RequestParam(value = "selector", defaultValue = RepositoryEntry.ALL + "") int selector,
      @RequestParam(value = "detailType", defaultValue = "") String detailType,
      @RequestParam(value = "isArchive", required=false) boolean isArchive,
      @RequestParam(value = "checkDetailType", defaultValue = "false") String checkDetailType,
      @RequestParam(value = "isReport", defaultValue = "true") boolean isReport,
      @RequestParam(value = "isFavoritesTree", defaultValue = "false") boolean isFavoritesTree,
      @RequestParam(value = "isGlobal", defaultValue = "false") boolean isGlobal,
      @RequestParam(value = "isPortalData", defaultValue = "false") boolean isPortalData,
      @RequestParam(value = "showVS", defaultValue = "false") boolean showVS,
      Principal principal) throws Exception
   {
      RepositoryEntry parentEntry =
         new RepositoryEntry(path, RepositoryEntry.FOLDER);
      RepositoryEntry[] pentries = getParentEntries(isArchive, parentEntry, path);
      RepositoryEntry[] entries = null;
      AnalyticEngine engine = (AnalyticEngine) analyticRepository;
      ResourceAction action = ResourceAction.valueOf(permission);
      RepletRegistry registry = RepletRegistry.getRegistry();


      // hyperlink asset tree need to check the detail type.
      if("true".equals(checkDetailType)) {
         entries = VSEventUtil.getRepositoryEntries(engine, principal, action,
            selector, detailType, pentries);
      }
      else {
         entries = engine.getRepositoryEntries(
            parentEntry.getPath(), principal, action, selector);
      }

      List<TreeNodeModel> folderNodes = new ArrayList<>();
      List<TreeNodeModel> fileNodes = new ArrayList<>();

      for(RepositoryEntry entry : entries) {
         if(!isVisible(entry, isReport, isPortalData, isFavoritesTree, isGlobal,
            showVS, principal))
         {
            continue;
         }

         entry.setFavoritesUser(repositoryTreeService.hasFavoritesUser(entry, principal));
         RepositoryEntryModel entryModel = repositoryEntryModelFactoryService
            .createModel(entry);
         entryModel.setOp(repositoryTreeService.getSupportedOperations(entry, principal));

         TreeNodeModel.Builder builder = TreeNodeModel.builder();
         builder.label(repositoryTreeService.getEntryLabel(entry, principal))
            .data(entryModel)
            .leaf(repositoryTreeService.isLeafEntry(entry))
            .tooltip(repositoryTreeService.getEntryTooltip(entry));

         builder.dragName("RepositoryEntry");

         TreeNodeModel node = builder.build();

         if(entry.isFolder()) {
            folderNodes.add(node);
         }
         else {
            fileNodes.add(node);
         }
      }

      RepositoryEntryModel parentEntryModel = repositoryEntryModelFactoryService
         .createModel(parentEntry);
      parentEntryModel.setOp(repositoryTreeService.getSupportedOperations(parentEntry, principal));
      String label = isFavoritesTree && parentEntry.isRoot()
         ? Catalog.getCatalog().getString("Favorites")
         : repositoryTreeService.getEntryLabel(parentEntry, principal);

      TreeNodeModel rootNode = TreeNodeModel.builder()
         .label(label)
         .data(parentEntryModel)
         .addChildren(folderNodes.toArray(new TreeNodeModel[folderNodes.size()]))
         .addChildren(fileNodes.toArray(new TreeNodeModel[fileNodes.size()]))
         .build();

      return rootNode;
   }

   /**
    * Check if the target entry is visible on the tree.
    * @param entry     the target entry.
    * @param isPortalData true if load tree for portal data auto drill dialog.
    * @param isFavoritesTree   true if load for favorites tree.
    * @param isGlobal          true if just load global item.s
    * @param principal
    * @return
    */
   private boolean isVisible(RepositoryEntry entry, boolean isReport,
                             boolean isPortalData,
                             boolean isFavoritesTree, boolean isGlobal, boolean showVS,
                             Principal principal)
   {
      if(entry.isMyReport() && isGlobal ||
         RecycleUtils.isInRecycleBin(entry.getPath()) ||
         !isReport && entry.getPath().startsWith("Built-in Admin Reports") ||
         isFavoritesTree && !repositoryTreeService.isFavoritesEntry(entry, principal))
      {
         return false;
      }

      if(entry instanceof RepletEntry) {
         return isPortalData || ((RepletEntry) entry).isVisible() && isReport;
      }

      if(entry instanceof ViewsheetEntry) {
         return isPortalData || showVS || ((ViewsheetEntry) entry).isOnReport();
      }

      return true;
   }

   @PostMapping(value = "/api/portal/tree/rename")
   public MessageCommand renameRepositoryEntry(
      @RequestBody RenameRepositoryEntryEvent event,
      Principal principal)
      throws Exception
   {
      return renameRepositoryEntry0(event.entry().createRepositoryEntry(),
                                    event.newName(), event.confirmed(), principal);
   }

   private MessageCommand renameRepositoryEntry0(
      RepositoryEntry entry, String newName, boolean confirmed, Principal principal)
      throws Exception
   {
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      RepletRegistry registry = SUtil.isMyReport(entry.getPath()) ?
              RepletRegistry.getRegistry(pId) : RepletRegistry.getRegistry(null);
      Catalog catalog = Catalog.getCatalog(principal);
      String oldName = entry.getPath();
      newName = SUtil.removeControlChars(newName);
      AssetEntry assetEntry = entry.getAssetEntry();
      String newPath = entry.getParent().isRoot() ? newName : entry.getParentPath() + "/" + newName;

      if(newPath.equals(Tool.MY_DASHBOARD)) {
         MessageCommand messageCommand = new MessageCommand();
         messageCommand
            .setMessage(Catalog.getCatalog().getString("em.repletFolderName.rename"));
         messageCommand.setType(MessageCommand.Type.ERROR);
         return messageCommand;
      }

      // log rename report action
      String actionName = ActionRecord.ACTION_NAME_RENAME;
      String objectName = assetEntry != null ? assetEntry.getDescription() : oldName;
      String objectType = getAuditObjectType(entry, assetEntry);
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName, objectName, objectType,
            null, ActionRecord.ACTION_STATUS_FAILURE, null);

      try {
         if(!SUtil.checkPermission(principal, entry, analyticRepository, ResourceAction.WRITE)) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError("Write access denied: " +
               objectName + ", Target Entry: " + newPath);
            throw new RemoteException(catalog.getString(
               "common.writeAuthority", objectName));
         }

         if(assetEntry == null) {
            String oalias = entry.isFolder() ?
                    registry.getFolderAlias(entry.getPath()) : null;
            boolean reAlias = oalias != null && !"".equals(oalias);

            if(SUtil.isDuplicatedRepositoryPath(newPath, pId, -1) && !reAlias) {
               MessageCommand messageCommand = new MessageCommand();
               messageCommand.setMessage(catalog.getString("em.schedule.action.dupFolder"));
               messageCommand.setType(MessageCommand.Type.ERROR);
               return messageCommand;
            }

            analyticRepository.renameRepositoryEntry(entry, newPath, principal);
         }
         else {
            boolean reAlias = assetEntry.getAlias() != null || containsSpecialChars(newName);
            String name = assetEntry.getScope() == AssetRepository.USER_SCOPE ?
               newPath.substring(Tool.MY_DASHBOARD.length() + 1) : newPath;
            AssetEntry nentry = new AssetEntry(assetEntry.getScope(),
               assetEntry.getType(), name, assetEntry.getUser());
            nentry.copyProperties(assetEntry);
            nentry.addFavoritesUser(assetEntry.getFavoritesUser());
            AssetRepository rep = AssetUtil.getAssetRepository(false);

            if(!nentry.equals(assetEntry) && SUtil.isDuplicatedEntry(rep, nentry) && !reAlias) {
               MessageCommand messageCommand = new MessageCommand();
               messageCommand.setMessage(Catalog.getCatalog().getString(
                  "common.duplicateName"));
               messageCommand.setType(MessageCommand.Type.ERROR);
               return messageCommand;
            }

            if(reAlias) {
               assetEntry.setAlias(newName);
               nentry = assetEntry;
            }

            if(nentry.isViewsheet() && assetEntry.getProperty("__bookmark_id__") != null) {
               nentry.setProperty("__bookmark_id__", nentry.toIdentifier());
            }

            if(nentry.isViewsheet() || nentry.isVSSnapshot()) {
               VSUtil.refreshBookmarkId(assetEntry, nentry);
            }

            rep.changeSheet(assetEntry, nentry, principal, confirmed);
            newPath = nentry.getDescription();
         }

         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
         actionRecord.setActionError("Target Entry: " + newPath);
      }
      catch(Exception ex) {
         if(ex instanceof ConfirmException) {
            if(!confirmed) {
               MessageCommand messageCommand = new MessageCommand();
               messageCommand.setMessage(ex.getMessage());
               messageCommand.setType(MessageCommand.Type.CONFIRM);
               return messageCommand;
            }
         }

         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         actionRecord.setActionError(ex.getMessage() + ", Target Entry: " + newPath);
         LOG.error("Failed to rename " + oldName + " to " + newName, ex);

         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setMessage(ex.getMessage());
         messageCommand.setType(MessageCommand.Type.ERROR);
         return messageCommand;
      }
      finally {
         try {
            Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
            actionRecord.setActionTimestamp(actionTimestamp);

            if(actionRecord != null && actionRecord.isValid()) {
               Audit.getInstance().auditAction(actionRecord, principal);
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to insert audit record for renaming " + oldName + " to " + newName, ex);
         }
      }

      return null;
   }

   private static boolean containsSpecialChars(String str) {
      if(str.length() > 0 && !Character.isUnicodeIdentifierPart(str.charAt(0))) {
         return true;
      }

      for(int i = 0; i < str.length(); i++) {
         char ch = str.charAt(i);

         if("\\\\\\/\"<%^".indexOf(ch) >= 0 || ch > 127) {
            return true;
         }
      }

      return false;
   }

   @PostMapping(value = "/api/portal/tree/remove")
   public MessageCommand removeRepositoryEntry(
      @RequestBody RemoveRepositoryEntryEvent event,
      Principal principal)
   {
      RepositoryEntry entry = event.entry().createRepositoryEntry();
      UserEnv.setProperty(principal, entry.getName(), "");
      RecycleBin recycleBin = RecycleBin.getRecycleBin();
      ActionRecord actionRecord = null;
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

      try {
         AssetEntry assetEntry = entry.getAssetEntry();
         String entryPath = entry.getPath();
         String parentPath = RecycleUtils.RECYCLE_BIN_FOLDER;

         try {
            int idx = entryPath.lastIndexOf("/");
            String ename = idx >= 0 ? entryPath.substring(idx + 1) : entryPath;
            String path = "/".equals(parentPath) ? ename : parentPath + "/" + ename;

            // check if new path is conflict with another path
            if(SUtil.isDuplicatedRepositoryPath(path, pId)) {
               MessageCommand messageCommand = new MessageCommand();
               messageCommand.setMessage(
                  Catalog.getCatalog().getString("em.schedule.action.dupFolder"));
               messageCommand.setType(MessageCommand.Type.ERROR);
               return messageCommand;
            }
         }
         catch(Exception ex) {
            throw new RemoteException("Failed to rename folder " + entryPath
               + " to " + parentPath, ex);
         }

         // log action
         String actionName = ActionRecord.ACTION_NAME_DELETE;
         String objectName = assetEntry != null ? assetEntry.getDescription() : entryPath;
         String objectType = getAuditObjectType(entry, assetEntry);

         Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());

         actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName, objectName, objectType,
            actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE, null);

         if(!SUtil.checkPermission(principal, entry, analyticRepository, ResourceAction.DELETE)) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError("Delete access denied: " + objectName);
            throw new RemoteException("Delete access denied: " + objectName);
         }

         if(entry.getAssetEntry() != null) {
            RecycleUtils.moveSheetToRecycleBin(
               entry.getAssetEntry(), principal, recycleBin, event.confirmed());
         }
         else {
            RecycleUtils.moveRepositoryToRecycleBin(entry, principal, recycleBin);
         }

         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
      }
      catch(Exception e) {
         if(e instanceof ConfirmException) {
            if(!event.confirmed()) {
               MessageCommand messageCommand = new MessageCommand();
               messageCommand.setMessage(e.getMessage());
               messageCommand.setType(MessageCommand.Type.CONFIRM);
               return messageCommand;
            }
         }
         else {
            LOG.error("Failed to remove asset", e);
            String message = e.getMessage();

            if(message == null) {
               message = Catalog.getCatalog(principal)
                  .getString("viewer.getOperationFailed", "delete", entry.getPath());
            }

            MessageCommand messageCommand = new MessageCommand();
            messageCommand.setMessage(message);
            messageCommand.setType(MessageCommand.Type.ERROR);
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError(message);
            return messageCommand;
         }
      }
      finally {
         if(actionRecord != null) {
            Audit.getInstance().auditAction(actionRecord, principal);
         }
      }

      return null;
   }

   @PostMapping(value = "/api/portal/tree/add-folder")
   public MessageCommand addRepositoryFolder(
      @RequestBody AddRepositoryFolderEvent event,
      Principal principal)
      throws Exception
   {
      Catalog catalog = Catalog.getCatalog(principal);
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

      String name = event.name();
      String alias = event.alias();
      String description = event.description();
      RepositoryEntry entry = event.entry().createRepositoryEntry();
      boolean editFolder = event.edit();

      if(editFolder) {
         if(entry == null) {
            MessageCommand messageCommand = new MessageCommand();
            messageCommand.setMessage("Folder not found.");
            messageCommand.setType(MessageCommand.Type.ERROR);
            return messageCommand;
         }

         String ppath = entry.getPath();
         int idx = ppath.lastIndexOf("/");

         if(idx >= 0) {
            ppath = ppath.substring(0, idx);
         }
         else {
            ppath = "";
         }

         String npath = ppath;

         if("/".equals(npath) || "".equals(npath)) {
            npath += name;
         }
         else {
            npath += "/" + name;
         }

         RepletRegistry registry = SUtil.isMyReport(entry.getPath()) ?
            RepletRegistry.getRegistry(pId) : RepletRegistry.getRegistry(null);

         // don't rename if the name did not change
         if(!entry.getName().equals(name)) {
            MessageCommand success = null;
            String oalias = registry.getFolderAlias(entry.getPath());

            try {
               registry.setFolderAlias(entry.getPath(), "");
               success = renameRepositoryEntry0(entry, name, event.confirmed(), principal);
            }
            finally {
               if(success != null) {
                  registry.setFolderAlias(entry.getPath(), oalias);
               }
            }

            if(success != null) {
               return success;
            }
         }

         registry.setFolderAlias(npath, alias, true);
         registry.setFolderDescription(npath, description);
         registry.save();
         return null;
      }

      String path = "/".equals(entry.getPath()) ? name : (entry.getPath() + "/" + name);

      try {
         // check if new path is conflict with another path
         if(SUtil.isDuplicatedRepositoryPath(path, pId)) {
            String error = catalog.getString("em.schedule.action.dupFolder");
            MessageCommand messageCommand = new MessageCommand();
            messageCommand.setMessage(error);
            messageCommand.setType(MessageCommand.Type.ERROR);
            return messageCommand;
         }
      }
      catch(Exception ex) {
         throw new RemoteException("Failed to create folder: " + entry.getPath(), ex);
      }

      // log action
      String actionName = ActionRecord.ACTION_NAME_CREATE;
      String objectType = ActionRecord.OBJECT_TYPE_FOLDER;
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName,
         Util.getObjectFullPath(RepositoryEntry.REPOSITORY | RepositoryEntry.FOLDER, path, principal),
         objectType, actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE, null);

      try {
         // does not have write permission of the folder?
         if(!analyticRepository.checkPermission(principal, ResourceType.REPORT, entry.getPath(),
            ResourceAction.WRITE))
         {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError("Write access denied: " + entry.getPath());
            throw new RemoteException(catalog.getString(
               "common.writeAuthority", entry.getPath()));
         }

         analyticRepository.addFolder(entry, name, alias, description, principal);
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
         actionRecord.setActionError("Target Entry: " + entry.getPath());
      }
      catch(Exception e) {
         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         actionRecord.setActionError(e.getMessage() + ", create folder to: " + entry.getPath());
         LOG.error("Failed to create folder: " + entry.getPath(), e);
         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setMessage(e.getMessage());
         messageCommand.setType(MessageCommand.Type.ERROR);
         return messageCommand;
      }
      finally {
         try {
            if(actionRecord != null) {
               Audit.getInstance().auditAction(actionRecord, principal);
            }
         }
         catch(Exception e) {
            LOG.error("Failed to insert audit record for creating folder: " + entry.getPath(), e);
         }
      }

      return null;
   }

   @PostMapping(value = "/api/portal/tree/change")
   public MessageCommand changeRepositoryEntry(
      @RequestBody ChangeRepositoryEntryEvent event,
      Principal principal)
      throws IOException, RepletException
   {
      Catalog catalog = Catalog.getCatalog(principal);
      RepositoryEntry entry = event.entry().createRepositoryEntry();
      AssetEntry assetEntry = entry.getAssetEntry();
      String entryPath = entry.getPath();
      RepositoryEntry parent = event.parent().createRepositoryEntry();
      String parentPath = parent.getPath();
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());

      if("/".equals(parentPath) && entryPath.indexOf("/") > -1 &&
         entryPath.substring(entryPath.lastIndexOf("/") + 1).equals(Tool.MY_DASHBOARD))
      {
         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setMessage(catalog.getString("em.repletFolderName.changeFolder"));
         messageCommand.setType(MessageCommand.Type.ERROR);
         return messageCommand;
      }

      try {
         int idx = entryPath.lastIndexOf("/");
         String ename = idx >= 0 ? entryPath.substring(idx + 1) : entryPath;
         String path = "/".equals(parentPath) ? ename : parentPath + "/" + ename;

         // check if new path is conflict with another path
         if(SUtil.isDuplicatedRepositoryPath(path, pId)) {
            MessageCommand messageCommand = new MessageCommand();
            messageCommand.setMessage(catalog.getString("em.schedule.action.dupFolder"));
            messageCommand.setType(MessageCommand.Type.ERROR);
            return messageCommand;
         }
      }
      catch(Exception ex) {
         throw new RemoteException("Failed to rename folder " + entryPath + " to " + parentPath, ex);
      }

      // log action
      String actionName = ActionRecord.ACTION_NAME_MOVE;
      String objectName = assetEntry != null ? assetEntry.getDescription() : entryPath;
      String objectType = getAuditObjectType(entry, assetEntry);
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName, objectName, objectType,
         actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE, null);

      try {
         // does not have write permission of the entry?
         if(!SUtil.checkPermission(principal, entry, analyticRepository, ResourceAction.WRITE)) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError("Write access denied: " +
               objectName + ", move to folder: " + parentPath);
            MessageCommand messageCommand = new MessageCommand();
            messageCommand.setMessage(catalog.getString(
               "common.writeAuthority", objectName));
            messageCommand.setType(MessageCommand.Type.ERROR);
            return messageCommand;
         }

         // does not have write permission of the folder?
         if(!analyticRepository.checkPermission(principal, ResourceType.REPORT, parentPath,
            ResourceAction.WRITE))
         {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError("Write access denied: " + parentPath);
            MessageCommand messageCommand = new MessageCommand();
            messageCommand.setMessage(catalog.getString(
               "common.writeAuthority", parentPath));
            messageCommand.setType(MessageCommand.Type.ERROR);
            return messageCommand;
         }

         String newPath = "";

         if(assetEntry == null) {
            analyticRepository.changeFolder(entry, parentPath, principal);
            newPath = "/".equals(parentPath) ? entry.getName() : parentPath + "/" + entry.getName();
         }
         else {
            AssetRepository assetRepository = AssetUtil.getAssetRepository(false);
            boolean ismy = SUtil.isMyReport(parentPath);
            String repletName = assetEntry.getName();
            String path = ismy ? parentPath.equals(Tool.MY_DASHBOARD) ?
               repletName : parentPath.substring(Tool.MY_DASHBOARD.length() + 1) + "/" + repletName
               : "/".equals(parentPath) ? repletName : parentPath + "/" + repletName;
            AssetEntry nentry = new AssetEntry(assetEntry.getScope(),
               assetEntry.getType(), path, assetEntry.getUser());

            if(!assetEntry.isVSSnapshot()) {
               Viewsheet vs = (Viewsheet) assetRepository.getSheet(assetEntry,
                  principal, false, AssetContent.ALL);

               if(vs == null) {
                  throw new RuntimeException("Viewsheet not found: " + repletName);
               }

               ViewsheetInfo vinfo = vs.getViewsheetInfo();
               boolean onReport = vinfo.isOnReport();

               if(onReport) {
                  assetEntry.setProperty("onReport", onReport + "");
               }
            }

            nentry.copyProperties(assetEntry);
            nentry.addFavoritesUser(assetEntry.getFavoritesUser());

            if(assetEntry.getAlias() != null && !"".equals(assetEntry.getAlias())) {
               nentry.setAlias(assetEntry.getAlias());
            }

            if(!nentry.equals(assetEntry) && SUtil.isDuplicatedEntry(assetRepository, nentry)) {
               MessageCommand messageCommand = new MessageCommand();
               messageCommand.setMessage(catalog.getString("common.duplicateName"));
               messageCommand.setType(MessageCommand.Type.ERROR);
               return messageCommand;
            }

            ScheduleManager manager = ScheduleManager.getScheduleManager();
            manager.viewSheetRenamed(assetEntry.toIdentifier(),
               nentry.toIdentifier(), nentry.getName());
            assetRepository.changeSheet(assetEntry, nentry, principal, event.confirmed());
            newPath = nentry.getDescription();
         }

         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
         actionRecord.setActionError("Target Entry: " + newPath);
      }
      catch(Exception e) {
         if(e instanceof ConfirmException) {
            actionRecord = null;

            if(!event.confirmed()) {
               MessageCommand messageCommand = new MessageCommand();
               messageCommand.setMessage(e.getMessage());
               messageCommand.setType(MessageCommand.Type.CONFIRM);
               return messageCommand;
            }
         }

         actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
         actionRecord.setActionError(e.getMessage() + ", move to folder: " + parentPath);
         LOG.error("Failed to rename folder " + entryPath + " to " + parentPath, e);
         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setMessage(catalog.getString(e.getMessage()));
         messageCommand.setType(MessageCommand.Type.ERROR);
         return messageCommand;
      }
      finally {
         try {
            if(actionRecord != null) {
               Audit.getInstance().auditAction(actionRecord, principal);
            }
         }
         catch(Exception e) {
            LOG.error("Failed to insert audit record for renaming folder " +
                  entryPath + " to " + parentPath, e);
         }
      }

      return null;
   }

   private RepositoryEntry[] getParentEntries(boolean isArchive,
      RepositoryEntry parentEntry, String path)
   {
      String parentPath = "";

      if(isArchive) {
         parentPath = path.lastIndexOf("/") == -1 ? "/" : path.substring(0, path.lastIndexOf("/"));
         parentEntry = new RepositoryEntry(parentPath, RepositoryEntry.FOLDER);
      }

      return new RepositoryEntry[]{parentEntry};
   }

   private String getAuditObjectType(RepositoryEntry entry, AssetEntry assetEntry) {
      if(assetEntry != null) {
         return AssetEventUtil.getObjectType(assetEntry);
      }

      return entry.isFolder() ? ActionRecord.OBJECT_TYPE_FOLDER : ActionRecord.OBJECT_TYPE_REPORT;
   }

   private final AnalyticRepository analyticRepository;
   private final RepositoryEntryModelFactoryService repositoryEntryModelFactoryService;
   private final RepositoryTreeService repositoryTreeService;
   private static final Logger LOG = LoggerFactory.getLogger(RepositoryTreeController.class);
}
