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
import { FlatTreeControl } from "@angular/cdk/tree";
import { HttpErrorResponse } from "@angular/common/http";
import { Component, EventEmitter, OnInit, Output } from "@angular/core";
import { MatSnackBar } from "@angular/material/snack-bar";
import { MatTreeFlatDataSource, MatTreeFlattener } from "@angular/material/tree";
import { Observable, of as observableOf, throwError } from "rxjs";
import { catchError } from "rxjs/operators";
import { ActionTreeNode } from "../action-tree-node";
import { SecurityActionService } from "../security-action.service";
import { Tool } from "../../../../../../../shared/util/tool";

export class ActionFlatTreeNode {
   constructor(public expandable: boolean, public type: string, public resource: string,
              public label: string, public level: number, public folder: boolean, public grant: boolean) {
   }
}

@Component({
   selector: "em-security-actions-tree",
   templateUrl: "./security-actions-tree.component.html",
   styleUrls: ["./security-actions-tree.component.scss"]
})
export class SecurityActionsTreeComponent implements OnInit {
   @Output() actionSelected = new EventEmitter<ActionTreeNode>();

   treeControl: FlatTreeControl<ActionFlatTreeNode>;
   treeFlattener: MatTreeFlattener<ActionTreeNode, ActionFlatTreeNode>;
   dataSource: MatTreeFlatDataSource<ActionTreeNode, ActionFlatTreeNode>;

   selectedType: string;
   selectedResource: string;

   constructor(private actionService: SecurityActionService, private snackBar: MatSnackBar) {
      this.treeFlattener = new MatTreeFlattener<ActionTreeNode, ActionFlatTreeNode>(
         this.transformer, this.getLevel, this.isExpandable, this.getChildren);
      this.treeControl = new FlatTreeControl<ActionFlatTreeNode>(this.getLevel, this.isExpandable);
      this.dataSource = new MatTreeFlatDataSource<ActionTreeNode, ActionFlatTreeNode>(
         this.treeControl, this.treeFlattener);
   }

   ngOnInit() {
      this.actionService.getActionTree()
         .pipe(catchError(error => this.handleTreeError(error)))
         .subscribe(root => {
            this.sortTree(root);
            this.dataSource.data = root.children;
         });
   }

   selectNode(node: ActionTreeNode) {
      if(!!node && !!node.resource) {
         this.selectedType = node.type;
         this.selectedResource = node.resource;
         this.actionSelected.emit(node);
      }
      else {
         this.selectedType = null;
         this.selectedResource = null;
         this.actionSelected.emit(null);
      }
   }

   sortTree(root: ActionTreeNode) {
      if(root) {
         root.children.sort((node1, node2) => {
            if(node1.folder && !node2.folder) {
               return -1;
            }

            if(!node1.folder && node2.folder) {
               return 1;
            }

            if(node1.folder && node2.folder) {
               if(node1.label == "_#(js:Others)") {
                  return 1;
               }
               else if(node2.label == "_#(js:Others)") {
                  return -1;
               }
            }

            return node1.label.localeCompare(node2.label);
         });
         root.children.forEach(child => this.sortTree(child));
      }
   }

   getIcon(node: ActionTreeNode): string {
      if(node.folder) {
         return "file-icon";
      }

      switch(node.type) {
      case "VIEWSHEET_TOOLBAR_ACTION":
         switch(node.resource) {
         case "Bookmark":
            return "bookmark-icon";
         case "ShareBookmark":
            return "bookmark-default-icon";
         default:
            return "file-icon";
         }
      case "PORTAL_TAB":
         switch(node.resource) {
         case "Data":
            return "database-icon";
         default:
            return "field-tree-icon";
         }
      case "MATERIALIZATION":
         return "auto-reload-icon";
      case "DASHBOARD":
         return "viewsheet-book-icon";
      case "DEVICE":
         return "mobile-icon";
      case "PHYSICAL_TABLE":
      case "FREE_FORM_SQL":
         return "db-table-icon";
      case "SCHEDULER":
         return "datetime-field-icon";
      case "VIEWSHEET":
         return "viewsheet-icon";
      case "WORKSHEET":
         return "worksheet-icon";
      case "CREATE_TABLE_STYLE":
         return "style-icon";
      case "CREATE_SCRIPT":
         return "javascript-icon";
      case "PROFILE":
         return "profile-icon";
      case "CREATE_DATA_SOURCE":
         return "database-icon";
      default:
         return "file-icon";
      }
   }

   transformer = (node: ActionTreeNode, level: number) => {
      return new ActionFlatTreeNode(
         node.folder, node.type, node.resource, node.label, level, node.folder, node.grant);
   };

   private getLevel = (node: ActionFlatTreeNode) => node.level;
   private isExpandable = (node: ActionFlatTreeNode) => node.expandable;
   private getChildren =
      (node: ActionTreeNode): Observable<ActionTreeNode[]> => observableOf(node.children);
   hasChild = (n: number, nodeData: ActionFlatTreeNode) => nodeData.expandable;

   private handleTreeError(error: HttpErrorResponse): Observable<ActionTreeNode> {
      this.snackBar.open("_#(js:em.security.action.getTreeError)", null, {
         duration: Tool.SNACKBAR_DURATION
      });
      console.error("Failed to get action tree: ", error);
      return throwError(error);
   }
}
