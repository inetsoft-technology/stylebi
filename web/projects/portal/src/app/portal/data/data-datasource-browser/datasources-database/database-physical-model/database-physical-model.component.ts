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
import {
   Component, OnInit, OnDestroy, DoCheck, ViewChild, TemplateRef, HostListener
} from "@angular/core";
import { CanComponentDeactivate } from "../../../../../../../../shared/util/guard/can-component-deactivate";
import { Observable, of, Subscription } from "rxjs";
import { NotificationData } from "../../../../../widget/repository-tree/repository-tree.service";
import { SplitPane } from "../../../../../widget/split-pane/split-pane.component";
import { PhysicalTableAliasesDialog } from "../../../../dialog/physical-table-aliases-dialog/physical-table-aliases-dialog.component";
import { DataModelNameChangeService } from "../../../services/data-model-name-change.service";
import { HttpClient, HttpParams } from "@angular/common/http";
import { ActivatedRoute, ParamMap, Router } from "@angular/router";
import { FolderChangeService } from "../../../services/folder-change.service";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { PhysicalModelDefinition } from "../../../model/datasources/database/physical-model/physical-model-definition";
import { Tool } from "../../../../../../../../shared/util/tool";
import { TreeNodeModel } from "../../../../../widget/tree/tree-node-model";
import { PhysicalTableModel } from "../../../model/datasources/database/physical-model/physical-table-model";
import { WarningModel } from "../../../model/datasources/database/physical-model/warning-model";
import { NameChangeModel } from "../../../model/name-change-model";
import { NotificationsComponent } from "../../../../../widget/notifications/notifications.component";
import { DatabaseTreeNodeModel } from "../../../model/datasources/database/physical-model/database-tree-node-model";
import { PhysicalModelTreeNodeModel } from "../../../model/datasources/database/physical-model/physical-model-tree-node-model";
import { tap } from "rxjs/operators";
import { PhysicalTableType } from "../../../model/datasources/database/physical-model/physical-table-type.enum";
import { FolderChangeModel } from "../../../model/folder-change-model";
import { AddPhysicalModelEvent } from "../../../model/datasources/database/events/add-physical-model-event";
import { ModifyPhysicalModelEvent } from "../../../model/datasources/database/events/modify-physical-model-event";
import { ToolbarAction } from "../../../../../widget/toolbar/toolbar-action";
import { PhysicalModelTableTreeComponent } from "./physical-model-table-tree/physical-model-table-tree.component";
import { ComponentTool } from "../../../../../common/util/component-tool";
import { ValidatorFn } from "@angular/forms";
import { ValidatorMessageInfo } from "../../../../../widget/dialog/input-name-dialog/input-name-dialog.component";
import { InlineViewDialogModel } from "../../../../dialog/inline-view-dialog/inline-view-dialog-model";
import { GuiTool } from "../../../../../common/util/gui-tool";
import { EditTableEvent } from "../../../model/datasources/database/events/edit-table-event";
import { SetAutoAliasEvent } from "../../../model/datasources/database/events/set-auto-alias-event";
import { DataPhysicalModelService } from "../../../services/data-physical-model.service";
import { GraphModel } from "../../../model/datasources/database/physical-model/graph/graph-model";
import { DropdownOptions } from "../../../../../widget/fixed-dropdown/dropdown-options";
import { ActionsContextmenuComponent } from "../../../../../widget/fixed-dropdown/actions-contextmenu.component";
import { FixedDropdownService } from "../../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { AssemblyActionGroup } from "../../../../../common/action/assembly-action-group";
import { DatabaseTreeNodeType } from "../../../model/datasources/database/database-tree-node-type";
import { AssetEntryHelper } from "../../../../../common/data/asset-entry-helper";
import { GraphViewModel } from "../../../model/datasources/database/physical-model/graph/graph-view-model";
import { GraphNodeModel } from "../../../model/datasources/database/physical-model/graph/graph-node-model";

const PHYSICAL_MODELS_INLINE_VIEW_URI: string = "../api/data/physicalmodel/inlineView/";
const PHYSICAL_MODELS_ALIAS_URI: string = "../api/data/physicalmodel/alias/";
const PHYSICAL_MODELS_AUTO_ALIAS_URI: string = "../api/data/physicalmodel/autoAlias";
const PHYSICAL_MODELS_URI: string = "../api/data/physicalmodel/models";
const PHYSICAL_MODEL_TABLE_URI: string = "../api/data/physicalmodel/table/";
const FULL_OUTER_URI: string = "../api/data/physicalmodel/fullOuterJoin";
const DATABASE_TREE_URI: string = "../api/data/physicalmodel/tree/nodes";
const DATABASE_TREE_ALL_URI: string = "../api/data/physicalmodel/tree/allNodes";
const MODEL_WARNING_URI: string = "../api/data/physicalmodel/warnings/";
const DESTROY_MODEL_URI: string = "../api/data/physicalmodel/destroy";
const HEARTBEAT_MODEL_URI: string = "../api/data/physicalmodel/heartbeat";

