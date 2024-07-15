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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../../../../../../../common/util/component-tool";
import { GraphNodeModel } from "../../../../../../model/datasources/database/physical-model/graph/graph-node-model";

const CHECK_TABLE_ALIAS_URI = "../api/data/datasource/query/table/alias/check";

@Component({
   selector: "query-table-properties-dialog",
   templateUrl: "./query-table-properties-dialog.component.html",
   styleUrls: ["./query-table-properties-dialog.component.scss"]
})
export class QueryTablePropertiesDialogComponent {
   @Input() newAlias: string;
   @Input() runtimeId: string;
   @Input() tableAliasCheck: (graphNode: GraphNodeModel, alias: string) => boolean =
      (graphNode: GraphNodeModel, alias: string) => false;
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @Output() onCommit = new EventEmitter<{name: string, alias: string}>();
   private _graphNode: GraphNodeModel;

   constructor(private modalService: NgbModal,
               private http: HttpClient)
   {
   }

   @Input() set graphNode(node: GraphNodeModel) {
      this._graphNode = node;
      this.newAlias = node.name;
   }

   get graphNode(): GraphNodeModel {
      return this._graphNode;
   }

   get okDisabled(): boolean {
      return !this.newAlias;
   }

   ok(): void {
      if(!!this.tableAliasCheck && this.tableAliasCheck(this.graphNode, this.newAlias)) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)",
            "_#(js:data.query.queryTableNameDuplicate)");
         return;
      }

      const params = new HttpParams()
         .set("runtimeId", this.runtimeId)
         .set("tableAlias", this.newAlias);

      this.http.get(CHECK_TABLE_ALIAS_URI, {params: params})
         .subscribe((result: boolean) => {
            if(result) {
               this.onCommit.emit({name: this.graphNode.aliasSource, alias: this.newAlias});
            }
            else {
               ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                  "_#(js:data.query.queryTableAliasCheck)");
               return;
            }
         })
   }

   cancel(): void {
      this.onCancel.emit(null);
   }
}
