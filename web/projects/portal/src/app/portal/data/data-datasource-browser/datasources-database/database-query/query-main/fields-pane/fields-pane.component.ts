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
import { HttpClient, HttpParams } from "@angular/common/http";
import {
   Component,
   EventEmitter,
   Input,
   OnInit,
   Output,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AssetEntry } from "../../../../../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../../../../../shared/data/asset-type";
import { Tool } from "../../../../../../../../../../shared/util/tool";
import { ComponentTool } from "../../../../../../../common/util/component-tool";
import { GuiTool } from "../../../../../../../common/util/gui-tool";
import { DragService } from "../../../../../../../widget/services/drag.service";
import { TreeNodeModel } from "../../../../../../../widget/tree/tree-node-model";
import {
   findNextNode,
   findTableChildren,
   getFieldFullNameByEntry,
   getShiftIndexesRange
} from "../../data-query-model.service";

const QUERY_COLUMN_CHECK_EXPRESSION_URI: string = "../api/data/datasource/query/column/check/expression";
const QUERY_COLUMN_BROWSE_URI: string = "../api/data/datasource/query/column/browse";
const QUERY_SORT_PANE_FIELDS_TREE_URI = "../api/data/datasource/query/column/sort/fields-tree";
const QUERY_GROUP_BY_CHECK_URI = "../api/data/datasource/query/groupby/check";

export enum SortTypes {
   ASC = "asc",
   DESC = "desc"
}

@Component({
   selector: "fields-pane",
   templateUrl: "./fields-pane.component.html",
   styleUrls: ["./fields-pane.component.scss"]
})
export class FieldsPaneComponent implements OnInit {
   @Input() runtimeId: string;
   @Input() fields: string[];
   @Input() orders: string[];
   @Input() grouping: boolean = false;
   @Input() queryFieldsMap: Map<string, string>;
   @Output() groupByValidityChange = new EventEmitter<boolean>();
   @Output() onFieldsChange = new EventEmitter<string[]>();
   @ViewChild("browseFieldValues") browseFieldValues: TemplateRef<any>;
   columnValues: string[] = [];
   fieldsTree: TreeNodeModel;
   selectedNodes: TreeNodeModel[] = [];
   selectedFieldIndexes: number[] = [];
   aliasColumns: boolean[] = []; // distinguish between query fields and database fields
   private _shiftStartIndex: number = -1;

   constructor(private modalService: NgbModal,
               private http: HttpClient,
               private dragService: DragService)
   {
   }

   ngOnInit() {
      const params = new HttpParams().set("runtimeId", this.runtimeId);
      this.http.get(QUERY_SORT_PANE_FIELDS_TREE_URI, {params})
         .subscribe(tree => {
            this.fieldsTree = tree;

            if(!this.grouping) {
               this.initAliasColumns();
            }
         });
   }

   initAliasColumns(): void {
      let queryFieldsNode: TreeNodeModel = this.fieldsTree.children[0];
      let queryFields: string[] = [];

      for(let i = 0; i < queryFieldsNode.children.length; i++) {
         let field = queryFieldsNode.children[i];
         queryFields.push(field.data.properties["attribute"]);
      }

      for(let i = 0; i < this.fields.length; i++) {
         let field = this.fields[i];
         this.aliasColumns.push(queryFields.includes(field));
      }
   }

   get fieldListLabel(): string {
      return this.grouping ? "_#(js:Group Fields)" : "_#(js:Sort Fields)";
   }

   get selectedField(): string {
      if(this.selectedFieldIndexes.length == 0) {
         return null;
      }

      let idx = this.selectedFieldIndexes[this.selectedFieldIndexes.length - 1];
      return this.fields[idx];
   }

   iconFunction(node: TreeNodeModel): string {
      return GuiTool.getTreeNodeIconClass(node, "");
   }

   selectNodes(nodes: TreeNodeModel[]): void {
      this.selectedNodes = nodes;
   }

   isDisabledAdd(): boolean {
      return this.selectedNodes.some(node => !node.data ||
         node.data.type == AssetType.PHYSICAL_TABLE);
   }

   isUpDisabled(): boolean {
      if(!this.fields || this.fields.length <= 1) {
         return true;
      }

      if(this.selectedFieldIndexes && this.selectedFieldIndexes.length == 1) {
         return this.selectedFieldIndexes[0] == 0;
      }

      return true;
   }

   isDownDisabled(): boolean {
      if(!this.fields || this.fields.length <= 1) {
         return true;
      }

      if(this.selectedFieldIndexes && this.selectedFieldIndexes.length == 1) {
         return this.fields.length - 1 == this.selectedFieldIndexes[0];
      }

      return true;
   }