@Component({
   selector: "database-physical-model",
   templateUrl: "database-physical-model.component.html",
   styleUrls: ["database-model-pane.scss", "database-physical-model.component.scss"]
})
export class DatabasePhysicalModelComponent implements OnInit, DoCheck, OnDestroy, CanComponentDeactivate {
   @ViewChild("splitPane") splitPane: SplitPane;
   @ViewChild("horizontalSplitPane") horizontalSplitPane: SplitPane;
   @ViewChild("notifications") notifications: NotificationsComponent;
   @ViewChild("tableTree") tableTree: PhysicalModelTableTreeComponent;
   @ViewChild("autoJoinTablesDialog") autoJoinTablesDialog: TemplateRef<any>;
   @ViewChild("aliasTableDialog") aliasTableDialog: TemplateRef<any>;
   @ViewChild("inlineViewDialog") inlineViewDialog: TemplateRef<any>;
   readonly DEFAULT_VERTICAL_SIZE: number[] = [55, 45];
   scale: number = 1;
   aliasValidators: ValidatorFn[] = this.dataPhysicalModelService.aliasValidators;
   aliasValidatorMessages: ValidatorMessageInfo[]
      = this.dataPhysicalModelService.aliasValidatorMessages;
   previousModel: PhysicalModelDefinition;
   originalName: string;
   editing: boolean = false;
   isModified: boolean = false;
   joinEditing = false;
   initialized: boolean = false;
   supportFullOuterJoin: boolean = false;
   databaseRoot: TreeNodeModel = {
      children: [],
      expanded: true
   };
   editingTable: PhysicalTableModel;
   warning: WarningModel;
   treeCollapsed: boolean = false;
   fullScreen = false;
   filterTablesString: string = "";
   showOnlySelectedTables: boolean = false;
   tableDuplicateCheck: (name: string) => Observable<boolean>;
   aliasOldName: string;
   inlineViewDialogModel: InlineViewDialogModel;
   searchMode: boolean;
   loadedFullTableTree: boolean = false;
   private routeParamSubscription: Subscription;
   private fullScreenSubscription: Subscription;
   private dataModelNameChangeSubscription: Subscription;
   private heartbeatIntervalId: any;
   public hiddenCollapsed: boolean = true;
   readonly INIT_TREE_PANE_SIZE = 35;
   treePaneSize: number = this.INIT_TREE_PANE_SIZE;
   private subscription: Subscription;
   loadingTree: boolean = false;
   private graphViewModel: GraphViewModel;
   selectedGraphNode: GraphNodeModel[] = [];

   actions: ToolbarAction[] =
      [
         {
            label: "_#(js:Auto Join Tables)",
            iconClass: "auto-join-icon",
            buttonClass: "",
            tooltip: () => "_#(js:Auto Join Tables)",
            enabled: () => !this.joinEditing,
            visible: () => this.physicalModel.tables.length > 1,
            action: () => this.showAutoJoinTablesDialog()
         },
         {
            label: "_#(js:Create Inline View)",
            iconClass: "file-sql-icon",
            buttonClass: "",
            tooltip: () => "_#(js:Create Inline View)",
            enabled: () => !this.joinEditing,
            visible: () => true,
            action: () => this.toggleInlineViewEditor()
         },
         {
            label: "_#(js:data.physicalmodel.allTables)",
            iconClass: "eye-icon",
            buttonClass: "",
            tooltip: () => "_#(js:data.physicalmodel.allTables)",
            enabled: () => !this.joinEditing,
            visible: () => this.showOnlySelectedTables,
            action: () => this.showOnlySelectedTables = false
         },
         {
            label: "_#(js:data.physicalmodel.onlySelectedTables)",
            iconClass: "eye-off-icon",
            buttonClass: "",
            tooltip: () => "_#(js:data.physicalmodel.onlySelectedTables)",
            enabled: () => !this.joinEditing,
            visible: () => !this.showOnlySelectedTables,
            action: () => this.showOnlySelectedTables = true
         }
      ];

   get physicalModel(): PhysicalModelDefinition {
      return this.dataPhysicalModelService.physicalModel;
   }

   get databaseName(): string {
      return this.dataPhysicalModelService.database;
   }

   set databaseName(database: string) {
      this.dataPhysicalModelService.database = database;
   }

   get parent(): string {
      return this.dataPhysicalModelService.parent;
   }

   set parent(parent: string) {
      this.dataPhysicalModelService.parent = parent;
   }

   get modelInitializing(): boolean {
      return this.dataPhysicalModelService.loadingModel;
   }

   get displayTitle(): string {
      return !this.physicalModel ? "" : this.physicalModel.name + (this.isModified ? "*" : "");
   }

   constructor(private dataModelNameChangeService: DataModelNameChangeService,
               private folderChangeService: FolderChangeService,
               private dataPhysicalModelService: DataPhysicalModelService,
               private httpClient: HttpClient,
               private modalService: NgbModal,
               private dropdownService: FixedDropdownService,
               private route: ActivatedRoute,
               private router: Router)
   {
      this.subscription = this.dataPhysicalModelService.modelChange
         .subscribe(() =>
         {
            this.dataPhysicalModelService.refreshModel().then(() => {
               this.refreshDatabaseRoots(true);
               this.refreshTreeSelectStatus(this.databaseRoot);
               this.refreshEditingTable();
            });
         });

      this.subscription.add(this.dataPhysicalModelService.onNotification.subscribe(data => {
         this.notify(data);
      }));

      this.subscription.add(this.dataPhysicalModelService.onRefreshWarning.subscribe(runtimeId => {
         this.refreshWarnings0(runtimeId);
      }));
   }

   ngOnInit(): void {
      // subscribe to route parameters and update current database model
      this.routeParamSubscription = this.route.paramMap
         .subscribe((params: ParamMap) => {
            this.databaseName = Tool.byteDecode(params.get("databasePath"));
            this.originalName = Tool.byteDecode(params.get("physicalName"));
            this.editing = !params.get("create");
            this.parent = Tool.byteDecode(params.get("parent"));
            this.refreshSupportFullOuterJoin();
            //edit table should be hidden when open another(create/editing) physical model.
            this.editingTable = null;

            if(this.editing) {
               this.dataPhysicalModelService.resetModel();
               this.openPhysicalModel();
            }
            else {
               const desc = params.get("desc") || "";
               const folder = params.get("folder") || "";
               this.dataPhysicalModelService.resetModel();
               this.physicalModel.name = this.originalName;
               this.physicalModel.folder = folder;
               this.physicalModel.description = desc;
               this.physicalModel.connection = params.get("connection");
               this.previousModel = Tool.clone(this.physicalModel);
               this.isModified = true;
               this.initialized = true;
               this.createPhysicalModel();
            }

            this.resetSearchMode();
         });

      this.dataModelNameChangeSubscription = this.dataModelNameChangeService.nameChangeObservable
         .subscribe(
            (data: NameChangeModel) => {
               if(!!data && this.originalName === data.oldName) {
                  if(data.newName != null) {
                     this.originalName = data.newName;
                     this.physicalModel.name = data.newName;
                     this.previousModel.name = data.newName;
                  }
                  else {
                     // model was deleted, go to datasources
                     this.router.navigate(["/portal/tab/data/datasources"],
                        {queryParams: {path: "/", scope: AssetEntryHelper.QUERY_SCOPE}});
                  }
               }
            }
         );

      this.fullScreenSubscription = this.dataPhysicalModelService.onFullScreen.subscribe((fullScreen) => {
         this.fullScreen = fullScreen;
         this.hiddenCollapsed = !fullScreen;
         this.treeCollapsed = fullScreen;
         this.updateDataTreePane();
      });

      this.heartbeatIntervalId = setInterval(() => {
         let params: HttpParams = new HttpParams().set("id", this.physicalModel.id);

         this.httpClient.get(HEARTBEAT_MODEL_URI, { params: params})
            .subscribe(timeout => {
               if(timeout) {
                  clearInterval(this.heartbeatIntervalId);
               }
         }, error1 => clearInterval(this.heartbeatIntervalId));
      }, 60000);
   }

