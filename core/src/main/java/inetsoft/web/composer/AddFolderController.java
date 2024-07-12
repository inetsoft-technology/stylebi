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
package inetsoft.web.composer;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.LibManager;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.sree.RepletRepository;
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.util.log.LogManager;
import inetsoft.web.composer.model.AddFolderEvent;
import inetsoft.web.composer.tablestyle.service.TableStyleService;
import inetsoft.web.viewsheet.command.MessageCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.sql.Timestamp;

@Controller
public class AddFolderController {
   @RequestMapping(
      value = "api/composer/asset-tree/add-folder",
      method = RequestMethod.POST)
   @ResponseBody
   public MessageCommand addFolder(
      @RequestBody AddFolderEvent event, Principal principal) throws Exception
   {
      Catalog catalog = Catalog.getCatalog(principal);
      AssetEntry parent = event.parent();
      String name = event.name();

      if(parent == null || !parent.isFolder()) {
         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setMessage(catalog.getString("common.invalidFolder"));
         messageCommand.setType(MessageCommand.Type.ERROR);
         return messageCommand;
      }

      name = SUtil.removeControlChars(name);
      String path = parent.isRoot() ? name : parent.getPath() + "/" + name;
      AssetEntry.Type type = parent.isRepositoryFolder() || parent.isTableStyleFolder() ?
         parent.getType() : AssetEntry.Type.FOLDER;
      AssetEntry entry = new AssetEntry(parent.getScope(), type,
                                        path, parent.getUser());


      String[] paths = Tool.split(path, '/');

      if(isViewsheetEntry(entry) && Tool.contains(paths, Tool.MY_DASHBOARD)) {
         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setMessage(Catalog.getCatalog().getString("viewer.viewsheet.nameReserved"));
         messageCommand.setType(MessageCommand.Type.ERROR);
         return messageCommand;
      }

      entry.copyProperties(parent);

      // log add folder action
      String actionName = ActionRecord.ACTION_NAME_CREATE;
      String objectName = entry.getDescription();
      String objectType = AssetEventUtil.getObjectType(entry);
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName, objectName,
                                      objectType, actionTimestamp,
                                      ActionRecord.ACTION_STATUS_FAILURE, null);

      try{
         // could throw MessageException(Write access denied)
         assetRepository.checkAssetPermission(principal, parent, ResourceAction.WRITE);
         LibManager manager = LibManager.getManager();
         boolean isStyleDuplicate = false;
         String pname = parent.getProperty("folder");

         if(entry.isTableStyleFolder() && pname == null) {
            pname = entry.getProperty("prefix");
         }

         if(entry.isTableStyleFolder()) {
            String[] folders = manager.getTableStyleFolders(pname);
            String folderName = !entry.isTableStyleSubFolder() ?
               name : pname + LibManager.SEPARATOR + name;

            if(tableStyleService.containsTableStyle(folderName)) {
               MessageCommand messageCommand = new MessageCommand();
               messageCommand.setMessage(catalog.getString("Duplicate Name"));
               messageCommand.setType(MessageCommand.Type.WARNING);
               return messageCommand;
            }

            for(String folder: folders) {
               if(Tool.equals(folder, folderName)) {
                  isStyleDuplicate = true;
                  break;
               }
            }
         }

         if(viewsheetService.isDuplicatedEntry(assetRepository, entry) || isStyleDuplicate) {
            MessageCommand messageCommand = new MessageCommand();
            messageCommand.setMessage(catalog.getString("Duplicate Name"));
            messageCommand.setType(MessageCommand.Type.WARNING);
            return messageCommand;
         }

         if(entry.isTableStyleFolder()) {
            String folderName = !entry.isTableStyleSubFolder() ?
               name : pname + LibManager.SEPARATOR + name;
            manager.addTableStyleFolder(folderName);
            manager.save();
         }
         else if(parent.isRepositoryFolder()) {
            String rpath = parent.getPath();

            if(parent.getScope() == AssetRepository.USER_SCOPE) {
               rpath = parent.isRoot() ? Tool.MY_DASHBOARD : Tool.MY_DASHBOARD +
                  "/" + rpath;
            }

            RepositoryEntry rentry = new RepositoryEntry(rpath,
                                                         RepositoryEntry.FOLDER, parent.getUser());
            rentry.setAssetEntry(entry);
            repletRepository.addFolder(rentry, name, null, null, principal);
         }
         else {
            assetRepository.addFolder(entry, principal);
         }

         if(actionRecord != null) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
         }
      }
      catch(Exception ex) {
         if(ex instanceof ConfirmException) {
            actionRecord = null;
         }

         if(actionRecord != null) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError(ex.getMessage());
         }

         if(ex instanceof java.lang.SecurityException ||
            ex.getCause() instanceof java.lang.SecurityException)
         {
            if(LOG.isDebugEnabled()) {
               LOG.warn("Failed to create folder: {}", entry.getPath(), ex);
            }
            else {
               LOG.warn("Failed to create folder: {}, reason: {}", entry.getPath(), ex.getMessage());
            }
         }
         else if(ex instanceof MessageException) {
            MessageException messageException = (MessageException) ex;
            Throwable throwable = messageException.isDumpStack() || LOG.isDebugEnabled() ? ex : null;
            LogManager.getInstance().logException(
               LOG, messageException.getLogLevel(), ex.getMessage(), throwable);
         }
         else {
            LOG.error("Failed to create folder: {}", entry.getPath(), ex);
         }

         MessageCommand messageCommand = new MessageCommand();
         messageCommand.setMessage(ex.getMessage());
         messageCommand.setType(MessageCommand.Type.ERROR);
         return messageCommand;
      }
      finally {
         if(actionRecord != null) {
            Audit.getInstance().auditAction(actionRecord, principal);
         }
      }

      return null;
   }

   private boolean isViewsheetEntry(AssetEntry entry) {
      return entry.getType() == AssetEntry.Type.VIEWSHEET ||
         entry.getType() == AssetEntry.Type.REPOSITORY_FOLDER ||
         entry.getType() == AssetEntry.Type.VIEWSHEET_SNAPSHOT;
   }

   @Autowired
   public void setAssetRepository(AssetRepository assetRepository) {
      this.assetRepository = assetRepository;
   }

   @Autowired
   public void setViewsheetService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @Autowired
   public void setRepletRepository(RepletRepository repletRepository) {
      this.repletRepository = repletRepository;
   }

   @Autowired
   public void setTableStyleService(TableStyleService tableStyleService) {
      this.tableStyleService = tableStyleService;
   }

   private AssetRepository assetRepository;
   private RepletRepository repletRepository;
   private ViewsheetService viewsheetService;
   private TableStyleService tableStyleService;
   private static final Logger LOG =
      LoggerFactory.getLogger(AddFolderController.class);
}
