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
package inetsoft.web.composer;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.LibManager;
import inetsoft.report.composition.RuntimeSheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.internal.StyleTreeModel;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.RepletRepository;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.content.repository.ResourcePermissionService;
import inetsoft.web.composer.model.RenameAssetEvent;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.rmi.RemoteException;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.Arrays;

@Controller
public class RenameAssetController {
   /**
    * Creates a new instance of <tt>RenameAssetController</tt>.
    */
   @Autowired
   public RenameAssetController(AssetRepository assetRepository,
                                RepletRepository repletRepository,
                                ViewsheetService viewsheetService,
                                SecurityProvider securityProvider)
   {
      this.assetRepository = assetRepository;
      this.repletRepository = repletRepository;
      this.viewsheetService = viewsheetService;
      this.securityProvider = securityProvider;
   }

   @PostMapping("api/composer/asset-tree/rename-asset")
   @ResponseBody
   public MessageCommand renameAsset(
      @RequestBody RenameAssetEvent event, Principal principal) throws Exception
   {
      AssetEntry entry = event.entry();

      // log action
      String actionName = ActionRecord.ACTION_NAME_RENAME;
      String objectName = entry.getDescription();
      String objectType = AssetEventUtil.getObjectType(entry);
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName,
         objectName, objectType, actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE, null);

      try {
         assetRepository.checkAssetPermission(principal, entry, ResourceAction.DELETE);

         if(entry.isScript() || entry.isTableStyle() || entry.isTableStyleFolder()) {
            assetRepository.checkAssetPermission(principal, entry, ResourceAction.WRITE);
         }
      }
      catch(Exception e) {
         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setMessage(Catalog.getCatalog().getString("composer.nopermission.rename",
                                                                  entry.getName()));
         messageCommand.setType(MessageCommand.Type.ERROR);

         if(actionRecord != null) {
            actionRecord.setActionError("Permission denied to rename: " + entry.getName());
            Audit.getInstance().auditAction(actionRecord, principal);
         }

         return messageCommand;
      }

      String name = event.newName();
      boolean reAlias = entry.getAlias() != null && entry.getAlias().length() > 0;
      String path;

      if(entry.getParent().isRoot()) {
         path = SUtil.removeControlChars(name);
      }
      else {
         path = SUtil.removeControlChars(entry.getParentPath() + "/" + name);
      }

      AssetEntry nentry = new AssetEntry(entry.getScope(), entry.getType(),
                                         path, entry.getUser());
      nentry.copyProperties(entry);
      nentry.copyMetaData(entry);
      nentry.addFavoritesUser(entry.getFavoritesUser());
      LibManager manager = LibManager.getManager();

      if(!reAlias && !nentry.equals(entry) &&
        (viewsheetService.isDuplicatedEntry(assetRepository, nentry) ||
        AssetEventUtil.isRenameDuplicate(nentry) ||
        (entry.isScript() && manager.getScript(event.newName()) != null)))
      {
         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setMessage(Catalog.getCatalog().getString("common.duplicateName"));
         messageCommand.setType(MessageCommand.Type.ERROR);
         return messageCommand;
      }

      String[] paths = Tool.split(path, '/');

      if(isViewsheetEntry(entry) && Tool.contains(paths, Tool.MY_DASHBOARD)) {
         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setMessage(Catalog.getCatalog().getString("viewer.viewsheet.nameReserved"));
         messageCommand.setType(MessageCommand.Type.ERROR);
         return messageCommand;
      }

