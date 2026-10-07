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

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.Catalog;
import inetsoft.util.InvalidOrgException;
import inetsoft.util.MessageException;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeModel;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeNode;
import inetsoft.web.admin.schedule.model.*;
import inetsoft.web.portal.data.CheckDuplicateResponse;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
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
                                         ScheduleService scheduleService,
                                         ScheduleTaskService scheduleTaskService,
                                         SecurityEngine securityEngine,
                                         ScheduleManager scheduleManager)
   {
      this.scheduleTaskFolderService = scheduleTaskFolderService;
      this.scheduleService = scheduleService;
      this.scheduleTaskService = scheduleTaskService;
      this.securityEngine = securityEngine;
      this.scheduleManager = scheduleManager;
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/tasks",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/schedule/add/checkDuplicate")
   public boolean checkAddFolderDuplicate(@RequestBody NewTaskFolderRequest req,
                                          Principal principal)
      throws Exception
   {
      ContentRepositoryTreeNode parentInfo = req.getParent();
      String path = parentInfo.path();
      String folderName = "".equals(path) || "/".equals(path) ? "" : path + "/";
      folderName += req.getFolderName();

      // Bug #77811, the same WRITE check addFolder makes, so the endpoint doesn't tell a user
      // who can't add to the folder which schedule folders exist
      if(!scheduleTaskFolderService.checkFolderPermission(path, principal, ResourceAction.WRITE)) {
         throw new SecurityException(
            "Unauthorized access to resource \"" + path + "\" by user " + principal);
      }

      AssetEntry parentEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, path, null);

      return scheduleTaskFolderService.checkAddDuplicate(parentEntry, folderName,
         AssetRepository.GLOBAL_SCOPE, principal).isDuplicate();
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/tasks",
         actions = ResourceAction.ACCESS
      )
   )
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

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/tasks",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/schedule/folder/checkRootPermission")
   public boolean checkRootPermission(Principal principal)
      throws Exception
   {
      AssetEntry rootEntry = scheduleTaskFolderService.getRootEntry();

      return scheduleTaskFolderService.checkFolderPermission(
         rootEntry.getPath(), principal, ResourceAction.READ);
   }


   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/tasks",
         actions = ResourceAction.ACCESS
      )
   )
   @GetMapping("/api/em/schedule/folder/get")
   public ContentRepositoryTreeModel getFolder(Principal principal)
           throws Exception
   {
      String currOrgID = OrganizationManager.getInstance().getCurrentOrgID(principal);

      if(securityEngine.getSecurityProvider().getOrganization(currOrgID) == null) {
         throw new InvalidOrgException(Catalog.getCatalog().getString("em.security.invalidOrganizationPassed"));
      }

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

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/tasks",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/schedule/check-folder")
   public boolean checkDuplicateFolderPath(@RequestBody MoveTaskFolderRequest request,
                                           Principal principal)
      throws Exception
   {
      String[] folders = request.getFolders();
      ContentRepositoryTreeNode target = request.getTarget();
      String pathTo = target.path();

      // Bug #77812, without the WRITE on the target that move-folder checks first, the answer
      // doesn't depend on which folders exist. The move that follows is refused with its message.
      if(!scheduleTaskFolderService.checkFolderPermission(pathTo, principal, ResourceAction.WRITE)) {
         return false;
      }

      AssetEntry assetEntry
         = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, pathTo, null);
      return scheduleTaskFolderService.checkDuplicateFolderPath(folders,assetEntry);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/tasks",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/schedule/move-folder")
   public void moveFolder(@RequestBody MoveTaskFolderRequest request, Principal principal)
           throws Exception
   {
      ScheduleTaskModel[] taskModels = request.getTasks();
      if(taskModels != null) {
         for(ScheduleTaskModel taskModel : taskModels) {
            // Bug #77503, check the stored task that the move changes, which is resolved from
            // the owner and name of the model, not the task stored under the bare model name
            ScheduleTask task = scheduleTaskFolderService.getMovableTask(taskModel);

            // the move skips the task too
            if(task == null) {
               continue;
            }

            if(!(securityEngine.checkPermission(principal,
               ResourceType.SCHEDULE_TASK, task.getTaskId(), ResourceAction.WRITE) ||
               scheduleTaskService.canDeleteTask(task, principal)))
            {
               // Bug #77813, refuse the whole move with an error the UI shows, as the portal does,
               // instead of answering 200 as if the move had worked
               throw new MessageException(Catalog.getCatalog().getString(
                  "common.writeAuthority", task.getName()));
            }
         }
      }

      String[] folders = request.getFolders();
      ContentRepositoryTreeNode target = request.getTarget();
      String pathTo = target.path();
      AssetEntry targetEntry
         = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, pathTo, null);
      scheduleTaskFolderService.moveScheduleItems(taskModels, folders, targetEntry, principal);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/tasks",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("api/em/schedule/rename/checkDuplicate")
   public CheckDuplicateResponse checkRenameItemDuplicate(
      @RequestBody EditTaskFolderDialogModel model, Principal principal)
      throws Exception
   {
      // Bug #77812, without the DELETE and WRITE on the folder that rename-folder checks first,
      // the answer doesn't depend on which folders exist. It isn't refused, because New Folder
      // asks this with a made-up path in the parent before add/checkDuplicate.
      if(model != null &&
         (!scheduleTaskFolderService.checkFolderPermission(
            model.oldPath(), principal, ResourceAction.DELETE) ||
          !scheduleTaskFolderService.checkFolderPermission(
             model.oldPath(), principal, ResourceAction.WRITE)))
      {
         return new CheckDuplicateResponse(false);
      }

      return scheduleTaskFolderService.checkRenameDuplicate(model);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/tasks",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/schedule/rename-folder")
   public void renameFolder(@RequestBody EditTaskFolderDialogModel model,
                            Principal principal)
           throws Exception
   {
      scheduleTaskFolderService.renameFolder(model, principal);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/tasks",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/schedule/folder/editModel")
   public EditTaskFolderDialogModel getFolderEditModel(@RequestParam("folderPath") String folderPath,
                                                       Principal principal)
      throws Exception
   {
      return scheduleTaskFolderService.getFolderEditModel(folderPath, principal);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/tasks",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/schedule/folder/check-dependency")
   public TaskListModel checkScheduledTaskDependency(
      @RequestBody TaskListModel model, Principal principal) throws Exception
   {
      return this.scheduleService.checkScheduleFolderDependency(model, principal);
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "settings/schedule/tasks",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/schedule/folder/remove")
   public void removeScheduledTasks(
      @RequestBody TaskListModel model,
      Principal principal) throws Exception
   {
      this.scheduleService.removeScheduleFolders(model, principal);
   }

   private final ScheduleTaskFolderService scheduleTaskFolderService;
   private final SecurityEngine securityEngine;
   private final ScheduleService scheduleService;

   private final ScheduleTaskService scheduleTaskService;
   private final ScheduleManager scheduleManager;

   private static final Logger LOG = LoggerFactory.getLogger(ScheduleTaskChangeController.class);

}
