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
import {
   Component,
   Input,
   ViewChild,
   TemplateRef,
   DoCheck,
   IterableDiffers,
   IterableDiffer,
   OnInit, Output, EventEmitter, OnDestroy
} from "@angular/core";

import { HttpClient, HttpParams } from "@angular/common/http";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import {
   joinMap
} from "../../../../../model/datasources/database/physical-model/join-type.config";
import { PhysicalModelDefinition } from "../../../../../model/datasources/database/physical-model/physical-model-definition";
import { TreeComponent } from "../../../../../../../widget/tree/tree.component";
import { JoinModel } from "../../../../../model/datasources/database/physical-model/join-model";
import { TreeNodeModel } from "../../../../../../../widget/tree/tree-node-model";
import { PhysicalTableModel } from "../../../../../model/datasources/database/physical-model/physical-table-model";
import { CardinalityHelperModel } from "../../../../../model/datasources/database/physical-model/cardinality-helper-model";
import { ComponentTool } from "../../../../../../../common/util/component-tool";
import {
   EditJoinEventItem,
   EditJoinsEvent, ModifyJoinEventItem, RemoveJoinEventItem
} from "../../../../../model/datasources/database/events/edit-joins-event";
import { Tool } from "../../../../../../../../../../shared/util/tool";
import {
   DataPhysicalModelService, HighlightInfo
} from "../../../../../services/data-physical-model.service";

const JOIN_CARDINALITY_URI: string = "../api/data/physicalmodel/cardinality/";
const JOIN_EDIT_URI: string = "../api/data/physicalmodel/join/";

class JoinTreeType {
   static get TABLE(): string {
      return "TABLE";
   }

   static get JOIN(): string {
      return "JOIN";
   }
}

@Component({
   selector: "physical-table-joins",
   templateUrl: "physical-table-joins.component.html",
   styleUrls: ["physical-table-joins.component.scss"]
})
export class PhysicalTableJoinsComponent implements DoCheck, OnDestroy {
   @Input() physicalModel: PhysicalModelDefinition;
   @Input() supportFullOuterJoin: boolean = false;
   @Input() disabled = false;
   @Input() databaseName: string;
   @Output() tableChange: EventEmitter<PhysicalTableModel> = new EventEmitter<PhysicalTableModel>();
   @ViewChild("addJoinDialog") addJoinDialog: TemplateRef<any>;
   @ViewChild("joinsTree") joinsTree: TreeComponent;
   @ViewChild("editJoinDialog") editJoinDialog: TemplateRef<any>;
   selectedTableNodes: TreeNodeModel[];
   selectedJoins: JoinModel[];
   selectedNodes: TreeNodeModel[];
   foreignTableRoot: TreeNodeModel = {
      leaf: false,
      children: []
   };
   initialized: boolean = false;
   private _table: PhysicalTableModel;
   private differ: IterableDiffer<any>;

   @Input()
   set table(model: PhysicalTableModel) {
      if(this._table != model) {
         this._table = model;
         this.updateForeignTables();
         this.selectedTableNodes = [];
         this.resetSelectedJoins();
         this.physicalModelService.highlightConnections(null);

         if(!!this.joinsTree) {
            this.joinsTree.selectedNodes = [];
         }
      }
   }

   get table(): PhysicalTableModel {
      return this._table;
   }

   constructor(differs: IterableDiffers, private httpClient: HttpClient,
               private modalService: NgbModal,
               private physicalModelService: DataPhysicalModelService)
   {
      this.differ = differs.find([]).create(null);
   }

   ngDoCheck(): void {
      // check for changes on table joins array. This can be changed from many actions in physical
      // mode so we check for differences in array on DoCheck and if differences are found then
      // recreate tree nodes and update selected join
      if(this.differ.diff(this.table.joins)) {
         this.updateForeignTables();
      }
   }

   ngOnDestroy(): void {
      this.physicalModelService.highlightConnections(null);
   }

