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
import { Component, Input } from "@angular/core";
import { Tool } from "../../../../../../shared/util/tool";
import { ImageAdvancedPaneModel } from "../../data/vs/image-advanced-pane-model";
import { ImageType } from "../../util/image-util";
import { PopLocation, PopComponentService } from "../../../vsobjects/objects/data-tip/pop-component.service";
import { TruncatePipe } from "../../../widget/pipe/truncate.pipe";
import { NotificationsComponent } from "../../../widget/notifications/notifications.component";
import { AlphaDropdown } from "../../../widget/format/alpha-dropdown.component";
import { FormsModule } from "@angular/forms";
import { ImageScalePane } from "./image-scale-pane.component";
import { DynamicImagePane } from "./dynamic-image-pane.component";
import { CustomSelectOption, CustomSelectComponent } from "../../../widget/custom-select/custom-select.component";

@Component({
    selector: "image-advanced-pane",
    templateUrl: "image-advanced-pane.component.html",
    imports: [
    DynamicImagePane,
    ImageScalePane,
    FormsModule,
    AlphaDropdown,
    NotificationsComponent,
    TruncatePipe, CustomSelectComponent]
})
export class ImageAdvancedPane {
   @Input() model: ImageAdvancedPaneModel;
   @Input() selectedImage: string;
   @Input() runtimeId: string;
   @Input() variableValues: string[];
   @Input() layoutObject: boolean = false;
   @Input() animateGif: boolean = false;
   @Input() objectAddRemoved: boolean = false;
   alphaInvalid = false;
   readonly popLocationKeys: string[] = Object.keys(PopLocation);

   constructor(public popService: PopComponentService) {
   }

   changeAlphaWarning($event: boolean): void {
      this.alphaInvalid = $event;
   }

   get popComponentOptions(): CustomSelectOption<string>[] {
      return [
         { label: "_#(None)", value: "" },
         ...(this.model?.popComponents ?? []).map((component) => ({
            label: component,
            value: component,
            title: component
         }))
      ];
   }

   get popLocationOptions(): CustomSelectOption<string>[] {
      return this.getKeys().map((key) => ({
         label: this.popService.getPopLocationLabel(key),
         value: key,
         title: this.popService.getPopLocationLabel(key)
      }));
   }

   previewEnabled(): boolean {
      if(!this.model.dynamicImagePaneModel.dynamicImageValue) {
         return true;
      }

      return !Tool.isDynamic(this.model.dynamicImagePaneModel.dynamicImageValue);
   }

   get selectedImagePath(): string {
      let imagePath = this.model.dynamicImagePaneModel.dynamicImageValue;

      if(this.model.dynamicImagePaneModel.dynamicImageSelected &&
         imagePath && !Tool.isDynamic(imagePath))
      {
         if(imagePath.indexOf(ImageType.UPLOAD) != -1) {
            return imagePath;
         }

         return ImageType.UPLOAD + imagePath;
      }

      return this.selectedImage;
   }

   getKeys(): string[] {
      return Object.keys(PopLocation);
   }
}
