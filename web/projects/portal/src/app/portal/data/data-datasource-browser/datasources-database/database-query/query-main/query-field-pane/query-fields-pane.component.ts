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
   Input,
   OnChanges,
   OnInit,
   SimpleChanges, TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { AssetEntry } from "../../../../../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../../../../../shared/data/asset-type";
import { ComponentTool } from "../../../../../../../common/util/component-tool";
import { GuiTool } from "../../../../../../../common/util/gui-tool";
import {
   FormulaEditorService
} from "../../../../../../../widget/formula-editor/formula-editor.service";
import { DragService } from "../../../../../../../widget/services/drag.service";
import { TreeNodeModel } from "../../../../../../../widget/tree/tree-node-model";
import {
   AddQueryColumnEvent
} from "../../../../../model/datasources/database/events/add-query-column-event";
import {
   RemoveQueryColumnEvent
} from "../../../../../model/datasources/database/events/remove-query-column-event";
import {
   AutoDrillInfoModel
} from "../../../../../model/datasources/database/physical-model/logical-model/auto-drill-info-model";
import {
   AutoDrillPathModel
} from "../../../../../model/datasources/database/physical-model/logical-model/auto-drill-path-model";
import { QueryFieldModel } from "../../../../../model/datasources/database/query/query-field-model";
import {
   QueryFieldPaneModel
} from "../../../../../model/datasources/database/query/query-field-pane-model";
import {
   AutoDrillDialog
} from "../../../database-physical-model/logical-model/attribute-editor/auto-drill-dialog/data-auto-drill-dialog.component";
import {
   DataQueryModelService,
   findNextNode,
   findTableChildren,
   getFieldFullName,
   getShiftIndexesRange
} from "../../data-query-model.service";
import { EditDataTypeDialogComponent } from "./edit-data-type-dialog/edit-data-type-dialog.component";
import { EditFieldDialogComponent } from "./edit-field-dialog/edit-field-dialog.component";
import {
   AddColumnInfoResult
} from "../../../../../model/datasources/database/query/free-form-sql-pane/add-column-info-result";
import { Tool } from "../../../../../../../../../../shared/util/tool";

const QUERY_FIELDS_TREE_URI = "../api/data/datasource/query/data-source-fields-tree";
const FORMAT_STRING_URI: string = "../api/data/datasource/query/field/format";
const QUERY_COLUMN_ADD_URI: string = "../api/data/datasource/query/column/add";
const QUERY_COLUMN_REMOVE_URI: string = "../api/data/datasource/query/column/remove";
const QUERY_COLUMN_UPDATE_URI: string = "../api/data/datasource/query/column/update";
const QUERY_COLUMN_BROWSE_URI: string = "../api/data/datasource/query/column/browse";
const QUERY_SAVE_EXPRESSION_URI: string = "../api/data/datasource/query/expression/save";

@Component({
   selector: "query-fields-pane",
   templateUrl: "./query-fields-pane.component.html",
   styleUrls: ["./query-fields-pane.component.scss"]
})
export class QueryFieldsPaneComponent implements OnInit, OnChanges {
   @Input() model: QueryFieldPaneModel;
   @Input() runtimeId: string;
   @ViewChild("browseFieldValues") browseFieldValues: TemplateRef<any>;
   databaseFieldsTree: TreeNodeModel = null;
   selectedFieldName: string;
   selectedFieldAlias: string;
   selectedFieldIndexes: number[] = [];
   formatString: string;
   selectedNodes: TreeNodeModel[] = [];
   returnTypes: {label: string, data: string}[] = FormulaEditorService.returnTypes;
   otherDataTypes: {label: string, data: string}[] = [
      { label: "_#(js:Enum)",  data: "enum" },
      { label: "_#(js:UserDefined)", data: "userDefined" }
   ];
   dataTypes = this.returnTypes.concat(this.otherDataTypes);
   columnValues: string[] = [];
   columnsOrderMap: {alias: string, name: string}[] = []; // alias -> name
   private _shiftStartIndex: number = -1;

   constructor(private http: HttpClient,
               private modalService: NgbModal,
               private queryModelService: DataQueryModelService,
               private dragService: DragService)
   {
   }

