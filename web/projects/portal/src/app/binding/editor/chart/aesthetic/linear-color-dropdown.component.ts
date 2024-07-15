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

@Component({
   selector: "linear-color-dropdown",
   templateUrl: "linear-color-dropdown.component.html",
   styleUrls: ["linear-color-dropdown.component.scss"]
})
export class LinearColorDropdown {
   @Input() colorFrame: string;
   @Input() disabled: boolean = false;
   @Input() colorFrames: string[] = [];
   @Output() colorFrameChange: EventEmitter<string> = new EventEmitter<string>();

   get imageSrc(): string {
      return this.getSrc(this.colorFrame);
   }

   getTooltip(frame: string): string {
      return frame.endsWith("ColorModel") ? frame.substring(0, frame.length - 10) : frame;
   }

   getSrc(frame: string): string {
      switch(frame) {
      case "BluesColorModel":
         return "assets/Blues.png";
      case "BrBGColorModel":
         return "assets/BrBG.png";
      case "BuGnColorModel":
         return "assets/BuGn.png";
      case "BuPuColorModel":
         return "assets/BuPu.png";
      case "GnBuColorModel":
         return "assets/GnBu.png";
      case "GreensColorModel":
         return "assets/Greens.png";
      case "GreysColorModel":
         return "assets/Greys.png";
      case "OrangesColorModel":
         return "assets/Oranges.png";
      case "OrRdColorModel":
         return "assets/OrRd.png";
      case "PiYGColorModel":
         return "assets/PiYG.png";
      case "PRGnColorModel":
         return "assets/PRGn.png";
      case "PuBuColorModel":
         return "assets/PuBu.png";
      case "PuBuGnColorModel":
         return "assets/PuBuGn.png";
      case "PuOrColorModel":
         return "assets/PuOr.png";
      case "PuRdColorModel":
         return "assets/PuRd.png";
      case "PurplesColorModel":
         return "assets/Purples.png";
      case "RdBuColorModel":
         return "assets/RdBu.png";
      case "RdGyColorModel":
         return "assets/RdGy.png";
      case "RdPuColorModel":
         return "assets/RdPu.png";
      case "RdYlGnColorModel":
         return "assets/RdYlGn.png";
      case "RedsColorModel":
         return "assets/Reds.png";
      case "SpectralColorModel":
         return "assets/Spectral.png";
      case "RdYlBuColorModel":
         return "assets/RdYlBu.png";
      case "YlGnBuColorModel":
         return "assets/YlGnBu.png";
      case "YlGnColorModel":
         return "assets/YlGn.png";
      case "YlOrBrColorModel":
         return "assets/YlOrBr.png";
      case "YlOrRdColorModel":
         return "assets/YlOrRd.png";
      default:
         return "assets/Blues.png";
      }
   }
}
