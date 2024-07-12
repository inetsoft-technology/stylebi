/*
 * inetsoft-web - StyleBI is a business intelligence web application.
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
import { HttpClient, HttpParams } from "@angular/common/http";
import {
   AfterContentChecked,
   Component,
   ElementRef,
   OnDestroy,
   OnInit,
   Renderer2,
   HostListener,
   ViewChild, NgZone
} from "@angular/core";
import { ActivatedRoute, ParamMap, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { KEY_DELIMITER, IdentityId } from "../../../../../../em/src/app/settings/security/users/identity-id";
import { ScheduleConditionModel } from "../../../../../../shared/schedule/model/schedule-condition-model";
import { ScheduleUsersService } from "../../../../../../shared/schedule/schedule-users.service";
import { AssemblyAction } from "../../../common/action/assembly-action";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { Point } from "../../../common/data/point";
import { getStoredCondition } from "../../../common/util/schedule-condition.util";
import { Tool } from "../../../../../../shared/util/tool";
import { ScheduleTaskList } from "../../../../../../shared/schedule/model/schedule-task-list";
import { ScheduleTaskModel } from "../../../../../../shared/schedule/model/schedule-task-model";
import { ActionsContextmenuComponent } from "../../../widget/fixed-dropdown/actions-contextmenu.component";
import { DropdownOptions } from "../../../widget/fixed-dropdown/dropdown-options";
import { ScheduleChangeService } from "../schedule-change.service";
import { ScheduleTaskDialogModel } from "../../../../../../shared/schedule/model/schedule-task-dialog-model";
import { ComponentTool } from "../../../common/util/component-tool";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { PortalNewTaskRequest } from "../../../../../../shared/schedule/model/portal-new-task-request";
import { MoveTaskDialogComponent } from "./move-task-dialog/move-task-dialog.component";
import { PortalMoveTaskFolderRequest } from "../../data/commands/portal-move-task-folder-request";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { TaskListModel } from "../../../../../../em/src/app/settings/schedule/model/task-list-model";
import { EditTaskFolderDialog } from "./edit-task-folder-dialog/edit-task-folder-dialog.component";
import { EditTaskFolderDialogModel } from "../../../../../../em/src/app/settings/schedule/model/edit-task-folder-dialog-model";
import { GuiTool } from "../../../common/util/gui-tool";
import { DragService } from "../../../widget/services/drag.service";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { NewTaskFolderEvent } from "../model/new-task-folder-event";
import { CheckTaskDuplicateRequest } from "../../data/commands/check-task-duplicate-request";
import { CheckDuplicateResponse } from "../../data/commands/check-duplicate-response";
import { DomService } from "../../../widget/dom-service/dom.service";
import { TimeConditionModel, TimeConditionType } from "../../../../../../shared/schedule/model/time-condition-model";
import {
   ScheduleFolderTreeAction
} from "../../../../../../em/src/app/settings/schedule/schedule-folder-tree/schedule-folder-tree-action";

const GET_SCHEDULED_TASKS_URI = "../api/portal/scheduledTasks";
export const NEW_TASKS_URI = "../api/portal/schedule/new";
const REMOVE_TASKS_URI = "../api/portal/schedule/remove";
const RUN_TASKS_URI = "../api/portal/schedule/run";
const STOP_TASKS_URI = "../api/portal/schedule/stop";
const ENABLE_TASK_URI = "../api/portal/schedule/enable/task";
const CHECK_TASK_DEPENDENCY = "../api/portal/schedule/check-dependency";
const CHECK_FOLDER_DEPENDENCY = "../api/portal/schedule/folder/check-dependency";
const TASKS_FOLDER_NAME_URI = "../api/portal/schedule/rename-folder";
const NEW_TASKS_FOLDER_URI = "../api/portal/schedule/folder/add";
const MOVE_FOLDER_NAME_URI = "../api/portal/schedule/move-items";
const REMOVE_FOLDER_URI = "../api/portal/schedule/folder/remove";
const CHANGE_SHOW_TYPE_URI = "../api/portal/schedule/change-show-type";
const GET_TASK_FOLDER_EDIT_MODEL_URI = "../api/portal/schedule/folder/editModel";
const CHECK_MOVE_DUPLICATE_URI: string = "../api/portal/schedule/move/checkDuplicate";
const CHECK_ADD_DUPLICATE_URI: string = "../api/portal/schedule/add/checkDuplicate";
const CHECK_ROOT_PERMISSION_URI = "../api/portal/schedule/folder/checkRootPermission";
const SYSTEM_USER = "INETSOFT_SYSTEM";
declare const window: any;

@Component({
   selector: "p-schedule-task-list",
   templateUrl: "./schedule-task-list.component.html",
   styleUrls: ["./schedule-task-list.component.scss"],
   providers: [ScheduleChangeService]
})
export class ScheduleTaskListComponent implements OnInit, OnDestroy, AfterContentChecked {
   @ViewChild("tree") tree: TreeComponent;
   tasks: ScheduleTaskModel[] = [];
   originalOrder: string[] = [];
   sortType: any;
   tableHeight: number;
   treeHeight: number;
   showOwners: boolean = false;
   rootNode: TreeNodeModel;
   selectedNodes: TreeNodeModel[] = [];
   path: string;
   showTasksAsList: boolean = false;
   _selectAllChecked: boolean = false;
   selectedItems: string[] = [];
   loading = false;
   noRootPermission: boolean = false;

   private subscriptions: Subscription;

   INIT_TREE_PANE_SIZE = 0;

   constructor(private http: HttpClient, private router: Router,
               private route: ActivatedRoute,
               private scheduleChangeService: ScheduleChangeService,
               private dropdownService: FixedDropdownService,
               private usersService: ScheduleUsersService,
               private modal: NgbModal, private renderer: Renderer2,
               private element: ElementRef, private dragService: DragService,
               private domService: DomService,
               private zone: NgZone)
   {
   }

   ngOnInit(): void {
      this.subscriptions = this.scheduleChangeService.onChange.subscribe(
         (task) => {
            for(let i = 0; i < this.tasks.length; i++) {
               if(this.tasks[i].name === task.name) {
                  this.tasks[i] = task;
               }
            }
         }
      );

      this.subscriptions.add(this.route.queryParamMap
         .subscribe((params: ParamMap) => {
            this.path = params.get("path");
            this.path = !this.path || this.path == "" ? "/" : this.path;
            this.navigateToPath();
         }));

      this.subscriptions.add(
         this.scheduleChangeService.onFolderChange.subscribe(() => this.loadTaskFolderTree())
      );

      this.http.get(CHANGE_SHOW_TYPE_URI).subscribe((showTasksAsList) => {
         this.showTasksAsList = <boolean> showTasksAsList;

         if(!this.showTasksAsList) {
            this.INIT_TREE_PANE_SIZE = 20;
            this.loadTaskFolderTree();
         }
         else {
            this.loadTasks();
         }
      });

      this.http.get(CHECK_ROOT_PERMISSION_URI).subscribe((rootPermission: boolean) => {
         this.noRootPermission = !rootPermission;
      });
   }

   ngOnDestroy(): void {
      if(this.subscriptions) {
         this.subscriptions.unsubscribe();
         this.subscriptions = null;
      }
   }

   get currentFolder(): AssetEntry {
      if(!this.showTasksAsList && this.selectedNodes.length > 0) {
         return this.selectedNodes[0].data;
      }

      return null;
   }

   get selectAllChecked() {
      return this._selectAllChecked && this.selectedItems.length == this.tasks.length;
   }

   loadTasks(freshListAndTree?: boolean): void {
      this.loading = true;

      if(!this.currentFolder && !this.showTasksAsList) {
         this.tasks = [];
         this.originalOrder = [];
         this.loadTaskFolderTree();
         this.loading = false;

         return;
      }

      this.http.post(GET_SCHEDULED_TASKS_URI, this.showTasksAsList ? null : this.currentFolder).subscribe(
         (list: ScheduleTaskList) => {
            this.initTaskList(list);
            this.loading = false;
         },
         (error) => this.handleError(error)
      );

      if(freshListAndTree && !this.showTasksAsList) {
         this.loadTaskFolderTree();
      }
   }

   changeShowType(value: boolean): void {
      this.showTasksAsList = value;
      this.INIT_TREE_PANE_SIZE = this.showTasksAsList ? 0 : 20;
      let params = new HttpParams().set("showTasksAsList", this.showTasksAsList + "");
      this.http.put(CHANGE_SHOW_TYPE_URI, null, {params}).subscribe(() => {
         this.loadTasks();
      });
   }

   initTaskList(list: ScheduleTaskList) {
      this.tasks = list.tasks;
      this.originalOrder = this.tasks.map(task => task.name);
      this.showOwners = list.showOwners;

      for(let task of this.tasks) {
         if(!!task.status && !!task.status.lastRunEnd) {
            task.lastRunTime = task.status.lastRunEnd;
         }
      }
   }

   private handleError(error: any) {
      this.loading = false;

      if(error.statusText && error.statusText.toLowerCase() == "forbidden") {
         ComponentTool.showMessageDialog(
            this.modal, "_#(js:Error)",
            "You have no schedule permission, please contact administrator");
      }
      else {
         ComponentTool.showHttpError(
            "Failed to load schedule tasks", error, this.modal);
      }
   }

   newTask(): void {
      this.loading = true;
      const condition: ScheduleConditionModel = this.getConditionModelsForServer(getStoredCondition());

      const requestModel: PortalNewTaskRequest = <PortalNewTaskRequest>{
         parentEntry: this.parentFolder,
         conditionModel: condition
      };

      this.http.post(NEW_TASKS_URI, requestModel).subscribe(
         (model: ScheduleTaskDialogModel) => this.navigateToTaskEditor(model.name, model.taskDefaultTime, true),
         (error) => {
            this.loading = false;
            ComponentTool.showHttpError("Failed to get schedule task model", error, this.modal);
         },
         () => this.loading = false
      );
   }

   getConditionModelsForServer(condition: ScheduleConditionModel): ScheduleConditionModel {
      if(!condition) {
         return condition;
      }

      if(condition.conditionType == "TimeCondition" &&
         (<TimeConditionModel> condition).type == TimeConditionType.AT)
      {
         let timeCondition: TimeConditionModel = <TimeConditionModel> condition;

         if(!timeCondition.changed || !!timeCondition.timeZone) {
            timeCondition.timeZoneOffset = -timeCondition.timeZoneOffset;
         }
      }

      return condition;
   }

   newFolder(parentFolder: any): void {
      let commit = (result) => {
         let addFolderEvent: NewTaskFolderEvent = {
            parent: parentFolder,
            folderName: result.folderName,
         };

         this.http.post(CHECK_ADD_DUPLICATE_URI, addFolderEvent).subscribe((res: CheckDuplicateResponse) => {
            if(res.duplicate) {
               let errorMessage: string = "_#(js:common.duplicateName)";
               ComponentTool.showMessageDialog(this.modal, "_#(js:Error)", errorMessage);
            }
            else {
               this.http.post(NEW_TASKS_FOLDER_URI, addFolderEvent).subscribe(() => {
                  this.loadTaskFolderTree();
                  this.loadTasks();
               });
            }
         });
      };

      const dialog = ComponentTool.showDialog(this.modal, EditTaskFolderDialog, commit);

      dialog.model =  {
         folderName: "",
         oldPath: (parentFolder.path ? parentFolder.path + "/" : "") + "x",
         securityEnabled: true,
         owner: {name: "null", organization: null}
      };


   }

   get parentFolder(): AssetEntry {
      if(!this.showTasksAsList && this.selectedNodes.length == 1) {
         return this.selectedNodes[0].data;
      }

      return null;
   }

   editTask(task: ScheduleTaskModel): void {
      this.navigateToTaskEditor(this.getTaskName(task));
   }

   private editTaskFolder(path: string): void {
      let params = new HttpParams().set("folderPath", path);

      this.http.post<EditTaskFolderDialogModel>(GET_TASK_FOLDER_EDIT_MODEL_URI, null, {params})
         .subscribe(data => {
            if(data) {
               let commit = (result) => {
                  this.http.post<string>(TASKS_FOLDER_NAME_URI, result).subscribe((newPath) => {
                     this.loadTaskFolderTree(!!newPath ? [newPath] : null);
                  });
               };

               const dialog = ComponentTool.showDialog(this.modal, EditTaskFolderDialog, commit);
               dialog.model = data;
            }
         });
   }

   getTaskName(task: ScheduleTaskModel): string {
      return !!task.owner && task.owner.name !== SYSTEM_USER && !task.name.startsWith(task.owner.name) ?
         task.owner.name + KEY_DELIMITER + task.owner.organization + ":" + task.name : task.name;
   }

   removeItems(): void {
      ComponentTool.showConfirmDialog(this.modal, "_#(js:Confirm)",
         "_#(js:em.schedule.delete.confirm)")
         .then((buttonClicked) => {
            if(buttonClicked === "ok") {
               let taskModels: ScheduleTaskModel[] = this.getSelectedTaskModels();
               this.http.post(CHECK_TASK_DEPENDENCY, taskModels).subscribe((taskListModel: TaskListModel) => {
                  let dependencies: string[] = taskListModel.taskNames;

                  if(dependencies.length > 0) {
                     let taskNames = dependencies.join(", ");

                     ComponentTool.showMessageDialog(this.modal, "_#(js:Error)",
                         Tool.formatCatalogString("_#(js:em.schedule.task.removeDependency)",
                                                  [taskNames]));
                  }
                  else {
                     this.http.post(REMOVE_TASKS_URI, taskModels).subscribe(
                        () => {
                           this.loadTasks();
                           this.loadTaskFolderTree();
                           this.selectedItems = [];
                           this._selectAllChecked = false;
                        },
                        (error) => {
                           ComponentTool.showHttpError("_#(js:em.schedule.task.removeFailed)",
                                                       error, this.modal);
                        }
                     );
                  }
               },
                  (error) => ComponentTool.showHttpError("_#(js:em.schedule.task.dependencyFailed)",
                                                         error, this.modal));
            }
         });
   }

   removeFolders(path: string): void {
      ComponentTool.showConfirmDialog(this.modal, "_#(js:Confirm)",
         "_#(js:em.schedule.delete.confirm)")
         .then((buttonClicked) => {
            if(buttonClicked === "ok") {
               let folders = new TaskListModel(this.getMutiEditPath(path));

               this.http.post(CHECK_FOLDER_DEPENDENCY, folders).subscribe((taskListModel: TaskListModel) => {
                     let dependencies: string[] = taskListModel.taskNames;

                     if(dependencies.length > 0) {
                        let taskNames = dependencies.join(", ");

                        ComponentTool.showMessageDialog(this.modal, "_#(js:Error)",
                           Tool.formatCatalogString("_#(js:em.schedule.task.removeDependency)",
                              [taskNames]));
                     }
                     else {
                        this.http.post(REMOVE_FOLDER_URI, folders).subscribe(
                           (res: any) => {
                              if(!res) {
                                 return;
                              }

                              if(res.refresh) {
                                 this.loadTasks();
                                 this.loadTaskFolderTree();
                              }

                              if(res.errorMessage) {
                                 ComponentTool.showMessageDialog(this.modal, "_#(js:Error)", res.errorMessage);
                              }
                           },
                           error => {
                              if(error.error.error === "messageException" && error.error.message) {
                                 ComponentTool.showMessageDialog(this.modal, "_#(js:Error)", error.error.message);
                              }
                           }
                        );
                     }
                  },
                  (error) => ComponentTool.showHttpError("_#(js:em.schedule.task.dependencyFailed)",
                     error, this.modal));
            }
         });
   }

   runTask(task: ScheduleTaskModel): void {
      const params = new HttpParams().set("name", Tool.byteEncode(this.getTaskName(task)));
      const options = { params: params };

      this.http.get(RUN_TASKS_URI, options).subscribe(
         () => this.loadTasks(),
         (error) => ComponentTool.showHttpError("_#(js:em.schedule.task.startFailed)", error,
                                                this.modal)
      );
   }

   stopTask(task: ScheduleTaskModel): void {
      const params = new HttpParams().set("name", Tool.byteEncode(this.getTaskName(task)));
      const options = { params: params };

      this.http.get(STOP_TASKS_URI, options).subscribe(
         () => this.loadTasks(),
         (error) => ComponentTool.showHttpError("Failed to stop selected task", error, this.modal)
      );
   }

   disableTask(task: ScheduleTaskModel): void {
      const params = new HttpParams().set("name", Tool.byteEncode(this.getTaskName(task)));
      const options = { params: params };

      this.http.get(ENABLE_TASK_URI, options).subscribe(
         () => this.loadTasks(),
         (error) => ComponentTool.showHttpError("_#(js:em.schedule.task.disableFailed)", error,
                                                this.modal)
      );
   }

   changeSortType(sortType: any) {
      this.sortType = sortType;

      if(sortType == "") {
         let newList: ScheduleTaskModel[] = [];

         for(let task of this.tasks) {
            const oldIndex = this.originalOrder.indexOf(task.name);

            if(oldIndex != -1) {
               newList[oldIndex] = task;
            }
         }

         this.tasks = newList;
      }
   }

   private navigateToTaskEditor(name: string, taskDefaultTime: boolean = true,
                                newTask: boolean = false): void
   {
      let path = !!this.selectedNodes && this.selectedNodes.length != 0 ?
         this.selectedNodes[0].data.path : "";
      this.router.navigate(["/portal/tab/schedule/tasks", name],
         { queryParams: { taskDefaultTime: taskDefaultTime, path: path, newTask: newTask}});
   }

   selectTask(task: ScheduleTaskModel): void {
      const index: number = this.selectedItems.indexOf(task.name);

      if(index !== -1) {
         this.selectedItems.splice(index, 1);
      }
      else {
         this.selectedItems.push(task.name);
      }
   }

   openError(task: ScheduleTaskModel): void {
      const winRef: Window = window.open("", "Error Window");
      const div: HTMLDivElement = winRef.document.createElement("div") as HTMLDivElement;
      const title: HTMLTitleElement = winRef.document.createElement("title") as HTMLTitleElement;

      div.innerText = task.status.errorMessage;
      title.textContent = "Error for " + task.label;

      winRef.document.head.appendChild(title);
      winRef.document.body.appendChild(div);
   }

   public ngAfterContentChecked(): void {
      this.onResize(null);
   }

   @HostListener("window:resize", ["$event"])
   onResize(event) {
      this.tableHeight = this.element.nativeElement.offsetHeight - 140;
      this.treeHeight = this.element.nativeElement.offsetHeight - 80;
   }

   public loadTaskFolderTree(selectedPaths?: string[]): void {
      this.http.get<TreeNodeModel>("../api/portal/schedule/tree")
          .subscribe(root => {
             let oldRoot = this.rootNode;
             this.rootNode = root;
             this.keepExpandedNodes(oldRoot, root);
             this.selectFolderByPath(selectedPaths);

             if(this.selectedNodes == null || this.selectedNodes.length == 0) {
                this.selectedNodes = [this.rootNode];
                this.navigateToPath();
             }
             else if(selectedPaths && selectedPaths.length > 0 && this.selectedNodes
                && this.selectedNodes.length > 0)
             {
                this.loadTasks();
             }
          });
   }

   private selectFolderByPath(selectedPaths: string[], root?: TreeNodeModel): void {
      if(!!selectedPaths) {
         this.selectedNodes = [];

         for(let selectedPath of selectedPaths) {
            let rootNode: TreeNodeModel = !!root ? root : this.rootNode;

            if(!rootNode || !selectedPath) {
               return;
            }

            let splitPaths: string[] = selectedPath.split("/");
            let parentNode: TreeNodeModel = rootNode;
            let currentPath: string = "";

            for(let splitPath of splitPaths) {
               currentPath += (currentPath === "" ? splitPath : "/" + splitPath);

               let findNode = GuiTool.findNode(parentNode, (n) => !!n.data &&
                  n.data.path === currentPath);

               if(!findNode) {
                  return;
               }

               if(currentPath === selectedPath) {
                  this.selectedNodes.push(findNode);
               }
               else {
                  parentNode = findNode;
                  findNode.expanded = true;
               }
            }
         }
      }
   }

   /**
    * Keep the tree expand status.
    * @param node old node.
    * @param root new root.
    */
   keepExpandedNodes(node: TreeNodeModel, root: TreeNodeModel): void {
      if(!node || node.leaf || !node.children) {
         return;
      }

      for(let child of node.children) {
         if(child.expanded) {
            let treeNode = GuiTool.findNode(root, (n) =>
               !!n.data && n.data.path === child.data.path && n.label === child.label);

            if(treeNode) {
               treeNode.expanded = true;
            }

            this.keepExpandedNodes(child, root);
         }
      }
   }

   public selectNode(node: TreeNodeModel[]): void {
      let oldCurrentFolder = this.currentFolder;
      this.selectedNodes = node;
      this.loadTasks();
      let newCurrentFolder = this.currentFolder;

      if(!oldCurrentFolder && !!newCurrentFolder || !!oldCurrentFolder && !newCurrentFolder ||
         !!oldCurrentFolder && !!newCurrentFolder && oldCurrentFolder.path != newCurrentFolder.path)
      {
         this.selectedItems = [];
         this._selectAllChecked = false;
      }
   }

   /**
    * Move the drag folder to drop folder.
    * @param event
    */
   public nodeDrop(event: any) {
      let node: TreeNodeModel = event.node;
      let entries: AssetEntry[] = [];
      let tasks: ScheduleTaskModel[] = [];

      if(!node || !node.data || node.data.type != AssetType.SCHEDULE_TASK_FOLDER) {
         return;
      }

      let parent: AssetEntry = node.data;
      let dragData = this.dragService.getDragData();

      for(let key of Object.keys(dragData)) {
         let dragEntries: AssetEntry[] = [];
         let dragTasks: ScheduleTaskModel[] = [];

         if("tasks" == key) {
            dragTasks = JSON.parse(dragData[key]);
         }
         else {
            dragEntries = JSON.parse(dragData[key]);
         }

         if(dragEntries && dragEntries.length > 0) {
            for(let entry of dragEntries) {
               if(Tool.isEquals(parent, entry) || this.getParentPath(entry) === parent.path) {
                  continue;
               }

               const entryPath = entry.path.endsWith("/") ? entry.path : entry.path + "/";

               if(parent.path.startsWith(entryPath)) {
                  // trying to move entry to descendant, don't allow
                  continue;
               }

               // make sure the drag entries are from this tree
               if(this.tree.getNodeByData("data", entry)) {
                  entries.push(entry);
               }
            }
         }

         if(dragTasks) {
            for(let task of dragTasks) {
               if(!task || task.path == parent.path) {
                  continue;
               }

               tasks.push(task);
            }
         }
      }

      let folderPaths = entries
         .filter(folderEntry => !!folderEntry)
         .map(folerEntry => folerEntry.path);

      if(folderPaths.length == 0 && tasks.length == 0) {
         return;
      }

      let checkTaskDuplicateRequest: CheckTaskDuplicateRequest = {
         path: parent.path,
         items: tasks,
         folders: folderPaths
      };

      this.http.post(CHECK_MOVE_DUPLICATE_URI, checkTaskDuplicateRequest)
         .subscribe(
            (res: CheckDuplicateResponse) => {
               if(res.duplicate) {
                  let errorMessage: string = "_#(js:common.duplicateName)";
                  ComponentTool.showMessageDialog(this.modal, "_#(js:Error)", errorMessage);
               }
               else {
                  let moveFolderRequest: PortalMoveTaskFolderRequest = <PortalMoveTaskFolderRequest>{
                     target: parent,
                     tasks: tasks,
                     folders: folderPaths
                  };

                  this.moveItems(moveFolderRequest);
               }
            }
         );
   }

   private getParentPath(entry: AssetEntry): string {
      if(entry.path === "/") {
         return null;
      }

      let index = entry.path.lastIndexOf("/");
      return index >= 0 ? entry.path.substring(0, index) : "/";
   }

   public navigateToPath(): void {
      if(this.selectedNodes != null && this.selectedNodes.length > 0 && this.path != null) {
         if(this.path == "/") {
            this.selectNode([this.rootNode]);
         }
         else {
            let pathNodes = this.path.split("/");
            let currentNode = this.rootNode;

            for(let nodeName of pathNodes) {
               let children = currentNode.children;
               let found = false;

               for(let child of children) {
                  let childName = child.data.path.substr(child.data.path.lastIndexOf("/") + 1);

                  if(childName == nodeName) {
                     currentNode = child;
                     children = currentNode.children;
                     found = true;
                     break;
                  }
               }

               if(!found) {
                  currentNode = this.rootNode;
                  break;
               }
            }

            this.selectNode([currentNode]);
         }
      }
   }

   private getTaskModel(task: ScheduleTaskModel): ScheduleTaskModel {
      if(!task) {
         return null;
      }

      let taskModel = <ScheduleTaskModel> {
         name: task.name,
         label: task.label,
         description: task.description,
         owner: task.owner,
         path: task.path,
         status: task.status,
         lastRunTime: task.lastRunTime,
         schedule: task.schedule,
         editable: task.editable,
         removable: task.removable,
         enabled: task.enabled,
         distribution: task.distribution
      };

      return taskModel;
   }

   public moveTasks(): void {
      let moveFolderRequest: PortalMoveTaskFolderRequest = <PortalMoveTaskFolderRequest>{
         target: null,
         tasks: this.getSelectedTaskModels(),
         folders: []
      };

      this.moveItems(moveFolderRequest);
   }

   public moveFolder(folderPath: string): void {
      let moveFolderRequest: PortalMoveTaskFolderRequest = <PortalMoveTaskFolderRequest>{
         target: null,
         tasks: [],
         folders: this.getMutiEditPath(folderPath)
      };

      this.moveItems(moveFolderRequest);
   }

   public moveItems(moveFolderRequest: PortalMoveTaskFolderRequest): void {
      let moveTaskItemsRequest = () => {
         this.http.post(MOVE_FOLDER_NAME_URI, moveFolderRequest)
            .subscribe(() => {
               let selectedPaths: string[] = this.getMovedPaths(moveFolderRequest.folders,
                  moveFolderRequest.target.path);

               if((!moveFolderRequest.folders || moveFolderRequest.folders.length == 0) && moveFolderRequest.tasks) {
                  selectedPaths = [ moveFolderRequest.target.path ];
               }

               this.loadTaskFolderTree(selectedPaths);
            },
            (error) => {
               if(error?.error) {
                  ComponentTool.showMessageDialog(this.modal, "_#(js:Error)", error.error.message);
               }
            });
      };

      if(!!moveFolderRequest && !!moveFolderRequest.target) {
         moveTaskItemsRequest();

         return;
      }
      const dialog = ComponentTool.showDialog(this.modal, MoveTaskDialogComponent,
          (result: AssetEntry) => {
             moveFolderRequest.target = result;
             moveTaskItemsRequest();
          });

      dialog.multi = true;
      dialog.items = moveFolderRequest.tasks;
      dialog.folders = moveFolderRequest.folders;
      dialog.parentPath = this.parentFolder.path || "/";
      dialog.grandparentFolder = "/";

      if(dialog.parentPath.indexOf("/") > 1) {
         dialog.grandparentFolder = dialog.parentPath.substring(0, dialog.parentPath.lastIndexOf("/"));
      }
   }

   /**
    * Get moved paths.
    * @param movePaths the paths before moving.
    * @param targetPath move target path.
    */
   private getMovedPaths(movePaths: string[], targetPath: string): string[] {
      let result: string[] = [];

      if(movePaths) {
         result = movePaths
            .filter(folder => !!folder)
            .map(folder => {
               let lastSeparatorIndex = folder.lastIndexOf("/");

               if(lastSeparatorIndex + 1 < folder.length) {
                  return targetPath === "/" ? folder.substring(lastSeparatorIndex + 1) :
                     targetPath + "/" + folder.substring(lastSeparatorIndex + 1);
               }

               return null;
            })
            .filter(folder => !!folder);
      }

      return result;
   }

   getSelectedTaskModels() {
      return this.findSelectedTasks().map((task) => this.getTaskModel(task));
   }

   selectAll(checked: boolean) {
      this.selectedItems = [];

      if(checked) {
         this.selectedItems.push(...this.tasks.map(task => task.name));
         this._selectAllChecked = true;
      }
   }

   removeEnable() {
      return this.selectedItems.length != 0 &&
         this.findSelectedTasks().every((task) => task.canDelete && task.removable);
   }

   private findSelectedTasks(): ScheduleTaskModel[] {
      return this.selectedItems.map(name => this.tasks.find(task => task.name === name))
         .filter(task => task != null);
   }

   isToggleTasksEnabledDisabled(task: ScheduleTaskModel): boolean {
      return !(task.editable && task.removable);
   }

   openTreeContextmenu(event: [MouseEvent | TouchEvent, TreeNodeModel, TreeNodeModel[]]) {
      let options: DropdownOptions = {
         position : new Point(),
         contextmenu: true
      };

      if(event[0] instanceof MouseEvent) {
         options.position = {x: (<MouseEvent> event[0]).clientX + 1,
            y: (<MouseEvent> event[0]).clientY};
      }
      else if(event[0] instanceof TouchEvent) {
         options.position = {x: (<TouchEvent> event[0]).targetTouches[0].pageX,
            y: (<TouchEvent> event[0]).targetTouches[0].pageY};
      }

      let contextmenu: ActionsContextmenuComponent =
         this.dropdownService.open(ActionsContextmenuComponent, options).componentInstance;
      contextmenu.sourceEvent = event[0];
      contextmenu.actions = this.createActions(event[1], event[2]);
   }

   hasMenuFunction(): any {
      return (node) => this.hasMenu(node);
   }

   hasMenu(node: TreeNodeModel): boolean {
      const actions = this.createActions( node, [node]);
      return actions.some(group => group.visible);
   }

   protected createActions(node: TreeNodeModel, selectedNodes: TreeNodeModel[]): AssemblyActionGroup[] {
      let group = new AssemblyActionGroup([]);
      let groups = [group];
      let entry: AssetEntry = node.data;

      // if root
      if(entry.path === "/") {
         group.actions.push(this.createNewTaskFolderAction(entry));
      }
      else {
         group.actions.push(this.createNewTaskFolderAction(entry));
         group.actions.push(this.createEditTaskFolderAction(entry));
         group.actions.push(this.createDeleteTaskFolderAction(entry));
      }

      return groups;
   }

   private createNewTaskFolderAction(entry: AssetEntry): AssemblyAction {
      return {
         id: () => "task-tree new-folder",
         label: () => "_#(js:New Folder)",
         icon: () => "",
         enabled: () => entry.properties[ScheduleFolderTreeAction.CREATE] === "true",
         visible: () => true,
         action: () => this.newFolder(entry)
      };
   }

   private createEditTaskFolderAction(entry: AssetEntry): AssemblyAction {
      return {
         id: () => "task-tree edit-folder",
         label: () => "_#(js:Edit Folder)",
         icon: () => "",
         enabled: () => entry.properties[ScheduleFolderTreeAction.EDIT] === "true",
         visible: () => true,
         action: () => this.editTaskFolder(entry.path)
      };
   }

   private createDeleteTaskFolderAction(entry: AssetEntry): AssemblyAction {
      return {
         id: () => "task-tree delete-folder",
         label: () => "_#(js:Delete Folder)",
         icon: () => "",
         enabled: () => entry.properties[ScheduleFolderTreeAction.DELETE] === "true",
         visible: () => true,
         action: () => this.removeFolders(entry.path)
      };
   }

   private getMutiEditPath(editNodePath: string): string[] {
      let selectedPaths = [];

      if(this.selectedNodes) {
         selectedPaths = this.selectedNodes
            .filter(node => !!node)
            .map(node => node.data.path);
      }

      return selectedPaths && selectedPaths.includes(editNodePath) ? selectedPaths :
         [editNodePath];
   }

   dragTask(event: any, taskModel: ScheduleTaskModel) {
      let moveTasks: ScheduleTaskModel[] = [taskModel];

      if(this.selectedItems.includes(taskModel?.name)) {
         moveTasks = this.tasks
            .filter(item => !!item)
            .filter(item => this.selectedItems.includes(item.name));
      }

      const labels: string[] = moveTasks.map(e => e.label);
      const elem = GuiTool.createDragImage(labels, ["tasks"]);
      GuiTool.setDragImage(event, elem, this.zone, this.domService);
      this.dragService.put("tasks", JSON.stringify(moveTasks));
   }

   getTaskOwnerLabel(taskModel: ScheduleTaskModel) {
      let ownerLabel = taskModel.ownerAlias || taskModel.owner.name;

      return ownerLabel ? ownerLabel : "";
   }

   isCreateTaskEnabled(): boolean {
      let node = this.selectedNodes?.length > 0 ? this.selectedNodes[0] : null;
      return node == null && !this.noRootPermission ||
         !!node?.data?.properties && node.data.properties[ScheduleFolderTreeAction.READ] == "true";
   }
}