   /**
    * Create tree nodes from this tables current joins.
    */
   private updateForeignTables(expandSelectedParent: boolean = false): void {
      let children: TreeNodeModel[] = [];
      let foundSelectedNode: boolean = Tool.isEmpty(this.selectedTableNodes);
      let selectedNode: TreeNodeModel;

      if(!!this.table) {
         this.table.joins.forEach(join => {
            const name: string = join.foreignTable;
            const joinNode: TreeNodeModel = this.createJoinTreeNode(join);
            const baseJoin = join.baseJoin;
            let tableNode: TreeNodeModel = children.find(child => child.data.name == name);

            if(!tableNode) {
               tableNode = this.createTableTreeNode(name, baseJoin);
               children.push(tableNode);
            }
            else if(baseJoin && !!tableNode.data) {
               tableNode.data.hasBaseJoin = true;
            }

            // check if this node is the same as previously selected node
            if(!foundSelectedNode) {
               if(this.selectedJoins.some(j => j == join)) {
                  foundSelectedNode = true;
                  selectedNode = joinNode;
                  tableNode.expanded = true;
               }
               else if(!Tool.isEmpty(this.selectedTableNodes) &&
                  this.selectedTableNodes
                     .some(selectedTableNode => selectedTableNode.data.name == name))
               {
                  foundSelectedNode = true;
                  selectedNode = tableNode;
               }
            }

            tableNode.children.push(joinNode);
         });
      }

      if(!foundSelectedNode) {
         this.resetSelectedJoins();
         this.resetSelectedTableNodes();
         this.physicalModelService.highlightConnections(null);
      }
      else if(!!selectedNode) {
         this.joinsTree.exclusiveSelectNode(selectedNode);
      }

      this.foreignTableRoot.children = children;
   }

   resetSelectedJoins(): void {
      this.selectedJoins = [];
   }

   resetSelectedTableNodes(): void {
      this.selectedTableNodes = [];
   }

   /**
    * Helper method to create a table tree node from its name.
    * @param name the name of the table.
    * @returns {TreeNodeModel}   the tree node created
    */
   private createTableTreeNode(name: string, hasBaseJoin: boolean): TreeNodeModel {
      const previousNode: TreeNodeModel = this.findTableNode(name)?.[0];

      return {
         label: name,
         data: {
            name: name,
            hasBaseJoin: hasBaseJoin
         },
         leaf: false,
         type: JoinTreeType.TABLE,
         children: [],
         tooltip: name,
         cssClass: "action-color",
         expanded: !previousNode ? false : previousNode.expanded
      };
   }

   /**
    * Helper method to create a join tree node from the JoinModel.
    * @param join the join model to create a tree node for
    * @returns {TreeNodeModel}   the tree node created
    */
   private createJoinTreeNode(join: JoinModel): TreeNodeModel {
      return {
         label: this.createJoinLabel(join),
         data: join,
         leaf: true,
         type: JoinTreeType.JOIN,
         children: [],
         tooltip: this.createJoinLabel(join),
         cssClass: "action-color"
      };
   }

   /**
    * Called when user selects node on tree. Select join/table.
    * @param nodes   the selected nodes on tree
    */
   selectNode(nodes: TreeNodeModel[]): void {
      let sNodes = [];

      if(nodes && nodes.length > 0) {
         this.resetSelectedJoins();
         this.resetSelectedTableNodes();
         let preType: JoinTreeType = nodes[0].type;

         nodes.forEach((node, i) => {
            if(node.type !== preType) {
               preType = node.type;
               this.resetSelectedTableNodes();
               this.resetSelectedJoins();
               sNodes = [];
            }

            sNodes.push(node);

            if(node.type == JoinTreeType.TABLE) {
               this.selectedTableNodes.push(node);
               this.resetSelectedJoins();
            }
            else if(node.type == JoinTreeType.JOIN) {
               const sJoin = <JoinModel> node.data;
               const sTableNode = this.findTableNode(sJoin.foreignTable)?.[0];

               if(!this.selectedTableNodes.some(tableNode => tableNode === sTableNode)) {
                  this.selectedTableNodes.push(sTableNode);
               }

               this.selectedJoins.push(sJoin);
            }
         });

         let hInfos: HighlightInfo[] = [];

         this.selectedJoins.forEach(sJoin => {
            const sTableNode = this.findTableNode(sJoin.foreignTable)?.[0];

            if(!!sTableNode) {
               hInfos.push({
                  sourceTable: this.table.qualifiedName,
                  targetTable: sTableNode.data?.name
               });
            }
         });

         this.physicalModelService.highlightConnections(hInfos);
      }

      this.selectedNodes = sNodes;
   }

