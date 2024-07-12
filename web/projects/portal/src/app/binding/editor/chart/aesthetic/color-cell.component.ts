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
import { Component, Input } from "@angular/core";
import {
   BluesColorModel,
   BrBGColorModel,
   BrightnessColorModel,
   BuGnColorModel,
   BuPuColorModel,
   GnBuColorModel,
   GradientColorModel,
   GreensColorModel,
   GreysColorModel,
   OrangesColorModel,
   OrRdColorModel,
   PiYGColorModel,
   PRGnColorModel,
   PuBuColorModel,
   PuBuGnColorModel,
   PuOrColorModel,
   PuRdColorModel,
   PurplesColorModel,
   RdBuColorModel,
   RdGyColorModel,
   RdPuColorModel,
   RdYlBuColorModel,
   RdYlGnColorModel,
   RedsColorModel,
   SaturationColorModel,
   SpectralColorModel,
   StaticColorModel,
   YlGnBuColorModel,
   YlGnColorModel,
   YlOrBrColorModel,
   YlOrRdColorModel
} from "../../../../common/data/visual-frame-model";
import { AestheticIconCell } from "./aesthetic-icon-cell";

@Component({
   selector: "color-cell",
   templateUrl: "color-cell.component.html",
   styleUrls: ["color-cell.component.scss"]
})
export class ColorCell extends AestheticIconCell {
   @Input() prefix: string = "";

   get frameType() {
      if(!this.frameModel) {
         return "";
      }

      let frameType: string = "";
      let idx: number = this.frameModel.clazz.lastIndexOf(".");

      if(idx != -1 && idx + 1 < this.frameModel.clazz.length) {
         frameType = this.frameModel.clazz.substring(idx + 1);
      }

      return frameType;
   }

   get colors(): string[] {
      let colors: string[] = new Array<string>("#aaaaaa", "#aaaaaa", "#aaaaaa");

      switch(this.frameType) {
      case "StaticColorModel":
         colors[0] = (<StaticColorModel>this.frameModel).color;
         colors[1] = colors[0];
         colors[2] = colors[0];
         break;
      case "SaturationColorModel":
         colors[0] = (<SaturationColorModel>this.frameModel).color;
         colors[1] = this.adjustBrightness(colors[0]);
         colors[2] = colors[1];
         break;
      case "GradientColorModel":
         colors[0] = this.getGradientColor(true);
         colors[1] = this.getGradientColor();
         colors[2] = colors[1];
         break;
      case "BrightnessColorModel":
         colors[0] = "#000000";
         colors[1] = (<BrightnessColorModel>this.frameModel).color;
         colors[2] = "#EEEEEE";
         break;
      default:
      }

      return colors;
   }

   get imageSrc(): string {
      switch(this.frameType) {
      case "CategoricalColorModel":
         return "assets/color_categorical.png";
      case "BipolarColorModel":
         return "assets/color_bipolar.png";
      case "RainbowColorModel":
         return "assets/color_rainbow.png";
      case "HeatColorModel":
         return "assets/color_heat.png";
      case "CircularColorModel":
         return "assets/color_circular.png";
      case "BluesColorModel":
         return "assets/color_Blues.png";
      case "BrBGColorModel":
         return "assets/color_BrBG.png";
      case "BuGnColorModel":
         return "assets/color_BuGn.png";
      case "BuPuColorModel":
         return "assets/color_BuPu.png";
      case "GnBuColorModel":
         return "assets/color_GnBu.png";
      case "GreensColorModel":
         return "assets/color_Greens.png";
      case "GreysColorModel":
         return "assets/color_Greys.png";
      case "OrangesColorModel":
         return "assets/color_Oranges.png";
      case "OrRdColorModel":
         return "assets/color_OrRd.png";
      case "PiYGColorModel":
         return "assets/color_PiYG.png";
      case "PRGnColorModel":
         return "assets/color_PRGn.png";
      case "PuBuColorModel":
         return "assets/color_PuBu.png";
      case "PuBuGnColorModel":
         return "assets/color_PuBuGn.png";
      case "PuOrColorModel":
         return "assets/color_PuOr.png";
      case "PuRdColorModel":
         return "assets/color_PuRd.png";
      case "PurplesColorModel":
         return "assets/color_Purples.png";
      case "RdBuColorModel":
         return "assets/color_RdBu.png";
      case "RdGyColorModel":
         return "assets/color_RdGy.png";
      case "RdPuColorModel":
         return "assets/color_RdPu.png";
      case "RdYlGnColorModel":
         return "assets/color_RdYlGn.png";
      case "RedsColorModel":
         return "assets/color_Reds.png";
      case "SpectralColorModel":
         return "assets/color_Spectral.png";
      case "RdYlBuColorModel":
         return "assets/color_RdYlBu.png";
      case "YlGnBuColorModel":
         return "assets/color_YlGnBu.png";
      case "YlGnColorModel":
         return "assets/color_YlGn.png";
      case "YlOrBrColorModel":
         return "assets/color_YlOrBr.png";
      case "YlOrRdColorModel":
         return "assets/color_YlOrRd.png";
      default:
         return "assets/color_categorical.png";
      }
   }

   getStyleId(svgName: string): string {
      return this.prefix + "_" + svgName;
   }

   getSvgUrl(svgName: string): string {
      return "url(#" + this.prefix + "_" + svgName + ")";
   }

   /**
    * Adjust brightness.
    */
   adjustBrightness(colorStr: string) {
      if(!colorStr) {
         return colorStr;
      }

      let rgb = parseInt(colorStr.replace("#", ""), 16);
      let r = (rgb >> 16) & 0xFF;
      let g = (rgb >> 8) & 0xFF;
      let b = rgb & 0xFF;
      let c = Math.min(r, g);
      c = Math.min(c, b);
      let n = (c << 16) | (c << 8) | c;

      let str = n.toString(16);
      str = "000000" + str;
      str = str.substring(str.length - 6, str.length);

      return "#" + str;
   }

   getGradientColor(from: boolean = false) {
      const frame = (<GradientColorModel>this.frameModel);

      if(from) {
         return frame.fromColor != null ? frame.fromColor :
            frame.cssFromColor != null ? frame.cssFromColor :
               frame.defaultFromColor;
      }
      else {
         return frame.toColor != null ? frame.toColor :
            frame.cssToColor != null ? frame.cssToColor :
               frame.defaultToColor;
      }
   }
}
