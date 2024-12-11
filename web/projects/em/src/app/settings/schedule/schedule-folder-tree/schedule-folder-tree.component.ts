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
import { FlatTreeControl } from "@angular/cdk/tree";
import { HttpClient, HttpErrorResponse, HttpParams } from "@angular/common/http";
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { MatTreeFlatDataSource, MatTreeFlattener } from "@angular/material/tree";
import { Observable, Subscription, throwError } from "rxjs";
import { catchError } from "rxjs/operators";
import { ScheduleTaskList } from "../../../../../../shared/schedule/model/schedule-task-list";
import { ScheduleTaskModel } from "../../../../../../shared/schedule/model/schedule-task-model";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";
import { FlatTreeNode, FlatTreeNodeMenuItem } from "../../../common/util/tree/flat-tree-model";
import { FlatTreeSelectNodeEvent } from "../../../common/util/tree/flat-tree-view.component";
import {
   RepositoryFlatNode,
   RepositoryTreeNode
} from "../../content/repository/repository-tree-node";
import { EditTaskFolderDialogComponent } from "../edit-task-folder-dialog/edit-task-folder-dialog.component";
import { EditTaskFolderDialogModel } from "../model/edit-task-folder-dialog-model";
import { MoveTaskFolderRequest } from "../model/move-task-folder-request";
import { TaskListModel } from "../model/task-list-model";
import { ScheduleTaskDragService } from "../schedule-task-list/schedule-task-drag.service";
import { StompClientConnection } from "../../../../../../shared/stomp/stomp-client-connection";
import { EmScheduleChangeService } from "../schedule-task-list/em-schedule-change.service";

const TASKS_CHECK_FOLDER_URI = "../api/em/schedule/check-folder";
const TASKS_MOVE_FOLDER_URI = "../api/em/schedule/move-folder";
const TASKS_FOLDER_NAME_URI = "../api/em/schedule/rename-folder";
const NEW_TASKS_FOLDER_URI = "../api/em/schedule/folder/add";
const GET_TASK_FOLDER_EDIT_MODEL_URI = "../api/em/schedule/folder/editModel";
const CHECK_FOLDER_DEPENDENCY = "../api/em/schedule/folder/check-dependency";
const REMOVE_FOLDER_URI = "../api/em/schedule/folder/remove";
const CHECK_ADD_DUPLICATE_URI = "../api/em/schedule/add/checkDuplicate";

@Component({
  selector: "em-schedule-folder-tree",
  templateUrl: "./schedule-folder-tree.component.html",
  styleUrls: ["./schedule-folder-tree.component.scss"]
})
export class ScheduleFolderTreeComponent implements OnInit, OnDestroy {
   @Input() treeControl: FlatTreeControl<RepositoryFlatNode>;
   @Input() treeSource: MatTreeFlatDataSource<any, RepositoryFlatNode>;
   @Input() treeFlattener: MatTreeFlattener<any, RepositoryFlatNode>;

   @Input() set path(path: string) {
      this._path = path;

      if(this.treeSource.data != null && this.treeSource.data.length != 0) {
         this.refreshTree(true, this._path);
      }
   }

   private _path = "/";

   @Output() selectNode = new EventEmitter<FlatTreeSelectNodeEvent>();
   @Output() errorResponse = new EventEmitter<HttpErrorResponse>();
   @Output() tasksMoved = new EventEmitter<any>();
   selectedNodes: FlatTreeNode<RepositoryTreeNode>[] = [];
   private subscriptions: Subscription = new Subscription();

   private readonly getIcon = function(expanded: boolean) {
      return expanded ? "folder-open-icon" : "folder-icon";
   };

   constructor(private http: HttpClient, public dialog: MatDialog,
               private dragService: ScheduleTaskDragService,
               private scheduleChangeService: EmScheduleChangeService)
   {
   }

   ngOnInit(): void {
      this.refreshTree(true, this._path);
      this.subscriptions.add(this.scheduleChangeService.onFolderChange.subscribe(() => this.refreshTree()));
   }

   ngOnDestroy() {
      if(this.subscriptions != null) {
         this.subscriptions.unsubscribe();
         this.subscriptions = null;
      }
   }

