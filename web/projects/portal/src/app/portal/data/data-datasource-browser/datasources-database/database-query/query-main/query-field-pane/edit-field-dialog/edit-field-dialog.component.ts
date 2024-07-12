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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../../../../../../../common/util/component-tool";
import { GuiTool } from "../../../../../../../../common/util/gui-tool";
import { TreeNodeModel } from "../../../../../../../../widget/tree/tree-node-model";
import { DataQueryModelService, getFieldFullName } from "../../../data-query-model.service";

const QUERY_FIELDS_TREE_URI = "../api/data/datasource/query/data-source-fields-tree";
const QUERY_EXPRESSION_CHECK_URI = "../api/data/datasource/query/expression/check";

@Component({
   selector: "edit-field-dialog",
   templateUrl: "./edit-field-dialog.component.html",
   styleUrls: ["./edit-field-dialog.component.scss"]
})
export class EditFieldDialogComponent implements OnInit {
   @Input() title: string;
   @Input() runtimeId: string;
   @Input() expression: string;
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @Output() onCommit = new EventEmitter<string>();
   fieldsTree: TreeNodeModel = null;
   functionsTree: TreeNodeModel = null;
   selectedFieldNodes: TreeNodeModel[] = [];
   selectedFunctionNodes: TreeNodeModel[] = [];

   constructor(private http: HttpClient,
               private modalService: NgbModal,
               private queryModelService: DataQueryModelService)
   {
   }

   ngOnInit() {
      this.initTrees();
   }

   initTrees(): void {
      this.initFieldsTree();
      this.initFunctionsTree();
   }

   initFieldsTree(): void {
      const params = new HttpParams().set("runtimeId", this.runtimeId);
      this.http.get(QUERY_FIELDS_TREE_URI, { params })
         .subscribe(tree => this.fieldsTree = tree);
   }

   initFunctionsTree(): void {
      this.functionsTree = this.queryModelService.getFieldFunctionTree();
   }

   ok(): void {
      const params = new HttpParams().set("expression", this.expression);
      this.http.get<boolean>(QUERY_EXPRESSION_CHECK_URI, { params })
         .subscribe(valid => {
            if(valid) {
               this.onCommit.emit(this.expression);
            }
            else {
               ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                  "_#(js:data.query.invalidExpression)");
            }
         });
   }

   cancel(): void {
      this.onCancel.emit(null);
   }

   selectFieldNodes(nodes: TreeNodeModel[]): void {
      this.selectedFieldNodes = nodes;
   }

   selectFunctionNodes(nodes: TreeNodeModel[]): void {
      this.selectedFunctionNodes = nodes;
   }

   iconFunction(node: TreeNodeModel): string {
      return GuiTool.getTreeNodeIconClass(node, "");
   }

   expressionIconFunction(node: TreeNodeModel): string {
      if(node.leaf) {
         return "formula-icon";
      }

      return GuiTool.getTreeNodeIconClass(node, "");
   }

   fieldDown(node?: TreeNodeModel): void {
      if(node) {
         this.selectFieldNodes([node]);
      }

      if(!!this.selectedFieldNodes) {
         let nodes = this.selectedFieldNodes.filter(node => node.leaf);

         if(nodes.length > 0) {
            const field = getFieldFullName(nodes[0], true);
            this.expression = !!this.expression ? this.expression + field : field;
         }
      }
   }

   functionDown(node?: TreeNodeModel): void {
      if(node) {
         this.selectFieldNodes([node]);
      }

      if(!!this.selectedFunctionNodes) {
         let nodes = this.selectedFunctionNodes.filter(node => node.leaf);

         if(nodes.length > 0) {
            const func = nodes[0];

            if(func.type == "function") {
               this.expression = !!this.expression ?
                  func.data + "(" + this.expression + ")" : func.data + "()";
            }
            else if(func.type == "operator") {
               this.expression = !!this.expression ?
                  this.expression + " " + func.data + " " : func.data;
            }
         }
      }
   }
}
