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
import {
   Component,
   ElementRef,
   HostListener,
   Inject,
   OnInit,
   Renderer2
} from "@angular/core";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { RepositoryTreeNode } from "../repository-tree-node";
import { BaseResizeableDialogComponent } from "../../../../common/util/base-dialog/resize-dialog/base-resizeable-dialog.component";

@Component({
   selector: "em-materialize-sheet-dialog",
   templateUrl: "./materialize-sheet-dialog.component.html",
   styleUrls: ["./materialize-sheet-dialog.component.scss"]
})
export class MaterializeSheetDialogComponent extends BaseResizeableDialogComponent implements OnInit {
   selectedNodes: RepositoryTreeNode[];
   mvChanged = false;

   constructor(public dialog: MatDialogRef<MaterializeSheetDialogComponent>,
               protected renderer: Renderer2, protected element: ElementRef,
               @Inject(MAT_DIALOG_DATA) private data: any) {
      super(renderer, element);
      this.selectedNodes = data;
   }

   ngOnInit() {
   }

   @HostListener("window:keyup.esc", [])
   onKeyUp() {
      this.dialog.close(null);
   }

}
