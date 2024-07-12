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
   Component,
   EventEmitter,
   Input,
   OnDestroy,
   Output,
   ViewChild,
   ViewEncapsulation
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { NgbNavChangeEvent } from "@ng-bootstrap/ng-bootstrap/nav/nav";
import { Subscription } from "rxjs";
import { TreeNodeModel } from "../../../../../widget/tree/tree-node-model";
import {
   AdvancedSqlQueryModel
} from "../../../model/datasources/database/query/advanced-sql-query-model";
import { QueryFieldModel } from "../../../model/datasources/database/query/query-field-model";
import {
   OperationModel
} from "../../../model/datasources/database/vpm/condition/clause/operation-model";
import { DatabaseQueryTabs, DataQueryModelService } from "./data-query-model.service";
import {
   QueryConditionsPaneComponent
} from "./query-main/query-condition-pane/query-conditions-pane.component";
import {
   QueryGroupingPaneComponent
} from "./query-main/query-grouping-pane/query-grouping-pane.component";
import { QueryLinkPaneComponent } from "./query-main/query-link-pane/query-link-pane.component";
import { ParseResult } from "./query-sql/parse-result";
import { ComponentTool } from "../../../../../common/util/component-tool";

const GET_QUERY_MODEL_URI = "../api/data/datasource/query/query-model";
const QUERY_UPDATE_URI = "../api/data/datasource/query/update";

@Component({
   selector: "database-query",
   templateUrl: "./database-query.component.html",
   styleUrls: [
      "../database-physical-model/database-model-pane.scss",
      "./database-query.component.scss"
   ],
   encapsulation: ViewEncapsulation.None
})
export class DatabaseQueryComponent implements OnDestroy {
   @Input() runtimeId: string;
   @Input() databaseName: string;
   @Input() dataSourceTreeRoot: TreeNodeModel;
   @Input() freeFormSqlEnabled: boolean;
   @Output() groupByValidityChange = new EventEmitter<boolean>();
   @ViewChild("queryLinkPane") queryLinkPane: QueryLinkPaneComponent;
   @ViewChild("queryGroupingPane") queryGroupingPane: QueryGroupingPaneComponent;
   private _queryModel: AdvancedSqlQueryModel;
   private validGroupBy: boolean = true;

   get queryModel(): AdvancedSqlQueryModel {
      return this._queryModel;
   }

   @Input()
   set queryModel(model: AdvancedSqlQueryModel) {
      this._queryModel = model;

      if(!!model?.freeFormSQLPaneModel.sqlString &&
         model?.freeFormSQLPaneModel.parseResult == ParseResult.PARSE_FAILED)
      {
         this.activeTab = DatabaseQueryTabs.SQL_STRING;
      }

      this.oldSqlString = model.freeFormSQLPaneModel.sqlString;
   }

   @Input() operations: OperationModel[];
   @Input() sessionOperations: OperationModel[];
   @ViewChild("queryConditionsPane") queryConditionsPane: QueryConditionsPaneComponent;
   activeTab: string = DatabaseQueryTabs.LINKS;
   private subscriptions: Subscription = new Subscription();
   private oldSqlString: string;

   constructor(private http: HttpClient,
               private modalService: NgbModal,
               private queryModelService: DataQueryModelService)
   {
      this.subscriptions.add(this.queryModelService.modelChange.subscribe((callback: () => void) => {
         if(!!this.runtimeId) {
            let params = new HttpParams().set("runtimeId", this.runtimeId);
            this.http.get<AdvancedSqlQueryModel>(GET_QUERY_MODEL_URI, {params: params}).subscribe(model => {
               this.changeQueryModel(model);

               if(callback != null) {
                  callback();
               }
            });
         }
      }));
   }

   ngOnDestroy() {
      if(this.subscriptions) {
         this.subscriptions.unsubscribe();
         this.subscriptions = null;
      }
   }

   isTabDisabled(tab: string): boolean {
      let parseFailed = !!this.queryModel?.freeFormSQLPaneModel?.sqlString &&
         this.queryModel?.freeFormSQLPaneModel?.parseResult == ParseResult.PARSE_FAILED;

      switch(tab) {
         case DatabaseQueryTabs.FIELDS:
            return parseFailed || !this.queryModel || !this.queryModel.linkPaneModel ||
               this.queryModel.linkPaneModel.tables.length == 0;
         case DatabaseQueryTabs.CONDITIONS:
         case DatabaseQueryTabs.SORT:
         case DatabaseQueryTabs.GROUPING:
            return parseFailed || !this.queryModel || !this.queryModel.fieldPaneModel ||
               this.queryModel.fieldPaneModel.fields.length == 0;
      }

      return false;
   }

