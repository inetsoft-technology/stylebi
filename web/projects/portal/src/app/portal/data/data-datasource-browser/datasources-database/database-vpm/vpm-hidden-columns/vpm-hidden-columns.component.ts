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
   Component,
   Input,
   Output,
   EventEmitter,
   ViewEncapsulation,
   OnInit,
   ViewChild
} from "@angular/core";
import { TreeNodeModel } from "../../../../../../widget/tree/tree-node-model";
import { Observable, of } from "rxjs";
import { DatabaseTreeNodeModel } from "../../../../model/datasources/database/physical-model/database-tree-node-model";
import { HttpClient } from "@angular/common/http";
import { HiddenColumnsModel } from "../../../../model/datasources/database/vpm/hidden-columns-model";
import { TreeComponent } from "../../../../../../widget/tree/tree.component";
import { AttributeRef } from "../../../../../../common/data/attribute-ref";
import { DataRef } from "../../../../../../common/data/data-ref";
import { DatabaseTreeNodeType } from "../../../../model/datasources/database/database-tree-node-type";
import { Tool } from "../../../../../../../../../shared/util/tool";
import { ComponentTool } from "../../../../../../common/util/component-tool";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { DataItem } from "../../../../model/datasources/database/vpm/test-data-model";
import { FullDataBaseTreeModel } from "../../../../model/full-database-tree-model";
import { SearchComparator } from "../../../../../../widget/tree/search-comparator";

const DATABASE_TREE_URI: string = "../api/data/vpm/hiddenColumn/tree";
const DATABASE_FULL_TREE_URI: string = "../api/data/vpm/hiddenColumn/fullTree/";
const MAX_HIDDEN_COLUMN = 500;

@Component({
   selector: "vpm-hidden-columns",
   templateUrl: "vpm-hidden-columns.component.html",
   styleUrls: ["vpm-hidden-columns.component.scss"],
   encapsulation: ViewEncapsulation.None
})
export class VPMHiddenColumnsComponent implements OnInit {
   @Input() hidden: HiddenColumnsModel;
   @Input() databaseName: string;
   @Input() availableRoles: DataItem[];
   @Output() expressionChange: EventEmitter<string> = new EventEmitter<string>();
   @Output() hiddenColumnsChange: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild("columnTree") columnTree: TreeComponent;
   databaseRoot: TreeNodeModel = {
      children: [],
      expanded: true
   };
   selectedHiddenColumns: DataRef[] = [];
   selectedGrantRoles: string[] = [];
   selectedAvailableRoles: string[] = [];
   fullTreeLoaded: boolean = false;
   loadingTree: boolean = false;
   filterStr: string = "";
   currOrg: string = "";

   constructor(private httpClient: HttpClient, private modalService: NgbModal) {
   }

   ngOnInit(): void {
      this.initDataSourceTree();
      this.httpClient.get<string>("../api/em/navbar/organization")
         .subscribe((org) => this.currOrg = org);
   }

   get selectedColumns(): TreeNodeModel[] {
      if(this.columnTree) {
         return this.columnTree.selectedNodes;
      }

      return [];
   }

   refreshFilterTreeModel(newFilter: string) {
      this.filterStr = newFilter;

      if(!this.filterStr || !this.filterStr.trim()) {
         return;
      }

      let searchSort = new SearchComparator(newFilter);

      if(!this.fullTreeLoaded) {
         this.loadingTree = true;
         this.loadFullDatabaseTree().subscribe(data => {
            this.databaseRoot.children = data.nodes;
            this.showLoadTimeOutMess(data.timeOut);
            this.fullTreeLoaded = true;
            this.loadingTree = false;
         });
      }

      setTimeout(() => this.expandAll(this.databaseRoot), 0);
   }

   private initDataSourceTree(): void {
      this.databaseRoot.data = <DatabaseTreeNodeModel> {
         path: this.databaseName,
         name: this.databaseName,
         type: DatabaseTreeNodeType.DATABASE,
         catalog: "",
         schema: "",
         qualifiedName: ""
      };

      this.openFolder(this.databaseRoot).subscribe(nodes => this.databaseRoot.children = nodes);
   }

   /**
    * Send request to open a folder and get its children nodes.
    * @param node the node to open
    * @returns {Observable<Object>}
    */
   private openFolder(node: TreeNodeModel = null): Observable<TreeNodeModel[]> {
      if(!!node?.data) {
         return this.httpClient.post<TreeNodeModel[]>(DATABASE_TREE_URI, node.data);
      }

      return of([]);
   }

