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
import {Component, Inject, OnInit} from "@angular/core";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import {MVExceptionModel} from "../../../../../../../shared/util/model/mv/mv-exception-model";
import {ColumnInfo} from "../../../../common/util/table/column-info";
import { ExpandableRowTableInfo } from "../../../../common/util/table/expandable-row-table/expandable-row-table-info";

@Component({
   selector: "em-mv-exceptions-dialog",
   templateUrl: "./mv-exceptions-dialog.component.html",
   styleUrls: ["./mv-exceptions-dialog.component.scss"]
})
export class MvExceptionsDialogComponent implements OnInit {
   exceptions: MVExceptionModel[];
   tableColumns: ColumnInfo[] = [
      {field: "viewsheet", header: "_#(js:Asset)"},
      {field: "reasons", header: "_#(js:Reasons)"}
   ];
   mediumDeviceHeaders: ColumnInfo[] = [
      {field: "viewsheet", header: "_#(js:Asset)"}
   ];
   tableInfo = <ExpandableRowTableInfo> {
      selectionEnabled: false,
      title: "_#(js:Materialized View Issues)",
      columns: this.tableColumns,
      mediumDeviceHeaders: this.mediumDeviceHeaders,
   };

   constructor(public dialogRef: MatDialogRef<MvExceptionsDialogComponent>,
               @Inject(MAT_DIALOG_DATA) public data: any) {
      this.exceptions = data.exceptions;
   }

   ngOnInit() {
   }

}
