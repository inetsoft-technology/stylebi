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
import { HttpClient, HttpHeaders, HttpParams } from "@angular/common/http";
import {
   Component,
   ElementRef,
   EventEmitter,
   Inject,
   Input,
   OnInit,
   Output,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ConditionOperation } from "../../../common/data/condition/condition-operation";
import { ConditionValueType } from "../../../common/data/condition/condition-value-type";
import { XSchema } from "../../../common/data/xschema";
import { Tool } from "../../../../../../shared/util/tool";
import { SqlQueryDialogModel } from "../../../composer/data/ws/sql-query-dialog-model";
import {
   DataQueryModelService
} from "../../../portal/data/data-datasource-browser/datasources-database/database-query/data-query-model.service";
import {
   DatabaseQueryComponent
} from "../../../portal/data/data-datasource-browser/datasources-database/database-query/database-query.component";
import {
   ClauseOperationSymbols
} from "../../../portal/data/model/datasources/database/vpm/condition/clause/clause-operation-symbols";
import {
   OperationModel
} from "../../../portal/data/model/datasources/database/vpm/condition/clause/operation-model";
import { ModelService } from "../../services/model.service";
import { TreeNodeModel } from "../../tree/tree-node-model";
import { TreeComponent } from "../../tree/tree.component";
import { AbstractTableAssembly } from "../../../composer/data/ws/abstract-table-assembly";
import { ComponentTool } from "../../../common/util/component-tool";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { SqlQueryDialogController } from "./sql-query-dialog-controller";
import { WsSqlQueryController } from "../../../composer/gui/ws/editor/ws-sql-query-controller";

const CHANGE_EDIT_MODE_URI: string = "../api/composer/ws/sql-query-dialog/change-edit-mode";
const QUERY_OPERATIONS_URI = "../api/data/datasource/query/operations";
const DESTROY_RUNTIME_QUERY_URI = "../api/data/datasource/query/runtime-query/destroy";
const CLEAR_MODEL_URI = "../api/composer/ws/sql-query-dialog/clear";

@Component({
   selector: "sql-query-dialog",
   templateUrl: "sql-query-dialog.component.html",
   styleUrls: ["sql-query-dialog.component.scss"]
})
export class SQLQueryDialog implements OnInit {
   @Input() model: SqlQueryDialogModel;
   @Input() tableName: string;
   @Input() initTableName: string;
   @Input() applyVisible: boolean = true;
   @Input() crossJoinEnabled: boolean;
   @Input() tables: AbstractTableAssembly[];
   @Input() controller: SqlQueryDialogController;
   @Input() mashUpData: boolean;
   @Input() helpLinkKey: string = "DatabaseQuery";
   @Output() onApply: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild("tree") tree: TreeComponent;
   @ViewChild("joinDialog") joinDialog: TemplateRef<any>;
   @ViewChild("conditionDialog") conditionDialog: TemplateRef<any>;
   @ViewChild("sqlTextArea") sqlTextArea: ElementRef;
   @ViewChild("advancedQueryPane") advancedQueryPane: DatabaseQueryComponent;
   public XSchema = XSchema;
   public ConditionOperation = ConditionOperation;
   public ConditionValueType = ConditionValueType;
   dataSourceTreeRoot: TreeNodeModel;
   headers: HttpHeaders;
   processing: boolean = false;
   loading: boolean = true;
   formValid = () => {
      if(this.processing || !this.form?.valid || !this.model) {
         return false;
      }

      let sqlModel = this.model.advancedEdit ?
            this.model.advancedModel.freeFormSQLPaneModel : this.model.simpleModel;
      return sqlModel && sqlModel.sqlString && sqlModel.sqlString.length > 0;
   };

   tableNameExists = false;
   form: UntypedFormGroup;
   _oldTotalModel: SqlQueryDialogModel;
   operations: OperationModel[] = [];
   sessionOperations: OperationModel[] = [null, null];
   validGroupBy: boolean = true;

   constructor(private modelService: ModelService,
               private modal: NgbModal,
               private http: HttpClient,
               private queryModelService: DataQueryModelService,
               @Inject(DOCUMENT) private document: any)
   {
      this.headers = new HttpHeaders({
         "Content-Type": "application/json"
      });
   }

   get advancedEditing(): boolean {
      return this.model && this.model.advancedEdit;
   }

   get subQuery(): boolean {
      return this.controller && this.controller.subQuery;
   }

   ngOnInit(): void {
      this.form = this.createForm();

      this.form.get("name").valueChanges.subscribe(value => {
         this.initTableName = value;
         this.updateNameValidation();
      });

      this.controller.getModel().subscribe(
         (data) => {
            this.model = <SqlQueryDialogModel> data;
            this._oldTotalModel = Tool.clone(this.model);

            if(this.model.dataSources.length == 0) {
               console.warn("Could not find any data sources.");
               this.loading = false;
               return;
            }

            if(this.model.dataSource) {
               this.dataSourceChanged(true);
            }
         },
         () => {
            console.warn("Could not fetch SQL Query Model");
         }
      );

      this.initOperations();
   }

