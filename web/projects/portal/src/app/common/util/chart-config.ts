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
import { StyleConstants } from "./style-constants";
import { LineStyle } from "../data/line-style";
import { NetTool } from "./net-tool";

/**
 * Utility class that provides library methods rendering charts of
 * different types.
 *
 * @version 12.3
 * @author InetSoft Technology Corp
 */

 export class ChartConfig {
   /**
    * Line styles for line model.
    */
   static get M_LINE_STYLES(): number[] {
      return [
               StyleConstants.THIN_LINE, StyleConstants.DOT_LINE,
               StyleConstants.DASH_LINE, StyleConstants.MEDIUM_DASH,
               StyleConstants.LARGE_DASH
             ];
   }

   /**
    * Texture styles.
    */
   static get TEXTURE_STYLES(): number[] {
      return [
               StyleConstants.PATTERN_NONE, StyleConstants.PATTERN_0,
               StyleConstants.PATTERN_1, StyleConstants.PATTERN_2,
               StyleConstants.PATTERN_3, StyleConstants.PATTERN_4,
               StyleConstants.PATTERN_5, StyleConstants.PATTERN_6,
               StyleConstants.PATTERN_7, StyleConstants.PATTERN_8,
               StyleConstants.PATTERN_9, StyleConstants.PATTERN_10,
               StyleConstants.PATTERN_11, StyleConstants.PATTERN_12,
               StyleConstants.PATTERN_13, StyleConstants.PATTERN_14,
               StyleConstants.PATTERN_15, StyleConstants.PATTERN_16,
               StyleConstants.PATTERN_17, StyleConstants.PATTERN_18,
               StyleConstants.PATTERN_19
            ];
   }

   /**
    * Shape styles.
    */
   static get SHAPE_STYLES(): string[] {
      return [
               StyleConstants.CIRCLE + "", StyleConstants.TRIANGLE + "",
               StyleConstants.SQUARE + "", StyleConstants.CROSS + "",
               StyleConstants.STAR + "", StyleConstants.DIAMOND + "",
               StyleConstants.X + "", StyleConstants.FILLED_CIRCLE + "",
               StyleConstants.FILLED_TRIANGLE + "", StyleConstants.FILLED_SQUARE + "",
               StyleConstants.FILLED_DIAMOND + "", StyleConstants.V_ANGLE + "",
               StyleConstants.RIGHT_ANGLE + "", StyleConstants.LT_ANGLE + "",
               StyleConstants.V_LINE + "", StyleConstants.H_LINE + ""
            ];
   }

   /**
    * Image shapes.
    */
   static get IMAGE_SHAPES(): string[] {
      return [
               "100ArrowDown.svg", "101ArrowUp.svg", "102Check.svg",
               "103Cancel.svg", "104Exclamation.svg", "105Flag.svg",
               "106Light.svg", "107Star.svg", "108No.svg",
               "109Man.svg", "110Woman.svg", "111FaceHappy.svg",
               "112FaceSad.svg", "113Face.svg",
               "114ArrowUperRight.svg", "115ArrowLowerRight.svg"
            ];
   }

   static getShapeSource(shape: string, isBiggerImage?: boolean) {
      if(!shape) {
         return null;
      }

      if(ChartConfig.IMAGE_SHAPES.indexOf(shape) >= 0) {
         return "assets/shapes/" + shape;
      }

      return "../api/composer/imageShape/" + shape + "?" + NetTool.xsrfToken();
   }

   /**
    * Get a name of a line style from a line style constant. For example,
    * getLineStyleName(StyleConstants.THIN_LINE) returns the string
    * "THIN_LINE"
    * @param val line style constant
    */
   static getLineStyleName(val: number): string {
      if(val == StyleConstants.NONE) {
         return LineStyle.NONE;
      }
      else if(val == StyleConstants.THIN_THIN_LINE) {
         return LineStyle.THIN_THIN_LINE;
      }
      else if(val == StyleConstants.ULTRA_THIN_LINE) {
         return LineStyle.ULTRA_THIN_LINE;
      }
      else if(val == StyleConstants.THIN_LINE) {
         return LineStyle.THIN_LINE;
      }
      else if(val == StyleConstants.MEDIUM_LINE) {
         return LineStyle.MEDIUM_LINE;
      }
      else if(val == StyleConstants.THICK_LINE) {
         return LineStyle.THICK_LINE;
      }
      else if(val == StyleConstants.DOUBLE_LINE) {
         return LineStyle.DOUBLE_LINE;
      }
      else if(val == StyleConstants.DOT_LINE) {
         return LineStyle.DOT_LINE;
      }
      else if(val == StyleConstants.DASH_LINE) {
         return LineStyle.DASH_LINE;
      }
      else if(val == StyleConstants.MEDIUM_DASH) {
         return LineStyle.MEDIUM_DASH;
      }
      else if(val == StyleConstants.LARGE_DASH) {
         return LineStyle.LARGE_DASH;
      }
      else if(val < 0) {
         return "Default";
      }

      return val.toString();
   }
}
