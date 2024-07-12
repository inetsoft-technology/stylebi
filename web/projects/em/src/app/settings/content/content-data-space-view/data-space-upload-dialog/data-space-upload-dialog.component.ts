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
import { Component, OnInit, ViewChild } from "@angular/core";
import { MatDialogRef } from "@angular/material/dialog";
import { StagedFileChooserComponent } from "../../../../common/util/file-chooser/staged-file-chooser/staged-file-chooser.component";
import { DeleteDialog } from "../../data-space/data-space-file-settings-view/data-space-file-settings-view.component";

@Component({
   selector: "em-data-space-upload-dialog",
   templateUrl: "./data-space-upload-dialog.component.html",
   styleUrls: ["./data-space-upload-dialog.component.scss"]
})
export class DataSpaceUploadDialogComponent implements OnInit {
   @ViewChild("fileChooser", { static: true }) fileChooser: StagedFileChooserComponent;
   extractArchives = false;

   get uploadHeader(): string {
      if(this.fileChooser && this.fileChooser.value && this.fileChooser.value.length) {
         return "_#(js:em.dataspace.uploadReady)";
      }

      return null;
   }

   constructor(public dialogRef: MatDialogRef<DeleteDialog>) {
   }

   ngOnInit(): void {
   }

   uploadFiles(): void {
      this.fileChooser.uploadFiles().subscribe(
         uploadId => this.dialogRef.close({uploadId, extract: this.extractArchives}),
         error => this.dialogRef.close({error})
      );
   }
}