   /**
    * @param expandRoot
    * @param selectedPath
    * @param expandPath true if need to expand the selectedPath, else not.
    */
   public refreshTree(expandRoot: boolean = false, selectedPath: string = null,
                      expandPath: boolean = false)
   {
      let expandedNodes: RepositoryFlatNode[] = this.treeControl.expansionModel.selected.slice();
      this.treeSource.data = [];
      const url = "../api/em/schedule/folder/get";
      this.treeControl.collapseAll();

      this.http.get<any>(url).subscribe(
         model => {
            this.treeSource.data = model.nodes;

            if((!selectedPath || selectedPath === "/") && (this.selectedNodes == null || this.selectedNodes.length == 0)) {
               let root: RepositoryFlatNode = new RepositoryFlatNode(this.treeSource.data[0].label, 0,
                  true, this.treeSource.data[0], false, true, null, this.getIcon);
               this.nodeSelected({node: root, event: null});
            }

            if(selectedPath != null) {
               if(this.selectedNodes[0]?.data.path != selectedPath) {
                  const nodes: RepositoryFlatNode[] = this.treeControl.dataNodes;
                  this.selectedNodes = [nodes.find(n => n.data.path === selectedPath)];
                  this.expandToPath(selectedPath, expandPath);
                  this.selectNode.emit({node: this.selectedNodes[0], event: null});
               }
               else {
                  this.expandToPath(selectedPath, expandPath);
               }
            }

            if(expandedNodes.length === 0) {
               if(expandRoot) {
                  const node = this.treeControl.dataNodes.find(n => n.data.path === "/");

                  if(!!node) {
                     this.treeControl.expand(node);
                  }
               }
            }
            else {
               this.treeControl.dataNodes.forEach(node => {
                  if(expandedNodes.findIndex((expandedNode) => expandedNode.equals(node)) != -1) {
                     this.treeControl.expand(node);
                  }
               });
            }
         });
   }

   public nodeSelected(evt: FlatTreeSelectNodeEvent): void {
      this.selectedNodes = [<FlatTreeNode<RepositoryTreeNode>>evt.node];
      this.selectNode.emit(evt);
   }

   selectNodes(nodes: FlatTreeNode<RepositoryTreeNode>[]): void {
      this.selectedNodes = nodes;

      if(!!nodes && nodes.length == 1) {
         this.selectNode.emit({node: nodes[0], event: null});
      }
   }

   contextMenuClick(node: FlatTreeNode<RepositoryTreeNode>) {
      if(!node || !node.data) {
         return;
      }

      let find = this.selectedNodes
         .filter(selected => !!selected)
         .some(selected => selected.data.path === node.data.path);

      if(!find) {
         this.selectNodes([node]);
      }
   }

   moveNodes(target: RepositoryFlatNode) {
      this.moveTaskFolder(target);
   }

   private expandToPath(path: string, expandSelf: boolean = false) {
      let folderNames = path.split("/");

      if(!expandSelf) {
         folderNames.splice(folderNames.length - 1, 1);
      }

      let currentPath = "";
      const nodes = this.treeControl.dataNodes;

      for(let folderName of folderNames) {
         if(currentPath != "") {
            currentPath += "/";
         }

         currentPath += folderName;
         let expandNode = nodes.find(n => n.data.path === currentPath);

         if(expandNode == null) {
            break;
         }
         else {
            this.treeControl.expand(expandNode);
         }
      }
   }

   get currentFolder(): RepositoryTreeNode {
      if(this.selectedNodes.length > 0) {
         return this.selectedNodes[0].data;
      }

      return null;
   }

   public newTaskFolder(node: RepositoryTreeNode): void {
      const dialogRef = this.dialog.open(EditTaskFolderDialogComponent, {
         role: "dialog",
         width: "500px",
         maxWidth: "100%",
         maxHeight: "100%",
         disableClose: true,
         data: {
            oldPath: (node.path ? node.path + "/" : "") + "x",
            securityEnabled: true,
            owner: {name: "null", orgID: null}
         }
      });

      dialogRef.afterClosed().subscribe((res) => {
         if(res) {
            this.http.post(CHECK_ADD_DUPLICATE_URI, {parent: node, folderName: res.folderName})
               .subscribe((data: boolean) => {
                  if(data) {
                     this.dialog.open(MessageDialog, this.setConfigs("_#(js:Error)",
                     "_#(js:common.duplicateName)", MessageDialogType.ERROR));
                  }
                  else {
                     // create a new task folder for selected node
                     this.http.post(NEW_TASKS_FOLDER_URI, {parent: node, folderName: res.folderName})
                        .pipe(catchError(error => this.handleError(error)))
                        .subscribe(() => {
                           this.refreshTree(false, this.selectedNodes[0].data.path, true);
                        });
               }
            });
         }
      });
   }