   createForm(): UntypedFormGroup {
      return new UntypedFormGroup({
         name: new UntypedFormControl(this.initTableName, [
            Validators.required,
            FormValidators.nameSpecialCharacters,
            FormValidators.exists(this.tables?.map((table) => table.name.toUpperCase()),
               {
                  trimSurroundingWhitespace: true,
                  ignoreCase: true,
                  originalValue: this.initTableName
               })
         ])
      });
   }

   dataSourceChanged(initial: boolean = false): void {
      this.controller.dataSource = this.model.dataSource;
      this.loadDataSourceTree(initial);
   }

   loadDataSourceTree(initial: boolean): void {
      this.controller.getDataSourceTree(null, !this.advancedEditing)
         .subscribe((data) => {
            this.dataSourceTreeRoot = data;
            this.loading = false;

            if(!initial) {
               this.clear();
            }
         });
   }

   clearDisabled(): boolean {
      if(!this.model || this.isJoinEditView()) {
         return true;
      }

      if(!this.advancedEditing) {
         let simpleModel = this.model.simpleModel;

         if(!simpleModel.sqlEdited && simpleModel.columns && simpleModel.columns.length === 0) {
            return true;
         }

         return !simpleModel.sqlString;
      }

      let advancedModel = this.model.advancedModel;
      let tables = advancedModel?.linkPaneModel?.tables;
      let sqlString = advancedModel?.freeFormSQLPaneModel?.sqlString;

      return !!tables && tables.length == 0 && !sqlString;
   }

   clear() {
      let params = new HttpParams()
         .set("runtimeId", !!this.model.runtimeId ? this.model.runtimeId : "")
         .set("dataSource", this.model.dataSource)
         .set("tableName", !!this.model.name ? this.model.name : "")
         .set("advancedEdit", this.model.advancedEdit);

      this.http.post<SqlQueryDialogModel>(CLEAR_MODEL_URI, null, { params: params })
         .subscribe((model: SqlQueryDialogModel) => {
            if(!!model) {
               this.model = model;

               if(this.advancedEditing) {
                  this.queryModelService.emitGraphViewChange();
                  this.advancedQueryPane.resetActiveTab();
               }
            }
         });
   }

   updateNameValidation() {
      this.tableNameExists = !!this.tables &&
         this.tables.map((table) => table.name.toUpperCase())
            .indexOf(this.initTableName.toUpperCase()) != -1;
   }

   cancel(): void {
      if(!this.model) {
         this.onCancel.emit("cancel");
      }

      if(!this.advancedEditing || !this.model?.simpleModel || !this.model?.simpleModel?.sqlEdited) {
         this.destroyRuntimeQuery();
         this.onCancel.emit("cancel");
      }
      else if(!this.advancedEditing && this.model?.simpleModel?.sqlEdited &&
         this._oldTotalModel?.simpleModel?.sqlString == this.model?.simpleModel?.sqlString)
      {
         this.destroyRuntimeQuery();
         this.onCancel.emit("cancel");
      }
      else {
         // Blur active element to avoid changed after checked error.
         if(this.document.activeElement) {
            this.document.activeElement.blur();
         }

         const message = "_#(js:Close without saving)";

         ComponentTool.showConfirmDialog(this.modal, "_#(js:Warning)", message,
            {"yes": "_#(js:OK)", "no": "_#(js:Cancel)"}, {backdrop: false, keyboard: false})
            .then((buttonClicked) => {
               if(buttonClicked === "yes") {
                  this.destroyRuntimeQuery();
                  this.onCancel.emit("cancel");
               }
            });
      }
   }

   checkIfNotSaved(event: KeyboardEvent) {
      if(!this.advancedEditing && this.model.simpleModel?.sqlEdited && !event.repeat) {
         event.stopPropagation();
         this.cancel();
      }
   }

   detachedCrossJoin(): boolean {
      if(!this.advancedEditing) {
         let simpleModel = this.model.simpleModel;

         if(!!simpleModel) {
            return !simpleModel.sqlEdited &&
               Object.keys(simpleModel.tables).length > 1 && simpleModel.joins.length == 0;
         }
      }
      else {
         return this.queryModelService.getUnjoinedTables().length > 0;
      }

      return false;
   }

