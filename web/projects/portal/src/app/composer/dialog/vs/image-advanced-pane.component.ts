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
import { Component, Input } from "@angular/core";
import { Tool } from "../../../../../../shared/util/tool";
import { ImageAdvancedPaneModel } from "../../data/vs/image-advanced-pane-model";
import { ImageType } from "../../util/image-util";
import { PopLocation, PopComponentService} from "../../../vsobjects/objects/data-tip/pop-component.service";

@Component({
   selector: "image-advanced-pane",
   templateUrl: "image-advanced-pane.component.html",
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

   constructor(public popService: PopComponentService) {
   }

   changeAlphaWarning($event: boolean): void {
      this.alphaInvalid = $event;
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
