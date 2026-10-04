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

import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.schedule.model.*;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.portal.data.CheckDuplicateResponse;
import inetsoft.web.portal.data.TaskFolderBrowserModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScheduleTaskFolderService {
   @Autowired
   public ScheduleTaskFolderService(ScheduleManager scheduleManager,
                                    SecurityEngine securityEngine,
                                    SecurityProvider securityProvider,
                                    IndexedStorage indexedStorage,
                                    RenameTransformHandler renameTransformHandler)
   {
      this.scheduleManager = scheduleManager;
      this.securityEngine = securityEngine;
      this.securityProvider = securityProvider;
      this.indexedStorage = indexedStorage;
      this.renameTransformHandler = renameTransformHandler;
   }

   public void addFolder(AssetEntry parentEntry, String folderPath, String parentPath,
                         int scope, Principal principal)
      throws Exception
   {
      if(!securityEngine.checkPermission(
         principal, ResourceType.SCHEDULE_TASK_FOLDER, parentPath, ResourceAction.WRITE))
      {
         throw new SecurityException(
            "Unauthorized access to resource \"" + parentEntry.getPath() + "\" by user " +
               principal);
      }

      // Bug #77454, the new folder must be a direct child of the parent the permission was
      // checked on, and it must not replace an existing folder. The duplicate endpoint the UI
      // calls first is only a hint, a request that skips it must not overwrite a folder
      String prefix = "".equals(parentPath) || "/".equals(parentPath) ? "" : parentPath + "/";
      String folderName = folderPath != null && folderPath.startsWith(prefix) ?
         folderPath.substring(prefix.length()) : null;
      checkFolderName(folderName);

      AssetFolder parentfolder =
         (AssetFolder) indexedStorage.getXMLSerializable(parentEntry.toIdentifier(), null);

      // Bug #77523, refuse a missing parent the way checkAddDuplicate does instead of failing
      // with a null pointer
      if(parentfolder == null) {
         throw new FileNotFoundException(parentEntry.getPath());
      }

      AssetEntry folderEntry = new AssetEntry(scope, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                                              folderPath, null);

      if(isStoredFolder(folderEntry)) {
         throw new MessageException(Catalog.getCatalog().getString("common.duplicateName"));
      }

      String actionName = ActionRecord.ACTION_NAME_CREATE;
      String objectType = ActionRecord.OBJECT_TYPE_FOLDER;
      String objectName = "Tasks/" + folderEntry.getPath();
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName, objectName,
                                                   objectType, actionTimestamp,
                                                   ActionRecord.ACTION_STATUS_SUCCESS, null);

      try {
         AssetFolder newFolder = new AssetFolder();
         newFolder.setOwner(parentfolder.getOwner());
         indexedStorage.putXMLSerializable(folderEntry.toIdentifier(), newFolder);
         parentfolder.addEntry(folderEntry);
         indexedStorage.putXMLSerializable(parentEntry.toIdentifier(), parentfolder);
      }
      catch(Exception e) {
         if(e instanceof ConfirmException) {
            actionRecord = null;
         }

         if(actionRecord != null) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError(e.getMessage());
         }

         throw new RuntimeException(e);
      }
      finally {
         if(actionRecord != null) {
            Audit.getInstance().auditAction(actionRecord, principal);
         }
      }
   }

   public void removeFolder(AssetEntry folderEntry)
      throws Exception
   {
      AssetEntry parentEntry = folderEntry.getParent();
      AssetFolder parentFolder =  getTaskFolder(parentEntry.toIdentifier());
      parentFolder.removeEntry(folderEntry);

      indexedStorage.putXMLSerializable(parentEntry.toIdentifier(), parentFolder);
      indexedStorage.remove(folderEntry.toIdentifier());
      securityProvider.removePermission(ResourceType.SCHEDULE_TASK_FOLDER, folderEntry.getPath());
   }

   public boolean checkFolderExists(String path) {
      String parentPath = path.indexOf('/') != - 1 ? path.substring(path.indexOf('/')) : "/";
      AssetEntry parentEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, parentPath, null);

      try {
         AssetFolder parentfolder =
            (AssetFolder) indexedStorage.getXMLSerializable(parentEntry.toIdentifier(), null);
         AssetEntry[] entries = parentfolder.getEntries();

         for(AssetEntry entry : entries) {
            if(entry.getName().equals(path)) {
               return true;
            }
         }
      }
      catch(Exception e) {
      }

      return false;
   }

   /**
    * Check duplicate name before rename.
    * @param editModel check model.
    * @return
    * @throws Exception
    */
   public CheckDuplicateResponse checkRenameDuplicate(EditTaskFolderDialogModel editModel) throws Exception {
      if(editModel == null) {
         return new CheckDuplicateResponse(true);
      }

      AssetEntry oldEntry = getFolderEntry(editModel.oldPath());
      AssetEntry parentEntry = oldEntry.getParent();
      // Bug #77454, the same path renameFolder writes to
      String npath = getRenamedPath(oldEntry.getPath(), editModel.folderName());
      AssetEntry newEntry = new AssetEntry(oldEntry.getScope(), oldEntry.getType(), npath,
         oldEntry.getUser());
      AssetFolder parentFolder = getTaskFolder(parentEntry.toIdentifier());

      if(parentFolder == null) {
         throw new FileNotFoundException(parentEntry.getPath());
      }

      // Bug #77454, the same rule renameFolder refuses with: a rename to the same name changes
      // nothing, any other stored target is a duplicate, listed in the parent or not
      if(Tool.equals(oldEntry.getPath(), npath)) {
         return new CheckDuplicateResponse(false);
      }

      return new CheckDuplicateResponse(isStoredFolder(newEntry));
   }

   public CheckDuplicateResponse checkAddDuplicate(AssetEntry parentEntry, String folderPath, int scope,
                                                   Principal principal)
      throws Exception
   {
      AssetEntry folderEntry = new AssetEntry(scope, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
         folderPath, null);
      AssetFolder parentFolder = getTaskFolder(parentEntry.toIdentifier());

      if(parentFolder == null) {
         throw new FileNotFoundException(parentEntry.getPath());
      }

      // Bug #77454, the same rule addFolder refuses with: a stored folder is a duplicate,
      // listed in the parent or not
      return new CheckDuplicateResponse(isStoredFolder(folderEntry));
   }

   public CheckDuplicateResponse checkItemsDuplicate(String[] folderPaths, AssetEntry entry)
      throws Exception
   {
      // Bug #77454, the same rule moveScheduleItems refuses with, for every moved folder
      return new CheckDuplicateResponse(hasMoveDuplicate(folderPaths, entry));
   }

   /**
    * Check that the folder name you want to move is the same as the original folder name
    * @param folders original folder
    * @param targetEntry targetEntry
    * @return true is same false isn't same
    * @throws Exception
    */
   public boolean checkDuplicateFolderPath(String[] folders, AssetEntry targetEntry)
      throws Exception
   {
      // Bug #77454, the same rule moveScheduleItems refuses with, for every moved folder
      return hasMoveDuplicate(folders, targetEntry);
   }

   public void moveScheduleItems(ScheduleTaskModel[] taskModels, String[] folders, AssetEntry targetEntry, Principal principal)
      throws Exception
   {
      boolean hasTasks = taskModels != null && taskModels.length > 0;
      boolean hasFolders = folders != null && folders.length > 0;

      if(hasFolders || hasTasks) {
         if(!checkFolderPermission(targetEntry.getPath(), principal, ResourceAction.WRITE)) {
            throw new MessageException(Catalog.getCatalog().getString(
               "common.writeAuthority", targetEntry.getPath()));
         }
      }

      checkMoveDuplicates(folders, targetEntry);

      for(String folderPath: folders) {
         // Bug #77454, a folder moved into the folder it's already in isn't rewritten either
         if(isSkippedMove(folderPath, targetEntry)) {
            continue;
         }

         if(!checkFolderPermission(folderPath, principal, ResourceAction.DELETE)) {
            throw new MessageException(Catalog.getCatalog().getString(
               "common.deleteAuthority", folderPath));
         }

         AssetEntry folderEntry = getFolderEntry(folderPath);
         String path = targetEntry.isRoot() ? folderEntry.getName() : targetEntry.getPath() + "/"
            + folderEntry.getName();
         AssetEntry newEntry
            = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, path, null);
         String actionName = ActionRecord.ACTION_NAME_MOVE;
         String actionError = "Target Entry: " + newEntry.getDescription();
         String objectName = "Tasks/" + folderEntry.getPath();
         String objectType = AssetEventUtil.getObjectType(folderEntry);
         Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
         ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName, objectName,
                                                      objectType, actionTimestamp,
                                                      ActionRecord.ACTION_STATUS_SUCCESS,
                                                      actionError);

         try {
            changeFolder(folderEntry, newEntry, principal);
         }
         catch(Exception ex) {
            if(ex instanceof ConfirmException) {
               actionRecord = null;
            }

            if(actionRecord != null) {
               actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
               actionRecord.setActionError(ex.getMessage() + ", Target Entry: " +
                                              newEntry.getDescription());
            }

            throw new RuntimeException(ex);
         }
         finally {
            if(actionRecord != null) {
               Audit.getInstance().auditAction(actionRecord, principal);
            }
         }
      }

      if(taskModels == null) {
         return;
      }

      for(ScheduleTaskModel taskModel : taskModels) {
         // Bug #77379, the client's task model isn't trusted, the stored task decides whether
         // the task can be moved (e.g. not a data cycle or an internal task) and where it is
         // moved from. The task is resolved before any folder is changed, so no entry is added
         // to the target folder for a task that doesn't exist.
         ScheduleTask task = getMovableTask(taskModel);

         if(task == null) {
            continue;
         }

         String taskName = getTaskName(taskModel);
         AssetEntry taskEntry
            = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK,
                             "/" + task.getTaskId(), null);
         AssetEntry parentEntry =
            new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
               getTaskFolderPath(task), null);
         moveTask(targetEntry, parentEntry, taskEntry, principal);
         Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
         ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), ActionRecord.ACTION_NAME_MOVE,
            taskName, ActionRecord.OBJECT_TYPE_TASK, actionTimestamp,
            ActionRecord.ACTION_STATUS_SUCCESS, "Target Entry: " + targetEntry.getPath());
         Audit.getInstance().auditAction(actionRecord, principal);
      }
   }

   /**
    * Bug #77454, refuses a folder move when any of the moved folders would replace an existing
    * folder in the target, or two moved folders would end up at the same path. Checked before
    * any folder is moved, the duplicate endpoints the UI calls first only look at the first
    * folder of the request.
    */
   private void checkMoveDuplicates(String[] folders, AssetEntry targetEntry) throws Exception {
      if(hasMoveDuplicate(folders, targetEntry)) {
         throw new MessageException(Catalog.getCatalog().getString("common.duplicateName"));
      }
   }

   /**
    * Bug #77454, determines if a folder move would replace a stored folder, or would move two
    * folders to the same path. This is the rule of both the move and its duplicate hints.
    */
   private boolean hasMoveDuplicate(String[] folders, AssetEntry targetEntry) throws Exception {
      if(folders == null) {
         return false;
      }

      Set<String> targets = new HashSet<>();

      for(String folderPath : folders) {
         if(isSkippedMove(folderPath, targetEntry)) {
            continue;
         }

         String path = getMovedPath(folderPath, targetEntry);

         if(!targets.add(path) || isStoredFolder(getFolderEntry(path))) {
            return true;
         }
      }

      return false;
   }

   /**
    * Checks if a folder of a move request is not moved: a missing path, a move into the folder
    * itself or one of its subfolders, or a move into the folder it is already in. Bug #77705, a
    * sibling whose name starts with the same text, e.g. "Fx" for "F", is not a subfolder.
    */
   private boolean isSkippedMove(String folderPath, AssetEntry targetEntry) {
      return folderPath == null ||
         DataSourceRegistry.isSameOrDescendantPath(folderPath, targetEntry.getPath()) ||
         Tool.equals(getMovedPath(folderPath, targetEntry), folderPath);
   }

   private String getMovedPath(String folderPath, AssetEntry targetEntry) {
      AssetEntry folderEntry = getFolderEntry(folderPath);
      return targetEntry.isRoot() ? folderEntry.getName() :
         targetEntry.getPath() + "/" + folderEntry.getName();
   }

   /**
    * Bug #77454, the duplicate rule of the folder writes and of their hints: a folder is a
    * duplicate when it is stored, whether its parent lists it or not.
    */
   private boolean isStoredFolder(AssetEntry folderEntry) throws Exception {
      return getTaskFolder(folderEntry.toIdentifier()) != null;
   }

   /**
    * Gets the path a folder is renamed to, the new name in the same parent folder.
    */
   private static String getRenamedPath(String opath, String newName) {
      int index = opath.lastIndexOf('/');
      return index < 0 ? newName : opath.substring(0, index) + "/" + newName;
   }

   /**
    * Bug #77454, refuses a new folder name that is empty or contains the path separator. Such a
    * name would put the folder into another folder than the one the permission was checked on.
    */
   private static void checkFolderName(String name) throws MessageException {
      if(Tool.isEmptyString(name) || name.contains("/")) {
         throw new MessageException(Catalog.getCatalog().getString(
            "common.sree.internal.invalidCharInName"));
      }
   }

   /**
    * Gets the stored task that a task model of a move request refers to, if the task can be
    * moved into another folder. The removable flag of the model is ignored, it's taken from the
    * stored task.
    *
    * @param taskModel the task model sent by the client.
    *
    * @return the stored task or {@code null} if it doesn't exist, has no owner or is a data
    *         cycle, internal or non-removable task.
    */
   public ScheduleTask getMovableTask(ScheduleTaskModel taskModel) {
      if(taskModel == null || taskModel.name() == null) {
         return null;
      }

      ScheduleTask task = scheduleManager.getScheduleTask(getTaskName(taskModel));

      if(task == null || task.getOwner() == null || !task.isRemovable() ||
         task.getType() == ScheduleTask.Type.CYCLE_TASK ||
         task.getType() == ScheduleTask.Type.INTERNAL_TASK ||
         ScheduleManager.isInternalTask(task.getTaskId()))
      {
         return null;
      }

      return task;
   }

   /**
    * Gets the path of the folder a stored task is in.
    */
   public static String getTaskFolderPath(ScheduleTask task) {
      return Tool.isEmptyString(task.getPath()) ? "/" : task.getPath();
   }

   private static String getTaskName(ScheduleTaskModel taskModel) {
      String taskName = taskModel.name();

      if(taskModel.owner() != null && !taskModel.owner().name.equals(XPrincipal.SYSTEM) &&
         !taskModel.name().startsWith(taskModel.owner().name))
      {
         taskName = taskModel.owner().convertToKey() + ":" + taskModel.name();
      }

      return taskName;
   }

   /**
    * Rename a task folder, change the folder name and owner.
    * @param renameModel rename model.
    * @param principal
    * @throws Exception
    * @return new entry
    */
   public AssetEntry renameFolder(EditTaskFolderDialogModel renameModel,
                                  Principal principal)
      throws Exception
   {
      if(renameModel == null) {
         return null;
      }

      AssetEntry folderEntry = getFolderEntry(renameModel.oldPath());

      if(!checkFolderPermission(renameModel.oldPath(), principal, ResourceAction.DELETE) ||
         !checkFolderPermission(renameModel.oldPath(), principal, ResourceAction.WRITE))
      {
         throw new SecurityException(
            "Unauthorized access to resource \"" + folderEntry.getPath() + "\" by user " +
               principal);
      }

      AssetFolder folder = getTaskFolder(folderEntry.toIdentifier());

      if(folder == null) {
         throw new FileNotFoundException(folderEntry.getPath());
      }

      // Bug #77454, the duplicate endpoint the UI calls first is only a hint. A name with a
      // separator would move the folder into another folder without a permission check on it
      String newName = renameModel.folderName();
      checkFolderName(newName);
      String opath = folderEntry.getPath();
      String npath = getRenamedPath(opath, newName);

      // renaming to the same name changes nothing
      if(Tool.equals(opath, npath)) {
         return folderEntry;
      }

      AssetEntry nEntry =
         new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, npath, null);

      if(isStoredFolder(nEntry)) {
         throw new MessageException(Catalog.getCatalog().getString("common.duplicateName"));
      }

      String actionName = ActionRecord.ACTION_NAME_RENAME;
      String objectName = "Tasks/" + folderEntry.getPath();
      String objectType = AssetEventUtil.getObjectType(folderEntry);
      Timestamp actionTimestamp = new Timestamp(System.currentTimeMillis());
      ActionRecord actionRecord = new ActionRecord(SUtil.getUserName(principal), actionName, objectName,
                                                   objectType, actionTimestamp,
                                                   ActionRecord.ACTION_STATUS_SUCCESS, null);

      try {
         changeFolder(folderEntry, nEntry, principal);
         updatePermission(ResourceType.SCHEDULE_TASK_FOLDER, folderEntry.getPath(), nEntry.getPath());
         actionRecord.setActionError("Target Entry: " + nEntry.getDescription());

      }
      catch(Exception ex) {
         if(ex instanceof ConfirmException) {
            actionRecord = null;
         }

         if(actionRecord != null) {
            actionRecord.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            actionRecord.setActionError(ex.getMessage() +
                                           ", Target Entry:: " + nEntry.getDescription());
         }
         throw new RuntimeException(ex);
      }
      finally {
         if(actionRecord != null) {
            Audit.getInstance().auditAction(actionRecord, principal);
         }
      }

      return nEntry;
   }

   public void changeFolder(AssetEntry oentry, AssetEntry nentry, Principal principal)
      throws Exception
   {
      // Bug #77454, a rename or move must not replace an existing folder, its entries and its
      // permission would be overwritten. Checked before anything is written. The subfolders are
      // moved into the new folder's subtree, which is not checked again
      if(!Tool.equals(oentry.toIdentifier(), nentry.toIdentifier()) &&
         isStoredFolder(oentry) &&
         isStoredFolder(nentry))
      {
         throw new MessageException(Catalog.getCatalog().getString("common.duplicateName"));
      }

      changeFolder0(oentry, nentry, principal);
   }

   private void changeFolder0(AssetEntry oentry, AssetEntry nentry, Principal principal)
      throws Exception
   {
      AssetFolder ofolder = getTaskFolder(oentry.toIdentifier());
      AssetFolder nfolder = new AssetFolder();

      if(ofolder == null) {
         return;
      }

      // Bug #77406, a rename or move keeps the owner, the owner sent by the client isn't used
      nfolder.setOwner(ofolder.getOwner());

      String oidentifier = oentry.toIdentifier();
      String nidentifier = nentry.toIdentifier();
      AssetEntry opentry = oentry.getParent();
      AssetFolder opfolder = null;
      String opidentifier = null;
      String npidentifier = null;
      AssetEntry npentry = nentry.getParent();
      AssetFolder npfolder;

      opidentifier = opentry.toIdentifier();
      npidentifier = npentry.toIdentifier();

      npfolder = getTaskFolder(npidentifier);

      if(npfolder != null) {
         npfolder.addEntry(nentry);
         indexedStorage.putXMLSerializable(npidentifier, npfolder);
      }

      AssetEntry[] entries = ofolder.getEntries();
      String opath = oentry.getPath();
      String npath = nentry.getPath();
      int scope = nentry.getScope();
      IdentityID user = nentry.getUser();
      AssetEntry[] nentries = new AssetEntry[entries.length];

      for(int i = 0; i < entries.length; i++) {
         if(!entries[i].isScheduleTaskFolder()) {
            nentries[i] = entries[i];
            continue;
         }

         String path = entries[i].getPath();
         path = oentry.isRoot() ? path : path.substring(opath.length() + 1);
         path = nentry.isRoot() ? path : npath + "/" + path;
         AssetEntry.Type type = entries[i].getType();
         AssetEntry entry2 = new AssetEntry(scope, type, path, user);

         nfolder.addEntry(entry2);
         nentries[i] = entry2;
      }

      indexedStorage.putXMLSerializable(nidentifier, nfolder);
      updatePermission(ResourceType.SCHEDULE_TASK_FOLDER, oentry.getPath(), nentry.getPath());
      opfolder = getTaskFolder(opidentifier);
      boolean pathChanged = !Tool.equals(oidentifier, nidentifier);

      if(opfolder != null) {
         if(pathChanged) {
            opfolder.removeEntry(oentry);
         }

         indexedStorage.putXMLSerializable(opidentifier, opfolder);
      }

      for(int i = 0; i < entries.length; i++) {
         if(entries[i].isFolder()) {
            changeFolder0(entries[i], nentries[i], principal);
         }
         else {
            changeTaskFolder(nentries[i], nentry, principal);
         }
      }

      if(pathChanged && indexedStorage.contains(oidentifier)) {
         indexedStorage.remove(oidentifier);
      }
   }

   public void moveTask(AssetEntry targetEntry, AssetEntry parentEntry, AssetEntry taskEntry, Principal principal)
      throws Exception
   {
      // Bug #77549, a task refused by setScheduleTask (a batch action query in another
      // organization) is refused before the folders are changed
      ScheduleTask task = scheduleManager.getScheduleTask(taskEntry.getName());

      if(task != null) {
         scheduleManager.checkBatchQueryOrganization(task.getTaskId(), task, principal);
      }

      AssetFolder npfolder = getTaskFolder(targetEntry.toIdentifier());
      AssetFolder opfolder = getTaskFolder(parentEntry.toIdentifier());

      if(npfolder != null) {
         npfolder.addEntry(taskEntry);
         indexedStorage.putXMLSerializable(targetEntry.toIdentifier(), npfolder);
      }

      if(opfolder != null) {
         opfolder.removeEntry(taskEntry);
         indexedStorage.putXMLSerializable(parentEntry.toIdentifier(), opfolder);
      }
      else if(parentEntry.isRoot()) {
         indexedStorage.remove(taskEntry.toIdentifier());
      }

      changeTaskFolder(taskEntry, targetEntry, principal);
   }

   public void changeTaskFolder(AssetEntry taskEntry, AssetEntry parentEntry, Principal principal)
      throws Exception
   {
      ScheduleTask task = scheduleManager.getScheduleTask(taskEntry.getName());

      if(task == null) {
         return;
      }

      // Bug #77549, checked before the path of the stored task is changed
      scheduleManager.checkBatchQueryOrganization(task.getTaskId(), task, principal);
      String oldPath = task.getPath();
      String newPath = parentEntry.getPath();
      task.setPath(parentEntry.getPath());
      scheduleManager.setScheduleTask(task.getTaskId(), task, parentEntry, principal);
      renameTransformHandler.addTransformTask(
         ScheduleService.getDependencyInfo(task.getTaskId(), task.getTaskId(), oldPath, newPath, scheduleManager));
   }

   /**
    * Update the task folder after rename task.
    * @param taskName
    * @param taskFolderPath
    * @throws Exception
    */
   public void removeTaskFromFolder(String taskName, String taskFolderPath) throws Exception {
      AssetEntry folderEntry;

      if(taskFolderPath != null) {
         folderEntry = new AssetEntry( AssetRepository.GLOBAL_SCOPE,
            AssetEntry.Type.SCHEDULE_TASK_FOLDER, taskFolderPath, null);
         AssetFolder folder = getTaskFolder(folderEntry.toIdentifier());
         AssetEntry oldTaskEntry = new AssetEntry(
            AssetRepository.GLOBAL_SCOPE,
            AssetEntry.Type.SCHEDULE_TASK, "/" + taskName, null);

         if(folder != null && folder.containsEntry(oldTaskEntry)) {
            folder.removeEntry(oldTaskEntry);

            if(folderEntry != null && folder != null) {
               indexedStorage.putXMLSerializable(folderEntry.toIdentifier(), folder);
            }
         }
      }
   }

   private TaskFolderBrowserModel getHomeBrowser() {
      AssetEntry task = new AssetEntry( AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
         "/", null);

      List<TreeNodeModel> folders = Arrays.stream(new AssetEntry[]{ task })
            .map(e -> getFolderNode(e))
            .collect(Collectors.toList());

      return TaskFolderBrowserModel.builder()
         .folderList(folders)
         .root(true)
         .build();
   }

   public TaskFolderBrowserModel getBrowserFolder(String path, boolean home, Principal principal) throws Exception {
      if(home) {
         return getHomeBrowser();
      }
      else {
         AssetEntry folderEntry = new AssetEntry( AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
            path, null);
         ArrayList<TreeNodeModel> result = new ArrayList<>();
         AssetFolder folder = getTaskFolder(folderEntry.toIdentifier());
         AssetEntry[] entries = folder.getEntries();
         Arrays.sort(entries, Comparator.comparing(AssetEntry::getName));

         for(AssetEntry entry : entries) {
            if(entry.isScheduleTaskFolder() &&
               checkFolderPermission(entry.getPath(), principal, ResourceAction.READ))
            {
               result.add(getFolderNode(entry));
            }
         }

         return TaskFolderBrowserModel.builder()
            .folderList(result)
            .paths(getBreadcrumbs(home ? "/" : path))
            .root("/".equals(path) || home)
            .build();
      }
   }

   public List<TreeNodeModel> getBreadcrumbs(String path)
      throws Exception
   {
      ArrayList<TreeNodeModel> result = new ArrayList<>();
      String[] paths;

      result.add(getFolderNode(getRootEntry()));


      if(path == null) {
         return result;
      }
      else {
         paths = path.split("/");
      }

      for(int i = 0; i < paths.length; i++) {
         if(i > 0) {
            paths[i] = paths[i - 1] + "/" + paths[i];
         }

         AssetEntry folderEntry = new AssetEntry( AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
            paths[i], null);

         TreeNodeModel node = getFolderNode(folderEntry);

         result.add(node);
      }

      return result;
   }

   private TreeNodeModel getFolderNode(AssetEntry entry) {
      Boolean isLeaf = false;
      AssetFolder childfolder = null;

      try{
         childfolder = getTaskFolder(entry.toIdentifier());
      }
      catch (Exception e) {

      }

      if(childfolder != null &&
         !Arrays.stream(childfolder.getEntries())
            .anyMatch(e -> e.getType() == AssetEntry.Type.SCHEDULE_TASK_FOLDER))
      {
         isLeaf = true;
      }

      String label = entry.getName();

      if(Objects.equals("/", entry.getPath())) {
         label = "Tasks";
      }
      if(!isSecurityEnabled() && entry.getName().contains(":")) {
         label = label.substring(label.indexOf(':') + 1);
      }

      TreeNodeModel model = TreeNodeModel.builder()
         .label(label)
         .data(entry)
         .leaf(isLeaf)
         .type("folder")
         .build();

      return model;
   }

   public AssetEntry getFolderEntry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                            AssetEntry.Type.SCHEDULE_TASK_FOLDER, path, null);
   }

   /**
    * Gets the root folder where all the asset entries are stored
    */
   public AssetFolder getRoot() throws Exception {
      return (AssetFolder) indexedStorage.getXMLSerializable(getRootIdentifier(), null);
   }

   /**
    * Gets the root folder where all the asset entries are stored
    */
   public AssetFolder getTaskFolder(String identifier) throws Exception {
      return (AssetFolder) indexedStorage.getXMLSerializable(identifier, null);
   }

   /**
    * Gets the identifier of the root asset entry.
    *
    * @return the root identifier.
    */
   public String getRootIdentifier() {
      return getRootEntry().toIdentifier();
   }

   public AssetEntry getRootEntry() {
      return new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
         "/", null);
   }

   /**
    * Check if user have the permission of task folder.
    * @param entry folder entry.
    * @param principal user.
    * @return
    */
   public boolean hasFolderPermission(AssetEntry entry, ResourceAction action,
                                       Principal principal)
      throws Exception
   {
      if(entry.isRoot()) {
         return true;
      }

      AssetFolder taskFolder = getTaskFolder(entry.toIdentifier());
      IdentityID owner = taskFolder.getOwner();
      boolean adminPermission = securityEngine.checkPermission(
         principal, ResourceType.SECURITY_USER, owner, ResourceAction.ADMIN);

      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      if(pId.equals(owner) || adminPermission) {
         return true;
      }

      if(!ScheduleManager.isShareInGroup()) {
         return false;
      }

      if(ResourceAction.DELETE.equals(action) && ScheduleManager.isDeleteByOwner()) {
         return false;
      }

      return ScheduleManager.hasShareGroupPermission(owner, principal);
   }

   /**
    * Get the folder edit model to change name and owner.
    * @param folderPath
    * @param principal
    * @return
    * @throws Exception
    */
   public EditTaskFolderDialogModel getFolderEditModel(String folderPath, Principal principal) throws Exception {
      EditTaskFolderDialogModel.Builder model = EditTaskFolderDialogModel.builder();
      AssetEntry entry = getFolderEntry(folderPath);
      model.oldPath(folderPath);
      setFolderNameAndOwner(model, entry);
      model.securityEnabled(isSecurityEnabled());

      return model.build();
   }

   /**
    * Get the task folder display label.
    * @param folderEntry folder entry.
    * @return
    */
   public String getTaskFolderLabel(AssetEntry folderEntry) {
      String name = folderEntry.getName();
      int separatorIndex = name.indexOf(":");

      if(!isSecurityEnabled() && separatorIndex != -1) {
         name = name.substring(separatorIndex + 1);
      }

      return name;
   }

   public boolean checkFolderPermission(String folder, Principal principal, ResourceAction action)
      throws SecurityException
   {
      return securityEngine.checkPermission(
         principal, ResourceType.SCHEDULE_TASK_FOLDER, folder, action);
   }

   public void setScheduleFolderPermission(AssetEntry entry, Principal principal) {
      String path = entry.getPath();

      try {
         if(!Tool.equals(path, "/")) {
            if(securityEngine.checkPermission(principal, ResourceType.SCHEDULE_TASK_FOLDER,
               path, ResourceAction.DELETE))
            {
               entry.setProperty(ScheduleFolderTreeAction.DELETE.name(), "true");
            }

            if(securityEngine.checkPermission(principal, ResourceType.SCHEDULE_TASK_FOLDER,
               path, ResourceAction.DELETE) &&
               securityEngine.checkPermission(principal, ResourceType.SCHEDULE_TASK_FOLDER,
                  path, ResourceAction.WRITE))
            {
               entry.setProperty(ScheduleFolderTreeAction.EDIT.name(), "true");
            }
         }

         if(securityEngine.checkPermission(principal, ResourceType.SCHEDULE_TASK_FOLDER,
            path, ResourceAction.READ))
         {
            entry.setProperty(ScheduleFolderTreeAction.READ.name(), "true");
         }

         boolean hasCreatPermission = securityEngine.checkPermission(
            principal, ResourceType.SCHEDULE_TASK_FOLDER, path, ResourceAction.WRITE);

         if(hasCreatPermission) {
            entry.setProperty(ScheduleFolderTreeAction.CREATE.name(), "true");
         }
      }
      catch(Exception e) {
         LOG.error("Failed to check permission for: " + path, e);
      }
   }

   public Map<String, String> getScheduleFolderPermission(String path, Principal principal) {
      HashMap<String, String> folderActions = new HashMap<>();

      try {
         if(!Tool.equals(path, "/")) {
            if(securityEngine.checkPermission(principal, ResourceType.SCHEDULE_TASK_FOLDER,
               path, ResourceAction.DELETE))
            {
               folderActions.put(ScheduleFolderTreeAction.DELETE.name(), "true");
            }

            if(securityEngine.checkPermission(principal, ResourceType.SCHEDULE_TASK_FOLDER,
               path, ResourceAction.DELETE) &&
               securityEngine.checkPermission(principal, ResourceType.SCHEDULE_TASK_FOLDER,
                  path, ResourceAction.WRITE))
            {
               folderActions.put(ScheduleFolderTreeAction.EDIT.name(), "true");
            }
         }

         if(securityEngine.checkPermission(principal, ResourceType.SCHEDULE_TASK_FOLDER,
            path, ResourceAction.READ))
         {
            folderActions.put(ScheduleFolderTreeAction.READ.name(), "true");
         }

         boolean hasCreatPermission = securityEngine.checkPermission(
            principal, ResourceType.SCHEDULE_TASK_FOLDER, path, ResourceAction.WRITE);

         if(hasCreatPermission) {
            folderActions.put(ScheduleFolderTreeAction.CREATE.name(), "true");
         }
      }
      catch(Exception e) {
         LOG.error("Failed to check permission for: " + path, e);
      }

      return folderActions;
   }

   private void updatePermission(ResourceType type, String oldFolder, String newFolder) {
      if(type == null || oldFolder == null || newFolder == null) {
         return;
      }

      SecurityEngine engine = securityEngine;

      if(engine.getSecurityProvider().isVirtual()) {
         return;
      }

      Permission permission = engine.getPermission(type, oldFolder);

      if(permission != null) {
         engine.removePermission(type, oldFolder);
         engine.setPermission(type, newFolder, permission);
      }
   }

   private void setResourcePermission(ResourceType type, Principal principal, String name) {
      AuthorizationProvider authz = securityProvider.getAuthorizationProvider();
      Permission perm = new Permission();
      Set<String> userGrants = new HashSet<>();
      userGrants.add(principal.getName());

      String orgId = OrganizationManager.getInstance().getUserOrgId(principal);
      for(ResourceAction action : ResourceAction.values()) {
         perm.setUserGrantsForOrg(action, userGrants, orgId);
      }

      authz.setPermission(type, name, perm);
   }

   /**
    * Get the task item name for example admin: folder1 -> folder1
    * @param item
    * @return
    * @throws Exception
    */
   private void setFolderNameAndOwner(EditTaskFolderDialogModel.Builder model, AssetEntry item)
      throws Exception
   {
      if(!item.isScheduleTaskFolder()) {
         throw new RuntimeException("item is not a task folder");
      }

      AssetFolder folder = getTaskFolder(item.toIdentifier());

      if(folder == null) {
         throw new FileNotFoundException(item.getPath());
      }

      model.owner(folder.getOwner());
      model.folderName(item.getName());
   }

   /**
    * Get the owners can be used.
    * @param principal
    * @return
    */
   private List<IdentityID> getOwners(Principal principal) {
      SecurityProvider provider = securityEngine.getSecurityProvider();

      if(provider != null) {
         return Arrays.stream(securityEngine.getSecurityProvider().getUsers())
            .filter(r -> provider.checkPermission(
               principal, ResourceType.SECURITY_USER, r.convertToKey(), ResourceAction.ADMIN))
            .sorted()
            .collect(Collectors.toList());
      }

      return new ArrayList();
   }

   /**
    * Check if security is enabled.
    */
   private boolean isSecurityEnabled() {
      SecurityProvider provider = securityEngine.getSecurityProvider();

      if(provider != null) {
         return !provider.isVirtual();
      }

      return false;
   }

   private final IndexedStorage indexedStorage;
   private final ScheduleManager scheduleManager;
   private final SecurityEngine securityEngine;
   private final SecurityProvider securityProvider;
   private final RenameTransformHandler renameTransformHandler;

   private static final Logger LOG = LoggerFactory.getLogger(ScheduleTaskActionService.class);
}