   ngOnInit() {
      this.initDatabaseFieldsTree();
      this.initColumnsOrder();
      this.selectField(null, 0);
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.model) {
         this.reloadColumnsOrder();
         this.updateSelectedField();
      }
   }

   initDatabaseFieldsTree(): void {
      const params = new HttpParams().set("runtimeId", this.runtimeId);
      this.http.get(QUERY_FIELDS_TREE_URI, { params })
         .subscribe(tree => this.databaseFieldsTree = tree);
   }

   initColumnsOrder(): void {
      if(this.model && this.model.fields) {
         this.columnsOrderMap = [];

         this.model.fields.forEach(field => {
            this.columnsOrderMap.push({ alias: field.alias, name: field.name });
         });
      }
   }

   reloadColumnsOrder(): void {
      if(this.columnsOrderMap.length > 0) {
         const fields: QueryFieldModel[] = [];

         this.columnsOrderMap.forEach((column) => {
            let index =
               this.model.fields.findIndex(f => f.name == column.name && f.alias == column.alias);
            let field = null;

            if(index >= 0) {
               field = this.model.fields.splice(index, 1);
            }

            if(field && field.length > 0) {
               fields.push(field[0]);
            }
         });

         if(this.model.fields.length > 0) {
            this.model.fields.forEach(f => {
               fields.push(f);
            });
         }

         this.model.fields = fields;
      }
   }

   iconFunction(node: TreeNodeModel): string {
      return GuiTool.getTreeNodeIconClass(node, "");
   }

   get selectedField(): QueryFieldModel {
      if(this.model && this.model.fields) {
         if(!!this.selectedFieldName) {
            return this.model.fields.find(f =>
               f.name == this.selectedFieldName && f.alias == this.selectedFieldAlias);
         }
      }

      return null;
   }

   /**
    * Get sample format string from the server.
    */
   getFormatString(): void {
      if(this.selectedField) {
         this.http.post(FORMAT_STRING_URI, this.selectedField.format, { responseType: "json" })
            .subscribe(
               (data: string) => {
                  this.formatString = data;
               },
               err => {}
            );
      }
   }

   /**
    * The drill description shown in the input.
    * @returns {any}
    */
   drillString(): string {
      if(this.selectedField && this.selectedField.drillInfo) {
         if(!this.selectedField.drillInfo.paths || this.selectedField.drillInfo.paths.length == 0) {
            return "None";
         }

         const paths: string[] = this.selectedField.drillInfo.paths
            .map((path: AutoDrillPathModel) => path.name);

         return paths.join(", ");
      }

      return "None";
   }

   openAutoDrillDialog(): void {
      let dialog = ComponentTool.showDialog(this.modalService, AutoDrillDialog,
         (data: AutoDrillInfoModel) => {
            this.selectedField.drillInfo = data;
            this.updateColumn("drill");
         }, {size: "lg", windowClass: "data-auto-drill-dialog"});

      dialog.portal = false;
      dialog.autoDrillModel = this.selectedField.drillInfo;
      dialog.fields = this.model.fields
   }

   isSelectedField(index: number): boolean {
      return this.selectedFieldIndexes.indexOf(index) != -1;
   }

   selectField(evt: MouseEvent, idx: number): void {
      //wait update alias finish.
      setTimeout(() => {
         if(!this.model || !this.model.fields || this.model.fields.length == 0) {
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
      });
   }

   doAddSelectedFieldIndex(indexes: number[]): void {
      if(!!indexes && indexes.length > 0) {
         indexes.forEach(i => {
            if(i >= 0 && this.selectedFieldIndexes.indexOf(i) == -1) {
               this.selectedFieldIndexes.push(i);
            }
         });

         let lastSelectedIndex = indexes[indexes.length - 1];
         this.selectedFieldName = this.model.fields[lastSelectedIndex].name;
         this.selectedFieldAlias = this.model.fields[lastSelectedIndex].alias;
         this.getFormatString();
      }
   }

   removeField(idx: number): void {
      if(this.model.fields && this.model.fields.length > idx) {
         this.removeColumns([this.model.fields[idx].name], [this.model.fields[idx].alias]);
      }
   }

   selectNodes(nodes: TreeNodeModel[]): void {
      this.selectedNodes = nodes;
   }

   getLeafNodes(children: TreeNodeModel[]): TreeNodeModel[] {
      let nodes: TreeNodeModel[] = [];

      if(children) {
         children.forEach(child => {
            if(child.leaf) {
               nodes.push(child);
            }
            else {
               nodes = nodes.concat(this.getLeafNodes(child.children));
            }
         });
      }

      return nodes;
   }

   addAll(): void {
      if(this.databaseFieldsTree) {
         const allLeafNodes = this.getLeafNodes(this.databaseFieldsTree.children);

         if(allLeafNodes.length > 0) {
            const entries = allLeafNodes.map(n => n.data);
            this.addColumns(entries);
         }
      }
   }

   add(): void {
      if(this.selectedNodes && this.selectedNodes.length > 0) {
         let entries = this.selectedNodes.filter(n => n.leaf).map(n => n.data);

         if(entries.length == 0) {
            entries = this.getLeafNodes(this.selectedNodes).map(n => n.data);
         }

         this.addColumns(entries);
         this.updateSelectedNodes();
      }
   }

   addColumns(entries: AssetEntry[]): void {
      if(entries && entries.length > 0) {
         const event: AddQueryColumnEvent = {
            runtimeId: this.runtimeId,
            columns: entries
         };
         this.http.post<AddColumnInfoResult>(QUERY_COLUMN_ADD_URI, event)
            .subscribe((data: AddColumnInfoResult) => {
               if(data) {
                  const result = this.getAliasNameList(data.columnMap);

                  if(result.length > 0) {
                     this.selectedFieldName = result[0].name;
                     this.selectedFieldAlias = result[0].alias;
                     this.queryModelService.emitModelChange();

                     result.forEach(entry => {
                        this.columnsOrderMap.push({ alias: entry.alias, name: entry.name });
                     });
                  }

                  if(data.limitMessage != null && data.limitMessage.length > 0) {
                     ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
                        data.limitMessage);
                  }
               }
            });
      }
   }

   remove(): void {
      if(!this.selectedFieldIndexes || this.selectedFieldIndexes.length == 0) {
         return;
      }

      if(this.model.fields) {
         const names: string[] = this.selectedFieldIndexes.map(idx => this.model.fields[idx].name);
         const aliases: string[] = this.selectedFieldIndexes.map(idx => this.model.fields[idx].alias);
         this.removeColumns(names, aliases);
      }
   }

   removeAll(): void {
      if(this.model && this.model.fields && this.model.fields.length > 0) {
         const names: string[] = this.model.fields.map(f => f.name);
         const aliases: string[] = this.model.fields.map(f => f.alias);
         this.removeColumns(names, aliases, true);
      }
   }

   removeColumns(names: string[], aliases: string[], all: boolean = false): void {
      if(this.model.fields) {
         this.removeColumnsInOrderMap(aliases, all);

         const event: RemoveQueryColumnEvent = {
            runtimeId: this.runtimeId,
            names: names,
            aliases: aliases
         };

         this.http.post(QUERY_COLUMN_REMOVE_URI, event).subscribe(() => {
            this.queryModelService.emitModelChange();
         });
      }
   }

   removeColumnsInOrderMap(aliases: string[], all: boolean): void {
      if(all) {
         this.selectedFieldName = null;
         this.selectedFieldAlias = null;
         this.selectedFieldIndexes = [];
         this.columnsOrderMap = [];
      }
      else {
         this.selectedFieldIndexes.sort();
         let idx: number =
            this.selectedFieldIndexes[0] > 0 ? this.selectedFieldIndexes[0] - 1 : 0;
         this.selectedFieldName = this.model.fields[idx].name;
         this.selectedFieldAlias = this.model.fields[idx].alias;
      }

      const newOrderMap = [];

      this.columnsOrderMap.forEach((column) => {
         if(aliases.indexOf(column.alias) == -1) {
            newOrderMap.push(column);
         }
      });

      this.columnsOrderMap = newOrderMap;
   }

   isUpDisabled(): boolean {
      if(!this.model.fields || this.model.fields.length <= 1) {
         return true;
      }

      if(this.selectedFieldIndexes && this.selectedFieldIndexes.length == 1) {
         return this.selectedFieldIndexes[0] == 0;
      }

      return true;
   }

   isDownDisabled(): boolean {
      if(!this.model || !this.model.fields || this.model.fields.length <= 1) {
         return true;
      }

      if(this.selectedFieldIndexes && this.selectedFieldIndexes.length == 1) {
         return this.selectedFieldIndexes[0] == this.model.fields.length - 1;
      }

      return true;
   }

   moveUp(): void {
      if(!this.isUpDisabled()) {
         let idx: number = this.selectedFieldIndexes[this.selectedFieldIndexes.length - 1];
         let temp: QueryFieldModel = this.model.fields[idx];
         this.model.fields[idx] = this.model.fields[idx - 1];
         this.model.fields[idx - 1] = temp;
         this.selectField(null, idx - 1);
         this.initColumnsOrder();
      }
   }

   moveDown(): void {
      if(!this.isDownDisabled()) {
         let idx: number = this.selectedFieldIndexes[this.selectedFieldIndexes.length - 1];
         let temp: QueryFieldModel = this.model.fields[idx];
         this.model.fields[idx] = this.model.fields[idx + 1];
         this.model.fields[idx + 1] = temp;
         this.selectField(null, idx + 1);
         this.initColumnsOrder();
      }
   }

   addExpression(): void {
      this.showFieldDialog(true);
   }

   editExpression(): void {
      this.showFieldDialog();
   }

   showFieldDialog(add: boolean = false): void {
      let modalOptions: NgbModalOptions = {
         backdrop: "static",
         windowClass: "query-field-dialog"
      };
      const dialog = ComponentTool.showDialog(this.modalService, EditFieldDialogComponent,
         (expression) => {
            let params = new HttpParams()
               .set("runtimeId", this.runtimeId)
               .set("expression", expression)
               .set("add", add);

            if(!add) {
               params = params.set("columnName", this.selectedField.name);
               params = params.set("columnAlias", this.selectedField.alias);
            }

            this.http.post(QUERY_SAVE_EXPRESSION_URI, null, { params: params })
               .subscribe((data) => {
                  if(data) {
                     if(add) {
                        this.columnsOrderMap.push({ alias: data[0], name:data[1] });
                        this.selectedFieldName = data[1];
                        this.selectedFieldAlias = data[0];
                     }
                     else {
                        this.columnsOrderMap.forEach(column => {
                           if(column.alias == this.selectedFieldAlias &&
                              column.name == this.selectedFieldName)
                           {
                              column.alias = this.selectedFieldAlias = data[0];
                              column.name = this.selectedFieldName = data[1];
                           }
                        });
                     }

                     this.queryModelService.emitModelChange();
                  }
               });
         }, modalOptions);

      dialog.title = add ? "_#(js:Add Expression)" : "_#(js:Edit Expression)";
      dialog.runtimeId = this.runtimeId;
      let selectedFieldTreeNode = this.getSelectedFieldTreeNode();
      dialog.expression = add ? null : selectedFieldTreeNode ?
         getFieldFullName(selectedFieldTreeNode, true) : this.selectedField.name;
   }

   browseColumnData(): void {
      if(this.selectedField) {
         const selectedNode = this.getSelectedFieldTreeNode();

         if(!selectedNode) {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)",
               "_#(js:designer.qb.jdbc.notSupportExpression)");
            return;
         }

         const params = new HttpParams()
            .set("runtimeId", this.runtimeId)
            .set("column", this.selectedField.name);
         this.http.get(QUERY_COLUMN_BROWSE_URI, { params: params })
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
      else {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)",
            "_#(js:data.query.columnRequired)");
      }
   }

   getSelectedFieldTreeNode(): TreeNodeModel {
      if(this.selectedField) {
         const nodes = this.getLeafNodes(this.databaseFieldsTree?.children);
         return nodes.find(node => getFieldFullName(node) == this.selectedField.name);
      }
      else {
         return null;
      }
   }

   updateSelectedField(): void {
      let idx = this.model.fields.indexOf(this.selectedField);
      this.selectField(null, idx >= 0 ? idx : 0);
   }

   updateSelectedNodes(): void {
      const sibling =
         findNextNode(this.databaseFieldsTree, this.selectedNodes[this.selectedNodes.length - 1]);

      if(sibling) {
         this.selectedNodes = [sibling];
      }
   }

   getAliasNameList(data: {[key: string]: string}): {alias: string, name: string}[] {
      const transformedData = [];

      for(const key in data) {
         if(data.hasOwnProperty(key)) {
            const value = data[key];
            transformedData.push({ alias: key, name: value });
         }
      }

      return transformedData;
   }

   updateFormat(): void {
      this.getFormatString();
      this.updateColumn("format");
   }

   updateAlias(event: any): void {
      const newAlias = event.target.value.trim();
      const oldAlias = Tool.clone(this.selectedFieldAlias);

      if(this.selectedField.alias == newAlias) {
         return;
      }

      if(!newAlias) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:data.query.invalidColumnAlias)");
         return;
      }
      else {
         if(!!this.model.fields.find(f => f.alias == newAlias)) {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
               "_#(js:data.query.columnAliasDuplicate)");
            return;
         }
         else {
            this.model.fields.find((f) => {
               if(f.name == this.selectedFieldName && f.alias == this.selectedFieldAlias) {
                  f.alias = newAlias;
               }
            });

            const newOrderMap = [];

            this.columnsOrderMap.forEach((colomn) => {
               if(this.selectedFieldAlias == colomn.alias && this.selectedFieldName == colomn.name) {
                  newOrderMap.push({ alias: newAlias, name: colomn.name });
               }
               else {
                  newOrderMap.push({ alias: colomn.alias, name: colomn.name });
               }
            });

            this.columnsOrderMap = newOrderMap;
            this.selectedFieldAlias = newAlias;
         }
      }

      this.updateColumn("alias", newAlias, oldAlias);
   }

   updateDataType(type: string): void {
      this.selectedField.dataType = type;
      this.updateColumn("dataType");
   }

   updateColumn(type: string, newAlias?: string, oldAlias?: string): void {
      const params = {
         runtimeId: this.runtimeId,
         type: type,
         oldAlias: oldAlias
      };

      this.http.post(QUERY_COLUMN_UPDATE_URI, this.selectedField, {params: params})
         .subscribe(() => {
            this.queryModelService.emitModelChange();
         });
   }

   dbClickToAdd(node: TreeNodeModel): void {
      if(node.leaf) {
         this.addColumns([node.data]);
      }
   }

   drop(event: any, remove: boolean = false): void {
      if(remove) {
         this.remove();
      }
      else {
         event.preventDefault();
         event.stopPropagation();

         let dataArray = !!this.dragService.getDragDataValues(event) ?
            this.dragService.getDragDataValues(event)[0] : null;

         if(!!dataArray && Array.isArray(dataArray) && dataArray.length > 0) {
            let fields: AssetEntry[] = [];

            if(dataArray[0].type === AssetType.PHYSICAL_TABLE) {
               fields = findTableChildren(this.databaseFieldsTree, dataArray).map(n => n.data);
            }
            else {
               fields.push(...dataArray);
            }

            this.addColumns(fields);
         }
      }
   }

   showDataTypeIcon(): boolean {
      return !!this.selectedField && this.selectedFieldIndexes?.length == 1 &&
         !this.getSelectedFieldTreeNode();
   }

   getDataTypeIconClass(): string {
      return GuiTool.getDataTypeIconClass(this.selectedField.dataType);
   }

   showEditDataTypeDialog(): void {
      const dialog = ComponentTool.showDialog(this.modalService, EditDataTypeDialogComponent,
         (dataType) => {
            if(dataType !== this.selectedField.dataType) {
               this.updateDataType(dataType);
            }
         }, { backdrop: "static" });

      dialog.columnName = this.selectedField.alias;
      dialog.dataType = this.selectedField.dataType;
   }
}
