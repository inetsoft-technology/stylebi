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
import { HttpClient, HttpEventType, HttpParams, HttpRequest, HttpResponse } from "@angular/common/http";
import { ChangeDetectorRef, Component, HostBinding, Input, OnInit } from "@angular/core";
import { MatSnackBar } from "@angular/material/snack-bar";
import { Observable, Subject } from "rxjs";
import { UploadFilesResponse } from "./upload-files-response";
import { MatProgressBar } from "@angular/material/progress-bar";
import { MatInput } from "@angular/material/input";
import { MatFormField } from "@angular/material/form-field";
import { FormsModule } from "@angular/forms";
import { MatDivider } from "@angular/material/divider";
import { MatIcon } from "@angular/material/icon";
import { MatIconButton, MatButton } from "@angular/material/button";
import { MatList, MatListItem } from "@angular/material/list";
import { NgIf, NgFor } from "@angular/common";
import { MatCard, MatCardHeader, MatCardContent, MatCardActions } from "@angular/material/card";

@Component({
    selector: "em-staged-file-chooser",
    templateUrl: "./staged-file-chooser.component.html",
    styleUrls: ["./staged-file-chooser.component.scss"],
    imports: [MatCard, NgIf, MatCardHeader, MatCardContent, MatList, NgFor, MatListItem, MatIconButton, MatIcon, MatDivider, FormsModule, MatFormField, MatInput, MatProgressBar, MatCardActions, MatButton]
})
export class StagedFileChooserComponent implements OnInit {
   @HostBinding("class") hostClass = "em-staged-file-chooser";
   @Input() header: string;
   @Input() accept: string;
   @Input() disabled = false;
   @Input() selectButtonLabel = "_#(js:Select)";
   @Input() displayList: boolean = true;
   @Input() uploadType: string= "driver";

   value: any[] = [];
   uploading = false;
   progress = 0;

   constructor(private http: HttpClient, private changeDetector: ChangeDetectorRef,
               private snackBar: MatSnackBar) {
   }

   ngOnInit() {
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

   removeFile(file: any): void {
      this.value = this.value.filter(f => f.name !== file.name);
   }

   uploadFiles(): Observable<string> {
      this.uploading = true;
      this.progress = 0;
      const data = new FormData();

      for(let i = 0; i < this.value.length; i++) {
         data.append(`uploadedFiles`, this.value[i]);
      }

      data.append("uploadType", this.uploadType);
      const options = { params: new HttpParams(), reportProgress: true };
      const request = new HttpRequest("POST", "../api/em/upload", data, options);
      const result = new Subject<string>();

      this.http.request(request).subscribe(
         (event) => {
            if(event.type == HttpEventType.UploadProgress) {
               this.progress = Math.round(100 * event.loaded / event.total);
               this.changeDetector.markForCheck();
            }
            else if(event instanceof HttpResponse) {
               this.value = [];
               const response = <HttpResponse<UploadFilesResponse>> event;
               result.next(response.body.identifier);
               result.complete();
            }
         },
         (error) => {
            const message = error?.error?.message || "_#(js:em.upload.error)";
            this.snackBar.open(message, null, {duration: 5000});
            this.uploading = false;
            this.progress = 0;
            this.changeDetector.detectChanges();
            result.error(error);
         },
         () => this.uploading = false
      );

      return result;
   }

   getDisplayValueStr() {
      return this.value.map(f => f.name).join(", ");
   }
}
