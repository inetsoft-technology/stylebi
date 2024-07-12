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
import { HttpClient } from "@angular/common/http";
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { Annotation } from "../../../common/data/annotation";
import { AnnotationFormatDialogModel } from "./annotation-format-dialog-model";

const INIT_VIEWSHEET_FORMAT_URI = "../annotation/resetViewsheetFormat";
const INIT_ASSEMBLY_FORMAT_URI = "../annotation/resetAssemblyFormat";
const INIT_DATA_FORMAT_URI = "../annotation/resetDataFormat";

@Component({
   selector: "annotation-format-dialog",
   templateUrl: "annotation-format-dialog.component.html",
})
export class AnnotationFormatDialog implements OnInit {
   @Input() model: AnnotationFormatDialogModel;
   @Input() annotationType: Annotation.Type;
   @Input() forChart: boolean = false;
   @Output() onCommit = new EventEmitter<AnnotationFormatDialogModel>();
   @Output() onCancel = new EventEmitter<string>();
   form: UntypedFormGroup;
   alphaInvalid: boolean = false;

   ngOnInit(): void {
      this.form = new UntypedFormGroup({
         roundCornerForm: new UntypedFormGroup({})
      });
   }

   constructor(private http: HttpClient) {
   }

   public showLine() {
      return !Annotation.isViewsheetAnnotation(this.annotationType);
   }

   /**
    * Reset the dialog to the initialized state.
    */
   async reset() {
      if(this.forChart) {
         this.model = await this.http.get<AnnotationFormatDialogModel>(INIT_ASSEMBLY_FORMAT_URI).toPromise();
         return;
      }

      const uris: [string, (t) => boolean][] = [
         [INIT_VIEWSHEET_FORMAT_URI, Annotation.isViewsheetAnnotation],
         [INIT_ASSEMBLY_FORMAT_URI, Annotation.isAssemblyAnnotation],
         [INIT_DATA_FORMAT_URI, Annotation.isDataAnnotation]
      ];

      const [matchedUri] = uris.find(([uri, predicate]) => predicate(this.annotationType));
      this.model = await this.http.get<AnnotationFormatDialogModel>(matchedUri).toPromise();
   }

   ok() {
      this.onCommit.emit(this.model);
   }

   cancel() {
      this.onCancel.emit("cancel");
   }

   changeAlphaWarning(event) {
      this.alphaInvalid = event;
   }
}
