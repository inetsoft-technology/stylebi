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
import { HttpClient } from "@angular/common/http";
import { Component, Input, OnInit } from "@angular/core";
import { SubqueryTable } from "../../../common/data/condition/subquery-table";
import { DataRef } from "../../../common/data/data-ref";
import { MVConditionPaneModel } from "../../data/ws/mv-condition-pane-model";
import { AppendConditionItemPaneProvider } from "./append-condition-item-pane-provider";
import { DeleteConditionItemPaneProvider } from "./delete-condition-item-pane-provider";

@Component({
   selector: "mv-condition-pane",
   templateUrl: "mv-condition-pane.component.html",
})
export class MVConditionPane implements OnInit {
   @Input() subqueryTables: SubqueryTable[];
   @Input() runtimeId: string;
   @Input() assemblyName: string;
   @Input() model: MVConditionPaneModel;
   @Input() expressionFields: DataRef[];
   @Input() variableNames: string[];
   appendProvider: AppendConditionItemPaneProvider;
   deleteProvider: DeleteConditionItemPaneProvider;

   constructor(private http: HttpClient) {
   }

   ngOnInit(): void {
      this.appendProvider = new AppendConditionItemPaneProvider(
         this.http, this.runtimeId, this.assemblyName);
      this.appendProvider.fields = this.expressionFields;
      this.appendProvider.variableNames = this.variableNames;

      this.deleteProvider = new DeleteConditionItemPaneProvider(
         this.http, this.runtimeId, this.assemblyName);
      this.deleteProvider.fields = this.expressionFields;
      this.deleteProvider.variableNames = this.variableNames;
   }
}
