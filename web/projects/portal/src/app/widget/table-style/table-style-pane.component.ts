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
import { Component, Input, Output, ViewChild, EventEmitter, OnInit } from "@angular/core";
import { TableStylePaneModel } from "./table-style-pane-model";
import { TreeNodeModel } from "../tree/tree-node-model";
import { Tool } from "../../../../../shared/util/tool";
import { TreeComponent } from "../tree/tree.component";
import { BaseHrefService } from "../../common/services/base-href.service";

@Component({
   selector: "table-style-pane",
   templateUrl: "table-style-pane.component.html",
   styleUrls: ["table-style-pane.component.scss"]
})
export class TableStylePane implements OnInit {
   @Input() model: TableStylePaneModel;
   @Input() isComposer: boolean = true;
   @Output() styleChanged: EventEmitter<any> = new EventEmitter<any>();
   @ViewChild(TreeComponent) tree: TreeComponent;
   selectedNodes: TreeNodeModel[] = [];
   previewFolders: TreeNodeModel[] = [];
   previewStyle: string;
   controller: string = "../composer/vs/table-view-style-pane/preview/";
   date: number;

   constructor(private baseHrefService: BaseHrefService) {
   }

   ngOnInit() {
      if(this.model && this.model.styleTree && this.model.styleTree.children
         && this.model.styleTree.children[0])
      {
         this.model.styleTree.children[0].expanded = true;

         if(this.model.tableStyle && this.getSelectedNode()) {
            this.selectedNodes.push(this.getSelectedNode());
         }

         this.previewStyle = this.model.tableStyle;

         if(!this.findNode(this.model.styleTree, this.previewStyle)) {
            this.clearStyle();
         }
      }

      this.date = new Date().getTime();
   }

   getSelectedNode(): TreeNodeModel {
      let node: TreeNodeModel = this.findNode(this.model.styleTree,
         this.model.tableStyle);

      return node;
   }

   findNode(node: TreeNodeModel, tableStyle: string): TreeNodeModel {
      if(node.children) {
         for(let i = 0; i < node.children.length; i++) {
            let child: TreeNodeModel = node.children[i];

            if(child.data == tableStyle) {
               return child;
            }

            let child0: TreeNodeModel = this.findNode(child, tableStyle);

            if(child0) {
               return child0;
            }
         }
      }

      return null;
   }

   openStyleFolder(node: TreeNodeModel): void {
      if(node.type == "folder") {
         this.previewFolders = node.children;
      }
   }

   selectStyle(node: TreeNodeModel, ontree: boolean = true): void {
      if(ontree) {
         this.previewFolders = [];
         this.previewStyle = null;

         if(node.type == "style") {
            this.previewStyle = node.data;
            this.model.tableStyle = node.data;
         }
         else {
            this.previewFolders = node.children;
         }
      }
      else {
         this.model.tableStyle = node.data;
      }

      if(this.model.tableStyle) {
         this.styleChanged.emit();
      }
   }

   getImgSrc(id: string, isStyle: boolean): string {
      return this.baseHrefService.getBaseHref() + "/" + this.controller +
         Tool.byteEncode(id) + "/" + isStyle + "?t=" + this.date;
   }

   clearStyle(): void {
      this.model.tableStyle = null;
      this.previewStyle = null;
      this.previewFolders = [];
      this.tree.selectedNodes = [];
      this.styleChanged.emit();
   }
}