   ok(mashUpData?: boolean): void {
      if(this.tables != null && this.initTableName != "") {
         this.model.name = this.initTableName;
      }

      this.model.mashUpData = mashUpData || !this.mashUpData;
      this.model.closeDialog = true;
      let url = this.controller.CONTROLLER_SOCKET || this.controller.CONTROLLER_MODEL;
      let payload = { model: this.model, controller: url };

      if(this.detachedCrossJoin()) {
         if(this.crossJoinEnabled) {
            ComponentTool
               .showConfirmDialog(this.modal, "_#(js:Cross Join)", "_#(js:cross.join.prompt)")
               .then(result => {
                  if(result === "ok") {
                     this.onCommit.emit(payload);
                  }
               });
         }
         else {
            ComponentTool
               .showMessageDialog(this.modal, "_#(js:Cross Join)", "_#(js:cross.join.forbidden)");
         }

         return;
      }

      if(this.advancedEditing) {
         this.checkQueryValidity(payload);
      }
      else {
         this.onCommit.emit(payload);
      }
   }

   apply(event: boolean): void {
      let url = this.controller.CONTROLLER_SOCKET || this.controller.CONTROLLER_MODEL;
      let payload = {
         collapse: event,
         result: {
            model: this.model,
            controller: url
         }
      };

      if(this.detachedCrossJoin()) {
         if(this.crossJoinEnabled) {
            ComponentTool
               .showConfirmDialog(this.modal, "_#(js:Cross Join)", "_#(js:cross.join.prompt)")
               .then(result => {
                  if(result === "ok") {
                     this.onApply.emit(payload);
                  }
               });
         }
         else {
            ComponentTool
               .showMessageDialog(this.modal, "_#(js:Cross Join)", "_#(js:cross.join.forbidden)");
         }

         return;
      }

      if(this.advancedEditing) {
         this.checkQueryValidity(payload, true);
      }
      else {
         this.onApply.emit(payload);
      }
   }

   checkQueryValidity(payload: any, apply: boolean = false): void {
      if(this.advancedQueryPane) {
         this.advancedQueryPane.checkQuery().then(() => {
            if(apply) {
               this.onApply.emit(payload);
            }
            else {
               this.onCommit.emit(payload);
            }
         });
      }
   }

   onSwitchChange(event: any): void {
      if(event.target.checked) {
         this.model.name = !!this.initTableName ? this.initTableName : (<any> this.controller).tableName;
         this.helpLinkKey = "AdvancedQuery";
         this.refreshModelOnModeChange(true);
      }
      else {
         ComponentTool.showConfirmDialog(this.modal, "_#(js:Warning)",
            "_#(js:data.query.changeQueryModeConfirm)", {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
            .then((buttonClicked) => {
               if(buttonClicked === "yes") {
                  this.refreshModelOnModeChange(false);
               }
               else {
                  this.model.advancedEdit = true;
                  event.target.checked = this.model.advancedEdit;
               }
            });
      }
   }

   refreshModelOnModeChange(advancedEdit: boolean): void {
      let runtimeId = this.controller instanceof WsSqlQueryController ? this.controller.runtimeId : null;
      let params = new HttpParams()
         .set("runtimeWsId", runtimeId)
         .set("runtimeQueryId", this.model.runtimeId)
         .set("advancedEdit", advancedEdit);

      this.http.post<SqlQueryDialogModel>(CHANGE_EDIT_MODE_URI, this.model, {params: params})
         .subscribe(data => {
            this.model = data;
            this.loadDataSourceTree(true);
         });
   }

   isApplyBtnDisabled(): boolean {
      if(!this.model || this.processing || this.tableNameExists || this.isJoinEditView()) {
         return true;
      }

      if(!this.advancedEditing) {
         return !this.model.simpleModel?.sqlString ||
            this.model.simpleModel?.sqlString?.length <= 0;
      }
      else {
         return this.model.advancedModel?.freeFormSQLPaneModel?.sqlString?.length <= 0 ||
            !this.validGroupBy;
      }
   }

   initOperations(): void {
      this.http.get<OperationModel[]>(QUERY_OPERATIONS_URI)
         .subscribe(
            data => {
               this.operations = data;

               data.forEach(operation => {
                  if(operation.symbol == ClauseOperationSymbols.EQUAL_TO) {
                     this.sessionOperations[0] = operation;
                  }
                  else if(operation.symbol == ClauseOperationSymbols.IN) {
                     this.sessionOperations[1] = operation;
                  }
               });
            },
            err => {}
         );
   }

   destroyRuntimeQuery(): void {
      if(!!this.model.runtimeId) {
         const params = new HttpParams()
            .set("runtimeId", this.model.runtimeId);
         this.http.delete(DESTROY_RUNTIME_QUERY_URI, {params: params})
            .subscribe(() => {});
      }
   }

   isJoinEditView(): boolean {
      if(!!this.advancedQueryPane) {
         return this.advancedQueryPane.isJoinEditView();
      }

      return false;
   }
}
