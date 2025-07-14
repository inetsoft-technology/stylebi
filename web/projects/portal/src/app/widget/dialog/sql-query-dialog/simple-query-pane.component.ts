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
import { DOCUMENT } from "@angular/common";
import { HttpClient, HttpParams } from "@angular/common/http";
import {
   Component,
   ElementRef, EventEmitter,
   Inject,
   Input, Output,
   TemplateRef,
   ViewChild, ViewEncapsulation
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { concat as observableConcat, Observable, of as observableOf } from "rxjs";
import { map } from "rxjs/operators";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { ConditionOperation } from "../../../common/data/condition/condition-operation";
import { ConditionValueType } from "../../../common/data/condition/condition-value-type";
import { XSchema } from "../../../common/data/xschema";
import { GuiTool } from "../../../common/util/gui-tool";
import { Tool } from "../../../../../../shared/util/tool";
import { SqlQueryDialogModel } from "../../../composer/data/ws/sql-query-dialog-model";
import {
   ClauseModel
} from "../../../portal/data/model/datasources/database/vpm/condition/clause/clause-model";
import {
   ClauseValueModel
} from "../../../portal/data/model/datasources/database/vpm/condition/clause/clause-value-model";
import {
   ClauseValueTypes
} from "../../../portal/data/model/datasources/database/vpm/condition/clause/clause-value-types";
import {
   OperationModel
} from "../../../portal/data/model/datasources/database/vpm/condition/clause/operation-model";
import {
   deleteCondition
} from "../../../portal/data/model/datasources/database/vpm/condition/util/vpm-condition.util";
import {
   VPMColumnModel
} from "../../../portal/data/model/datasources/database/vpm/condition/vpm-column-model";
import {
   VPMConditionDialogModel
} from "../../../portal/data/model/datasources/database/vpm/condition/vpm-condition-dialog-model";
import { ModelService } from "../../services/model.service";
import { TreeNodeModel } from "../../tree/tree-node-model";
import { TreeComponent } from "../../tree/tree.component";
import { JoinItem } from "../../../composer/data/ws/join-item";
import { SQLQueryDialogColumn } from "../../../composer/data/ws/sql-dialog-column";
import { BasicSqlQueryModel } from "../../../composer/data/ws/basic-sql-query-model";
import { ComponentTool } from "../../../common/util/component-tool";
import { SlideOutOptions } from "../../slide-out/slide-out-options";
import { UntypedFormGroup } from "@angular/forms";
import { SqlQueryDialogController } from "./sql-query-dialog-controller";
import { ConditionItemPaneProvider } from "../../../common/data/condition/condition-item-pane-provider";
import { BaseTableCellModel } from "../../../vsobjects/model/base-table-cell-model";
import {
   DataQueryModelService
} from "../../../portal/data/data-datasource-browser/datasources-database/database-query/data-query-model.service";
import {
   SqlQueryPreviewPaneComponent
} from "../../../portal/data/data-datasource-browser/datasources-database/database-query/query-preview/sql-query-preview-pane.component";
import { NgbNavChangeEvent } from "@ng-bootstrap/ng-bootstrap/nav/nav";

interface ColumnTablePair {
   columns: AssetEntry[];
   table: AssetEntry;
}

const UPDATE_QUERY_URI: string = "../api/composer/ws/sql-query-dialog/query/update";

@Component({
   selector: "simple-query-pane",
   templateUrl: "simple-query-pane.component.html",
   styleUrls: ["simple-query-pane.component.scss"],
   encapsulation: ViewEncapsulation.None
})
export class SimpleQueryPaneComponent {
   @Input() queryModel: SqlQueryDialogModel;
   @Input() dataSources: string[];
   @Input() dataSource: string;
   @Input() freeFormSqlEnabled: boolean;
   @Input() runtimeId: string;
   @Input() controller: SqlQueryDialogController;
   @Input() loading: boolean = false;
   @Input() dataSourceTreeRoot: TreeNodeModel;
   @Input() supportsFullOuterJoinArr: boolean[];
   @Input() operations: OperationModel[];
   @Input() sessionOperations: OperationModel[];
   @Input() datasource: string;
   @Input() supportPreview: boolean = true;
   @ViewChild("tree") tree: TreeComponent;
   @ViewChild("joinDialog") joinDialog: TemplateRef<any>;
   @ViewChild("conditionDialog") conditionDialog: TemplateRef<any>;
   @ViewChild("sqlTextArea") sqlTextArea: ElementRef;
   @Output() onProcessing = new EventEmitter<boolean>();
   public XSchema = XSchema;
   public ConditionOperation = ConditionOperation;
   public ConditionValueType = ConditionValueType;
   @Input() set model(model: BasicSqlQueryModel) {
      this._model = model;

      this.oldSqlString = this.model.sqlString;
      this.updateNumTables();
      let keys = Object.keys(this.model.tables);

      if(keys.length > 0) {
         for(let tableName of keys) {
            let entry = this.model.tables[tableName];
            this.columnCache[tableName] = this.controller.getTableColumns(entry);
         }
      }
      else {
         this.columnCache = {};
      }
   }

   get model(): BasicSqlQueryModel {
      return this._model;
   }

   get tableCount(): number {
      return this.model.tables ? Object.keys(this.model.tables).length : 0;
   }

   _model: BasicSqlQueryModel;
   allowedColumnDropNames: string[] =
         [AssetType[AssetType.PHYSICAL_TABLE], AssetType[AssetType.PHYSICAL_COLUMN]];
   selectedJoin: JoinItem;
   numTables: number = 0;
   columnCache: {[tableName: string]: Observable<AssetEntry[]>} = {};
   conditionFields: VPMColumnModel[] = [];
   form: UntypedFormGroup;
   oldSqlString: string;
   editTab: string = "edit-tab";
   previewTab: string = "preview-tab";
   tableData: BaseTableCellModel[][];
   conditionDialogModel: VPMConditionDialogModel;
   selectedConditionIndex: number = -1;
   private _defaultTab: string = this.editTab;
   private _oldTab: string = this._defaultTab;

   get defaultTab(): string {
      return this._defaultTab;
   }

   set defaultTab(tab: string) {
      this._defaultTab = tab;
   }

   constructor(private modelService: ModelService,
               private modal: NgbModal,
               private http: HttpClient,
               private queryModelService: DataQueryModelService,
               @Inject(DOCUMENT) private document: any)
   {
   }

   get supportsFullOuterJoin(): boolean {
      const dsIndex: number = this.dataSources.indexOf(this.dataSource);
      return this.supportsFullOuterJoinArr[dsIndex];
   }


   get sqlConditionProvider(): ConditionItemPaneProvider {
      return this.controller.sqlConditionProvider;
   }

   private updateNumTables() {
      if(!this.model.tables) {
         this.model.tables = {};
      }

      this.numTables = Object.keys(this.model.tables).length;
   }

   textChanged(): void {
      this.model.sqlParseResult="_#(js:designer.qb.parseInit)";
   }

   newJoin(): void {
      this.selectedJoin = null;
      this.modal.open(this.joinDialog, {size: "lg", backdrop: false}).result.then(
            (result: JoinItem) => {
               if(this.model.joins == null) {
                  this.model.joins = [];
               }

               this.model.joins.push(result);
               this.getSQLString();
            },
            (reject) => {}
      );
   }

   editJoin(join: JoinItem): void {
      this.selectedJoin = Tool.clone(join);
      this.modal.open(this.joinDialog, {size: "lg", backdrop: false}).result.then(
            (result: JoinItem) => {
               let index: number = this.model.joins.indexOf(join);
               this.model.joins.splice(index, 1, result);
               this.getSQLString();
            },
            (reject) => {}
      );
   }

   editConditions(): void {
      this.setUpConditionDialogModel();
      let copy = Tool.clone(this.model.conditionList);
      this.conditionFields = [];
      let positions: number[] = [];
      let i: any = 0;

      // Logic for resolving an Observable[] into a fields array.
      // The array is updated every time an observable returns, preserving order.
      for(let tableName in this.columnCache) {
         if(this.columnCache.hasOwnProperty(tableName)) {
            let index = i;
            positions[index] = this.conditionFields.length;

            this.columnCache[tableName].subscribe((cols) => {
               let fields: VPMColumnModel[] = [];

               for(let entry of cols) {
                  fields.push({
                     name: entry.properties["source"] + "." + entry.properties["attribute"],
                     type: entry.properties["dtype"],
                     columnName: entry.properties["attribute"],
                     tableName: entry.properties["source"]
                  });
               }

               // Insert refs into the correct array position.
               for(let j = 0; j < fields.length; j++) {
                  this.conditionFields.splice(positions[index] + j, 0, fields[j]);
               }

               // Offset subsequent positions
               for(let j = index + 1; j < positions.length; j++) {
                  positions[j] += fields.length;
               }

               // force property to change so condition dialog will be updated
               this.conditionFields = this.conditionFields.concat([]);
               this.setUpConditionDialogModel();
            });

            i++;
         }
      }

      const options: SlideOutOptions = {
         size: "lg",
         windowClass: "condition-dialog",
         backdrop: false
      };

      this.modal.open(this.conditionDialog, options).result
         .then(
            (result: any[]) => {
               this.model.conditionList = result;
               this.getSQLString();
            },
            (reject) => {
               this.model.conditionList = copy;
            }
         );
   }

   iconFunction(node: TreeNodeModel): string {
      return GuiTool.getTreeNodeIconClass(node, "");
   }

   nodeExpanded(node: TreeNodeModel): void {
      if(node.data == null) {
         return;
      }

      this.controller.getDataSourceTree(node.data, true)
      .subscribe((data) => node.children = data.children);
   }

   columnsChange(columns: any[]) {
      this.model.columns = columns;
      this.getSQLString();
   }

   /**
    * Add columns into the column list, and update the tables in the model.
    * @param columnParentPairs the column-table pairs to add
    * @param index the index to add the columns to
    */
   addColumns(columnParentPairs: {columnEntry: AssetEntry, parentEntry: AssetEntry}[],
              index: number): void
   {
      if(this.model.columns == null) {
         this.model.columns = [];
      }

      columnParentPairs.forEach((pair) => {
         const columnEntry = pair.columnEntry;
         const columnModel: SQLQueryDialogColumn = {
            name: columnEntry.properties["source"] + "." +
                  columnEntry.properties["qualifiedAttribute"],
         };
         const oldIndex = this.model.columns.findIndex((c) => c.name === columnModel.name);

         if(this.model.maxColumnCount > 0 && this.model.columns != null &&
            this.model.columns.length >= this.model.maxColumnCount)
         {
            const message = "_#(js:common.oganization.colMaxCount)" +  "_*" +
               this.model.maxColumnCount;

            ComponentTool.showConfirmDialog(this.modal, "_#(js:Confirm)", message);

            return;
         }

         if(oldIndex < 0) {
            this.model.columns.splice(index++, 0, columnModel);
         }
         else {
            if(oldIndex < index) {
               this.model.columns.splice(index, 0, columnModel);
               this.model.columns.splice(oldIndex, 1);
            }
            else if(oldIndex > index) {
               this.model.columns.splice(oldIndex, 1);
               this.model.columns.splice(index++, 0, columnModel);
            }
            else {
               this.model.columns.splice(index, 1, columnModel);
            }
         }

         this.addTable(columnEntry.properties["source"], pair.parentEntry);
      });

      this.getSQLString();
   }

   droppedIntoColumnList(tuple: [any, number]) {
      if(this.model.maxColumnCount > 0 && this.model.columns != null &&
         this.model.columns.length >= this.model.maxColumnCount)
      {
         const message = "_#(js:common.oganization.colMaxCount)" +  "_*" +
            this.model.maxColumnCount;

         ComponentTool.showConfirmDialog(this.modal, "_#(js:Confirm)", message);

         return;
      }

      let data = tuple[0];
      let index = tuple[1];
      let tablesData = data[AssetType[AssetType.PHYSICAL_TABLE]];
      let columnsData = data[AssetType[AssetType.PHYSICAL_COLUMN]];
      let tableColumnsObs: Observable<ColumnTablePair>;
      let columns: {columnEntry: AssetEntry, parentEntry: AssetEntry}[] = [];

      if(tablesData) {
         let tableEntries: AssetEntry[] = JSON.parse(tablesData);
         tableColumnsObs = this.getTableColumns(tableEntries);
      }

      if(columnsData) {
         let columnEntries: AssetEntry[] = JSON.parse(columnsData);
         columns = this.getStandaloneColumns(columnEntries);
      }

      if(!!tableColumnsObs) {
         let tableColumns: ColumnTablePair[] = [];

         tableColumnsObs.subscribe((tablePair) => {
                  tablePair.columns.forEach((tableColumn) => {
                     let columnIndex = !!columns ?
                           columns.findIndex((columnsPair) => Tool.isEquals(tableColumn, columnsPair.columnEntry)) : -1;

                     if(columnIndex >= 0) {
                        columns.splice(columnIndex, 1);
                     }
                     else {
                        tableColumns.push(tablePair);
                     }
                  });
               }, (err) => {
               },
               () => {
                  let allColumns: {columnEntry: AssetEntry, parentEntry: AssetEntry}[] = [...columns];

                  tableColumns.forEach((pair) => {
                     pair.columns.forEach((column) => {
                        allColumns.push({columnEntry: column, parentEntry: pair.table});
                     });
                  });

                  this.addColumns(allColumns, index);
               });
      }
      else {
         this.addColumns(columns, index);
      }
   }

   /**
    * Return columns which are individually added to the column pane, as opposed to
    * those columns added as a result of adding a whole table, as in {@link getTableColumns}.
    */
   private getStandaloneColumns(columnEntries: AssetEntry[]): {columnEntry: AssetEntry, parentEntry: AssetEntry}[] {
      let columns: {columnEntry: AssetEntry, parentEntry: AssetEntry}[] = [];

      columnEntries.forEach((columnEntry) => {
         let parentNode = this.tree.getParentNodeByData(columnEntry);
         let parentEntry: AssetEntry = parentNode.data;
         let tableName = parentEntry.properties["source"];
         columns.push({columnEntry, parentEntry});

         if(!this.columnCache[tableName]) {
            let tableColumns: AssetEntry[] = [];
            parentNode.children.forEach((item) => tableColumns.push(item.data));
            this.columnCache[tableName] = observableOf(tableColumns);
         }
      });

      return columns;
   }

   /** Return an observable which will emit, in order, the columns of the provided tables.*/
   private getTableColumns(tables: AssetEntry[]): Observable<ColumnTablePair> {
      let columnsObservables: Observable<ColumnTablePair>[] = [];

      tables.forEach((table) => {
         let tableName = table.properties["source"];

         let post = this.controller.getTableColumns(table).pipe(
               map(cols => <ColumnTablePair> { columns: cols, table: table })
         );
         this.columnCache[tableName] = post.pipe(map((pair) => pair.columns));
         columnsObservables.push(post);
         post.subscribe(() => {});
      });

      return observableConcat(...columnsObservables);
   }

   deleteColumn(index: number) {
      const columnName = this.model.columns[index].name;
      this.model.columns.splice(index, 1);
      const table = columnName.substring(0, columnName.lastIndexOf("."));
      let found = false;

      // search for other columns which are in the same table
      for(let col of this.model.columns) {
         const colName = col.name;
         let table2 = colName.substring(0, colName.lastIndexOf("."));

         if(table === table2) {
            found = true;
            break;
         }
      }

      // if not found, clean everything that depends on the table
      if(!found) {
         this.deleteJoins(table);
         this.deleteConditions(table);
         delete this.model.tables[table];
         delete this.columnCache[table];
         this.updateNumTables();
      }

      this.getSQLString();
   }

   columnToString(column: SQLQueryDialogColumn): string {
      return column.name;
   }

   private deleteJoins(table: string) {
      for(let i = this.model.joins.length - 1; i >= 0; i--) {
         let join = this.model.joins[i];

         if(join.table1 === table || join.table2 === table) {
            this.model.joins.splice(i, 1);
         }
      }
   }

   joinsChange(joins: JoinItem[]) {
      this.model.joins = joins;
      this.getSQLString();
   }

   deleteJoin(index: number) {
      this.model.joins.splice(index, 1);
      this.getSQLString();
   }

   private deleteConditions(table: string) {
      for(let i = this.model.conditionList.length - 1; i >= 0; i--) {
         let condition = this.model.conditionList[i];

         if(condition?.type != "clause") {
            continue;
         }

         let con: ClauseModel = <ClauseModel> condition;
         let value1: ClauseValueModel = con.value1;
         let value2: ClauseValueModel = con.value2;
         let value3: ClauseValueModel = con.value3;

         if(value1.type == ClauseValueTypes.FIELD && value1.expression.startsWith(table) ||
            value2.type == ClauseValueTypes.FIELD && value2.expression.startsWith(table) ||
            value3.type == ClauseValueTypes.FIELD && value3.expression.startsWith(table))
         {
            deleteCondition(this.model.conditionList, i);
         }
      }
   }

   addTable(tableName: string, entry: AssetEntry): void {
      if(this.model.tables == null) {
         this.model.tables = {};
      }

      if(!this.model.tables[tableName]) {
         this.model.tables[tableName] = entry;
         this.updateNumTables();
      }
   }

   joinToString(join: JoinItem): string {
      let all1: string = join.all1 ? "*" : "";
      let all2: string = join.all2 ? "*" : "";

      return join.table1 + "." + join.column1 + " " + all1 + join.operator +
            all2 + " " + join.table2 + "." + join.column2;
   }

   getSQLString() {
      this.onProcessing.emit(true);

      this.controller.getSQLString<string>(this.queryModel)
         .subscribe((sqlString) => {
            this.model.sqlString = sqlString;
            this.onProcessing.emit(false);
         });
   }

   getSqlParseResult() {
      this.controller.getSqlParseResult<string>(this.model.sqlString)
      .subscribe((sqlParseResult) => {
         this.model.sqlParseResult = sqlParseResult;
      });
   }

   isParseFailed() {
      return this.model.sqlParseResult == "_#(js:designer.qb.parseFailed)";
   }

   editSQLDirectly() {
      const message = "_#(js:common.sqlquery.editsql)";

      ComponentTool.showConfirmDialog(this.modal, "_#(js:Confirm)", message,
            {"yes": "_#(js:Yes)", "no": "_#(js:No)"}, {backdrop: false})
      .then((buttonClicked) => {
         if(buttonClicked === "yes") {
            this.model.sqlEdited = true;
         }
      });
   }

   nodeClicked(node: TreeNodeModel) {
      if(this.model.sqlEdited) {
         const entry = <AssetEntry> node.data;

         if(entry != null && AssetEntryHelper.isPhysicalTable(entry) ||
               AssetEntryHelper.isColumn(entry))
         {
            let str = node.label;

            if(AssetEntryHelper.isColumn(entry)) {
               str = entry.properties["source"] + "." + str;
            }

            this.insertText(str);
         }
      }
   }

   /**
    * Insert text into the sql text area at the current caret position
    */
   private insertText(text: string) {
      const sqlTextAreaElem = this.sqlTextArea.nativeElement;
      let caretPos = sqlTextAreaElem.selectionStart;

      const front = sqlTextAreaElem.value.substring(0, caretPos);
      const back = sqlTextAreaElem.value.substring(sqlTextAreaElem.selectionEnd,
            sqlTextAreaElem.value.length);
      sqlTextAreaElem.value = front + text + back;
      caretPos = caretPos + text.length;
      sqlTextAreaElem.selectionStart = caretPos;
      sqlTextAreaElem.selectionEnd = caretPos;
      sqlTextAreaElem.focus();
      this.model.sqlString = sqlTextAreaElem.value;
   }

   setUpConditionDialogModel(): void {
      this.conditionDialogModel = new VPMConditionDialogModel(this.dataSource,
         "", false, this.conditionFields, Tool.clone(this.model.conditionList),
         this.operations, this.sessionOperations);
   }

   updateQueryTab(event: NgbNavChangeEvent<any>) {
      event.preventDefault();
      let next = event.nextId;

      if(next == this.previewTab) {
         let params: HttpParams = new HttpParams()
            .set("runtimeId", this.runtimeId)
            .set("datasource", this.datasource);

         this.http.post(UPDATE_QUERY_URI, this.model, {params: params}).subscribe(data => {
            this._defaultTab = next;
         });
      }
      else {
         this._defaultTab = next;
         this._oldTab = this._defaultTab;
      }
   }

   goBackToPreviousTab(): void {
      this._defaultTab = this._oldTab;
   }
}
