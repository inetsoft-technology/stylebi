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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { DataSpaceTreeNode } from "../data-space-tree-node";

export interface DataSpaceFileChange {
   newPath: string;
   oldPath: string;
   newName: string;
}

@Component({
   selector: "em-data-space-editor-page",
   templateUrl: "./data-space-editor-page.component.html",
   styleUrls: ["./data-space-editor-page.component.scss"]
})
export class DataSpaceEditorPageComponent implements OnInit {
   @Input() data: DataSpaceTreeNode;
   @Input() newFile: boolean;
   @Input() newFolder: boolean;
   @Input() smallDevice = false;
   @Output() newFileChange = new EventEmitter<boolean>();
   @Output() newFolderChange = new EventEmitter<boolean>();
   @Output() newFileClicked = new EventEmitter<void>();
   @Output() newFolderClicked = new EventEmitter<void>();
   @Output() uploadFilesClicked = new EventEmitter<void>();
   @Output() deleteNodeChange = new EventEmitter<DataSpaceTreeNode>();
   @Output() folderFileAdded = new EventEmitter<string>();
   @Output() folderFileEdited = new EventEmitter<DataSpaceFileChange>();
   @Output() cancel = new EventEmitter<void>();

   constructor() {
   }

   ngOnInit() {
   }
}
