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
   HttpEventType,
   HttpParams,
   HttpRequest,
   HttpResponse
} from "@angular/common/http";
import { ChangeDetectorRef, Component, Input, OnInit } from "@angular/core";
import { Observable, Subject } from "rxjs";
import { UploadFilesResponse } from "./upload-files-response";

@Component({
   selector: "em-staged-file-chooser",
   templateUrl: "./staged-file-chooser.component.html",
   styleUrls: ["./staged-file-chooser.component.scss"],
   host: { // eslint-disable-line @angular-eslint/no-host-metadata-property
      "class": "em-staged-file-chooser"
   }
})
export class StagedFileChooserComponent implements OnInit {
   @Input() header: string;
   @Input() accept: string;
   @Input() disabled = false;
   @Input() selectButtonLabel = "_#(js:Select)";

   value: any[] = [];
   uploading = false;
   progress = 0;

   constructor(private http: HttpClient, private changeDetector: ChangeDetectorRef) {
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
      // const data = this.value.reduce((form, file, i) => form.append(`file${i + 1}`, file), new FormData());
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
         (error) => result.error(error),
         () => this.uploading = false
      );

      return result;
   }
}