   public moveTaskFolder(target?: RepositoryFlatNode): void {
      let moveNodes: RepositoryTreeNode[] = [];
      const toFolder: RepositoryTreeNode = target.data;
      let sortedSelectedNodes: FlatTreeNode<RepositoryTreeNode>[] = [...this.selectedNodes];
      let moveTasks: ScheduleTaskModel[] = null;

      if(this.dragService.get("tasks") != null) {
         moveTasks = JSON.parse(this.dragService.get("tasks"));
         this.dragService.reset();
      }

      if(moveTasks == null) {
         sortedSelectedNodes.sort((node1, node2) => {
            return node1.level >= node2.level ? 1 : -2;
         });

         for(let selectedNode of sortedSelectedNodes) {
            if(!selectedNode) {
               continue;
            }

            const fromNode: RepositoryTreeNode = selectedNode.data;
            const curPath = fromNode.path;

            if(!fromNode || curPath === toFolder.path || fromNode.path === "/" ||
               this.isDescendant([fromNode], toFolder))
            {
               continue;
            }

            // do not move node when move ancestor node.
            if(this.isDescendant(moveNodes, fromNode)) {
               continue;
            }

            moveNodes.push(fromNode);
         }

         // do not move children to parent.
         moveNodes = moveNodes.filter(node => toFolder.children.indexOf(node) < 0);

         if(moveNodes.length == 0) {
            return;
         }
      }

      let moveFolderPath: string[] = moveNodes
         .filter(selectedNodeData => !!selectedNodeData)
         .map(selectedNodeData => selectedNodeData.path);

      let moveTaskFolderRequest: MoveTaskFolderRequest = {
         target: target.data,
         tasks: moveTasks,
         folders: moveFolderPath
      };

      this.http.post(TASKS_CHECK_FOLDER_URI, moveTaskFolderRequest).subscribe(exist => {
         if(!exist) {
            this.http.post(TASKS_MOVE_FOLDER_URI, moveTaskFolderRequest).subscribe(() => {
               let selectedPath = "";

               if(moveTasks?.length > 0) {
                  selectedPath = target.data.path;
               }
               else {
                  selectedPath = target.data.path;
                  selectedPath = selectedPath != "/" ?
                     target.data.path + "/" + moveNodes[0]?.label   : moveNodes[0]?.label;
               }

               this.refreshTree(false, selectedPath);

               if(moveTaskFolderRequest.tasks?.length > 0) {
                  this.tasksMoved.emit();
               }
            },
            (error) => {
               if(error?.error?.type == "MessageException") {
                  this.dialog.open(MessageDialog, this.setConfigs("_#(js:Error)",
                     error.error.message, MessageDialogType.ERROR));
               }
            });
         }
         else {
            this.dialog.open(MessageDialog, this.setConfigs("_#(js:Error)",
               "_#(js:common.duplicateName)", MessageDialogType.ERROR));
         }
      });
   }

   /**
    * Whether the searchNode is descendant of root node.
    * @param root
    * @param searchNode
    */
   private isDescendant(parents: RepositoryTreeNode[], searchNode: RepositoryTreeNode): boolean {
      if(!parents || parents.length == 0 || !searchNode) {
         return false;
      }

      return parents.some(parent => !!searchNode && !!parent && !!searchNode.path
         && searchNode.path.startsWith(parent.path));
   }

   excludeCurrentPath(parent: RepositoryTreeNode, originalPaths: string[]): void {
      if(!parent || parent.children.length === 0) {
         return;
      }

      for(let i = parent.children.length - 1; i >= 0; i--) {
         if(originalPaths.indexOf(parent.children[i].path) >= 0) {
            parent.children.splice(i, 1);
         }
         else {
            if(parent.children[i].children.length > 0) {
               this.excludeCurrentPath(parent.children[i], originalPaths);
            }
         }
      }
   }

