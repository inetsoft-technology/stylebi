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

import {
   HttpClient,
} from "@angular/common/http";
import {
   Component,
   Inject,
   ViewChild
} from "@angular/core";
import {
   MAT_DIALOG_DATA,
   MatDialog,
   MatDialogConfig, MatDialogRef,
} from "@angular/material/dialog";
import { Tool } from "../../../../../../shared/util/tool";
import { StagedFileChooserComponent } from "../../../common/util/file-chooser/staged-file-chooser/staged-file-chooser.component";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";
import { FlatTreeNode } from "../../../common/util/tree/flat-tree-model";
import { DataSpaceTreeNode } from "../../content/data-space/data-space-tree-node";
import { DeleteDataSpaceTreeNodesRequest } from "../../content/data-space/model/delete-data-space-tree-nodes-request";
import { ShapesTreeDataSource } from "./shapes-tree-data-source";

@Component({
   selector: "em-custom-shape-dialog",
   templateUrl: "custom-shape-dialog.component.html",
   styleUrls: ["custom-shape-dialog.component.scss"],
})
export class CustomShapeDialogComponent {
   @ViewChild("fileChooser", { static: true }) fileChooser: StagedFileChooserComponent;
   extractArchives = false;
   orgId: string;
   value: any[];
   uploading = false;
   progress = 0;
   dataSource: ShapesTreeDataSource;
   selectedNodes: FlatTreeNode<DataSpaceTreeNode>[] = [];
   currentNode: FlatTreeNode<DataSpaceTreeNode>;

   constructor(private http: HttpClient,
               public dialog: MatDialog,
               @Inject(MAT_DIALOG_DATA) public data: any,
               public dialogRef: MatDialogRef<CustomShapeDialogComponent>)
   {
      this.orgId = data.orgId;
      this.dataSource = new ShapesTreeDataSource(this.http, this.orgId);
   }

   handleNodeSelected(nodes: FlatTreeNode<DataSpaceTreeNode>[]) {
      this.selectedNodes = nodes;

      if(nodes.length == 1) {
         this.currentNode = nodes[0];
      }
   }

   get uploadHeader(): string {
      if(this.fileChooser && this.fileChooser.value && this.fileChooser.value.length) {
         return "_#(js:em.dataspace.uploadReady)";
      }

      return "_#(js:em.dataspace.selectShapes)";
   }

   addFiles(event: any): void {
      if(event && event.target && event.target.files) {
         for(let i = 0; i < event.target.files.length; i++) {
            const file = event.target.files[i];

            if(!this.value.some(f => f.name === file.name)) {
               this.value.push(file);
            }
         }
      }
   }

   uploadFiles() {
      this.uploading = true;
      this.fileChooser.uploadFiles().subscribe((result) => {
         const uri = "../api/em/content/data-space/folder/upload";
         const request = {
            path: this.currentNode? this.currentNode.data.path : "portal/shapes",
            global: !this.orgId,
            files: result,
            extractArchives: this.extractArchives
         };

         this.http.post(uri, request).subscribe(
            () => {
               this.dataSource.refresh();
               this.uploading = false;
            }
         );
      });
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

      if(this.selectedNodes) {
         let index = this.selectedNodes.findIndex(n => Tool.isEquals(n.data, node));

         if(index >= 0) {
            this.selectedNodes.splice(index, 1);
         }
      }
   }

   deleteDisable() {
      return this.selectedNodes?.length == 0 ||
         this.selectedNodes.findIndex(n => n.data.path == "portal/host-org/shapes") != -1; //hardcode as getDefaultOrgID()
   }
}