   private refreshTreeSelectStatus(node: TreeNodeModel): void {
      if(!!!node || !node.leaf && !!!node.children) {
         return;
      }

      if(node.leaf) {
         this.refreshLeafNodeSelectStatus(node);

         return;
      }

      node.children.forEach(child => this.refreshTreeSelectStatus(child));
   }

   private refreshLeafNodeSelectStatus(node: TreeNodeModel) {
      const childData: PhysicalModelTreeNodeModel = <PhysicalModelTreeNodeModel> node.data;
      let findTable = this.physicalModel.tables
         .find((table) => {
            if(!table.alias) {
               return this.getTablePath(table) == childData.path;
            }
            else {
               return table.qualifiedName == childData.qualifiedName &&
                  this.databaseName + "/" + (table.alias || table.name) == childData.path;
            }
         });

      if(findTable) {
         childData.selected = true;
         childData.baseTable = findTable.baseTable;
      }
      else {
         childData.selected = false;
      }

      childData.autoAlias = this.physicalModel.tables
         .some(table => table.qualifiedName == childData.qualifiedName && table.autoAliasesEnabled);

      childData.joins = this.physicalModel.tables
         .some(table => {
            if(table.qualifiedName === childData.qualifiedName && table.joins.length > 0) {
               return true;
            }

            return this.physicalModel.tables
               .filter(t => t != table)
               .some(t => t.joins.some(join => join.foreignTable === childData.qualifiedName));
         });
   }

   /**
    * table path start with current connection, but tree node path start with the base
    * database name, here we use databaseName to replace the connection in table path
    * to search the selected tree nodes.
    */
   private getTablePath(table: PhysicalTableModel): string {
      let tpath = !!table ? table.path : null;

      if(!!this.physicalModel?.connection && !!tpath) {
         let arr = tpath.split("/");

         if(arr.length > 0) {
            arr[0] = this.databaseName;
         }

         tpath = arr.join("/");
      }

      return tpath;
   }

   showAutoJoinTablesDialog() {
      this.modalService.open(this.autoJoinTablesDialog, { backdrop: "static" }).result
         .then(
            (result: string) => {
               this.dataPhysicalModelService.emitModelChange();
            },
            () => {}
         );
   }

   showAutoAliasDialog(node: TreeNodeModel) {
      this.changeEditingTable(node);
      this.showAutoAliasDialog0();
   }

   showAutoAliasDialog0(): void {
      const dialog = ComponentTool.showDialog(this.modalService, PhysicalTableAliasesDialog, (result: PhysicalTableModel) => {
         let event: SetAutoAliasEvent = new SetAutoAliasEvent(result, this.physicalModel.id);
         this.httpClient.put(PHYSICAL_MODELS_AUTO_ALIAS_URI, event).subscribe(() => {
            this.dataPhysicalModelService.emitModelChange();
         });
      }, {
         backdrop: "static",
         scrollable: true
      });

      dialog.physicalModel = Tool.clone(this.physicalModel);
      dialog.table = Tool.clone(this.editingTable);
      dialog.isDuplicateTableName = this.isDuplicateTableName;
   }

   createAutoAliasByGraph(qualifiedName: string): void {
      this.changeEditingTableByName(qualifiedName);
      this.showAutoAliasDialog0();
   }

   /**
    * Open the alias table dialog and then create/update the alias with the returned name.
    * @param node the node an alias is being created for
    */
   showCreateAliasDialog(node: TreeNodeModel = null): void {
      node = node ?? this.tableTree.selectedNode[0];
      this.changeEditingTable(node);

      this.tableDuplicateCheck = (name: string) => {
         if(name == this.aliasOldName) {
            return of(false);
         }
         else {
            return of(this.isDuplicateTableName(name));
         }
      };

      this.modalService.open(this.aliasTableDialog, { backdrop: "static" }).result
         .then(
            (result: string) => {
               this.createAliasTable(node, result);
            },
            () => {}
         );
   }

