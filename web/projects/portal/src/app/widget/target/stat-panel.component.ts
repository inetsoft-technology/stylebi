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
import { TargetInfo, MeasureInfo } from "./target-info";
import { ComboMode, ValueMode } from "../dynamic-combo-box/dynamic-combo-box-model";
import { GraphTypes } from "../../common/graph-types";
import { DefaultPalette } from "../color-picker/default-palette";

@Component({
   selector: "stat-panel",
   templateUrl: "stat-panel.component.html",
   styleUrls: ["stat-panel.component.scss"]
})
export class StatPanel implements OnInit {
   @Input() model: TargetInfo;
   @Input() availableFields: MeasureInfo[];
   @Input() variables: string[] = [];
   @Input() vsId: string = null;
   @Input() assetId: string = null;
   @Input() hideDcombox: boolean;
   @Input() chartType: number;
   categoricalOpen: boolean = false;
   alphaInvalid: boolean = false;
   fillPalette = DefaultPalette.bgWithTransparent;

   ngOnInit() {
      if(!this.model.measure.label && this.availableFields) {
         for(let field of this.availableFields) {
            if(field && field.label) {
               this.model.measure = field;
               break;
            }
         }
      }
   }

   onLabelChange(label: any) {
      this.model.labelFormats = label ? label : "";
      this.model.label = label;
   }

   changeAlphaWarning(event) {
      this.alphaInvalid = event;
   }

   get alphaEnabled(): boolean {
      return this.model.supportFill || this.chartType == GraphTypes.CHART_3D_BAR || this.chartType == GraphTypes.CHART_3D_BAR_STACK;
   }
}