   /**
    * Helper method to create the label for the join tree node.
    * @param join the join to create a label for
    * @returns {string} the label
    */
   private createJoinLabel(join: JoinModel): string {
      return `${join.column} ${joinMap(join.type)} ${join.foreignColumn}`;
   }

   /**
    * Open the add join dialog, then send request to update cardinality of the new join and add
    * the join to the table.
    */
   addJoin(): void {
      this.modalService.open(this.addJoinDialog, { backdrop: "static" }).result.then(
         (result: JoinModel) => {
            const command: CardinalityHelperModel = new CardinalityHelperModel(
               this.table.qualifiedName, this.physicalModel, result);
            let params = new HttpParams()
               .set("database", this.databaseName)
               .set("additional", this.physicalModel?.connection);

            this.httpClient.post<JoinModel>(JOIN_CARDINALITY_URI, command, {params: params})
               .subscribe(
                  data => {
                     this.addJoinToTable(data);
                  },
                  err => {
                     this.addJoinToTable(result);
                  }
               );
         },
         (reject) => {}
      );
   }

   /**
    * Add a join to the table and update the joins tree.
    * @param join the join to add
    */
   private addJoinToTable(join: JoinModel): void {
      //this.table.joins.push(join);
      let tableNode: TreeNodeModel = this.findTableNode(join.foreignTable)?.[0];

      if(!tableNode) {
         tableNode = this.createTableTreeNode(join.foreignTable, join.baseJoin);
         this.foreignTableRoot.children.push(tableNode);
      }

      const joinNode: TreeNodeModel = this.createJoinTreeNode(join);
      tableNode.children.push(joinNode);
      tableNode.expanded = true;
      this.joinsTree.exclusiveSelectNode(joinNode);
      this.selectedTableNodes = [tableNode];
      this.selectedJoins = [join];
      this.physicalModelService.highlightConnections([{
         sourceTable: this.table.qualifiedName,
         targetTable: tableNode?.data?.name
      }]);
      let item: EditJoinEventItem = new EditJoinEventItem(this.table, join);
      let event: EditJoinsEvent = new EditJoinsEvent([ item ], this.physicalModel.id);
      this.httpClient.post(JOIN_EDIT_URI + "add", event)
         .subscribe(() => this.tableChange.emit(this.table));
   }

