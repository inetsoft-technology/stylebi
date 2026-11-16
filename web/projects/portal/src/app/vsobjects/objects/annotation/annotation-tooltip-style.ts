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
import { VSAnnotationModel } from "../../model/annotation/vs-annotation-model";

const DEFAULT_FILL = "#ffffff";
const HEX = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i;
const RGB = /^rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*(,\s*[\d.]+\s*)?\)$/i;

/**
 * The fill of an annotation tooltip card: the annotation's own background (the canvas fill is a
 * server-painted image, so the model value is the only source) or white. Only plain hex/rgb
 * colors are accepted so the value can never carry other style declarations.
 */
export function annotationTooltipFill(model: VSAnnotationModel): string {
   const bg = model?.annotationRectangleModel?.objectFormat?.background?.trim();
   return bg && (HEX.test(bg) || RGB.test(bg)) ? bg : DEFAULT_FILL;
}

/**
 * Text color for content without its own inline color: dark or light, whichever contrasts with
 * the fill. Null (keep the tooltip default) on the default white fill.
 */
export function annotationTooltipColor(fill: string): string {
   if(!fill || fill === DEFAULT_FILL) {
      return null;
   }

   let rgb: number[] = null;
   const hex = HEX.exec(fill);

   if(hex) {
      const h = hex[1].length === 3 ? hex[1].replace(/./g, "$&$&") : hex[1];
      rgb = [0, 2, 4].map(i => parseInt(h.substring(i, i + 2), 16));
   }
   else {
      const m = RGB.exec(fill);
      rgb = m ? [+m[1], +m[2], +m[3]] : null;
   }

   if(!rgb) {
      return null;
   }

   const luminance = (0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2]) / 255;
   return luminance < 0.5 ? "#ffffff" : "#000000";
}
