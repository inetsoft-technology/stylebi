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
import inetsoft.report.LibManagerProvider;
import inetsoft.report.composition.RuntimeSheet;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.DependencyTransformer;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.RecycleBin;
import inetsoft.web.RecycleUtils;
import inetsoft.web.composer.model.RemoveAssetEvent;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.wiz.service.GenerateWsService;
import inetsoft.web.wiz.service.WizVisualizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.rmi.RemoteException;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;

@Controller
public class RemoveAssetController {
   /**
    * Creates a new instance of <tt>RemoveAssetController</tt>.
    */
   @Autowired
   public RemoveAssetController(AssetRepository assetRepository,
                                ViewsheetService viewsheetService,
                                SecurityProvider securityProvider,
                                LibManagerProvider libManagerProvider,
                                RecycleBin recycleBin,
                                DependencyHandler dependencyHandler)
   {
      this.assetRepository = assetRepository;
      this.viewsheetService = viewsheetService;
      this.securityProvider = securityProvider;
      this.libManagerProvider = libManagerProvider;
      this.recycleBin = recycleBin;
      this.dependencyHandler = dependencyHandler;
   }

   @PostMapping("api/composer/asset-tree/remove-asset")
   @ResponseBody
   public MessageCommand removeAsset(
      @RequestBody RemoveAssetEvent event, Principal principal) throws Exception
   {
      AssetEntry entry = event.entry();

      // log action
      String actionName = ActionRecord.ACTION_NAME_DELETE;
      String objectName = entry.getDescription();
      String objectType = AssetEventUtil.getObjectType(entry);
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName, objectName,
                                                   objectType, actionTimestamp, ActionRecord.ACTION_STATUS_FAILURE,
                                                   null);