   public editTaskFolder(node: RepositoryTreeNode): void {
      if(!node) {
         return;
      }

      let params = new HttpParams().set("folderPath", node.path);

      this.http.post<EditTaskFolderDialogModel>(GET_TASK_FOLDER_EDIT_MODEL_URI, null, {params})
         .subscribe(data => {
            const dialogRef = this.dialog.open(EditTaskFolderDialogComponent, {
               role: "dialog",
               width: "500px",
               maxWidth: "100%",
               maxHeight: "100%",
               disableClose: true,
               data: data
            });

            dialogRef.afterClosed().subscribe((res) => {
               if(res) {
                  this.http.post(TASKS_FOLDER_NAME_URI, res).subscribe(() => {
                     let newPath = res.oldPath;
                     const index = newPath.indexOf("/");

                     if(index != -1) {
                        newPath = newPath.substr(0, index + 1) + res.folderName;
                     }
                     else {
                        newPath = res.folderName;
                     }

                     this.refreshTree(false, newPath);
                  });
               }
            });
         });
   }

   removeTasks(node: RepositoryTreeNode): void {
      let body = (this.selectedNodes.length == 1) ? `_#(js:em.schedule.delete.confirm)` : `_#(js:em.schedule.deleteMultiple.confirm)`;
      const dialogRef = this.dialog.open(MessageDialog, this.setConfigs(`_#(js:Confirmation)`, body, MessageDialogType.DELETE));
      dialogRef.afterClosed().subscribe(result => {
         if(result) {
            let model = new TaskListModel([node.path]);
            let inSelected = this.selectedNodes
               .some(selected => selected.data && selected.data.path == node.path);

            if(inSelected) {
               model.taskNames = this.selectedNodes
                  .map(slected => slected.data.path)
                  .filter(path => path != "/");
            }

            this.http.post(CHECK_FOLDER_DEPENDENCY, model).subscribe(
               (dependencies: string[]) => {
                  if(dependencies.length) {
                     // we make a copy so that pointer won"t get messed up
                     let paramCopy = model.taskNames.slice();

                     // filter out all the dependency tasks by keeping those that are not found in dependencies
                     // we delete the filtered list
                     model.taskNames = paramCopy.filter(task => !dependencies.includes(task));

                     // if dependencies exist, we won"t delete them; let user know there are dependencies
                     this.dialog.open(MessageDialog, this.setConfigs(`_#(js:em.schedule.dependenciesFound)`,
                        `Because there are dependents of these tasks, we cannot delete the following: [${dependencies}]`,
                        MessageDialogType.DEPENDENCY
                     ));
                  }

                  // REMOVE TASKS
                  this.http.post<ScheduleTaskList>(REMOVE_FOLDER_URI, model).subscribe(
                     () => {
                        let pathParent = this.selectedNodes[0].data.path;
                        let index = pathParent.indexOf(this.selectedNodes[0].label);
                        pathParent = index > 0 ? pathParent.substr(0, index - 1) : "/";
                        this.refreshTree(false, pathParent);
                     },
                     (error) => {
                        const message = error.error != null && error.error.type == "MessageException" ?
                           error.error.message : "Failed to remove selected tasks";

                        this.dialog.open(MessageDialog, this.setConfigs(`_#(js:Error)`,
                           message, MessageDialogType.ERROR));
                     }
                  );
               },
               () => {
                  this.dialog.open(MessageDialog, this.setConfigs(`_#(js:Error)`,
                     "Failed to check task dependency",
                     MessageDialogType.ERROR));
               }
            );
         }
      });
   }

   private setConfigs(title: string, content: string, type: MessageDialogType): any {
      return {
         width: "350px",
         data: {
            title: title,
            content: content,
            type: type
         }
      };
   }

   public editFolderEnabled(): boolean {
      return this.selectedNodes.length > 0 &&
         !this.selectedNodes
            .filter(node => !!node && !!node.data)
            .some(node => node.data.path === "/");
   }

   onContextMenu(node: FlatTreeNode<any>, menu: FlatTreeNodeMenuItem): void {
      switch(menu.name) {
         case "new-task-folder":
            this.newTaskFolder(node.data);
            break;
         case "edit-task-folder":
            this.editTaskFolder(node.data);
            break;
         case "delete-task-folder":
            this.removeTasks(node.data);
            break;
         default:
            console.warn("Unsupported context action: " + menu.name);
      }
   }

   private handleError<T>(error: HttpErrorResponse): Observable<T> {
      this.errorResponse.emit(error);
      return throwError(error);
   }
}
