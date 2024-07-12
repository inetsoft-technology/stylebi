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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { QueryNode } from "../../data/ws/query-node";

@Component({
   selector: "query-plan-dialog",
   templateUrl: "query-plan-dialog.component.html",
   styleUrls: ["query-plan-dialog.component.scss"]
})
export class QueryPlanDialog implements OnInit {
   @Input() runtimeId: string;
   @Input() tableName: string;
   root: TreeNodeModel;
   focusedQueryNode: QueryNode;
   @Output() onCommit = new EventEmitter<string>();
   @Output() onCancel = new EventEmitter<string>();

   ngOnInit(): void {
      this.root = this.convertToTreeNodeModel(this.focusedQueryNode);
   }

   private getNodeLabel(node: QueryNode): string {
      if(node == null)
      {
         return null;
      }
      else {
         const relation = node.relation ? `: [${node.relation}]` : "";
         return node.name + relation;
      }
   }

   private convertNodeIcon(node: QueryNode): string {
      if(node == null) {
         return null;
      }

      const iconPath: string = node.iconPath;
      let name: string;

      if(!iconPath) {
         name = "query";
      }
      else if(iconPath.indexOf("concanate.png") > 0) {
         name = "concat";
      }
      else if(iconPath.indexOf("mv.png") > 0) {
         name = "mv";
      }
      else if(iconPath.indexOf("embedded.png") > 0) {
         name = "embedded";
      }
      else if(iconPath.indexOf("join.png") > 0) {
         name = "join";
      }
      else if(iconPath.indexOf("mirror.png") > 0) {
         name = "mirror";
      }
      else if(iconPath.indexOf("physical.png") > 0) {
         name = "bound";
      }
      else if(iconPath.indexOf("rotate.png") > 0) {
         name = "rotated";
      }
      else {
         name = "query";
      }

      return `data-block-type-${name}-icon`;
   }

   private convertToTreeNodeModel(node: QueryNode): TreeNodeModel {
      const root: TreeNodeModel = {};
      root.data = node;
      root.children = [];
      root.leaf = node.nodes.length === 0;
      root.label = this.getNodeLabel(node);
      root.cssClass = node.sql ? "" : "non-sql-query-node";
      root.icon = this.convertNodeIcon(node);
      root.tooltip = node.tooltip;

      for(let child of node.nodes) {
         root.children.push(this.convertToTreeNodeModel(child));
      }

      return root;
   }

   public nodeSelected(e: TreeNodeModel) {
      this.focusedQueryNode = e.data;
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }
}