   /**
    * Confirm then remove a join from this table.
    */
   removeJoin(): void {
      const sJoins = this.selectedJoins?.filter(j => !j.baseJoin);

      if(!Tool.isEmpty(this.selectedJoins) && Tool.isEmpty(sJoins)) {
         // select base joins
         return;
      }

      const message: string = Tool.isEmpty(sJoins)
         ? "_#(js:data.physicalmodel.confirmRemoveAllTableJoins)"
         : "_#(js:data.physicalmodel.confirmRemoveJoin)";

      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Remove)", message)
         .then((buttonClicked) => {
            if(buttonClicked === "ok") {
               const tableNode: TreeNodeModel = this.findTableNode()?.[0];
               const tableNodeIndex: number = this.foreignTableRoot.children.indexOf(tableNode);

               if(!Tool.isEmpty(sJoins)) {
                  sJoins.forEach(selectedJoin => {
                     this.table.joins.splice(this.table.joins.indexOf(selectedJoin), 1);
                     tableNode.children = tableNode.children
                        .filter(child => child.data != selectedJoin);

                     if(tableNode.children.length == 0) {
                        this.foreignTableRoot.children.splice(tableNodeIndex, 1);
                     }
                  });

                  this.removeSelectedJoins(sJoins);
               }
               else {
                  this.foreignTableRoot.children.splice(tableNodeIndex, 1);
                  this.removeSelectedTableNodesJoins(this.selectedTableNodes);
               }
            }
         });
   }

   private removeSelectedTableNodesJoins(selectedTableNodes: TreeNodeModel[]): void {
      let tableNodeItems: EditJoinEventItem[] = [];

      selectedTableNodes?.forEach(tableNode => {
         if(!!tableNode && !!tableNode.children) {
            tableNode.children.forEach(join => {
               tableNodeItems.push(new RemoveJoinEventItem(this.table, join?.data));
            });
         }
      });

      let event: EditJoinsEvent = new EditJoinsEvent(tableNodeItems, this.physicalModel.id);
      this.doRemoveJoinsAction(event);
   }

   private removeSelectedJoins(joins: JoinModel[]): void {
      let joinItems: EditJoinEventItem[] = [];
      joins?.forEach(join => joinItems.push(new RemoveJoinEventItem(this.table, join)));

      let event: EditJoinsEvent = new EditJoinsEvent(joinItems, this.physicalModel.id);
      this.doRemoveJoinsAction(event);
   }

   private doRemoveJoinsAction(event: EditJoinsEvent): void {
      this.httpClient.put(JOIN_EDIT_URI + "remove", event)
         .subscribe(() => {
            this.resetSelectedTableNodes();
            this.resetSelectedJoins();
            this.physicalModelService.highlightConnections(null);
            this.tableChange.emit(this.table);
         });
   }

   private findTableNode(tableName?: string): TreeNodeModel[] {
      if(!!tableName) {
         return this.foreignTableRoot.children
            .filter(child => child.data && (child.data.name == tableName));
      }

      if(!Tool.isEmpty(this.selectedTableNodes)) {
         return this.foreignTableRoot.children
            .filter(child => child.data &&
               this.selectedTableNodes
                  .some(sTableNode => sTableNode.data.name === child.data.name));
      }

      return null;
   }

   editJoin(): void {
      if(!this.editJoinModel || this.editJoinModel.baseJoin) {
         return;
      }

      const selectedJoin = this.selectedJoins[0];
      let oldJoin: JoinModel = Tool.clone<JoinModel>(selectedJoin);

      this.modalService.open(this.editJoinDialog, { backdrop: "static" }).result
         .then(() => {
            if(!Tool.isEquals(selectedJoin, oldJoin)) {
               let item: EditJoinEventItem = new ModifyJoinEventItem(this.table, oldJoin,
                  selectedJoin);
               let editEvent: EditJoinsEvent = new EditJoinsEvent([ item ],
                  this.physicalModel.id);
               this.httpClient.put(JOIN_EDIT_URI + "modify", editEvent)
                  .subscribe(() => this.tableChange.emit(this.table));
            }
         });
   }

   isDeleteDisabled() {
      if(this.disabled || Tool.isEmpty(this.selectedTableNodes)) {
         return true;
      }

      if(!Tool.isEmpty(this.selectedJoins)) {
         return this.selectedJoins.some(selectedJoin => selectedJoin.baseJoin);
      }
      else {
         return this.selectedTableNodes.some(tableNode => tableNode?.data?.hasBaseJoin);
      }
   }

   isEditJoinDisabled() {
      return this.disabled || Tool.isEmpty(this.selectedTableNodes)
         || Tool.isEmpty(this.selectedJoins) || this.selectedJoins.length > 1
         || this.selectedJoins.some(selectedJoin => selectedJoin.baseJoin);
   }

   get editJoinModel(): JoinModel {
      return !Tool.isEmpty(this.selectedJoins) && this.selectedJoins.length === 1
         ? this.selectedJoins[0]
         : null;
   }
}
