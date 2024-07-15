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
   EventEmitter,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   Output,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { Observable, Subscription } from "rxjs";
import { FileUploadService } from "../../../common/services/file-upload.service";
import { ContextProvider } from "../../context-provider.service";
import { VSUploadModel } from "../../model/vs-upload-model";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { UploadCompleteEvent } from "../../event/upload-complete-event";
import { NavigationComponent } from "../abstract-nav-component";
import { NavigationKeys } from "../navigation-keys";
import { DataTipService } from "../data-tip/data-tip.service";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../../common/util/component-tool";
import { FormInputService } from "../../util/form-input.service";

const UPLOAD_FILE_URI = "../api/upload/uploadFile/";
const UPLOAD_COMPLETE_URI = "/events/composer/viewsheet/uploadComplete";

@Component({
   selector: "vs-upload",
   templateUrl: "vs-upload.component.html",
   styleUrls: ["vs-upload.component.scss"]
})
export class VSUpload extends NavigationComponent<VSUploadModel> implements OnChanges, OnDestroy {
   @Input() submitted: Observable<boolean>;
   @Output() uploadChanged = new EventEmitter();
   @ViewChild("fileButton") button: ElementRef;
   fileUploadProgress: number = null;
   fileUploadSubscription: Subscription;
   submittedForm: Subscription;
   fileList: FileList;
   private unappliedSelection = false;

   constructor(private fileUploadService: FileUploadService,
               zone: NgZone,
               protected context: ContextProvider,
               protected viewsheetClient: ViewsheetClientService,
               protected dataTipService: DataTipService,
               private formInputService: FormInputService,
               private modalService: NgbModal)
   {
      super(viewsheetClient, zone, context, dataTipService);
   }

   ngOnChanges(changes: SimpleChanges) {
      if(this.viewer && changes.submitted && this.submitted) {
         if(this.submittedForm) {
            this.submittedForm.unsubscribe();
            this.submittedForm = null;
         }

         this.submittedForm = this.submitted.subscribe((isSubmitted) => {
            if(isSubmitted && this.unappliedSelection) {
               this.applySelection();
            }
         });
      }
   }

   ngOnDestroy() {
      super.ngOnDestroy();

      if(this.viewer) {
         this.submittedForm.unsubscribe();
      }
   }

   onChange(event: any) {
      this.model.fileName =
         event.target.files[0] ? event.target.files[0].name : "";
      this.fileList = event.target.files;
      this.unappliedSelection = true;

      if(this.model.submitOnChange) {
         this.applySelection();
      }
      else {
         this.formInputService.addPendingValue(this.model.absoluteName, null);
      }
   }

   private applySelection(): void {
      if(this.fileList.length > 0) {
         this.fileUploadSubscription =
            this.fileUploadService.getObserver().subscribe((val: ProgressEvent) => {
               this.fileUploadProgress = val.loaded / val.total * 100;
            });

         let file: File = this.fileList[0];

         this.fileUploadService.upload(UPLOAD_FILE_URI, [file]).then(() => {
            this.fileUploadProgress = null;
            this.fileUploadSubscription.unsubscribe();

            let e: UploadCompleteEvent = new UploadCompleteEvent(
               this.model.absoluteName, this.model.fileName);
            this.viewsheetClient.sendEvent(UPLOAD_COMPLETE_URI, e);
         }).
         catch((res: any) => {
            const error = JSON.parse(res);
            const msg = error.message || res;
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", msg);

            this.fileUploadProgress = null;
            this.fileUploadSubscription.unsubscribe();
         });
      }

      if(this.model.refresh) {
         this.uploadChanged.emit(this.model.absoluteName);
      }

      this.fileList = null;
      this.unappliedSelection = false;
   }

   /**
    * Keyboard navigation for this component.
    * @param {NavigationKeys} key
    */
   protected navigate(key: NavigationKeys): void {
      if(!!this.button) {
         this.button.nativeElement.focus();

         if(key == NavigationKeys.SPACE) {
            this.button.nativeElement.click();
         }
      }
   }

   /**
    * Clear selection made by navigating.
    */
   protected clearNavSelection(): void {
      // Do nothing
   }
}
