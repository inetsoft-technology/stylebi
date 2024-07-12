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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { HttpClient, HttpErrorResponse } from "@angular/common/http";
import { RepositoryEditorModel } from "../../../../../../../shared/util/model/repository-editor-model";
import { MatSnackBar } from "@angular/material/snack-bar";
import { catchError } from "rxjs/operators";
import { throwError } from "rxjs";
import { Tool } from "../../../../../../../shared/util/tool";

export interface RepositoryRecycleBinModel extends RepositoryEditorModel {
   originalPath: string;
   originalName: string;
   time: string;
   originalType: string;
   overwrite: boolean;
}

const RECOVER_RECYCLE_BIN_ENTRY: string = "../api/em/content/repository/tree/recycleNode/restore";

@Component({
   selector: "em-repository-recycle-bin-page",
   templateUrl: "./repository-recycle-bin-page.component.html"
})
export class RepositoryRecycleBinPageComponent {
   @Input() model: RepositoryRecycleBinModel;
   @Input() nodeType: string;
   @Input() smallDevice: boolean;
   @Output() cancel = new EventEmitter<void>();
   @Output() editorChanged = new EventEmitter<string>();
   @Output() unsavedChanges = new EventEmitter<boolean>();

   constructor(private http: HttpClient, private snackBar: MatSnackBar) {
   }

   recoverEntry() {
      const body = {"path": this.model.path, "overwrite": this.model.overwrite};

      this.http.post(RECOVER_RECYCLE_BIN_ENTRY, body)
         .pipe(catchError((error: HttpErrorResponse) => {
            this.snackBar.open(error.error.message || error.message,
               "_#(js:Close)", {duration: Tool.SNACKBAR_DURATION});

            return throwError(error);
         }))
         .subscribe((data: string) => {
            if(data) {
               this.snackBar.open(data, "_#(js:Close)", {duration: Tool.SNACKBAR_DURATION});
            }

            this.editorChanged.emit(null);
         });
   }
}
