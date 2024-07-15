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
import { Component, Input, Output, EventEmitter } from "@angular/core";
import { ColorMode } from "./color-mode.enum";
import { ColorPalette } from "./color-classes";
import { DefaultPalette } from "./default-palette";
import { GradientColor } from "../../common/data/base-format-model";

@Component({
   selector: "color-editor",
   templateUrl: "color-editor.component.html",
   styleUrls: ["color-editor.component.scss"]
})
export class ColorEditor {
   ColorMode = ColorMode;
   @Input() color: string = undefined;
   @Input() enabled: boolean = true;
   @Input() gradientModel: GradientColor;
   @Input() gradient: boolean = false;
   @Input() mode: ColorMode = ColorMode.BUTTON;
   @Input() palette: ColorPalette = DefaultPalette.chart;
   @Input() label: string = null;
   @Input() isTableStyle: boolean = false;
   @Output() colorChange: EventEmitter<string> = new EventEmitter<string>();
   private firstChange: boolean = true;

   changeColor(color: string): void {
      this.color = color;
      this.colorChange.emit(color);
   }

   changeGradientColor(value: GradientColor) {
      this.gradientModel.direction = value.direction;
      this.gradientModel.colors =  value.colors;
   }
}
