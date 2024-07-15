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
import { Component, HostListener, Input } from "@angular/core";
import { MatDialogRef } from "@angular/material/dialog";
import { FlatTreeControl } from "@angular/cdk/tree";
import { RepositoryFlatNode } from "../../content/repository/repository-tree-node";
import { MatTreeFlatDataSource } from "@angular/material/tree";
import { FlatTreeSelectNodeEvent } from "../../../common/util/tree/flat-tree-view.component";

@Component({
   selector: "em-move-task-folder-dialog",
   templateUrl: "./move-task-folder-dialog.component.html",
   styleUrls: ["./move-task-folder-dialog.component.scss"],
})
export class MoveTaskFolderDialogComponent {
   @Input() treeControl: FlatTreeControl<RepositoryFlatNode>;
   @Input() treeSource: MatTreeFlatDataSource<any, RepositoryFlatNode>;
   selectedNodes: RepositoryFlatNode[] = [];

   constructor(private dialogRef: MatDialogRef<MoveTaskFolderDialogComponent>)
   {
   }

   public nodeSelected(evt: FlatTreeSelectNodeEvent): void {
      this.selectedNodes = [<RepositoryFlatNode>evt.node];
   }

   public finish(): void {
      this.dialogRef.close(this.selectedNodes[0]);
   }

   public cancel(): void {
      this.dialogRef.close();
   }

   @HostListener("window:keyup.enter", [])
   onEnter() {
      this.finish();
   }

   @HostListener("window:keyup.esc", [])
   onEsc() {
      this.cancel();
   }
}
