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
import { BreakpointObserver } from "@angular/cdk/layout";
import { HttpClient } from "@angular/common/http";
import { Component, OnInit } from "@angular/core";
import { MatDialog, MatDialogConfig } from "@angular/material/dialog";
import { MatSnackBar, MatSnackBarConfig } from "@angular/material/snack-bar";
import { Tool } from "../../../../../../shared/util/tool";
import { FlatTreeNode } from "../../../common/util/tree/flat-tree-model";
import { ContextHelp } from "../../../context-help";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { Searchable } from "../../../searchable";
import { Secured } from "../../../secured";
import { TopScrollService } from "../../../top-scroll/top-scroll.service";
import { DataSpaceFileChange } from "../data-space/data-space-editor-page/data-space-editor-page.component";
import { DataSpaceTreeDataSource } from "../data-space/data-space-tree-data-source";
import { DataSpaceTreeNode } from "../data-space/data-space-tree-node";
import { DataSpaceUploadDialogComponent } from "./data-space-upload-dialog/data-space-upload-dialog.component";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";
import { DeleteDataSpaceTreeNodesRequest } from "../data-space/model/delete-data-space-tree-nodes-request";

const SMALL_WIDTH_BREAKPOINT = 720;

@Secured({
   route: "/settings/content/data-space",
   label: "Data Space",
   hiddenForMultiTenancy: true
})
@Searchable({
   route: "/settings/content/data-space",
   title: "Data Space",
   keywords: []
})
@ContextHelp({
   route: "/settings/content/data-space",
   link: "EMSettingsContentDataSpace"
})
@Component({
   selector: "em-content-data-space-view",
   templateUrl: "./content-data-space-view.component.html",
   styleUrls: ["./content-data-space-view.component.scss"],
   providers: [DataSpaceTreeDataSource]
})
export class ContentDataSpaceViewComponent implements OnInit {
   selectedNodes: FlatTreeNode<DataSpaceTreeNode>[] = [];
   currentNode: FlatTreeNode<DataSpaceTreeNode>;
   newFile: boolean = false;
   newFolder: boolean = false;
   snackBarConfig: MatSnackBarConfig;

   get editing(): boolean {
      return this._editing;
   }

   set editing(value: boolean) {
      if(value !== this._editing) {
         this._editing = value;
         this.scrollService.scroll("up");
      }
   }

   //For small device use only
   private _editing = false;

   constructor(private pageTitle: PageHeaderService, private dialog: MatDialog,
               private http: HttpClient, public dataSource: DataSpaceTreeDataSource,
               private breakpointObserver: BreakpointObserver,
               private scrollService: TopScrollService, private snackbar: MatSnackBar)
   {
   }

   ngOnInit() {
      this.pageTitle.title = "_#(js:Data Space)";

      this.snackBarConfig = new MatSnackBarConfig();
      this.snackBarConfig.duration = Tool.SNACKBAR_DURATION;
   }

   handleNodeSelected(nodes: FlatTreeNode<DataSpaceTreeNode>[]) {
      this.selectedNodes = nodes;

      if(nodes.length == 1) {
         this.currentNode = nodes[0];
      }

      this.newFile = false;
      this.newFolder = false;
   }

   handleNodeDeleted(node: DataSpaceTreeNode) {
      let nextNode: FlatTreeNode<DataSpaceTreeNode> = null;

      if(this.currentNode) {
         for(let i = 0; i < this.dataSource.data.length - 1; i++) {
            if(Tool.isEquals(this.currentNode, this.dataSource.data[i])) {
               nextNode = this.dataSource.data[i + 1];
            }
         }
      }

      this.currentNode = nextNode;
      this.dataSource.deleteNode(node);
      this.editing = false;

      if(this.selectedNodes) {
         let index = this.selectedNodes.findIndex(n => Tool.isEquals(n.data, node));

         if(index >= 0) {
            this.selectedNodes.splice(index, 1);
         }
      }
   }

   handleNewFile() {
      this.newFile = true;
      this.newFolder = false;
   }

   handleNewFolder() {
      this.newFile = false;
      this.newFolder = true;
   }

   handleUploadFiles() {
      const ref = this.dialog.open(DataSpaceUploadDialogComponent);
      ref.afterClosed().subscribe(result => {
         if(!!result.error) {
            this.snackbar.open(
               "_#(js:em.dataspace.uploadError)", "_#(js:Close)", this.snackBarConfig);
         }
         else {
            const uri = "../api/em/content/data-space/folder/upload";
            const folder = this.currentNode?.data?.path;
            const request = {
               path: folder,
               files: result.uploadId,
               extractArchives: result.extract,
               global: folder == "portal/shapes"
            };

            this.http.post(uri, request).subscribe(
               () => this.dataSource.refresh(this.currentNode),
               () => this.snackbar.open("_#(js:em.dataspace.uploadError)", "_#(js:Close)", this.snackBarConfig)
            );
         }
      });
   }

   handleFolderFileAdded(path: string) {
      this.dataSource.fetchAndSelectNode(path);
      this.editing = false;
   }

   handleFolderFileEdited(change: DataSpaceFileChange) {
      this.dataSource.updateAndSelectNode(change);
      this.editing = false;
   }

   isScreenSmall(): boolean {
      return this.breakpointObserver.isMatched(`(max-width: ${SMALL_WIDTH_BREAKPOINT}px)`);
   }

   deleteSelectedNodes() {
      if(!this.selectedNodes) {
         return;
      }

      const nodes = this.selectedNodes.map((node) => node.data);
      const nodeNames = this.selectedNodes.map((node) => node.data.label).sort().join(", ");
      let content = "_#(js:em.common.items.delete) " + nodeNames + "?";

      this.dialog.open(MessageDialog, <MatDialogConfig>{
         width: "350px",
         data: {
            title: "_#(js:Confirm)",
            content: content,
            type: MessageDialogType.CONFIRMATION
         }
      }).afterClosed().subscribe(confirmed => {
         if(confirmed) {
            const requestBody = new DeleteDataSpaceTreeNodesRequest(nodes, false);
            this.http.post("../api/em/content/data-space/tree/delete", requestBody).subscribe(() => {
               let deleteNodes = [...this.selectedNodes];

               for(let deleteNode of deleteNodes) {
                  this.handleNodeDeleted(deleteNode.data);
               }
            });
         }
      });
    }
}
