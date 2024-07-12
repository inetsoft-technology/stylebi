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
import {Component, EventEmitter, Input, Output} from "@angular/core";
import {AutoSaveRecycleBinModel} from "./auto-save-recycle-bin";
import {HttpClient, HttpErrorResponse} from "@angular/common/http";
import {MatSnackBar} from "@angular/material/snack-bar";
import {catchError} from "rxjs/operators";
import {throwError} from "rxjs";
import {RestoreAssetDialogComponent} from "./restore-asset-dialog.component";
import {MatDialog} from "@angular/material/dialog";
import {RepositoryEntryType} from "../../../../../../../shared/data/repository-entry-type.enum";

const GET_AUTO_SAVE_TIME: string = "../api/em/content/repository/autosave/gettime";

@Component({
   selector: "em-auto-save-recycle-bin-page",
   templateUrl: "./auto-save-recycle-bin-page.component.html"
})
export class AutoSaveRecycleBinPageComponent {
   @Input() nodeType: string;
   @Input() smallDevice: boolean;
   @Output() cancel = new EventEmitter<void>();
   @Output() editorChanged = new EventEmitter<string>();
   @Output() unsavedChanges = new EventEmitter<boolean>();
   _model: AutoSaveRecycleBinModel;

   constructor(private http: HttpClient, private dialog: MatDialog, private snackBar: MatSnackBar) {
   }

   @Input() set model(asset: AutoSaveRecycleBinModel) {
      this._model = asset;

      if(this._model.time == null) {
         this.getCreateTime();
      }
   }

   get model(): AutoSaveRecycleBinModel {
      return this._model;
   }

   recoverEntry() {
      let isVS = this._model.type == RepositoryEntryType.AUTO_SAVE_VS;

      const dialogRef = this.dialog.open(RestoreAssetDialogComponent, {
         role: "dialog",
         width: "750px",
         maxWidth: "100%",
         maxHeight: "100%",
         disableClose: true,
         data: {isVS: isVS, ids: [this.model.path]}
      });

      dialogRef.afterClosed().subscribe((res) => {
         this.editorChanged.emit(null);
      });
   }

   getCreateTime() {
      this.http.post(GET_AUTO_SAVE_TIME, {id: this.model.path})
         .pipe(catchError((error: HttpErrorResponse) => {
            return throwError(error);
         }))
         .subscribe((date) => {
            this.model.time = date + "";
         });
   }
}
