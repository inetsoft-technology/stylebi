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
   Component,
   ElementRef,
   EventEmitter,
   Input,
   OnInit,
   Output,
   ViewChild
} from "@angular/core";
import { convertRGBToHEX, convertRGBToHSV, getHSVLuminance, RGB } from "./color-utils";

@Component({
   selector: "cp-color-component-editor",
   templateUrl: "color-component-editor.component.html",
   styleUrls: ["color-component-editor.component.scss"]
})
export class ColorComponentEditor implements OnInit {
   @ViewChild("colorEditor") colorEditor: ElementRef;
   @Output() componentSizeChanged: EventEmitter<number> = new EventEmitter<number>();

   @Input() hue = 0;
   @Output() hueChange = new EventEmitter<number>();

   @Input() saturation = 0;
   @Output() saturationChange = new EventEmitter<number>();

   @Input() brightness = 0;
   @Output() brightnessChange = new EventEmitter<number>();

   @Input() red = 0;
   @Output() redChange = new EventEmitter<number>();

   @Input() green = 0;
   @Output() greenChange = new EventEmitter<number>();

   @Input() blue = 0;
   @Output() blueChange = new EventEmitter<number>();

   @Input() hex = "000000";
   @Output() hexChange = new EventEmitter<number>();

   get colorBackground(): string {
      return convertRGBToHEX(this.rgb);
   }

   get colorForeground(): string {
      let l: number = getHSVLuminance(convertRGBToHSV(this.rgb));
      return (l > Math.sqrt(1.05 * 0.05) - 0.05) ? "#000000" : "#ffffff";
   }

   private get rgb(): RGB {
      return new RGB(this.red, this.green, this.blue);
   }

   ngOnInit(): void {
      setTimeout(() => {
         this.componentSizeChanged.emit(this.colorEditor.nativeElement.offsetHeight);
      });
   }
}