      try {
         assetRepository.checkAssetPermission(principal, entry, ResourceAction.DELETE);

         if(entry.getScope() == AssetRepository.QUERY_SCOPE) {
            return null;
         }

         if(entry.isScript()) {
            LibManager manager = libManagerProvider.getManager(principal);

            checkScriptRemoveable(event, entry, principal);

            manager.removeScript(entry.getName());
            manager.save();
            securityProvider.removePermission(ResourceType.SCRIPT, entry.getName());
            AssetEntry scriptEntry = new AssetEntry(AssetRepository.COMPONENT_SCOPE,
                                                    AssetEntry.Type.SCRIPT, entry.getName(), null);
            dependencyHandler.deleteDependenciesKey(scriptEntry);
         }
         else if(entry.isTableStyle()) {
            LibManager manager = libManagerProvider.getManager(principal);
            String styleID = entry.getProperty("styleID");
            manager.removeTableStyle(styleID);
            manager.save();
            securityProvider.removePermission(ResourceType.TABLE_STYLE, entry.getProperty("styleName"));
            //Because the tableStyle Dependenc is stored by id, the entry needs to be recreated based on the id
            AssetEntry style = new AssetEntry(AssetRepository.COMPONENT_SCOPE,
               AssetEntry.Type.TABLE_STYLE, styleID, null);
            dependencyHandler.deleteDependenciesKey(style);
         }
         else if(entry.isTableStyleFolder()) {
            LibManager manager = libManagerProvider.getManager(principal);
            AssetEventUtil.removeStyleFolder(entry.getProperty("folder"), manager);
            manager.save();
            securityProvider.removePermission(ResourceType.TABLE_STYLE, entry.getProperty("folder"));
         }
         else if(isWizManagedFolder(entry)) {
            // Wiz visualization/chat folders are created directly via
            // assetRepository.addFolder() (see WizVisualizationService#ensureFolder), never
            // through repletRepository.addFolder(), so they are never registered in the
            // RepletRegistry's folder map. The RecycleUtils.moveRepositoryToRecycleBin() path
            // below renames the entry inside the RepletRegistry -- for a folder that isn't
            // registered there, RepletRegistry#changeFolder finds nothing to rename but still
            // returns "true", so the delete silently no-ops while the folder stays in place.
            // Remove it directly from the AssetRepository instead, mirroring
            // WizVisualizationService#deleteVisualizations.
            assetRepository.removeFolder(entry, principal, true);
            removeCompanionWorksheetFolder(entry, principal);
         }
         else if(entry.isRepositoryFolder()) {
            String rpath = entry.getPath();

            if(entry.getScope() == AssetRepository.USER_SCOPE) {
               rpath = Tool.MY_DASHBOARD + "/" + rpath;
            }

            RepositoryEntry rentry = new RepositoryEntry(rpath,
                                                         RepositoryEntry.FOLDER, entry.getUser());

            RecycleUtils.moveRepositoryToRecycleBin(rentry, principal, recycleBin);
         }
         else if(entry.isFolder()) {
            RecycleUtils.moveAssetFolderToRecycleBin(entry, principal, recycleBin, event.confirmed());
         }
         else if(entry.getScope() == AssetRepository.REPORT_SCOPE) {
            assetRepository.removeSheet(entry, principal, event.confirmed());
         }
         else {
            RecycleUtils.moveSheetToRecycleBin(entry, principal, recycleBin, event.confirmed());
         }

         if(actionRecord != null) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
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
            actionRecord.setActionError(ex.getMessage());
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

   @PostMapping("api/composer/asset-tree/remove-asset/check-open-sheets")
   @ResponseBody
   public MessageCommand canRemoveAssets(
      @RequestBody AssetEntry[] entries, Principal principal) throws Exception
   {
      AssetEntry[] validEntries = Arrays.stream(entries)
         .filter((entry) -> entry.getScope() != AssetRepository.QUERY_SCOPE)
         .toArray(AssetEntry[]::new);
      RuntimeSheet[] openSheets = viewsheetService.getRuntimeSheets(null);
      MessageCommand messageCommand = new MessageCommand();
      Catalog catalog = Catalog.getCatalog(principal);

      for(AssetEntry entry : validEntries) {
         boolean openSheetToBeDeleted = Arrays.stream(openSheets).anyMatch((sheet) -> {
            AssetEntry sheetEntry = sheet.getEntry();

            return sheetEntry != null && (entry.equals(sheetEntry) ||
               sheetEntry.getParentPath().contains(entry.getPath()));
         });

         if(openSheetToBeDeleted) {
            messageCommand.setMessage(
               catalog.getString("common.tree.deleteForbidden"));
            messageCommand.setType(MessageCommand.Type.WARNING);
            return messageCommand;
         }
      }

      if(Arrays.stream(validEntries).anyMatch(AssetEntry::isActualFolder)) {
         messageCommand.setMessage(catalog.getString("common.tree.removeFolder"));
      }
      else {
         messageCommand.setMessage(catalog.getString("common.tree.deleteSelected"));
      }

      messageCommand.setType(MessageCommand.Type.CONFIRM);
      return messageCommand;
   }

   /**
    * @return {@code true} if {@code entry} is a repository folder under one of the wiz-managed
    *         roots ({@code VISUALIZATION_ROOT_FOLDER_PATH}, {@code VISUALIZATION_COMPONENTS_FOLDER_PATH})
    *         -- folders created via {@code assetRepository.addFolder()} and never registered in
    *         the RepletRegistry, so they need the AssetRepository-direct removal path above
    *         rather than the RepletRegistry-based recycle-bin path.
    */
   private boolean isWizManagedFolder(AssetEntry entry) {
      if(!entry.isRepositoryFolder() || entry.getPath() == null) {
         return false;
      }

      String path = entry.getPath();

      return path.equals(WizVisualizationService.VISUALIZATION_ROOT_FOLDER_PATH) ||
         path.startsWith(WizVisualizationService.VISUALIZATION_ROOT_FOLDER_PATH + "/") ||
         path.equals(WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH) ||
         path.startsWith(WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/");
   }

   /**
    * Removes the backing worksheet folder for a just-deleted {@code VISUALIZATION_COMPONENTS_FOLDER_PATH}
    * folder, if one exists. Every saved visualization under that root has a parallel worksheet under
    * {@link GenerateWsService#WORKSHEET_COMPONENTS_FOLDER_PATH} (same path suffix) -- mirrors
    * {@code WizVisualizationService#deleteVisualizations}'s identical cleanup, without which deleting a
    * visualization folder through this generic composer path (unlike the wiz-native delete path) would
    * leave its backing worksheet folder orphaned in the repository. A no-op for {@code
    * VISUALIZATION_ROOT_FOLDER_PATH} ("Wiz Chats") folders, which have no such companion.
    */
   private void removeCompanionWorksheetFolder(AssetEntry entry, Principal principal) {
      String path = entry.getPath();

      if(!path.startsWith(WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH)) {
         return;
      }

      String suffix = path.substring(WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH.length());
      String wsFolderPath = GenerateWsService.WORKSHEET_COMPONENTS_FOLDER_PATH + suffix;
      AssetEntry wsFolderEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER, wsFolderPath, null);

      try {
         if(assetRepository.containsEntry(wsFolderEntry)) {
            assetRepository.removeFolder(wsFolderEntry, principal, true);
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to delete worksheet folder (visualization folder deleted): {}", wsFolderPath, e);
      }
   }

   private void checkScriptRemoveable(RemoveAssetEvent event, AssetEntry entry, Principal principal) {
      if(!event.confirmed()) {
         List<Object> aentries = new ArrayList<>();
         AssetEntry scriptEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCRIPT,
            entry.getPath(), IdentityID.getIdentityIDFromKey(principal.getName()), null);
         List<AssetObject> deps = DependencyTransformer.getDependencies(scriptEntry.toIdentifier());

         if(deps.size() > 0) {
            for(int i = 0; i < deps.size(); i++) {
               AssetObject obj = deps.get(i);

               if(obj instanceof AssetEntry) {
                  AssetEntry entry0 = (AssetEntry)obj;
                  Collections.addAll(aentries, scriptEntry);
                  DependencyException ex = new DependencyException(entry0);
                  ex.addDependencies(aentries.toArray(new Object[0]));
                  throw ex;
               }
            }
         }
      }
   }

   private final AssetRepository assetRepository;
   private final ViewsheetService viewsheetService;
   private final SecurityProvider securityProvider;
   private final LibManagerProvider libManagerProvider;
   private final RecycleBin recycleBin;
   private final DependencyHandler dependencyHandler;
   private static final Logger LOG = LoggerFactory.getLogger(RemoveAssetController.class);
}