   /**
    * Create an alias table from the given tree node and name.
    * @param node       the node to create an alias table from
    * @param aliasName  the name of the alias table
    */
   private createAliasTable(node: TreeNodeModel, aliasName: string): void {
      const nodeData: PhysicalModelTreeNodeModel = <PhysicalModelTreeNodeModel> node.data;

      if(!!this.aliasOldName) {
         const aliasTable: PhysicalTableModel = this.physicalModel.tables
            .find(table => table.qualifiedName == nodeData.qualifiedName);
         //this.updateForeignJoins(aliasTable.qualifiedName, aliasName);
         nodeData.alias = aliasName;
         nodeData.qualifiedName = aliasName;
         nodeData.path = this.databaseName + "/" + aliasName;
         node.label = this.createTableNodeLabel(nodeData);
         aliasTable.alias = aliasName;
         aliasTable.qualifiedName = aliasName;
         let event = new EditTableEvent(this.physicalModel.id, aliasTable, this.aliasOldName);
         this.httpClient.post<PhysicalModelDefinition>(PHYSICAL_MODELS_ALIAS_URI + "modify", event)
            .subscribe(() => {
               this.dataPhysicalModelService.emitModelChange();
            });
      }
      else {
         const aliasNodeData: PhysicalModelTreeNodeModel = Tool.clone(nodeData);
         aliasNodeData.aliasSource = aliasNodeData.alias || aliasNodeData.name;
         aliasNodeData.alias = aliasName;
         aliasNodeData.selected = true;
         aliasNodeData.path = this.databaseName + "/" + aliasName;
         this.databaseRoot.children.push({
            label: this.createTableNodeLabel(aliasNodeData),
            data: aliasNodeData,
            leaf: true,
            type: aliasNodeData.type
         });

         let newTable: PhysicalTableModel = this.createPhysicalTableModel(aliasNodeData);
         let event = new EditTableEvent(this.physicalModel.id, newTable);

         this.httpClient.post<PhysicalModelDefinition>(PHYSICAL_MODELS_ALIAS_URI + "add", event)
            .subscribe((invalidMsg: any) => {
               if(!!invalidMsg && !!invalidMsg.body) {
                  ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                     invalidMsg.body);
               }
               else {
                  this.dataPhysicalModelService.emitModelChange();
               }
            });
      }
   }

   /**
    * Update all table joins that use the given old table name to use the given new table name or
    * delete all joins that used the given older table name.
    * @param oldName       the old table name
    * @param newName       the new table name
    * @param deleteJoins   true if should delete joins using the old table name
    */
   private updateForeignJoins(oldName: string, newName: string, deleteJoins: boolean = false): void
   {
      this.physicalModel.tables.forEach(table => {
         if(deleteJoins) {
            table.joins = table.joins
               .filter(join => join.foreignTable != oldName);
         }
         else {
            table.joins.filter(join => join.foreignTable == oldName)
               .forEach(join => join.foreignTable = newName);
         }
      });
   }

   private findEditingInlineNode(): TreeNodeModel {
      if(!!!this.editingTable) {
         return null;
      }

      return this.databaseRoot.children
         .find(node => node.data && (node.data.qualifiedName
            === this.editingTable.qualifiedName));
   }

   toggleInlineViewEditor(edit: boolean = false): void {
      const node = this.findEditingInlineNode();

      if(edit && !!node) {
         // edit
         this.editView(node);
      }
      else {
         // create
         this.showCreateInlineViewDialog();
      }
   }

   /**
    * Edit the selected sql table.
    */
   editView(node: TreeNodeModel): void {
      this.changeEditingTable(node);

      if(this.editingTable.type == PhysicalTableType.VIEW) {
         this.showCreateInlineViewDialog(node);
      }
   }

   /**
    * Open the inline view dialog and then create/update the inline view.
    */
   showCreateInlineViewDialog(node?: TreeNodeModel): void {
      let nodeData: PhysicalModelTreeNodeModel;
      let oldName: string;
      let oldSql: string;

      if(node) {
         nodeData = <PhysicalModelTreeNodeModel> node.data;
         oldName = nodeData.alias || nodeData.name;
         oldSql = nodeData.sql;
      }

      this.inlineViewDialogModel = new InlineViewDialogModel(oldName, oldSql);
      this.tableDuplicateCheck = (name: string) => {
         if(name == oldName) {
            return of(false);
         }
         else {
            return of(this.isDuplicateTableName(name));
         }
      };

      this.modalService.open(this.inlineViewDialog, { backdrop: "static" }).result
         .then(
            (result: InlineViewDialogModel) => {
               const newName: string = result.name;

               if(node) {
                  const viewTable: PhysicalTableModel = this.physicalModel.tables
                     .find(table => table.qualifiedName == nodeData.qualifiedName);
                  const sqlChanged: boolean = oldSql != result.sql;

                  if(newName != oldName) {
                     if(nodeData.alias != null) {
                        nodeData.alias = newName;
                     }
                     else {
                        nodeData.name = newName;
                     }

                     nodeData.qualifiedName = newName;
                     nodeData.path = this.databaseName + "/" + newName;
                     node.label = newName;

                     if(viewTable.alias != null) {
                        viewTable.alias = newName;
                     }
                     else {
                        viewTable.name = newName;
                     }

                     viewTable.qualifiedName = newName;
                     viewTable.path = nodeData.path;
                  }

                  if(sqlChanged) {
                     nodeData.sql = result.sql;
                     viewTable.sql = result.sql;
                     // if sql changes, reset joins
                     viewTable.joins = [];
                  }

                  let event: EditTableEvent = new EditTableEvent(this.physicalModel.id, viewTable,
                     oldName);
                  this.httpClient.post<PhysicalModelDefinition>(PHYSICAL_MODELS_INLINE_VIEW_URI + "modify", event)
                     .subscribe(() => {
                        this.dataPhysicalModelService.emitModelChange();
                     });

               }
               else {
                  nodeData = {
                     path: this.databaseName + "/" + newName,
                     name: newName,
                     type: "table",
                     catalog: null,
                     schema: null,
                     qualifiedName: newName,
                     alias: null,
                     sql: result.sql,
                     selected: true
                  };
                  node = {
                     label: newName,
                     data: nodeData,
                     leaf: true,
                     type: nodeData.type
                  };
                  this.databaseRoot.children.push(node);
                  this.tableTree.selectedNodes = [];
                  this.tableTree.selectedNodes.push(node)
                  this.selectNode([node]);
                  let newTable = this.createPhysicalTableModel(nodeData);
                  let event: EditTableEvent = new EditTableEvent(this.physicalModel.id, newTable);
                  this.httpClient.post<PhysicalModelDefinition>(PHYSICAL_MODELS_INLINE_VIEW_URI + "add", event)
                     .subscribe(() => {
                        this.dataPhysicalModelService.emitModelChange();
                     });
               }
            },
            () => {}
         );
   }

   /**
    * Create a alias table.
    * @param node
    */
   createAlias(node: TreeNodeModel): void {
      this.aliasOldName = null;
      this.showCreateAliasDialog(node);
   }

   /**
    * Edit the selected alias.
    */
   editAlias(node: TreeNodeModel): void {
      this.changeEditingTable(node);
      this.aliasOldName = (<PhysicalModelTreeNodeModel> node.data).alias;
      this.showCreateAliasDialog(node);
   }

   get treePaneCollapsed(): boolean {
      return !!this.splitPane && this.splitPane.getSizes()[1] < 10;
   }

   private refreshView() {
      this.refreshWarnings();
      this.refreshDatabaseRoots();
   }

   /**
    * Send request to get tables under database and add them to root tree node.
    */
   private refreshDatabaseRoots(refreshAliasAndView: boolean = false): void {
      // clear alias
      if(!!this.databaseRoot && !!this.databaseRoot.children) {
         this.databaseRoot.children = this.databaseRoot.children.filter(node =>
            !!!node.data.alias && !!!node.data.sql);
      }

      if(refreshAliasAndView) {
         this.addAliasInlineNodes(this.databaseRoot.children);
         return;
      }

      this.loadingTree = true;

      // add alias
      this.openFolder()
         .subscribe(
            data => {
               this.loadingTree = false;
               // add alias and inline view tables to tree
               this.addAliasInlineNodes(data);
               this.databaseRoot.children = data;
               this.showOnlySelectedTables = false;
            },
            err => {}
         );
   }

   private addAliasInlineNodes(nodes: TreeNodeModel[], onlyAlias = false) {
      if(!nodes) {
         nodes = [];
      }

      // add alias and inline view tables to tree
      this.physicalModel.tables.forEach(table => {
         if(!onlyAlias && table.type == PhysicalTableType.VIEW || !!table.alias) {
            const nodeData: PhysicalModelTreeNodeModel = {
               path: this.databaseName + "/" + (table.alias || table.name),
               name: table.name || table.alias,
               type: "table",
               catalog: table.catalog,
               schema: table.schema,
               qualifiedName: table.qualifiedName,
               alias: table.alias,
               sql: table.sql,
               selected: true,
               aliasSource: table.aliasSource,
               baseTable: table.baseTable
            };

            nodes.push({
               label: this.createTableNodeLabel(nodeData),
               data: nodeData,
               leaf: true,
               type: nodeData.type
            });
         }
      });
   }
   /**
    * Helper method to create a tree node label for the given physical model node data.
    * @param nodeData   the physical model node data
    * @returns {string} the tree node label
    */
   private createTableNodeLabel(nodeData: PhysicalModelTreeNodeModel): string {
      let label: string = nodeData.alias || nodeData.name;

      if(!!nodeData.alias) {
         if(this.isAliasTable(nodeData.aliasSource)) {
            label += " [" + nodeData.aliasSource + "]";
         }
         else {
            label += " [" + (nodeData.catalog ? (nodeData.catalog + ".") : "") +
               (nodeData.schema ? (nodeData.schema + ".") : "") + nodeData.name + "]";
         }
      }

      return label;
   }

   private isAliasTable(tableName: string): boolean {
      return this.physicalModel.tables.some((table) => table.alias == tableName);
   }

   /**
    * Send request to get the physical model definition for the given name and database.
    */
   private openPhysicalModel(): void {
      this.dataPhysicalModelService.openPhysicalModel(this.databaseName, this.originalName, this.parent)
         .subscribe((data) => {
            this.previousModel = Tool.clone(data);
            this.isModified = false;
            this.initialized = true;
            this.refreshView();
         });
   }

   private createPhysicalModel(): void {
      this.dataPhysicalModelService.createPhysicalModel(this.parent).subscribe(data => this.refreshView());
   }

   /**
    * Send request to get the warnings for the current physical model.
    */
   private refreshWarnings(callback?: Function, confirm?: boolean): void {
      this.refreshWarnings0(this.physicalModel.id, callback, confirm);
   }

   /**
    * Send request to get the warnings for the current physical model.
    */
   private refreshWarnings0(runtimeId: string, callback?: Function, confirm?: boolean): void {
      if(!!!runtimeId) {
         return;
      }

      this.httpClient.get<WarningModel>(MODEL_WARNING_URI + Tool.byteEncode(runtimeId))
         .subscribe(
            data => {
               if(!callback) {
                  this.warning = data;
                  return;
               }

               // valid
               if(!!callback && !data) {
                  callback();
               }
               // invalid, show confirm dialog.
               else if(!!callback && !!data && !!confirm) {
                  if(data.canContinue) {
                     ComponentTool.showConfirmDialog(
                        this.modalService, "_#(js:Confirm)",
                        `${data.message} _#(js:designer.qb.partitionProp.confirmMsg2)`)
                        .then(result => {
                           if(result == "ok") {
                              callback();
                           }

                           this.warning == data;
                        });
                  }
                  else {
                     ComponentTool
                        .showMessageDialog(this.modalService, "_#(js:Error)", data.message)
                        .then(() => this.warning = data);
                  }
               }
            },
            err => {
               this.notifications.danger("_#(js:data.physicalmodel.refreshDataError)");
            }
         );
   }

   private notify(data: NotificationData): void {
      const type = data.type;
      const content = data.content;

      switch(type) {
         case "success":
            this.notifications.success(content);
            break;
         case "info":
            this.notifications.info(content);
            break;
         case "warning":
            this.notifications.warning(content);
            break;
         case "danger":
            this.notifications.danger(content);
            break;
         default:
            this.notifications.warning(content);
      }
   }

   /**
    * Send request to check if database supports full outer join.
    */
   private refreshSupportFullOuterJoin(): void {
      let params: HttpParams = new HttpParams().set("database", this.databaseName);

      if(!!this.physicalModel && !!this.physicalModel.connection) {
         params = params.set("additional", this.physicalModel.connection);
      }

      this.httpClient.get<boolean>(FULL_OUTER_URI, { params: params })
         .subscribe(
            data => {
               this.supportFullOuterJoin = data;
            },
            err => {}
         );
   }

   /**
    * Send request to save the physical model on the server.
    */
   save(): void {
      let callback = () => {
         let request: Observable<PhysicalModelDefinition>;

         if(this.editing) {
            request = this.httpClient.put<PhysicalModelDefinition>(
               PHYSICAL_MODELS_URI,
               new ModifyPhysicalModelEvent(this.databaseName, this.originalName,
                  this.physicalModel, this.parent));
         }
         else {
            request = this.httpClient.post<PhysicalModelDefinition>(
               PHYSICAL_MODELS_URI,
               new AddPhysicalModelEvent(this.databaseName, this.physicalModel, this.parent));
         }

         request
            .subscribe(
               data => {
                  this.isModified = false;
                  this.originalName = this.physicalModel.name;

                  if(!this.editing) {
                     this.folderChangeService.emitFolderChange(new FolderChangeModel(true));
                     this.editing = true;
                  }

                  let msg = !!this.parent ? "_#(js:data.physicalmodel.extended.saveModelSuccess)" :
                     "_#(js:data.physicalmodel.saveModelSuccess)";
                  this.notifications.success(msg);
               },
               err => {
                  this.notifications.danger("_#(js:data.physicalmodel.saveModelError)");
               }
            );
      };

      this.refreshWarnings(callback, true);
   }

   toggleRepositoryTreePane(): void {
      if(this.treePaneCollapsed) {
         this.splitPane.setSizes(this.DEFAULT_VERTICAL_SIZE);
      }
      else {
         this.splitPane.collapse(1);
      }
   }

   /**
    * Called when user expands folder on tree. Open folder and attach contents as children of node.
    * @param node    the expanded tree node
    */
   expandNode(node: TreeNodeModel): void {
      if(!node.childrenLoaded && (!node.children || node.children.length == 0)) {
         this.openFolder(node)
            .subscribe(
               data => {
                  node.children = data;
                  node.childrenLoaded = true;
               },
               err => {
               }
            );
      }
   }

   /**
    * Called when user toggles table inclusion on tree. Add/remove table to/from physical model.
    * @param node the node toggled
    */
   checkboxToggledNode(node: TreeNodeModel): void {
      const nodeData: PhysicalModelTreeNodeModel = <PhysicalModelTreeNodeModel> node.data;

      // can not edit base table.
      if(nodeData.baseTable) {
         return;
      }

      // check on to add table
      if(nodeData.selected) {
         // if node qualified name is already present, deselect node and open alias dialog
         if(this.isDuplicateTableName(nodeData.qualifiedName)) {
            this.showCreateAliasDialog(node);
            nodeData.selected = false;
            return;
         }

         const newTable: PhysicalTableModel = this.createPhysicalTableModel(nodeData);
         let event: EditTableEvent = new EditTableEvent(this.physicalModel.id, newTable,
            null, nodeData);
         this.httpClient.post<PhysicalModelDefinition>(PHYSICAL_MODEL_TABLE_URI + "add", event)
            .subscribe(() => this.dataPhysicalModelService.emitModelChange());

         if(this.tableTree.selectedNode == node) {
            this.editingTable = newTable;
         }
      }
      else {
         if(!!nodeData.alias || !!nodeData.sql || nodeData.joins ||
            nodeData.autoAlias || !!nodeData.aliasSource)
         {
            const message: string = "_#(js:data.physicalmodel.confirmRemoveTable)";
            ComponentTool.showConfirmDialog(this.modalService, "_#(js:Warning)",
               message).then((result) => {
               if("ok" === result) {
                  this.removePhysicalTable(node, nodeData);
               }
               else {
                  // user did not confirm removal, reselect node
                  nodeData.selected = true;
               }
            });
         }
         else {
            this.removePhysicalTable(node, nodeData);
         }
      }
   }

   /**
    * Called when user selects node on tree. Navigate router to the selected nodes path.
    * @param node   the selected node on tree
    */
   selectNode(node: TreeNodeModel[]): void {
      if(node != null && node.length == 1) {
         this.selectedGraphNode = [];
         this.selectPhysicalGraphNode(node[0])
         this.changeEditingTable(node[0]);
      }
      else if(node == null) {
         this.selectedGraphNode = [];
         this.changeEditingTable(null);
      }
      else {
         this.editingTable = null;
         node.some(n => {
            this.selectPhysicalGraphNode(n);
         })
      }
   }

   onPhysicalGraph(graphViewModel: GraphViewModel): void {
      this.graphViewModel = graphViewModel;
   }

   selectPhysicalGraphNode(node: TreeNodeModel): void {
      if(!(node && this.graphViewModel && this.graphViewModel.graphs)) {
         return;
      }

      for(let i = 0; i < this.graphViewModel.graphs.length; i++) {
         if(!this.graphViewModel.graphs[i].node) {
            continue;
         }

         if(node.data?.path == this.graphViewModel.graphs[i].node.treeLink) {
            this.selectedGraphNode.push(this.graphViewModel.graphs[i].node);
         }
      }
   }

   private changeEditingTable(node: TreeNodeModel) {
      this.changeEditingTableByName(node?.data.qualifiedName);
   }

   private changeEditingTableByName(qualifiedName: string) {
      if(!!qualifiedName) {
         this.editingTable = this.physicalModel.tables
            .find(table => qualifiedName === table.qualifiedName);
      }
      else {
         this.editingTable = null;
      }
   }

   get databaseParr(): string {
      return this.databaseName.replace(/\//g, AssetEntryHelper.PATH_ARRAY_SEPARATOR);
   }

   /**
    * Send request to open a folder and get its children nodes.
    * @param node the node to open
    * @returns {Observable<Object>}
    */
   private openFolder(node: TreeNodeModel = null): Observable<TreeNodeModel[]> {
      const data = <DatabaseTreeNodeModel> node?.data;
      let path: string;
      let parr: string;

      if(!!data) {
         path = data.path;
         parr = data.parr;
      }
      else {
         path = this.databaseName;
         parr = this.databaseParr;
      }

      let params: HttpParams = new HttpParams().set("parentPath", path);

      if(!!parr) {
         params = params.set("parr", parr);
      }

      if(this.physicalModel && this.physicalModel.connection) {
         params = params.set("additional", this.physicalModel.connection);
      }

      return this.httpClient.get<TreeNodeModel[]>(DATABASE_TREE_URI, {params: params})
         .pipe(tap(children => {

            children.forEach(child => {
               if(child.leaf) {
                  this.refreshLeafNodeSelectStatus(child);
               }
            });
         }));
   }

   private loadDatabaseTree(): Observable<TreeNodeModel[]> {
      let param: HttpParams = new HttpParams().set("database", this.databaseName);

      if(this.physicalModel && this.physicalModel.connection) {
         param = param.set("additional", this.physicalModel.connection);
      }

      return this.httpClient.get<TreeNodeModel[]>(DATABASE_TREE_ALL_URI, { params: param });
   }

   /**
    * Check if the given name is already present on the physical model.
    * @param name the name to check
    * @returns {boolean}   true if duplicate
    */
   isDuplicateTableName(name: string): boolean {
      return this.physicalModel.tables
         .some(table => {
            let duplicate: boolean = table.qualifiedName == name || table.alias == name;

            if(!duplicate) {
               duplicate = table.autoAliases.some(alias => alias.selected && alias.alias == name);
            }

            return duplicate;
         });
   }

   /**
    * Greater a PhysicalTableModel from a tree node.
    * @param node the node to create the physical table from
    * @returns {PhysicalTableModel} the model created
    */
   private createPhysicalTableModel(node: PhysicalModelTreeNodeModel): PhysicalTableModel {
      const table: PhysicalTableModel = {
         name: node.name,
         catalog: node.catalog,
         schema: node.schema,
         qualifiedName: node.qualifiedName,
         path: node.path,
         alias: node.alias,
         sql: node.sql,
         type: !!node.sql ? PhysicalTableType.VIEW : PhysicalTableType.PHYSICAL,
         joins: [],
         autoAliases: [],
         autoAliasesEnabled: false,
         aliasSource: node.aliasSource,
         baseTable: false
      };

      return table;
   }

   /**
    * Remove a physical table from the selected tables on the physical model.
    * @param node       the node that was de-selected
    * @param nodeData   the nodeData containing the name of the table to remove
    */
   private removePhysicalTable(node: TreeNodeModel, nodeData: PhysicalModelTreeNodeModel): void {
      const removeTable: PhysicalTableModel = this.physicalModel.tables
         .find(table => table.qualifiedName == nodeData.qualifiedName);

      if(!!removeTable) {
         let event: EditTableEvent = new EditTableEvent(this.physicalModel.id,
            removeTable);
         this.httpClient.post<PhysicalModelDefinition>(PHYSICAL_MODEL_TABLE_URI + "remove", event)
            .subscribe(() => {
               if(this.tableTree.selectedNode == node) {
                  this.tableTree.selectedNode = null;
               }

               this.dataPhysicalModelService.emitModelChange();
            });
      }

      // if node is sql or inline view, remove from tree
      if(!!nodeData.sql || !!nodeData.alias) {
         const index: number = this.databaseRoot.children.indexOf(node);

         if(index != -1) {
            this.databaseRoot.children.splice(index, 1);
         }
      }

      if(!!this.editingTable && this.editingTable.qualifiedName == nodeData.qualifiedName) {
         // if the removed table was selected, set selected table to null
         this.editingTable = null;
      }
   }

   /**
    * Check if any changes were made and unsaved, then confirm user navigation away without saving.
    */
   canDeactivate(): Observable<boolean> | Promise<boolean> | boolean {
      if(!this.isModified) {
         return true;
      }
      else {
         let msg: string = !!this.parent ? "_#(js:data.extended.physicalmodel.confirmLeaving)"
            : "_#(js:data.physicalmodel.confirmLeaving)";
         return ComponentTool.showConfirmDialog(this.modalService, "_#(js:dialog.changedTitle)", msg)
            .then(
               (buttonClicked) => buttonClicked === "ok",
               () => false
            );
      }
   }

   /**
    * search table. to load all table and keep expanded, selected when first search.
    */
   search(): void {
      if(!this.filterTablesString) {
         this.searchMode = false;

         return;
      }

      if(!this.loadedFullTableTree) {
         this.loadingTree = true;

         this.loadDatabaseTree().subscribe(data => {
            this.loadedFullTableTree = true;
            this.loadingTree = false;

            let oldRoot: TreeNodeModel = {
               children: this.databaseRoot.children,
               expanded: true
            };

            this.databaseRoot.children = data;
            this.refreshTreeSelectStatus(this.databaseRoot);
            this.expandAll(this.databaseRoot);
            this.keepSelectedNodes(this.databaseRoot);
            this.searchMode = true;
         });
      }
      else {
         this.expandAll(this.databaseRoot);
         this.searchMode = true;
      }
   }

   resetSearchMode(): void {
      if(!this.searchMode) {
         this.filterTablesString = null;
      }

      if(this.joinEditing || !this.searchMode) {
         return;
      }

      this.searchMode = false;
      this.filterTablesString = null;
      this.refreshDatabaseRoots(true);
   }

   expandAll(node: TreeNodeModel): void {
      if(node.children && node.children.length > 0) {
         node.expanded = true;
         node.children.forEach(child => this.expandAll(child));
      }
   }

   keepSelectedNodes(node: TreeNodeModel) {
      if(node.leaf) {
         const childData: PhysicalModelTreeNodeModel = <PhysicalModelTreeNodeModel> node.data;
         childData.selected = this.physicalModel.tables
            .some(table => table.path == childData.path);
      }
      else {
         if(node.children && node.children.length > 0) {
            node.children.forEach(child => this.keepSelectedNodes(child));
         }
      }
   }

   keepExpandedNodes(root: TreeNodeModel, node: TreeNodeModel) {
      if(!node || node.leaf || !node.children) {
         return;
      }

      for(let child of node.children) {
         if(child.expanded) {
            let treeNode = GuiTool.findNode(root, (n) =>
               !!n.data && n.data.path === child.data.path && n.label === child.label &&
               n.data.type === child.data.type);

            if(treeNode) {
               treeNode.expanded = true;
            }

            this.keepExpandedNodes(child, root);
         }
      }
   }

   getExpandedPaths(root: TreeNodeModel, paths: string[]): void {
      if(root.expanded && root.data) {
         paths.push(root.data.path);
      }

      if(root.children && root.children.length > 0) {
         root.children.forEach(child => this.getExpandedPaths(child, paths));
      }
   }

   refreshModel(): void {
      this.dataPhysicalModelService.emitModelChange();
   }

   ngDoCheck(): void {
      if(this.initialized) {
         // check if physical model has changed
         if(!Tool.isEquals(this.physicalModel, this.previousModel)) {
            // if changed, set modified to true
            this.isModified = true;
            // store new physical model
            this.previousModel = Tool.clone(this.physicalModel);
            // refresh warnings
            this.refreshWarnings();
         }
      }
   }

   ngOnDestroy(): void {
      if(!!this.subscription) {
         this.subscription.unsubscribe();
         this.subscription = null;
      }

      clearInterval(this.heartbeatIntervalId);
      let params: HttpParams = new HttpParams().set("id", this.physicalModel.id);
      this.httpClient.delete(DESTROY_MODEL_URI, { params: params }).subscribe();
   }

   onModified(isModified: boolean): void {
      this.isModified = this.isModified || isModified;
   }

   graphNodesSelected(nodes: string[]) {
      this.tableTree.selectedNodes = [];

      if(!nodes || nodes.length == 0) {
         this.tableTree.selectedNode = [];
         this.selectNode(null);
      }
      else {
         this.resetSearchMode();
         this.selectAndExpandToPath(nodes);
      }
   }

   tableRemoved(tables: GraphModel[]): void {
      if(!tables) {
         return;
      }

      tables.forEach((table) => {
         let findIndex = this.databaseRoot.children.findIndex(child => {
            return !!child && !!child.data && !!table.node &&
               child.data.path === table.node.treeLink;
         });


         if(findIndex >= 0) {
            let tableData = this.databaseRoot.children[findIndex].data;

            if(!!tableData && (!!tableData.sql || !!tableData.alias)) {
               this.databaseRoot.children.splice(findIndex, 1);

               if(!!this.editingTable &&
                  this.editingTable.qualifiedName == tableData.qualifiedName)
               {
                  // if the removed table was selected, set selected table to null
                  this.editingTable = null;
               }
            }
         }
      });
   }

   /**
    * Expand nodes until the node with the given path is found then select it.
    * @param path    the path to find
    * @param parent  the current parent node to search
    */
   private selectAndExpandToPath(paths: string[], parent: TreeNodeModel = this.databaseRoot): void {
      for(let child of parent.children) {
         const childPath: string = (<DatabaseTreeNodeModel> child.data).path;

         if(paths.includes(childPath)) {
            this.tableTree.selectNode(child);
         }
         else if(paths.some(p => p.indexOf(childPath + "/") === 0)) {
            child.expanded = true;

            if(child.children.length == 0) {
               this.openFolder(child)
                  .subscribe(
                     data => {
                        child.children = data;
                        this.selectAndExpandToPath(paths, child);
                     });
            }
            else {
               this.selectAndExpandToPath(paths, child);
            }
         }
      }
   }

   toggleTreeCollapsed(): void {
      this.treeCollapsed = !this.treeCollapsed;
      this.updateDataTreePane();
   }

   private updateDataTreePane(): void {
      if(!!!this.horizontalSplitPane) {
         return;
      }

      if(!this.treeCollapsed) {
         this.horizontalSplitPane.setSizes([this.treePaneSize, 100 - this.treePaneSize]);
      }
      else {
         this.horizontalSplitPane.collapse(0);
      }
   }

   splitPaneDragEnd(): void {
      this.treePaneSize = this.splitPane.getSizes()[0];

      if(this.treePaneSize > 1) {
         this.treeCollapsed = false;
      }
      else {
         this.treeCollapsed = true;
         this.treePaneSize = this.INIT_TREE_PANE_SIZE;
      }
   }

   onJoinEditing(joinEditing: boolean): void {
      this.joinEditing = joinEditing;
   }

   @HostListener("keydown", ["$event"])
   onKeyDown(event: KeyboardEvent): void {
      if(event.ctrlKey && event.key == "s") {
         event.stopPropagation();
         event.preventDefault();
         this.save();
      }
   }

   /**
    * Refresh editing table when physical model joins are changed.
    */
   private refreshEditingTable() {
      if(this.physicalModel && this.physicalModel.tables && this.editingTable) {
         let newTable = this.physicalModel.tables.find((table) => {
            return table && table.qualifiedName === this.editingTable.qualifiedName &&
               table.path === this.editingTable.path;
         });

         if(newTable) {
            this.editingTable = newTable;
         }
         else {
            this.editingTable = null;
         }
      }
   }

   showTreeContextMenu(event: { node: TreeNodeModel, event: MouseEvent}) {
      let options: DropdownOptions = {
         position: {x: event.event.clientX, y: event.event.clientY},
         contextmenu: true
      };

      let contextmenu: ActionsContextmenuComponent =
         this.dropdownService.open(ActionsContextmenuComponent, options).componentInstance;
      contextmenu.sourceEvent = event[0];
      contextmenu.actions = this.createTableActions(event.node);
   }

   createTableActions(node: TreeNodeModel): AssemblyActionGroup[] {
      let group = new AssemblyActionGroup();
      let groups: AssemblyActionGroup[] = [group];
      group.actions = [
         {
            id: () => "physical view create alias",
            label: () => "_#(js:Create Alias)",
            icon: () => "",
            enabled: () => !this.joinEditing,
            visible: () => this.isTable(node),
            action: () => this.createAlias(node)
         },
         {
            id: () => "physical view edit alias",
            label: () => "_#(js:Edit Alias)",
            icon: () => "",
            enabled: () => !this.joinEditing,
            visible: () => this.isTable(node) && !this.isBaseTable(node) && node.data &&
               node.data.alias,
            action: () => this.editAlias(node)
         },
         {
            id: () => "physical view edit view",
            label: () => "_#(js:Edit View)",
            icon: () => "",
            enabled: () => !this.joinEditing,
            visible: () => this.isTable(node) && !this.isBaseTable(node) && node.data &&
               node.data.sql,
            action: () => this.editView(node)
         },
         {
            id: () => "physical view auto alias",
            label: () => "_#(js:Auto Alias)",
            icon: () => "",
            enabled: () => !this.joinEditing,
            visible: () => this.isTable(node) && !this.isBaseTable(node) && node.data
               && node.data.selected && !!!node.data.alias,
            action: () => this.showAutoAliasDialog(node)
         }
      ];

      return groups;
   }

   /**
    * Whether node is table node.
    */
   private isTable(node: TreeNodeModel): boolean {
      return node && node.type == DatabaseTreeNodeType.TABLE;
   }

   /**
    * Whether node is base table node.
    * @param node
    */
   private isBaseTable(node: TreeNodeModel) {
      return this.isTable(node) && node.data && node.data.baseTable;
   }
}