   moveUp(): void {
      if(!this.isUpDisabled()) {
         let idx: number = this.selectedFieldIndexes[this.selectedFieldIndexes.length - 1];

         let temp: any = this.fields[idx];
         this.fields[idx] = this.fields[idx - 1];
         this.fields[idx - 1] = temp;

         if(!this.grouping) {
            temp = this.orders[idx];
            this.orders[idx] = this.orders[idx - 1];
            this.orders[idx - 1] = temp;
            temp = this.aliasColumns[idx];
            this.aliasColumns[idx] = this.aliasColumns[idx - 1];
            this.aliasColumns[idx - 1] = temp;
         }

         this.selectField(null, idx - 1);
      }
   }

   moveDown(): void {
      if(!this.isDownDisabled()) {
         let idx: number = this.selectedFieldIndexes[this.selectedFieldIndexes.length - 1];

         let temp: any = this.fields[idx];
         this.fields[idx] = this.fields[idx + 1];
         this.fields[idx + 1] = temp;

         if(!this.grouping) {
            temp = this.orders[idx];
            this.orders[idx] = this.orders[idx + 1];
            this.orders[idx + 1] = temp;
            temp = this.aliasColumns[idx];
            this.aliasColumns[idx] = this.aliasColumns[idx + 1];
            this.aliasColumns[idx + 1] = temp;
         }

         this.selectField(null, idx + 1);
      }
   }

   add(): void {
      if(this.selectedNodes && this.selectedNodes.length > 0) {
         let fields: AssetEntry[] = [];

         for(let i = 0; i < this.selectedNodes.length; i++) {
            const node = this.selectedNodes[i];

            if(!node.leaf) {
               continue;
            }

            fields.push(<AssetEntry> node.data);
         }

         if(fields.length > 0) {
            this.doAdd(fields);
         }
      }
   }

   doAdd(fields: AssetEntry[]): void {
      if(!!fields && fields.length > 0) {
         let duplicateFields: string[] = [];

         for(let i = 0; i < fields.length; i++) {
            let field = fields[i];
            let column: string = null;
            let isAliasColumn: boolean = field.properties["isAliasColumn"] == "true";

            if(isAliasColumn) {
               column = field.properties["attribute"];
            }
            else {
               column = getFieldFullNameByEntry(field);
            }

            if(this.fields.indexOf(column) == -1) {
               this.fields.push(column);
               this.selectField(null, this.fields.length - 1);

               if(!this.grouping) {
                  this.orders.push(SortTypes.ASC);
                  this.aliasColumns.push(isAliasColumn);
               }
            }
            else {
               duplicateFields.push(column);
            }
         }

         if(duplicateFields.length > 0) {
            const message: string =
               Tool.formatCatalogString("_#(js:designer.qb.jdbc.addFieldOnlyOnce)", [duplicateFields.join("; ")]);
            ComponentTool.showMessageDialog(this.modalService, "Warning", message);
         }

         this.updateSelectedNodes();
         this.validate();
      }
   }

   remove(): void {
      if(!this.selectedFieldIndexes || this.selectedFieldIndexes.length == 0) {
         return;
      }

      if(this.fields) {
         this.selectedFieldIndexes.sort((a, b) => a - b);
         let nextFocusIdx: number =
            this.selectedFieldIndexes[0] > 0 ? this.selectedFieldIndexes[0] - 1 : 0;

         for(let i = this.selectedFieldIndexes.length - 1; i >= 0; i--) {
            const idx: number = this.selectedFieldIndexes[i];
            this.doRemove(idx);
         }

         this.selectField(null, nextFocusIdx);
         this.validate();
         this.onFieldsChange.emit(this.fields);
      }
   }

   doRemove(idx: number): void {
      this.fields.splice(idx, 1);

      if(!this.grouping) {
         this.orders.splice(idx, 1);
         this.aliasColumns.splice(idx, 1);
      }
   }

