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
package inetsoft.web.portal.controller;

import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.admin.schedule.*;
import inetsoft.web.admin.schedule.model.*;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.portal.data.*;
import inetsoft.web.portal.model.NewTaskFolderEvent;
import inetsoft.web.portal.model.PortalMoveTaskFolderRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

@RestController
public class ScheduleTaskFolderController {
   @Autowired
   public ScheduleTaskFolderController(ScheduleTaskFolderService scheduleTaskFolderService,
                                       ScheduleService scheduleService)
   {
      this.scheduleTaskFolderService = scheduleTaskFolderService;
      this.scheduleService = scheduleService;
   }

   @PostMapping("api/portal/schedule/add/checkDuplicate")
   public CheckDuplicateResponse checkAddItemDuplicate(@RequestBody NewTaskFolderEvent req,
                                                       Principal principal)
      throws Exception
   {
      String path = req.getParent().getPath();
      String folderName = "".equals(path) || "/".equals(path) ?
         "" : path + "/";
      folderName += req.getFolderName();

      return scheduleTaskFolderService.checkAddDuplicate(req.getParent(), folderName,
         AssetRepository.GLOBAL_SCOPE, principal);
   }

   @PostMapping("/api/portal/schedule/folder/add")
   public void addFolder(@RequestBody NewTaskFolderEvent req, Principal principal)
      throws Exception
   {
      String path = req.getParent().getPath();
      String folderName = "".equals(path) || "/".equals(path) ?
         "" : path + "/";
      folderName += req.getFolderName();

      scheduleTaskFolderService.addFolder(
         req.getParent(), folderName, path, AssetRepository.GLOBAL_SCOPE, principal);
   }

   @GetMapping("/api/portal/schedule/folder/checkRootPermission")
   public boolean checkRootPermission(Principal principal)
      throws Exception
   {
      AssetEntry rootEntry = scheduleTaskFolderService.getRootEntry();

      return scheduleTaskFolderService.checkFolderPermission(
         rootEntry.getPath(), principal, ResourceAction.READ);
   }

   @GetMapping("/api/portal/schedule/tree")
   public TreeNodeModel getNewTaskDialogModel(Principal principal)
      throws Exception
   {
      AssetEntry rootEntry = scheduleTaskFolderService.getRootEntry();
      List<TreeNodeModel> children = getSubTree(rootEntry, principal);
      boolean readPermission = scheduleTaskFolderService.checkFolderPermission(
         rootEntry.getPath(), principal, ResourceAction.READ);
      rootEntry.setProperty(ScheduleFolderTreeAction.READ.name(), readPermission + "");
      TreeNodeModel rootNode = TreeNodeModel.builder()
         .label(Catalog.getCatalog().getString("Tasks"))
         .data(rootEntry)
         .children(children)
         .expanded(true)
         .build();

      return rootNode;
   }

   private List<TreeNodeModel> getSubTree(AssetEntry entry, Principal principal) throws Exception
   {
      if(!entry.isScheduleTaskFolder()) {
         return null;
      }

      AssetFolder folder = scheduleTaskFolderService.getTaskFolder(entry.toIdentifier());
      List<TreeNodeModel> nodes = new ArrayList<>();

      if(folder == null) {
         return null;
      }

      scheduleTaskFolderService.setScheduleFolderPermission(entry, principal);

      for(AssetEntry value : folder.getEntries()) {
         if(!value.isScheduleTaskFolder()) {
            continue;
         }

         boolean readPermission = scheduleTaskFolderService.checkFolderPermission(
            value.getPath(), principal, ResourceAction.READ);

         if(!readPermission) {
            continue;
         }

         AssetEntry assetEntry = (AssetEntry) Tool.clone(value);
         List<TreeNodeModel> children = getSubTree(assetEntry, principal);
         TreeNodeModel node = TreeNodeModel.builder()
            .label(scheduleTaskFolderService.getTaskFolderLabel(value))
            .data(assetEntry)
            .children(children)
            .dragName(assetEntry.getType().name().toLowerCase())
            .build();

         nodes.add(node);
      }

      nodes.sort(Comparator.comparing(TreeNodeModel::label));

      return nodes;
   }

   private boolean subtreeHasTasks(AssetEntry entry, Principal principal) throws Exception {
      ScheduleTaskList taskListModel = scheduleService
         .getScheduleTaskList("", "", entry, principal);

      return taskListModel.tasks().stream()
         .anyMatch(t -> principal.getName().equals(t.owner()) ||
            scheduleService.isGroupShare(t, principal)
         );
   }

   @GetMapping("api/portal/schedule/task-folder-browser")
   public TaskFolderBrowserModel getDatasourcesBrowser(
      @RequestParam(value="path", required = false) String path,
      @RequestParam(value="home", required = false) boolean home,
      Principal principal)
      throws Exception
   {
      return scheduleTaskFolderService.getBrowserFolder(path, home, principal);
   }

   @PostMapping("api/portal/schedule/move/checkDuplicate")
   public CheckDuplicateResponse checkItemsDuplicate(
      @RequestBody CheckTaskDuplicateRequest request)
      throws Exception
   {
      AssetEntry parent
         = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, request.path(), null);
      return scheduleTaskFolderService.checkItemsDuplicate(request.folders(), parent);
   }

   @PostMapping("api/portal/schedule/rename/checkDuplicate")
   public CheckDuplicateResponse checkRenameItemDuplicate(
      @RequestBody EditTaskFolderDialogModel model)
      throws Exception
   {
      return scheduleTaskFolderService.checkRenameDuplicate(model);
   }

   @PostMapping("api/portal/schedule/move-items")
   public void moveFolder(@RequestBody PortalMoveTaskFolderRequest request, Principal principal)
      throws Exception
   {
      ScheduleTaskModel[] taskModels = request.getTasks();
      String[] folders = request.getFolders();
      AssetEntry targetEntry = request.getTarget();

      scheduleTaskFolderService.moveScheduleItems(taskModels, folders, targetEntry, principal);
   }

   @PostMapping("/api/portal/schedule/rename-folder")
   public String renameFolder(@RequestBody EditTaskFolderDialogModel model,
                              Principal principal)
      throws Exception
   {
      AssetEntry assetEntry = scheduleTaskFolderService.renameFolder(model, principal);

      return assetEntry != null ? assetEntry.getPath() : null;
   }

   @PostMapping("/api/portal/schedule/folder/editModel")
   public EditTaskFolderDialogModel getFolderEditModel(
      @RequestParam("folderPath") String folderPath, Principal principal)
      throws Exception
   {
      return scheduleTaskFolderService.getFolderEditModel(folderPath, principal);
   }

   private final ScheduleTaskFolderService scheduleTaskFolderService;
   private final ScheduleService scheduleService;

   private static final Logger LOG = LoggerFactory.getLogger(ScheduleTaskChangeController.class);

}
