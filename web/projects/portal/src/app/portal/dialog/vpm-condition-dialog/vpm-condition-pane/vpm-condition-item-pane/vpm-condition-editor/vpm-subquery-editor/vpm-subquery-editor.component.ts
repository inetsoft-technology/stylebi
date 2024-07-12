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
import { Component, EventEmitter, Input, OnInit, Output, TemplateRef, ViewChild } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../../../../../../common/util/component-tool";
import {
   WsSqlQueryController
} from "../../../../../../../composer/gui/ws/editor/ws-sql-query-controller";
import {
   SqlQueryDialogController
} from "../../../../../../../widget/dialog/sql-query-dialog/sql-query-dialog-controller";
import {
   SQLQueryDialog
} from "../../../../../../../widget/dialog/sql-query-dialog/sql-query-dialog.component";
import { ModelService } from "../../../../../../../widget/services/model.service";
import { HttpClient } from "@angular/common/http";
import { SlideOutOptions } from "../../../../../../../widget/slide-out/slide-out-options";
import { VpmSqlQueryController } from "./vpm-sql-query-controller";
import {
   ClauseValueModel
} from "../../../../../../data/model/datasources/database/vpm/condition/clause/clause-value-model";
import { Tool } from "../../../../../../../../../../shared/util/tool";
import { SqlQueryDialogModel } from "../../../../../../../composer/data/ws/sql-query-dialog-model";

@Component({
   selector: "vpm-subquery-editor",
   templateUrl: "./vpm-subquery-editor.component.html",
   styleUrls: ["./vpm-subquery-editor.component.scss"]
})
export class VpmSubqueryEditorComponent implements OnInit {
   @Input() isWSQuery: boolean;
   @Output() valueChange: EventEmitter<SqlQueryDialogModel> = new EventEmitter<SqlQueryDialogModel>();
   subSqlQueryController: SqlQueryDialogController;
   private _model: ClauseValueModel;
   private _datasource: string;

   constructor(private modalService: NgbModal,
               private modelService: ModelService,
               private http: HttpClient)
   {
   }

   @Input()
   set datasource(ds: string) {
      this._datasource = ds;
   }

   get datasource() {
      return this._datasource;
   }

   @Input()
   set model(model: ClauseValueModel) {
      this._model = model;
   }

   ngOnInit(): void {
      if(!this.isWSQuery) {
         this.subSqlQueryController = new VpmSqlQueryController(this.http);
      }
      else {
         this.subSqlQueryController = new WsSqlQueryController(this.http, this.modelService);
      }

      this.subSqlQueryController.dataSource = this.datasource;
      this.subSqlQueryController.subQuery = true;
      this.setupModel();
   }

   editSubQuery() {
      this.setupModel();

      const modalOptions: SlideOutOptions = {
         size: "lg",
         backdrop: "static",
         keyboard: false
      };

      const dialog = ComponentTool.showDialog(this.modalService, SQLQueryDialog, (result: any) => {
         this._model.query = Tool.clone(<SqlQueryDialogModel> result.model);
         this.valueChange.emit(this._model.query);
      }, modalOptions);

      dialog.controller = this.subSqlQueryController;
      dialog.applyVisible = false;
   }

   setupModel(): void {
      this.subSqlQueryController.setModel(Tool.clone(this._model?.query));
   }
}