   /**
    * expand children nodes.
    * @param node
    */
   expandNode(node: TreeNodeModel, callback?: (children: TreeNodeModel[]) => void): void {
      if(node && !node.childrenLoaded && (!node.children || node.children.length == 0)) {
         this.openFolder(node).subscribe(data => {
                  node.children = data;

                  if(callback) {
                     callback(data);
                  }
                  else {
                     node.childrenLoaded = true;
                  }
               },
               err => {
               }
            );
      }
   }

   public expandAll(node: TreeNodeModel) {
      if(!node.expanded && !node.leaf) {
         node.expanded = true;
      }

      if(node.children) {
         node.children.forEach(n => this.expandAll(n));
      }
   }

   private loadFullDatabaseTree(): Observable<FullDataBaseTreeModel> {
      return this.httpClient.get<FullDataBaseTreeModel>(DATABASE_FULL_TREE_URI + this.databaseName);
   }

   private showLoadTimeOutMess(timeOut: boolean) {
      if(timeOut) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)",
            "_#(js:designer.loading.metadataTimeout)");
      }
   }

   /**
    * add selected available columns node to hidden column
    */
   addHiddenColumn(): void {
      let treeSelectedNode = this.selectedColumns;

      treeSelectedNode
         .filter(node => this.supportAddAction(node))
         .forEach(node => this.addNodeToHiddenColumn(node));

      this.hiddenColumnsChange.emit();
   }

   private supportAddAction(node: TreeNodeModel): boolean {
      return !!node && (node.type == DatabaseTreeNodeType.COLUMN ||
         node.type == DatabaseTreeNodeType.TABLE || node.type == DatabaseTreeNodeType.ALIAS_TABLE);
   }

   /**
    * add node to hidden columns.
    * @param node
    */
   addNodeToHiddenColumn(node: TreeNodeModel): void {
      if(!node) {
         return;
      }

      if(node.type == DatabaseTreeNodeType.COLUMN) {
         this.addColumnNodeToHiddenColumn(node);
      }
      else if(node.type == DatabaseTreeNodeType.TABLE ||
         node.type == DatabaseTreeNodeType.ALIAS_TABLE)
      {
         this.addTableNodeToHiddenColumn(node);
      }
   }

   /**
    * add table node to hidden columns.
    * @param node
    */
   addTableNodeToHiddenColumn(node: TreeNodeModel): void {
      if(!node) {
         return;
      }

      let addAllColumnToHidden = (data: TreeNodeModel[]) => {
         if(data) {
            data.forEach(column => this.addColumnNodeToHiddenColumn(column));
            this.hiddenColumnsChange.emit();
         }
      };

      if(node && !node.childrenLoaded && (!node.children || node.children.length == 0)) {
         this.expandNode(node, addAllColumnToHidden);
      }
      else {
         addAllColumnToHidden(node.children);
      }
   }

   /**
    * add available column node to hidden column.
    * @param node
    * @return false if hidden column count more than max count.
    */
   addColumnNodeToHiddenColumn(node: TreeNodeModel): boolean {
      if(!this.hidden) {
         this.hidden = {
            hiddens: [],
            name: null,
            roles: [],
            script: null
         };
      }

      if(this.hidden.hiddens.length >= MAX_HIDDEN_COLUMN) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)",
            "_#(js:designer.qb.jdbc.maxColReached)");

         return false;
      }

      let attributeRef: AttributeRef = new AttributeRef();
      attributeRef.classType = "AttributeRef";
      let attr = node.data.attribute;
      let entity = node.data.entity;
      let fullName = node.data.fullName;
      let qname = node.data.qualifiedName;

      attributeRef.entity = entity;
      attributeRef.attribute = attr;
      attributeRef.name = !qname || qname.length == 0 ?
         (!attr ? "" : attr) : qname + "." + attr;

      if(fullName) {
         attributeRef.caption = fullName;
      }

      let findIndex = this.hidden.hiddens
         .findIndex((value: DataRef) => {
            return value && value.name == attributeRef.name && value.entity == attributeRef.entity;
         });

      if(findIndex >= 0) {
         return true;
      }

      this.hidden.hiddens.push(attributeRef);

      return true;
   }

   /**
    * select a hidden columns list item.
    * @param event
    * @param column
    */
   selectHiddenColumn(event: MouseEvent, column: DataRef): void {
      event.stopPropagation();

      if(!event.ctrlKey) {
         this.selectedHiddenColumns = [];
      }

      if(!this.selectedHiddenColumns.includes(column)) {
         this.selectedHiddenColumns.push(column);
      }
   }

   /**
    * whether column is selected in hidden column list.
    * @param column
    */
   isSelectedHiddenColumn(column: DataRef): boolean {
      return this.selectedHiddenColumns.includes(column);
   }

   /**
    * hidden column list has selected items.
    */
   hasSelectedHiddenColumn(): boolean {
      return this.selectedHiddenColumns && this.selectedHiddenColumns.length > 0;
   }

   clearHiddenSelected(): void {
      this.selectedHiddenColumns = [];
   }

   /**
    * add all available columns to hidden column.
    */
   addAllToHiddenColumns(): void {
      if(this.fullTreeLoaded) {
         this.clearHiddenColumns();
         this.addAllColumnsToHidden(this.databaseRoot);
      }
      else {
         this.loadingTree = true;

         this.loadFullDatabaseTree().subscribe(data => {
            this.databaseRoot.children = data.nodes;
            this.showLoadTimeOutMess(data.timeOut);
            this.fullTreeLoaded = true;
            this.loadingTree = false;
            this.clearHiddenColumns();
            this.addAllColumnsToHidden(this.databaseRoot);
            this.hiddenColumnsChange.emit();
         });
      }
   }

   addAllColumnsToHidden(node: TreeNodeModel): void {
      this.addAllColumnsToHidden0(node, { value: false });
   }

   private addAllColumnsToHidden0(node: TreeNodeModel, stop: { value: boolean }) {
      if(stop.value) {
         return;
      }

      if(node && node.type == "column") {
         if(!this.addColumnNodeToHiddenColumn(node)) {
            stop.value = true;
         }
      }
      else if(node && node.children && node.children.length > 0) {
         node.children.forEach(child => this.addAllColumnsToHidden0(child, stop));
      }
   }

   /**
    * remove selected columns form hidden column list.
    */
   removeHiddenColumn(): void {
      this.selectedHiddenColumns.forEach(selected => {
         let index = this.hidden.hiddens.indexOf(selected);

         if(index != -1) {
            this.hidden.hiddens.splice(index, 1);
         }
      });

      this.selectedHiddenColumns = [];
      this.hiddenColumnsChange.emit();
   }

   /**
    * clear all hidden columns.
    */
   clearHiddenColumns(): void {
      this.hidden.hiddens = [];
      this.selectedHiddenColumns = [];
      this.hiddenColumnsChange.emit();
   }

   selectGrantRole(event: MouseEvent, role: string): void {
      event.stopPropagation();

      if(!event.ctrlKey) {
         this.clearSelectedGrantRoles();
      }

      if(!this.selectedGrantRoles.includes(role)) {
         this.selectedGrantRoles.push(role);
      }
   }

   selectAvailableRole(event: MouseEvent, role: string): void {
      event.stopPropagation();

      if(!event.ctrlKey) {
         this.selectedAvailableRoles = [];
      }

      if(!this.selectedAvailableRoles.includes(role)) {
         this.selectedAvailableRoles.push(role);
      }
   }

   clearSelectedGrantRoles(): void {
      this.selectedGrantRoles = [];
   }

   addGrantRoles(): void {
      this.selectedAvailableRoles.forEach(role => {
         if(!this.hidden.roles.includes(role)) {
            this.hidden.roles.push(role);
         }
      });
   }

   removeGrantRoles(): void {
      this.selectedGrantRoles.forEach(role => {
         let index = this.hidden.roles.indexOf(role);

         if(index != -1) {
            this.hidden.roles.splice(index, 1);
         }
      });

      this.selectedGrantRoles = [];
   }

   getTreeNodeIcon(node: TreeNodeModel): string {
      if(!node) {
         return "folder-icon";
      }

      if(node.type === DatabaseTreeNodeType.FOLDER) {
         return "folder-icon";
      }
      else if(node.type === DatabaseTreeNodeType.PHYSICAL_MODEL) {
         return "db-model-icon";
      }
      else if(node.type === DatabaseTreeNodeType.TABLE ||
              node.type === DatabaseTreeNodeType.ALIAS_TABLE)
      {
         return "data-table-icon";
      }
      else if(node.type === DatabaseTreeNodeType.COLUMN) {
         return "column-icon";
      }

      return "folder-icon";
   }

   getBaseName(roleName: string): string {
      let orgind = roleName.indexOf(this.currOrg);
      if(orgind > 0) {
         return roleName.substring(0,orgind - 3);
      }
      return roleName;
   }
}
