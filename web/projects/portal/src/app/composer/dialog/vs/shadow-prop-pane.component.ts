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
import { FormsModule } from "@angular/forms";
import { ShadowPropPaneModel } from "../../data/vs/shadow-prop-pane-model";
import { ShapeShadowUtil } from "../../../common/util/shape-shadow-util";
import { DefaultPalette } from "../../../widget/color-picker/default-palette";
import { ColorEditor } from "../../../widget/color-picker/color-editor.component";
import { AlphaDropdown } from "../../../widget/format/alpha-dropdown.component";

/** Upper bound for the distance and blur fields, in pixels. */
const MAX_LENGTH = 50;

@Component({
    selector: "shadow-prop-pane",
    templateUrl: "shadow-prop-pane.component.html",
    imports: [
        FormsModule,
        ColorEditor,
        AlphaDropdown,
    ]
})
export class ShadowPropPane implements OnInit {
   @Input() model: ShadowPropPaneModel;
   shadowColors = DefaultPalette.chart;
   alphaInvalid: boolean = false;

   readonly directions: {value: string, label: string}[] = [
      { value: "N", label: "_#(js:North)" },
      { value: "NE", label: "_#(js:Northeast)" },
      { value: "E", label: "_#(js:East)" },
      { value: "SE", label: "_#(js:Southeast)" },
      { value: "S", label: "_#(js:South)" },
      { value: "SW", label: "_#(js:Southwest)" },
      { value: "W", label: "_#(js:West)" },
      { value: "NW", label: "_#(js:Northwest)" }
   ];

   ngOnInit() {
      // guard against a model persisted before these settings existed
      if(!this.model.color) {
         this.model.color = ShapeShadowUtil.DEFAULT_SHADOW.color;
      }

      if(!this.model.direction) {
         this.model.direction = ShapeShadowUtil.DEFAULT_SHADOW.direction;
      }
   }

   get enabled(): boolean {
      return !!this.model.apply;
   }

   changeColor(color: string): void {
      this.model.color = color;
   }

   changeAlphaWarning(invalid: boolean): void {
      this.alphaInvalid = invalid;
   }

   /** Keep a cleared or out-of-range entry from reaching the server. */
   clamp(field: "distance" | "blur", value: any): void {
      const num = parseInt(value, 10);
      this.model[field] = isNaN(num) ? 0 : Math.max(0, Math.min(MAX_LENGTH, num));
   }
}