   updateQueryTab(event: NgbNavChangeEvent): void {
      event.preventDefault();
      let nextTab = event.nextId;

      if(this.activeTab == DatabaseQueryTabs.FIELDS) {
         this.updateQuery(DatabaseQueryTabs.FIELDS, () => this.activeTab = nextTab);
      }
      else if(this.activeTab == DatabaseQueryTabs.CONDITIONS) {
         this.queryConditionsPane.checkDirtyConditions().then(() => {
            this.updateQuery(DatabaseQueryTabs.CONDITIONS, () => this.activeTab = nextTab);
         });
      }
      else if(this.activeTab == DatabaseQueryTabs.SORT) {
         this.updateQuery(DatabaseQueryTabs.SORT, () => this.activeTab = nextTab);
      }
      else if(this.activeTab == DatabaseQueryTabs.GROUPING) {
         this.switchFromGroupingTab(nextTab);
      }
      else if(this.activeTab == DatabaseQueryTabs.SQL_STRING && this.shouldParseSql()) {
         this.queryModelService.parseSql(this.queryModel.freeFormSQLPaneModel, this.runtimeId, false,
            (res: AdvancedSqlQueryModel) => {
               this.changeQueryModel(res);
               this.switchFromFreeSqlTab(nextTab);
            });
      }
      else if(this.activeTab == DatabaseQueryTabs.LINKS && nextTab == DatabaseQueryTabs.SQL_STRING) {
         this.queryModelService.emitModelChange(() => this.activeTab = nextTab);
      }
      else {
         this.activeTab = nextTab;
      }
   }

   // remove spaces, empty lines, replace all single quotes with double quotes
   trimSqlString(str: string) {
      return !str ? null :
         str.replace(/\s+|\n+|'+/g, match => match === "'" ? "\"" : "");
   }

   shouldParseSql(): boolean {
      if(!this.queryModel.sqlEdited) {
         return false;
      }

      return this.trimSqlString(this.oldSqlString) != this.trimSqlString(
         this.queryModel?.freeFormSQLPaneModel?.sqlString);
   }

   switchFromFreeSqlTab(nextTab: any): void {
      let sql = this.queryModel.freeFormSQLPaneModel;

      // @by larryl, if switching from sql view to structured view,
      // verify that information (sql parts) would not be lost.
      if(nextTab != DatabaseQueryTabs.PREVIEW) {
         let generatedSqlString = this.trimSqlString(sql.generatedSqlString);
         let sqlString = this.trimSqlString(sql.sqlString);

         if(sql.parseSql && sql.hasSqlString && sql.parseResult == ParseResult.PARSE_SUCCESS &&
            generatedSqlString != sqlString)
         {
            ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
                  "_#(js:designer.qb.infoLost)",
                  {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
            .then((response) => {
               if(response == "yes") {
                  this.activeTab = nextTab;
               }
            });
         }
         else if(!sql.parseSql) {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Info)",
                  "_#(js:designer.qb.switchNoParseSql)");
         }
         else if(!!sql.sqlString && sql.parseResult != ParseResult.PARSE_SUCCESS) {
            let msg = sql.parseResult == ParseResult.PARSE_PARTIALLY ?
                  "_#(js:designer.qb.query.parseSqlString)" : "";
            msg = "_#(js:designer.qb.query.errFound)" + " " + msg;
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", msg);
         }
         else {
            this.activeTab = nextTab;
         }
      }
      else {
         this.activeTab = nextTab;
      }
   }

   switchFromGroupingTab(nextTab: any): void {
      if(this.validGroupBy) {
         if(this.queryGroupingPane.isGroupByPane()) {
            this.updateQuery(DatabaseQueryTabs.GROUPING, () => this.activeTab = nextTab);
         }
         else {
            this.queryGroupingPane.havingConditionsPane.checkDirtyConditions().then(() => {
               this.updateQuery(DatabaseQueryTabs.GROUPING, () => this.activeTab = nextTab);
            });
         }
      }
      else {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)",
            "_#(js:common.uql.queryJdbc.colNotFound)");
      }
   }

   updateQuery(tab: DatabaseQueryTabs, callback: () => void): void {
      if(this.queryModel) {
         const params = new HttpParams()
            .set("runtimeId", this.runtimeId)
            .set("tab", tab);
         this.http.post(QUERY_UPDATE_URI, this.queryModel, { params: params })
            .subscribe(() => {
               this.queryModelService.emitModelChange(callback);
            });
      }
   }

   groupByChanged(valid: boolean): void {
      if(this.validGroupBy != valid) {
         this.validGroupBy = valid;
         this.groupByValidityChange.emit(valid);
      }
   }

   public isJoinEditView(): boolean {
      if(!!this.queryLinkPane) {
         return this.queryLinkPane.isJoinEditView();
      }

      return false;
   }

   public resetActiveTab(): void {
      this.activeTab = DatabaseQueryTabs.LINKS;
   }

   public checkQuery(): Promise<void> {
      if(this.activeTab == DatabaseQueryTabs.CONDITIONS) {
         return this.queryConditionsPane.checkDirtyConditions();
      }
      else if(this.activeTab == DatabaseQueryTabs.GROUPING &&
         !!this.queryGroupingPane.havingConditionsPane)
      {
         return this.queryGroupingPane.havingConditionsPane.checkDirtyConditions();
      }
      else {
         return Promise.resolve();
      }
   }

   changeQueryModel(newModel: AdvancedSqlQueryModel) {
      if(this._queryModel) {
         Object.assign(this._queryModel, newModel);
      }
      else {
         this._queryModel = newModel;
      }
   }

   getQueryFieldsMap(): Map<string, string> {
      if(!this.queryModel?.fieldPaneModel?.fields) {
         return new Map();
      }

      return this.queryModel.fieldPaneModel.fields
         .reduce((map: Map<string, string>, field: QueryFieldModel) => {
            map.set(field.alias, field.name);
            return map;
         }, new Map());
   }
}
