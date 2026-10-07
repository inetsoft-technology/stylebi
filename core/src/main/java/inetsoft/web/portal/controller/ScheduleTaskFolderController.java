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

import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.web.admin.schedule.*;
import inetsoft.web.admin.schedule.model.*;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.portal.data.*;
import inetsoft.web.portal.model.NewTaskFolderEvent;
import inetsoft.web.portal.model.PortalMoveTaskFolderRequest;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
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

   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
   @PostMapping("api/portal/schedule/add/checkDuplicate")
   public CheckDuplicateResponse checkAddItemDuplicate(@RequestBody NewTaskFolderEvent req,
                                                       Principal principal)
      throws Exception
   {
      String path = req.getParent().getPath();
      String folderName = "".equals(path) || "/".equals(path) ?
         "" : path + "/";
      folderName += req.getFolderName();

      // Bug #77523, the same WRITE check addFolder makes, so the endpoint doesn't tell a user
      // who can't add to the folder which schedule folders exist
      if(!scheduleTaskFolderService.checkFolderPermission(path, principal, ResourceAction.WRITE)) {
         throw new SecurityException(
            "Unauthorized access to resource \"" + path + "\" by user " + principal);
      }

      return scheduleTaskFolderService.checkAddDuplicate(getParentFolderEntry(path), folderName,
         AssetRepository.GLOBAL_SCOPE, principal);
   }

   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
   @PostMapping("/api/portal/schedule/folder/add")
   public void addFolder(@RequestBody NewTaskFolderEvent req, Principal principal)
      throws Exception
   {
      String path = req.getParent().getPath();
      String folderName = "".equals(path) || "/".equals(path) ?
         "" : path + "/";
      folderName += req.getFolderName();

      scheduleTaskFolderService.addFolder(
         getParentFolderEntry(path), folderName, path, AssetRepository.GLOBAL_SCOPE, principal);
   }

   /**
    * Bug #77523, only the path of the client's parent entry is used. The scope, type, user and
    * organization are the server's, as in the EM controller, so the parent written to is always
    * the schedule task folder the permission was checked on.
    */
   private AssetEntry getParentFolderEntry(String path) {
      return new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, path, null);
   }

   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
   @GetMapping("/api/portal/schedule/folder/checkRootPermission")
   public boolean checkRootPermission(Principal principal)
      throws Exception
   {
      AssetEntry rootEntry = scheduleTaskFolderService.getRootEntry();

      return scheduleTaskFolderService.checkFolderPermission(
         rootEntry.getPath(), principal, ResourceAction.READ);
   }

   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
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
         .anyMatch(t -> principal.getName().equals(t.owner().convertToKey()) ||
            scheduleService.isGroupShare(t, principal)
         );
   }

   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
   @GetMapping("api/portal/schedule/task-folder-browser")
   public TaskFolderBrowserModel getDatasourcesBrowser(
      @RequestParam(value="path", required = false) String path,
      @RequestParam(value="home", required = false) boolean home,
      Principal principal)
      throws Exception
   {
      return scheduleTaskFolderService.getBrowserFolder(path, home, principal);
   }

   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
   @PostMapping("api/portal/schedule/move/checkDuplicate")
   public CheckDuplicateResponse checkItemsDuplicate(
      @RequestBody CheckTaskDuplicateRequest request, Principal principal)
      throws Exception
   {
      // Bug #77812, without the WRITE on the target that move-items checks first, the answer
      // doesn't depend on which folders exist. The move that follows is refused with its message.
      if(!scheduleTaskFolderService.checkFolderPermission(
         request.path(), principal, ResourceAction.WRITE))
      {
         return new CheckDuplicateResponse(false);
      }

      AssetEntry parent
         = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, request.path(), null);
      return scheduleTaskFolderService.checkItemsDuplicate(request.folders(), parent);
   }

   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
   @PostMapping("api/portal/schedule/rename/checkDuplicate")
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

   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
   @PostMapping("api/portal/schedule/move-items")
   public void moveFolder(@RequestBody PortalMoveTaskFolderRequest request, Principal principal)
      throws Exception
   {
      ScheduleTaskModel[] taskModels = request.getTasks();
      String[] folders = request.getFolders();
      AssetEntry target = request.getTarget();
      // Bug #77379, the organization of the client's target entry isn't trusted, the target is
      // a folder of the user's organization, the same as for a folder move and in the EM
      AssetEntry targetEntry = target == null ? null :
         new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                        target.getPath(), null);

      checkMoveTaskPermission(taskModels, principal);
      scheduleTaskFolderService.moveScheduleItems(taskModels, folders, targetEntry, principal);
   }

   /**
    * Bug #77379, checks that the user can move each stored task that is moved, the same way the
    * portal offers the move: the task is the user's own or shared with the user's group, can be
    * deleted by the user and is in a folder the user can read. Tasks that can't be moved at all
    * (e.g. data cycle and internal tasks) are skipped by the move.
    */
   private void checkMoveTaskPermission(ScheduleTaskModel[] taskModels, Principal principal)
      throws Exception
   {
      if(taskModels == null) {
         return;
      }

      IdentityID user = IdentityID.getIdentityIDFromKey(principal.getName());

      for(ScheduleTaskModel taskModel : taskModels) {
         ScheduleTask task = scheduleTaskFolderService.getMovableTask(taskModel);

         if(task == null) {
            continue;
         }

         boolean allowed = (Objects.equals(user, task.getOwner()) ||
            scheduleService.isGroupShareTask(task, principal)) &&
            scheduleService.canDeleteTask(task, principal);

         if(!allowed) {
            throw new MessageException(Catalog.getCatalog().getString(
               "common.writeAuthority", task.getName()));
         }

         String folder = ScheduleTaskFolderService.getTaskFolderPath(task);

         if(!scheduleTaskFolderService.checkFolderPermission(folder, principal, ResourceAction.READ)) {
            throw new MessageException(Catalog.getCatalog().getString(
               "common.readAuthority", folder));
         }
      }
   }

   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
   @PostMapping("/api/portal/schedule/rename-folder")
   public String renameFolder(@RequestBody EditTaskFolderDialogModel model,
                              Principal principal)
      throws Exception
   {
      AssetEntry assetEntry = scheduleTaskFolderService.renameFolder(model, principal);

      return assetEntry != null ? assetEntry.getPath() : null;
   }

   @Secured({
      @RequiredPermission(resourceType = ResourceType.PORTAL_TAB, resource = "Schedule"),
      @RequiredPermission(
         resourceType = ResourceType.SCHEDULER,
         resource = "*",
         actions = ResourceAction.ACCESS
      )
   })
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