      try {
         ResourceType resourceType = AssetUtil.isLibraryType(entry) ?
                 AssetUtil.getLibraryAssetType(entry) : ResourceType.ASSET;
         String oldPath = AssetUtil.isLibraryType(entry) ?
                 AssetUtil.getLibraryPermissionPath(entry, resourceType) : entry.getPath();
         oldPath = ResourcePermissionService.getPermissionResourcePath(oldPath, resourceType, entry.isTableStyleFolder());
         Permission oldPermission = securityProvider.getPermission(resourceType, oldPath);

         if(reAlias) {
            entry.setAlias(name);
            nentry = entry;
         }

         String desc = nentry.getDescription();
         desc = desc.substring(0, desc.indexOf("/") + 1);
         desc += viewsheetService.localizeAssetEntry(nentry.getPath(),
                                              principal, true, nentry,
                                              nentry.getScope() == AssetRepository.USER_SCOPE);
         nentry.setProperty("_description_", desc);
         nentry.setProperty("localStr",
                            desc.substring(desc.lastIndexOf("/") + 1));

         if(entry.isScript()) {
            manager.renameScript(entry.getName(), nentry.getName());
            manager.save();
         }
         else if(entry.isTableStyle()) {
            String oname = entry.getProperty("styleName");
            String folder = entry.getProperty("folder");
            String newName = Tool.isEmptyString(folder) ? name : folder + LibManager.SEPARATOR + name;
            String oid = entry.getProperty("styleID");
            manager.renameTableStyle(oname, newName, oid);
            manager.save();
         }
         else if(entry.isTableStyleFolder()) {
            String oname = entry.getProperty("folder");
            String pname = StyleTreeModel.getParentPath(oname);
            String newPath = pname == null ? nentry.getName() : pname + StyleTreeModel.SEPARATOR + nentry.getName();
            renameTableStyleFolder(oname, newPath, manager);
            manager.save();
         }
         else if(entry.isRepositoryFolder()) {
            String rpath = entry.getPath();

            if(entry.getScope() == AssetRepository.USER_SCOPE) {
               rpath = Tool.MY_DASHBOARD + "/" + rpath;
               path = Tool.MY_DASHBOARD + "/" + path;
            }

            RepositoryEntry rentry = new RepositoryEntry(rpath,
                                                         RepositoryEntry.FOLDER, entry.getUser());
            repletRepository.renameRepositoryEntry(rentry, path, principal);
         }
         else if(entry.isFolder()) {
            if(!entry.getName().equals(nentry.getName())) {
               assetRepository.changeFolder(entry, nentry, principal, event.confirmed());
            }
         }
         else {
            assetRepository.changeSheet(entry, nentry, principal, true);
         }

         if(!AssetUtil.isLibraryType(nentry)) {
            updatePermission(resourceType, oldPath, nentry.getPath(), oldPermission);
         }

         if(actionRecord != null) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
            actionRecord.setActionError("Target Entry: " +
                                           nentry.getDescription());
         }
      }
      catch(Exception ex) {
         MessageCommand messageCommand = null;

         if(ex instanceof ConfirmException) {
            actionRecord = null;

            if(!event.confirmed()) {
               ConfirmException confirmException = (ConfirmException) ex;
               String message = ex.getMessage();
               messageCommand = new MessageCommand();
               messageCommand.setMessage(message);
               messageCommand.setType(MessageCommand.Type.CONFIRM);
            }
         }
         else if(ex instanceof MessageException) {
            MessageException messageException = (MessageException) ex;

            messageCommand = new MessageCommand();
            messageCommand.setMessage(messageException.getMessage());
            messageCommand.setType(MessageCommand.Type
                                      .fromCode(messageException.getWarningLevel()));
         }
         else if(ex instanceof RemoteException) {
            RemoteException remoteException = (RemoteException) ex;

            messageCommand = new MessageCommand();
            messageCommand.setMessage(remoteException.getMessage());
            messageCommand.setType(MessageCommand.Type.WARNING);
         }

         if(actionRecord != null) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError(ex.getMessage() +
                                           ", Target Entry:: " + nentry.getDescription());
         }

         if(messageCommand != null) {
            return messageCommand;
         }

         throw ex;
      }
      finally {
         if(actionRecord != null) {
            Audit.getInstance().auditAction(actionRecord, principal);
         }
      }

      return null;
   }

   @PostMapping("api/composer/asset-tree/rename-asset/check-open-sheets")
   @ResponseBody
   public MessageCommand checkOpenSheets(
      @RequestBody AssetEntry entry, Principal principal) throws Exception
   {
      RuntimeSheet[] openSheets = viewsheetService.getRuntimeSheets(null);
      Catalog catalog = Catalog.getCatalog(principal);

      boolean openAsset = Arrays.stream(openSheets).anyMatch((sheet) -> {
         AssetEntry sheetEntry = sheet.getEntry();

         return sheetEntry != null && (entry.equals(sheetEntry) ||
            sheetEntry.getParentPath().contains(entry.getPath()));
      });

      if(openAsset) {
         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setMessage(
            catalog.getString("common.tree.renameForbidden"));
         messageCommand.setType(MessageCommand.Type.WARNING);
         return messageCommand;
      }

      return null;
   }

   private void updatePermission(ResourceType resourceType, String oldPath, String newPath, Permission oldPermission) {
      securityProvider.setPermission(resourceType, newPath, oldPermission);
      securityProvider.removePermission(resourceType, oldPath);
   }

   private void renameTableStyleFolder(String oname, String nname, LibManager manager) {
      XTableStyle[] tstyles = manager.getTableStyles(oname);

      for(XTableStyle tstyle : tstyles) {
         String tstyleName = tstyle.getName();
         int idx = tstyleName.lastIndexOf(LibManager.SEPARATOR);
         tstyleName = idx < 0 ? tstyleName : tstyleName.substring(idx + 1);
         manager.renameTableStyle(
            tstyle.getName(), nname + LibManager.SEPARATOR + tstyleName, tstyle.getID());
      }

      manager.renameTableStyleFolder(oname, nname);

      String[] folders = manager.getTableStyleFolders(oname);

      for(String folder : folders) {
         String subName = folder;
         int idx = folder.lastIndexOf(LibManager.SEPARATOR);
         subName = idx < 0 ? subName : subName.substring(idx + 1);
         renameTableStyleFolder(folder, nname + LibManager.SEPARATOR + subName, manager);
      }
   }

   /**
    * Check if it is a viewsheet entry.
    *
    * @param entry the asset entry.
    *
    * @return true if it is a viewsheet entry, false otherwise.
    */
   private boolean isViewsheetEntry(AssetEntry entry) {
      return entry.getType() == AssetEntry.Type.VIEWSHEET ||
         entry.getType() == AssetEntry.Type.REPOSITORY_FOLDER ||
         entry.getType() == AssetEntry.Type.VIEWSHEET_SNAPSHOT;
   }

   private final AssetRepository assetRepository;
   private final ViewsheetService viewsheetService;
   private final RepletRepository repletRepository;
   private final SecurityProvider securityProvider;
}
