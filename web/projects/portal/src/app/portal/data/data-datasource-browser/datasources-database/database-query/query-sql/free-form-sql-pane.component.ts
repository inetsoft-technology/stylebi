/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import {
   FreeFormSqlPaneModel
} from "../../../../model/datasources/database/query/free-form-sql-pane/free-form-sql-pane-model";
import { HttpClient, HttpParams } from "@angular/common/http";
import {
   GetColumnInfoResult
} from "../../../../model/datasources/database/query/free-form-sql-pane/get-column-info-result";
import {
   GetColumnInfoEvent
} from "../../../../model/datasources/database/query/free-form-sql-pane/get-column-info-event";
import { DataQueryModelService } from "../data-query-model.service";
import { ParseResult } from "./parse-result";
import {
   AdvancedSqlQueryModel
} from "../../../../model/datasources/database/query/advanced-sql-query-model";

const GET_COLUMN_INFO_URL = "../api/data/datasource/query/get/columnInfo";
const CLEAR_COLUMN_INFO_URL = "../api/data/datasource/query/clear/columnInfo";

@Component({
   selector: "free-form-sql-pane",
   templateUrl: "./free-form-sql-pane.component.html",
   styleUrls: ["./free-form-sql-pane.component.scss"]
})
export class FreeFormSqlPaneComponent {
   @Input() runtimeId: string;
   @Input() model: FreeFormSqlPaneModel;
   @Output() onUpdated = new EventEmitter<AdvancedSqlQueryModel>();
   @Output() sqlEdited = new EventEmitter<boolean>();

   refreshColumnInfo: boolean;

   constructor(private http: HttpClient,
               private modalService: NgbModal,
               private queryService: DataQueryModelService) {
   }

   getColumnInfo(): void {
      this.queryService.getVariables(this.runtimeId, this.model.sqlString, () => {
         let event = new GetColumnInfoEvent(this.runtimeId, this.model.sqlString);

         this.http.post(GET_COLUMN_INFO_URL, event).subscribe((res: GetColumnInfoResult) => {
            this.model.hasColumnInfo = res.hasColumnInfo;
            this.refreshColumnInfo = false;
         });
      });
   }

   clearColumnInfo(): void {
      this.refreshColumnInfo = true;
      const params = new HttpParams().set("runtimeId", this.runtimeId);

      this.http.get(CLEAR_COLUMN_INFO_URL, { params: params }).subscribe(() => {
         this.model.hasColumnInfo = false;
      });
   }

   editSqlString(): void {
      this.refreshColumnInfo = true;
      this.sqlEdited.emit(true);
   }

   parseNow() {
      this.queryService.parseSql(this.model, this.runtimeId, false,
            (res: AdvancedSqlQueryModel) => this.onUpdated.emit(res));
      this.sqlEdited.emit(false);
   }

   getStatusString() {
      let prefix = "_#(js:Status)" + ": ";

      switch(this.model?.parseResult) {
         case ParseResult.PARSE_INIT:
            return prefix + "_#(js:designer.qb.parseInit)";
         case ParseResult.PARSE_SUCCESS:
            return prefix + "_#(js:designer.qb.parseSuccess)";
         case ParseResult.PARSE_PARTIALLY:
            return prefix + "_#(js:designer.qb.parsePartially)";
         case ParseResult.PARSE_FAILED:
            return prefix + "_#(js:designer.qb.parseFailed)";
      }

      return "";
   }

   parseSqlChanged(parseSql: boolean): void {
      this.model.parseSql = parseSql;
      this.sqlEdited.emit(true);
   }
}
