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
import { Component, HostListener, Inject, Input, OnInit } from "@angular/core";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { QueryParamInfo } from "./query-param-info";

@Component({
   selector: "em-input-query-params-dialog",
   templateUrl: "./input-query-params-dialog.component.html",
   styleUrls: ["./input-query-params-dialog.component.scss"]
})
export class InputQueryParamsDialogComponent implements OnInit {
   title: string = "_#(js:Query Parameters)";
   params: QueryParamInfo[] = [];

   constructor(@Inject(MAT_DIALOG_DATA) data: any,
      private dialogRef: MatDialogRef<InputQueryParamsDialogComponent>)
   {
      this.params = data.params ?? this.params;
      this.title = data.title ?? this.title;
   }

   ngOnInit(): void {
   }

   submit() {
      this.dialogRef.close(this.params);
   }

   @HostListener("window:keyup.esc", [])
   onKeyUp() {
      this.cancel();
   }

   cancel(): void {
      this.dialogRef.close(null);
   }
}