   browseColumnData(): void {
      if(this.selectedField) {
         if(this.isAliasColumn()) {
            const params = new HttpParams()
               .set("runtimeId", this.runtimeId)
               .set("column", this.selectedField);
            this.http.get(QUERY_COLUMN_CHECK_EXPRESSION_URI, {params: params})
               .subscribe((data: boolean) => {
                  if(data) {
                     ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)",
                        "_#(js:designer.qb.jdbc.notSupportExpression)");
                  }
                  else {
                     this.doBrowseData();
                  }
               });
         }
         else {
            this.doBrowseData();
         }
      }
      else {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)",
            "_#(js:data.query.columnRequired)");
      }
   }

   doBrowseData(): void {
      const params = new HttpParams()
         .set("runtimeId", this.runtimeId)
         .set("column", this.selectedField)
         .set("isAliasColumn", this.isAliasColumn());
      this.http.get(QUERY_COLUMN_BROWSE_URI, {params: params})
         .subscribe((data: string[]) => {
            if(data && data.length > 0) {
               this.columnValues = data;
               this.modalService.open(this.browseFieldValues, {backdrop: "static"}).result
                  .then(() => {}, () => {});
            }
            else {
               ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)",
                  "_#(js:data.query.columnResultEmpty)");
            }
         });
   }

   changeOrder(): void {
      if(this.selectedFieldIndexes && this.selectedFieldIndexes.length > 0) {
         for(let i = 0; i < this.selectedFieldIndexes.length; i++) {
            const idx: number = this.selectedFieldIndexes[i];
            this.orders[idx] = this.orders[idx] === SortTypes.ASC ? SortTypes.DESC : SortTypes.ASC;
         }
      }
   }

   selectField(evt: MouseEvent, idx: number): void {
      if(!this.fields || this.fields.length == 0) {
         this.selectedFieldIndexes = [];
         return;
      }

      if(!evt || !(evt.ctrlKey || evt.shiftKey)) {
         this.selectedFieldIndexes = [];
         this._shiftStartIndex = idx;
      }

      if(!evt || !evt.shiftKey) {
         this.doAddSelectedFieldIndex([idx]);
      }
      else {
         this.selectedFieldIndexes = [];
         this.doAddSelectedFieldIndex(getShiftIndexesRange(this._shiftStartIndex, idx));
      }
   }

   doAddSelectedFieldIndex(indexes: number[]): void {
      if(!!indexes && indexes.length > 0) {
         indexes.forEach(i => {
            if(i >= 0 && this.selectedFieldIndexes.indexOf(i) == -1) {
               this.selectedFieldIndexes.push(i);
            }
         });
      }
   }

   removeField(idx: number): void {
      if(this.fields && this.fields.length > idx) {
         const nextFocusIdx: number = idx > 0 ? idx - 1 : 0;
         this.doRemove(idx);
         this.selectField(null, nextFocusIdx);
         this.validate();
         this.onFieldsChange.emit(this.fields);
      }
   }

   isAliasColumn(): boolean {
      return this.aliasColumns[this.fields.indexOf(this.selectedField)];
   }

   isSelectedField(index: number): boolean {
      return this.selectedFieldIndexes.indexOf(index) != -1;
   }

   getOrderIcon(index: number): string {
      if(index >= 0) {
         return this.orders[index] === SortTypes.ASC ? "sort-ascending-icon" : "sort-descending-icon";
      }

      return "sort-ascending-icon";
   }

   updateSelectedNodes(): void {
      const sibling =
         findNextNode(this.fieldsTree, this.selectedNodes[this.selectedNodes.length - 1]);

      if(sibling) {
         this.selectedNodes = [sibling];
      }
   }

   validate(): void {
      if(this.grouping) {
         if(!this.fields || this.fields.length == 0) {
            this.groupByValidityChange.emit(true);
            return;
         }

         const params = new HttpParams().set("runtimeId", this.runtimeId);

         this.http.post(QUERY_GROUP_BY_CHECK_URI, this.fields, {params: params})
            .subscribe((result: boolean) => {
               this.groupByValidityChange.emit(result);
            });
      }
   }

   dbClickToAdd(node: TreeNodeModel): void {
      if(node.leaf) {
         this.doAdd([node.data]);
      }
   }

   drop(event: DragEvent, remove: boolean = false): void {
      if(remove) {
         this.remove();
      }
      else {
         event.preventDefault();
         event.stopPropagation();

         let dataArray = !!this.dragService.getDragDataValues(event) ?
            this.dragService.getDragDataValues(event)[0] : null;

         if(!!dataArray && Array.isArray(dataArray)) {
            let fields: AssetEntry[] = [];

            if(dataArray[0].type === AssetType.PHYSICAL_TABLE) {
               fields = findTableChildren(this.fieldsTree.children[1], dataArray).map(n => n.data);
            }
            else {
               fields.push(...dataArray);
            }

            this.doAdd(fields);
         }
      }
   }

   getFieldTitle(field: string): string {
      if(this.queryFieldsMap) {
         let title = this.queryFieldsMap.get(field);
         return !!title ? title : field;
      }

      return field;
   }
}
