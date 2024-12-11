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
import { Component, Input, OnInit } from "@angular/core";
import { Tool } from "../../../../../../shared/util/tool";
import { ImageScalePaneModel } from "../../data/vs/image-scale-pane-model";
import { getImageName, getImageType } from "../../util/image-util";

declare const window: any;

const IMAGE_URL: string = "../api/image/composer/vs/image-preview-pane/image/";
const EMPTY_IMAGE: string = "assets/emptyimage.gif";

@Component({
   selector: "image-scale-pane",
   templateUrl: "image-scale-pane.component.html",
   styleUrls: ["image-scale-pane.component.scss"]
})
export class ImageScalePane implements OnInit {
   @Input() model: ImageScalePaneModel;
   @Input() selectedImage: string;
   @Input() runtimeId: string;
   @Input() animateGif: boolean;
   preview: boolean = false;
   naturalHeight: number = 0;
   naturalWidth: number = 0;
   isSvg = false;
   timestamp = (new Date()).getTime();
   _previewEnabled = true;

   @Input()
   set previewEnabled(enabled: boolean) {
      this._previewEnabled = enabled;

      if(!this._previewEnabled) {
         this.preview = false;
      }
   }

   get previewEnabled(): boolean {
      return this._previewEnabled;
   }

   ngOnInit(): void {
      let imgNode: any = new Image();
      imgNode.onload = () => {
         this.naturalHeight = imgNode.height;
         this.naturalWidth = imgNode.width;
      };

      imgNode.src = this.imageSrc;
      this.isSvg = !!this.selectedImage && this.selectedImage.endsWith(".svg");

      if(this.isSvg) {
         this.model.maintainAspectRatio = true;
      }
   }

   public get imageSrc(): string {
      if(this.selectedImage && getImageName(this.selectedImage) && getImageType(this.selectedImage)) {
         return IMAGE_URL + Tool.byteEncode(getImageName(this.selectedImage))
            + "/" + getImageType(this.selectedImage) + "/" + Tool.byteEncode(this.runtimeId)
            + "?" + this.timestamp;
      }
      else {
         return EMPTY_IMAGE;
      }
   }

   public get scaleImgStyle(): any {
      let scaledStyle: any = {
         "border-image-source": "url('" + this.imageSrc + "')",
         "height": this.model.objectHeight + "px",
         "width": this.model.objectWidth + "px"
      };

      // if image source is smaller than scale size, don't apply scale9
      if(this.naturalHeight >= (this.model.top + this.model.bottom) &&
         this.naturalWidth >= (this.model.left + this.model.right))
      {
         return Object.assign({
            "border-image-slice": (this.model.top || 0) + " " + (this.model.right || 0) + " " +
            (this.model.bottom || 0) + " " + (this.model.left || 0) + " fill",
            "border-image-width": (this.model.top || 0) + "px " + (this.model.right || 0) + "px " +
            (this.model.bottom || 0) + "px " + (this.model.left || 0) + "px"
         }, scaledStyle);
      }
      else {
         return Object.assign({
            "border-image-slice": "0 fill",
            "border-image-width": "0px"
         }, scaledStyle);
      }
   }

   public get scaleImgPadding(): string {
      let rightTemp: number = this.model.right || 0;
      let leftTemp: number = this.model.left || 0;
      let bottomTemp: number = this.model.bottom || 0;
      let topTemp: number = this.model.top || 0;

      if(topTemp + bottomTemp > this.model.objectHeight) {
         if(topTemp > this.model.objectHeight || bottomTemp > this.model.objectHeight) {
            topTemp = 0;
         }

         bottomTemp = 0;
      }

      if(rightTemp + leftTemp > this.model.objectWidth) {
         if(rightTemp > this.model.objectWidth || leftTemp > this.model.objectWidth) {
            leftTemp = 0;
         }

         rightTemp = 0;
      }

      return topTemp + "px " + rightTemp + "px " + bottomTemp + "px " + leftTemp + "px";
   }

   public getMaintainedRatioSize(isHeight: boolean): number {
      let xratio: number = this.model.objectWidth / this.naturalWidth;
      let yratio: number = this.model.objectHeight / this.naturalHeight;
      let ratio = xratio < yratio ? xratio : yratio;

      if(isHeight) {
         return this.naturalHeight * ratio;
      }
      else {
         return this.naturalWidth * ratio;
      }
   }

   reloadImg(): void {
      this.timestamp = (new Date()).getTime();
   }
}
