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
import inetsoft.report.internal.Util;
import inetsoft.report.style.XTableStyle;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.DependencyTransformer;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.composer.model.ChangeAssetEvent;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;

@Controller
public class ChangeAssetController {
   /**
    * Creates a new instance of <tt>ChangeAssetController</tt>.
    */
   @Autowired
   public ChangeAssetController(AssetRepository assetRepository,
                                ViewsheetService viewsheetService)
   {
      this.assetRepository = assetRepository;
      this.viewsheetService = viewsheetService;
   }

   @PostMapping("api/composer/asset-tree/change-asset")
   @ResponseBody
   public MessageCommand changeAsset(
      @RequestBody ChangeAssetEvent event, Principal principal) throws Exception
   {
      AssetEntry parent = event.parent();
      List<AssetEntry> entries = event.entries();
      Catalog catalog = Catalog.getCatalog();

      if(parent.getPath().startsWith("Built-in Admin Reports") || !parent.isFolder()) {
         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setMessage(catalog.getString(
            "common.invalidFolder"));
         messageCommand.setType(MessageCommand.Type.ERROR);
         return messageCommand;
      }

      // @by jasonguo, fix bug1255421825412, sort entries for deleting
      // viewsheet before snapshot.
      Collections.sort(entries, new EntriesComparator());
      Iterator it = entries.iterator();

      while(it.hasNext()) {
         final AssetEntry entry = (AssetEntry) it.next();
         String path = parent.isRoot() ? entry.getName() : parent.getPath() + "/" + entry.getName();
         AssetEntry nentry = new AssetEntry(parent.getScope(), entry.getType(),
            path, parent.getUser());
         nentry.copyProperties(parent);
         nentry.copyMetaData(entry);
         nentry.setReportDataSource(entry.isReportDataSource());

         if(entry.getAlias() != null && !"".equals(entry.getAlias())) {
            nentry.setAlias(entry.getAlias());
         }

         if(entry.getProperty("description") != null &&
            !"".equals(entry.getProperty("description")))
         {
            nentry.setProperty("description", entry.getProperty("description"));
         }

         if(nentry.isAncestor(entry) || entry.isAncestor(nentry)) {
            MessageCommand messageCommand = new MessageCommand();
            messageCommand.setMessage(catalog.getString(
               "common.invalidFolder"));
            messageCommand.setType(MessageCommand.Type.ERROR);
            return messageCommand;
         }
         else if(nentry.equals(entry)) {
            // just ignore if dragging to the same folder, no need to show an error
            return null;
         }

         // check if the asset is open
         RuntimeSheet[] openSheets = viewsheetService.getRuntimeSheets(null);

         boolean openAsset = Arrays.stream(openSheets).anyMatch((sheet) -> {
            AssetEntry sheetEntry = sheet.getEntry();

            return sheetEntry != null && sheet.getMode() == Viewsheet.SHEET_DESIGN_MODE &&
               (entry.equals(sheetEntry) ||
                  sheetEntry.getParentPath().contains(entry.getPath()));
         });

         if(openAsset) {
            MessageCommand messageCommand = new MessageCommand();
            messageCommand.setMessage(
               catalog.getString("common.tree.changeFolderForbidden"));
            messageCommand.setType(MessageCommand.Type.ERROR);
            return messageCommand;
         }

         // check if it's a duplicate
         if(!nentry.equals(entry) && (viewsheetService.isDuplicatedEntry(
            assetRepository, nentry) || ((entry.isTableStyle() || entry.isTableStyleFolder()) &&
            AssetEventUtil.isChangeDuplicate(entry, nentry))))
         {
            MessageCommand messageCommand = new MessageCommand();
            messageCommand.setMessage(catalog.getString(
               "common.duplicateName"));
            messageCommand.setType(MessageCommand.Type.ERROR);
            return messageCommand;
         }

         // Moving between different scopes is not allowed for repository
         // folders. The same is true in the portal.
         if(entry.isRepositoryFolder() && entry.getScope() != parent.getScope()) {
            MessageCommand messageCommand = new MessageCommand();
            messageCommand.setMessage(catalog.getString(
               "common.moveFoldersBetweenScopes"));
            messageCommand.setType(MessageCommand.Type.ERROR);
            return messageCommand;
         }

         if(entry.getScope() == AssetRepository.GLOBAL_SCOPE &&
            nentry.getScope() == AssetRepository.USER_SCOPE && !event.confirmed())
         {
            String deps = getDependencies(entry);

            if(deps != null) {
               MessageCommand cmd = new MessageCommand();
               cmd.setType(MessageCommand.Type.CONFIRM);
               cmd.setMessage(getDependencies(entry));

               return cmd;
            }
         }

         // log action
         String actionName = ActionRecord.ACTION_NAME_MOVE;
         String objectName = entry.getDescription();
         String objectType = AssetEventUtil.getObjectType(entry);
         Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
         ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName, objectName,
            objectType, actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE,
            null);

         try {
            if(entry.isTableStyle()) {
               changeTableStyle(parent, entry, principal);
            }
            else if(entry.isTableStyleFolder()) {
               changeTableStyleFolder(parent, entry, principal);
            }
            else if(entry.isRepositoryFolder()) {
               String rpath = entry.getPath();

               if(entry.getScope() == AssetRepository.USER_SCOPE) {
                  rpath = Tool.MY_DASHBOARD + "/" + rpath;
               }

               path = parent.getPath();

               if(parent.getScope() == AssetRepository.USER_SCOPE) {
                  path = parent.isRoot() ? Tool.MY_DASHBOARD :
                     Tool.MY_DASHBOARD + "/" + path;
               }

               RepositoryEntry rentry = new RepositoryEntry(rpath,
                  RepositoryEntry.FOLDER, entry.getUser());
               SUtil.getRepletRepository().changeFolder(rentry, path, principal);
            }
            else if(entry.isFolder()) {
               assetRepository.changeFolder(entry, nentry, principal, event.confirmed());
            }
            else {
               assetRepository.changeSheet(entry, nentry, principal, true);
            }

            if(actionRecord != null) {
               actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
               actionRecord.setActionError(
                  "Target Entry: " + nentry.getDescription());
            }
         }
         catch(Exception ex) {
            MessageCommand messageCommand = null;

            if(ex instanceof ConfirmException) {
               actionRecord = null;

               if(!event.confirmed()) {
                  ConfirmException confirmException = (ConfirmException) ex;
                  String message = ex.getMessage();

                  if(confirmException.getLevel() == ConfirmException.CONFIRM) {
                     message += "! " +
                        Catalog.getCatalog().getString(
                           "designer.composition.worksheetEngine.goOnAnyway");
                  }

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

            if(actionRecord != null) {
               actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
               actionRecord.setActionError(ex.getMessage() + ", Target Entry: " +
                  nentry.getDescription());
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
      }

      return null;
   }

   private void changeTableStyle(AssetEntry parent, AssetEntry entry, Principal principal) throws Exception {
      LibManager manager = LibManager.getManager();
      XTableStyle tableStyle = manager.getTableStyle(entry.getName());
      String folder = parent.getProperty("folder");

      if(!assetRepository.checkPermission(principal, ResourceType.TABLE_STYLE, tableStyle.getName(),
         EnumSet.of(ResourceAction.DELETE)))
      {
         throw new MessageException(Catalog.getCatalog().getString(
            "common.deleteAuthority", tableStyle.getName()));
      }

      if(!assetRepository.checkPermission(principal, ResourceType.TABLE_STYLE, tableStyle.getName(),
         EnumSet.of(ResourceAction.WRITE)))
      {
         throw new MessageException(Catalog.getCatalog().getString(
            "common.writeAuthority", tableStyle.getName()));
      }

      // does not have write permission of the folder?
      if(!Tool.equals(folder, entry.getProperty("folder")) &&
         !assetRepository.checkPermission(principal, getTableStyleResourceType(parent),
         Tool.isEmptyString(folder) ? "*" : folder, EnumSet.of(ResourceAction.WRITE)))
      {
         throw new MessageException(Catalog.getCatalog().getString(
            "common.writeAuthority", parent));
      }


      XTableStyle style = tableStyle.clone();
      manager.removeTableStyle(entry.getProperty("styleID"));
      String name = Tool.isEmptyString(folder) ? entry.getName() :
         folder + LibManager.SEPARATOR + entry.getName();
      style.setName(name);
      manager.setTableStyle(style.getID(), style);
      manager.save();
   }

   private void changeTableStyleFolder(AssetEntry parent, AssetEntry entry, Principal principal) {
      LibManager manager = LibManager.getManager();
      String pfolder = parent.getProperty("folder");
      String folder = entry.getProperty("folder");

      if(!assetRepository.checkPermission(principal, getTableStyleResourceType(entry), folder,
         EnumSet.of(ResourceAction.WRITE)))
      {
         throw new MessageException(Catalog.getCatalog().getString(
            "common.writeAuthority", entry));
      }

      // does not have write permission of the folder?
      if(!Tool.equals(pfolder, folder) && !assetRepository.checkPermission(principal, getTableStyleResourceType(parent),
         Tool.isEmptyString(pfolder) ? "*" : pfolder, EnumSet.of(ResourceAction.WRITE)))
      {
         throw new MessageException(Catalog.getCatalog().getString(
            "common.writeAuthority", parent));
      }

      if(!assetRepository.checkPermission(principal, getTableStyleResourceType(entry), folder,
         EnumSet.of(ResourceAction.DELETE)))
      {
         throw new MessageException(Catalog.getCatalog().getString(
            "common.deleteAuthority", entry));
      }

      AssetEventUtil.changeTableStyleFolder(pfolder, folder, manager);
   }

   private ResourceType getTableStyleResourceType(AssetEntry folderEntry) {
      return folderEntry.isRoot() ? ResourceType.TABLE_STYLE_LIBRARY : ResourceType.TABLE_STYLE;
   }

   private String getDependencies(AssetEntry entry) {
      if(entry == null) {
         return null;
      }

      StringBuffer buf = new StringBuffer();
      List<AssetObject> deps = DependencyTransformer.getDependencies(entry.toIdentifier());

      if(deps == null || deps.size() == 0) {
         return null;
      }

      for(int i = 0; i < deps.size(); i++) {
         AssetObject asset = deps.get(i);
         String id = null;

         if(asset instanceof AssetEntry) {
            id = ((AssetEntry) asset).getName();
         }

         if(i > 0) {
            buf.append(",");
         }

         buf.append(id);
      }

      return Catalog.getCatalog().getString("viewer.saveas.dependencies", buf.toString());
   }

   /**
    * Sort entries.
    */
   private static final class EntriesComparator implements Comparator {
      @Override
      public int compare(Object v1, Object v2) {
         int type1 = ((AssetEntry) v1).getType().id();
         int type2 = ((AssetEntry) v2).getType().id();

         return type1 - type2;
      }
   }

   private final AssetRepository assetRepository;
   private final ViewsheetService viewsheetService;
}
