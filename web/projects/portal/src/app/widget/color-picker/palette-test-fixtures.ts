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
import { ColorPalette } from "./color-classes";

export const LEGACY_HEAD: string[] = [
   "#518db9", "#b9dbf4", "#62a640", "#ade095", "#fc8f2a", "#fde3a7", "#d64541", "#fda7a5"
];

export const MODERN_HEAD: string[] = [
   "#0490ff", "#ff5a35", "#241c4f", "#03d9b3", "#9a2ddc", "#ffb020", "#e5197e", "#8ed604"
];

export const DARK_HEAD: string[] = [
   "#4fa5ff", "#ff8367", "#49447d", "#2deec6", "#ae41f5", "#ffcb82", "#fe3290", "#9feb28"
];

export const LEGACY_TAIL: string[] = [
   "#9368be", "#be90d4", "#95a5a6", "#dadfe1", "#19b5fe", "#c5eff7", "#869530", "#c8d96f",
   "#a88637", "#d2b267", "#019875", "#68c3a3", "#99ccff", "#999933", "#cc9933", "#006666",
   "#993300", "#666666", "#663366", "#cccccc", "#669999", "#cccc66", "#cc6600", "#9999ff",
   "#0066cc", "#ffcc00", "#009999", "#99cc33", "#ff9900", "#66cccc", "#339966", "#cccc33"
];

export function palette40(head: string[]): string[] {
   return head.concat(LEGACY_TAIL);
}

export function grid40(colors: string[]): ColorPalette {
   const rows: string[][] = [];

   for(let i = 0; i < colors.length; i += 8) {
      rows.push(colors.slice(i, i + 8));
   }

   return rows as ColorPalette;
}
