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
import type { ShapeShadow } from "../data/base-format-model";

/**
 * Direction/distance to offset conversion for shape drop shadows. Mirrors
 * inetsoft.uql.viewsheet.ShapeShadow so the browser and the server side
 * exports agree on where the shadow falls.
 */
export namespace ShapeShadowUtil {
   export const DIRECTIONS: string[] =
      ["N", "NE", "E", "SE", "S", "SW", "W", "NW"];

   export const DEFAULT_SHADOW: ShapeShadow = {
      color: "#000000",
      alpha: 30,
      direction: "SE",
      distance: 5,
      blur: 6
   };

   /**
    * Get the horizontal offset in pixels, positive to the right.
    */
   export function getOffsetX(shadow: ShapeShadow): number {
      if(!shadow) {
         return 0;
      }

      if(shadow.direction == "NE" || shadow.direction == "E" ||
         shadow.direction == "SE")
      {
         return shadow.distance;
      }

      if(shadow.direction == "NW" || shadow.direction == "W" ||
         shadow.direction == "SW")
      {
         return -shadow.distance;
      }

      return 0;
   }

   /**
    * Get the vertical offset in pixels, positive downwards.
    */
   export function getOffsetY(shadow: ShapeShadow): number {
      if(!shadow) {
         return 0;
      }

      if(shadow.direction == "SE" || shadow.direction == "S" ||
         shadow.direction == "SW")
      {
         return shadow.distance;
      }

      if(shadow.direction == "NE" || shadow.direction == "N" ||
         shadow.direction == "NW")
      {
         return -shadow.distance;
      }

      return 0;
   }

   /**
    * Get the shadow color as an rgba() string, with the opacity applied.
    */
   export function getRgba(shadow: ShapeShadow): string {
      const alpha = Math.max(0, Math.min(100, shadow ? shadow.alpha : 0)) / 100;
      const rgb = toRgb(shadow ? shadow.color : null);

      return `rgba(${rgb[0]}, ${rgb[1]}, ${rgb[2]}, ${alpha})`;
   }

   /**
    * Get a CSS box-shadow value for the settings, or null when there is none.
    */
   export function getBoxShadow(shadow: ShapeShadow): string {
      if(!shadow) {
         return null;
      }

      const blur = Math.max(0, shadow.blur);

      return `${getOffsetX(shadow)}px ${getOffsetY(shadow)}px ${blur}px ` +
         getRgba(shadow);
   }

   function toRgb(color: string): [number, number, number] {
      if(!color) {
         return [0, 0, 0];
      }

      let hex = color.trim();

      if(hex.startsWith("#")) {
         hex = hex.substring(1);
      }

      // expand the #rgb shorthand
      if(hex.length == 3) {
         hex = hex[0] + hex[0] + hex[1] + hex[1] + hex[2] + hex[2];
      }

      const num = parseInt(hex, 16);

      if(hex.length != 6 || isNaN(num)) {
         return [0, 0, 0];
      }

      return [(num >> 16) & 0xff, (num >> 8) & 0xff, num & 0xff];
   }
}
