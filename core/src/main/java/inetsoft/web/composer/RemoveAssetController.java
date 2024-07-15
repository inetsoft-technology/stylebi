/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.composer;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.LibManager;
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
                                SecurityProvider securityProvider)
   {
      this.assetRepository = assetRepository;
      this.viewsheetService = viewsheetService;
      this.securityProvider = securityProvider;
   }

   @PostMapping("api/composer/asset-tree/remove-asset")
   @ResponseBody
   public MessageCommand removeAsset(
      @RequestBody RemoveAssetEvent event, Principal principal) throws Exception
   {
      AssetEntry entry = event.entry();
      RecycleBin recycleBin = RecycleBin.getRecycleBin();

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
            LibManager manager = LibManager.getManager();

            checkScriptRemoveable(event, entry, principal);

            manager.removeScript(entry.getName());
            manager.save();
            securityProvider.removePermission(ResourceType.SCRIPT, entry.getName());
            AssetEntry scriptEntry = new AssetEntry(AssetRepository.COMPONENT_SCOPE,
                                                    AssetEntry.Type.SCRIPT, entry.getName(), null);
            DependencyHandler.getInstance().deleteDependenciesKey(scriptEntry);
         }
         else if(entry.isTableStyle()) {
            LibManager manager = LibManager.getManager();
            String styleID = entry.getProperty("styleID");
            manager.removeTableStyle(styleID);
            manager.save();
            securityProvider.removePermission(ResourceType.TABLE_STYLE, entry.getProperty("styleName"));
            //Because the tableStyle Dependenc is stored by id, the entry needs to be recreated based on the id
            AssetEntry style = new AssetEntry(AssetRepository.COMPONENT_SCOPE,
               AssetEntry.Type.TABLE_STYLE, styleID, null);
            DependencyHandler.getInstance().deleteDependenciesKey(style);
         }
         else if(entry.isTableStyleFolder()) {
            LibManager manager = LibManager.getManager();
            AssetEventUtil.removeStyleFolder(entry.getProperty("folder"), manager);
            manager.save();
            securityProvider.removePermission(ResourceType.TABLE_STYLE, entry.getProperty("folder"));
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
}
