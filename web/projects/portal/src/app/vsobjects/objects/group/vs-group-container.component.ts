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
import { Component, EventEmitter, NgZone, Input, Output } from "@angular/core";
import { DomSanitizer, SafeStyle } from "@angular/platform-browser";
import { Dimension } from "../../../common/data/dimension";
import { Tool } from "../../../../../../shared/util/tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client/viewsheet-client.service";
import { ContextProvider } from "../../context-provider.service";
import { VSGroupContainerModel } from "../../model/vs-group-container-model";
import { AbstractVSObject } from "../abstract-vsobject.component";
import { DataTipService } from "../data-tip/data-tip.service";

@Component({
   selector: "vs-group-container",
   templateUrl: "vs-group-container.component.html",
   styleUrls: ["vs-group-container.component.scss"]
})
export class VSGroupContainer extends AbstractVSObject<VSGroupContainerModel> {
   @Input() selected: boolean = false;

   public imageSize = new Dimension(null, null);
   constructor(protected viewsheetClient: ViewsheetClientService,
               private sanitization: DomSanitizer,
               zone: NgZone,
               protected context: ContextProvider,
               protected dataTipService: DataTipService)
   {
      super(viewsheetClient, zone, context, dataTipService);
   }

   public getSrc(): SafeStyle {
      if(this.model.noImageFlag || !this.vsInfo) {
         return "";
      }

      return this.vsInfo.linkUri + "getAssemblyImage" +
            "/" + Tool.byteEncodeURLComponent(this.viewsheetClient.runtimeId) +
            "/" + Tool.byteEncodeURLComponent(this.model.absoluteName) +
            "/" + this.model.objectFormat.width +
            "/" + this.model.objectFormat.height +
            "?" + this.model.genTime;
   }

   public getOpacity(): number {
      if(this.model.noImageFlag) {
         return this.model.objectFormat.alpha;
      }
      else {
         return parseFloat(this.model.imageAlpha) / 100;
      }
   }

   public onImageLoad(event: Event): void {
      if(this.model.scaleInfo.preserveAspectRatio && this.model.scaleInfo.scaleImage) {
         const image = event.target as HTMLImageElement;
         const size = new Dimension(this.model.objectFormat.width, this.model.objectFormat.height);
         this.imageSize = size.getScaledDimension(image.naturalWidth / image.naturalHeight);
      }
      else if(this.model.scaleInfo.scaleImage) {
         this.imageSize =
            new Dimension(this.model.objectFormat.width, this.model.objectFormat.height);
      }
      else {
         this.imageSize = new Dimension(null, null);
      }
   }
}
