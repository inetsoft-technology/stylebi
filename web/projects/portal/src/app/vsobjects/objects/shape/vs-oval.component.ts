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
import { Component, Input, NgZone, OnChanges, SimpleChanges } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ContextProvider } from "../../context-provider.service";
import { ShapeShadowUtil } from "../../../common/util/shape-shadow-util";
import { VSOvalModel } from "../../model/vs-oval-model";
import { VSShape } from "./vs-shape";
import { DataTipService } from "../data-tip/data-tip.service";
import { VSAnnotation } from "../annotation/vs-annotation.component";
import { VSHiddenAnnotation } from "../annotation/vs-hidden-annotation.component";
import { VSPopComponentDirective } from "../data-tip/vs-pop-component.directive";
import { VSDataTipDirective } from "../data-tip/vs-data-tip.directive";


@Component({
    selector: "vs-oval",
    templateUrl: "vs-oval.component.html",
    styleUrls: ["vs-oval.component.scss"],
    imports: [VSDataTipDirective, VSPopComponentDirective, VSHiddenAnnotation, VSAnnotation]
})
export class VSOval extends VSShape<VSOvalModel> implements OnChanges {
   @Input() selected: boolean = false;
   public ovalFilterId: string;
   public ovalMaskId: string;
   public shadowOffsetX: number = 0;
   public shadowOffsetY: number = 0;
   public shadowStdDeviation: number = 0;
   public shadowColor: string;
   public shadowOpacity: number = 0;
   // how far the shadow reaches past the ellipse, used to size the mask
   public shadowMargin: number = 0;

   constructor(protected viewsheetClient: ViewsheetClientService,
               protected modalService: NgbModal,
               zone: NgZone,
               contextProvider: ContextProvider,
               protected dataTipService: DataTipService)
   {
      super(viewsheetClient, modalService, zone, contextProvider, dataTipService);
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.model) {
         this.updateLineStyle();
         this.ovalFilterId = "ovalblur" + this.validateID(
            this.viewsheetClient.runtimeId + this.getAssemblyName());
         this.ovalMaskId = "ovalmask" + this.validateID(
            this.viewsheetClient.runtimeId + this.getAssemblyName());
         this.updateShadow();
      }
   }

   private updateShadow(): void {
      const shadow = this.model.shadowInfo;
      this.shadowOffsetX = ShapeShadowUtil.getOffsetX(shadow);
      this.shadowOffsetY = ShapeShadowUtil.getOffsetY(shadow);
      const blur = shadow ? Math.max(0, shadow.blur) : 0;
      // feGaussianBlur takes a standard deviation, not a css blur radius
      this.shadowStdDeviation = blur / 2;
      this.shadowColor = shadow ? shadow.color : "#000000";
      this.shadowOpacity =
         Math.max(0, Math.min(100, shadow ? shadow.alpha : 0)) / 100;
      // the mask has to clear the blur as well as the offset, or the shadow
      // gets clipped at larger settings
      this.shadowMargin = blur * 3 +
         Math.max(Math.abs(this.shadowOffsetX), Math.abs(this.shadowOffsetY));
   }

   validateID(id: string): string {
      return id.replace(/ /g, "_");
   }
}
