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
   Component, Output, EventEmitter, Input, ViewChild, TemplateRef
} from "@angular/core";
import { PresenterPropertyDialogModel } from "./data/presenter-property-dialog-model";
import { ImagePreviewPaneModel } from "../image-editor/image-preview-pane-model";
import { NgbModalOptions, NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { PresenterDescriptorModel } from "./data/presenter-descriptor-model";
import { ImageDescriptorModel } from "./data/image-descriptor-model";
import { BooleanDescriptorModel } from "./data/boolean-descriptor-model";
import { ColorDescriptorModel } from "./data/color-descriptor-model";
import { EditImageDialog } from "./edit-image-dialog.component";
import { ColorEditor } from "../color-picker/color-editor.component";
import { FontPane } from "../font-pane/font-pane.component";
import { FormsModule } from "@angular/forms";
import { StyleDropdown } from "../format/style-dropdown.component";

import { ModalHeaderComponent } from "../modal-header/modal-header.component";

import { NumberStepperComponent } from "../number-stepper/number-stepper.component";
@Component({
    selector: "presenter-property-dialog",
    templateUrl: "presenter-property-dialog.component.html",
    styleUrls: ["./presenter-property-dialog.component.scss"],
    imports: [ModalHeaderComponent, StyleDropdown, FormsModule, FontPane, ColorEditor, EditImageDialog, NumberStepperComponent]
})
export class PresenterPropertyDialog {
   @Input() model: PresenterPropertyDialogModel;
   @Input() runtimeId: string;
   @Input() animateGif: boolean = true;
   @Output() onCommit: EventEmitter<PresenterPropertyDialogModel>
      = new EventEmitter<PresenterPropertyDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild("editImageDialog") editImageDialog: TemplateRef<any>;
   imagePreviewPaneModel: ImagePreviewPaneModel;

   constructor(private modalService: NgbModal) {}

   openEditImageDialog(index: number) {
      const descriptor: PresenterDescriptorModel = this.model.descriptors[index];
      this.imagePreviewPaneModel = (<ImageDescriptorModel> descriptor).value;
      this.imagePreviewPaneModel.presenter = true;
      const options: NgbModalOptions = {
         windowClass: "property-dialog-window"
      };

      this.modalService.open(this.editImageDialog, options).result.then(
         (result: PresenterPropertyDialogModel) => {
            this.imagePreviewPaneModel = null;
         },
         (reason: string) => {
            this.imagePreviewPaneModel = null;
         }
      );
   }

   isColorEnable(): boolean {
      if(this.model.presenter && this.model.presenter.endsWith(".BulletGraphPresenter")) {
         return true;
      }

      let colorEnable: boolean = false;
      let hasImagePropertyEditor: boolean = false;

      for(let i = 0; i < this.model.descriptors.length; i++) {
         let descriptor: PresenterDescriptorModel = this.model.descriptors[i];

         if(descriptor.editor == "ImagePropertyEditor") {
            let imageDescriptor: ImageDescriptorModel = <ImageDescriptorModel>descriptor;
            hasImagePropertyEditor = true;

            if(imageDescriptor.value.selectedImage == null) {
               colorEnable = true;
               return colorEnable;
            }
         }
      }

      return colorEnable || !hasImagePropertyEditor;
   }

   /**
    * A bar presenter with no color of its own is painted in a default color chosen by the table,
    * so the empty color box is labelled instead of looking like a missing value.
    */
   isDefaultColorInUse(descriptor: PresenterDescriptorModel): boolean {
      return this.isBarColor(descriptor) && !(<ColorDescriptorModel> descriptor).value;
   }

   /** A bar's own color can be dropped to go back to the default. */
   canResetColor(descriptor: PresenterDescriptorModel): boolean {
      return this.isBarColor(descriptor) && !!(<ColorDescriptorModel> descriptor).value &&
         this.isColorEnable();
   }

   resetColor(descriptor: PresenterDescriptorModel): void {
      (<ColorDescriptorModel> descriptor).value = "";
   }

   private isBarColor(descriptor: PresenterDescriptorModel): boolean {
      const presenter = this.model?.presenter;

      return !!presenter && (presenter.endsWith(".BarPresenter") ||
            presenter.endsWith(".Bar2Presenter")) && descriptor.editor == "ColorPropertyEditor";
   }

   isImageOptionDisable(attr: string): boolean {
      if(!this.model.presenter || !this.model.presenter.endsWith(".ImagePresenter")) {
         return false;
      }

      if(attr == "autoSize") {
         return this.model.descriptors &&
            (<BooleanDescriptorModel> this.model.descriptors[1]).value == true;
      }
      else if(attr == "maintainAspectRatio") {
         return this.model.descriptors &&
            (<BooleanDescriptorModel> this.model.descriptors[0]).value == true;
      }

      return false;
   }

   ok(): void {
      this.onCommit.emit(this.model);
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }
}
