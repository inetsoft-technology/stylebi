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
const RGB = /^rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*(?:,\s*([\d.]+)\s*)?\)$/i;

/**
 * The fill of an annotation tooltip card: the annotation's own background (the canvas fill is a
 * server-painted image, so the model value is the only source) or white. The server sends
 * rgba(r,g,b,alpha); a translucent fill is composited over white so the card is always opaque
 * (alpha 0 gives white). Only plain hex/rgb[a] colors are accepted so the value can never carry
 * other style declarations.
 */
export function annotationTooltipFill(model: VSAnnotationModel): string {
   const bg = model?.annotationRectangleModel?.objectFormat?.background?.trim();

   if(!bg) {
      return DEFAULT_FILL;
   }

   if(HEX.test(bg)) {
      return bg;
   }

   const m = RGB.exec(bg);

   if(!m) {
      return DEFAULT_FILL;
   }

   const alpha = m[4] == null ? 1 : parseFloat(m[4]);

   if(isNaN(alpha)) {
      return DEFAULT_FILL;
   }

   const a = Math.min(1, Math.max(0, alpha));
   const [r, g, b] = [m[1], m[2], m[3]]
      .map(v => Math.round(Math.min(255, +v) * a + 255 * (1 - a)));
   return `rgb(${r}, ${g}, ${b})`;
}
