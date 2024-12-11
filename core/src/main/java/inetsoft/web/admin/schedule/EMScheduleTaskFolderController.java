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
package inetsoft.web.admin.schedule;

import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.Catalog;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeModel;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeNode;
import inetsoft.web.admin.schedule.model.*;
import inetsoft.web.portal.data.CheckDuplicateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.*;

import static inetsoft.sree.RepositoryEntry.FOLDER;

@RestController
public class EMScheduleTaskFolderController {
   @Autowired
   public EMScheduleTaskFolderController(ScheduleTaskFolderService scheduleTaskFolderService,
                                         ScheduleService scheduleService)
   {
      this.scheduleTaskFolderService = scheduleTaskFolderService;
      this.scheduleService = scheduleService;
   }

   @PostMapping("/api/em/schedule/add/checkDuplicate")
   public boolean checkAddFolderDuplicate(@RequestBody NewTaskFolderRequest req,
                                          Principal principal)
      throws Exception
   {
      ContentRepositoryTreeNode parentInfo = req.getParent();
      String path = parentInfo.path();
      String folderName = "".equals(path) || "/".equals(path) ? "" : path + "/";
      folderName += req.getFolderName();

      AssetEntry parentEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, path, null);

      return scheduleTaskFolderService.checkAddDuplicate(parentEntry, folderName,
         AssetRepository.GLOBAL_SCOPE, principal).isDuplicate();
   }

   @PostMapping("/api/em/schedule/folder/add")
   public void addFolder(@RequestBody NewTaskFolderRequest req, Principal principal)
           throws Exception
   {
      ContentRepositoryTreeNode parentInfo = req.getParent();
      String path = parentInfo.path();
      String folderName = "".equals(path) || "/".equals(path) ? "" : path + "/";
      folderName += req.getFolderName();

      AssetEntry parentEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, path, null);

      scheduleTaskFolderService.addFolder(
         parentEntry, folderName, path, AssetRepository.GLOBAL_SCOPE, principal);
   }

   @GetMapping("/api/em/schedule/folder/checkRootPermission")
   public boolean checkRootPermission(Principal principal)
      throws Exception
   {
      AssetEntry rootEntry = scheduleTaskFolderService.getRootEntry();

      return scheduleTaskFolderService.checkFolderPermission(
         rootEntry.getPath(), principal, ResourceAction.READ);
   }


   @GetMapping("/api/em/schedule/folder/get")
   public ContentRepositoryTreeModel getFolder(Principal principal)
           throws Exception
   {
      AssetEntry rootEntry = scheduleTaskFolderService.getRootEntry();
      List<ContentRepositoryTreeNode> children = getSubTree(rootEntry, principal);

      ContentRepositoryTreeNode root = new ContentRepositoryTreeNode.Builder()
              .label(Catalog.getCatalog().getString("Tasks"))
              .path(rootEntry.getPath())
              .type(FOLDER)
              .addAllChildren(children)
              .properties(scheduleTaskFolderService.getScheduleFolderPermission(rootEntry.getPath(), principal))
              .build();

      List<ContentRepositoryTreeNode> nodes = new ArrayList<>();
      nodes.add(root);
      return new ContentRepositoryTreeModel(nodes);
   }

   private List<ContentRepositoryTreeNode> getSubTree(AssetEntry entry, Principal principal)
      throws Exception
   {
      if(!entry.isScheduleTaskFolder()) {
         return null;
      }

      AssetFolder folder = scheduleTaskFolderService.getTaskFolder(entry.toIdentifier());
      List<ContentRepositoryTreeNode> nodes = new ArrayList<>();

      if(folder == null) {
         return nodes;
      }

      for(AssetEntry value : folder.getEntries()) {
         if(!value.isScheduleTaskFolder() || !scheduleTaskFolderService.checkFolderPermission(
            value.getPath(), principal, ResourceAction.READ))
         {
            continue;
         }

         List<ContentRepositoryTreeNode> children = getSubTree(value, principal);
         final ContentRepositoryTreeNode node = new ContentRepositoryTreeNode.Builder()
            .label(scheduleTaskFolderService.getTaskFolderLabel(value))
            .path(value.getPath())
            .type(FOLDER)
            .addAllChildren(children)
            .properties(scheduleTaskFolderService.getScheduleFolderPermission(value.getPath(), principal))
            .build();

         nodes.add(node);
      }

      nodes.sort(Comparator.comparing(ContentRepositoryTreeNode::label));

      return nodes;
   }

   private boolean subtreeHasTasks(AssetEntry entry, Principal principal) throws Exception {
      ScheduleTaskList taskListModel = scheduleService
         .getScheduleTaskList("", "", entry, principal);

      return taskListModel.tasks().stream()
         .anyMatch(t -> principal.getName().equals(t.owner().convertToKey()) ||
            scheduleService.isGroupShare(t, principal)
         );
   }

   @PostMapping("/api/em/schedule/check-folder")
   public boolean checkDuplicateFolderPath(@RequestBody MoveTaskFolderRequest request) throws Exception {
      String[] folders = request.getFolders();
      ContentRepositoryTreeNode target = request.getTarget();
      String pathTo = target.path();
      AssetEntry assetEntry
         = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, pathTo, null);
      return scheduleTaskFolderService.checkDuplicateFolderPath(folders,assetEntry);
   }

   @PostMapping("/api/em/schedule/move-folder")
   public void moveFolder(@RequestBody MoveTaskFolderRequest request, Principal principal)
           throws Exception
   {
      ScheduleTaskModel[] taskModels = request.getTasks();
      String[] folders = request.getFolders();
      ContentRepositoryTreeNode target = request.getTarget();
      String pathTo = target.path();
      AssetEntry targetEntry
         = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, pathTo, null);
      scheduleTaskFolderService.moveScheduleItems(taskModels, folders, targetEntry, principal);
   }

   @PostMapping("api/em/schedule/rename/checkDuplicate")
   public CheckDuplicateResponse checkRenameItemDuplicate(
      @RequestBody EditTaskFolderDialogModel model)
      throws Exception
   {
      return scheduleTaskFolderService.checkRenameDuplicate(model);
   }

   @PostMapping("/api/em/schedule/rename-folder")
   public void renameFolder(@RequestBody EditTaskFolderDialogModel model,
                            Principal principal)
           throws Exception
   {
      scheduleTaskFolderService.renameFolder(model, principal);
   }

   @PostMapping("/api/em/schedule/folder/editModel")
   public EditTaskFolderDialogModel getFolderEditModel(@RequestParam("folderPath") String folderPath,
                                                       Principal principal)
      throws Exception
   {
      return scheduleTaskFolderService.getFolderEditModel(folderPath, principal);
   }

   @PostMapping("/api/em/schedule/folder/check-dependency")
   public TaskListModel checkScheduledTaskDependency(
      @RequestBody TaskListModel model, Principal principal) throws Exception
   {
      return this.scheduleService.checkScheduleFolderDependency(model, principal);
   }

   @PostMapping("/api/em/schedule/folder/remove")
   public void removeScheduledTasks(
      @RequestBody TaskListModel model,
      Principal principal) throws Exception
   {
      this.scheduleService.removeScheduleFolders(model, principal);
   }

   private final ScheduleTaskFolderService scheduleTaskFolderService;
   private final ScheduleService scheduleService;

   private static final Logger LOG = LoggerFactory.getLogger(ScheduleTaskChangeController.class);

}
