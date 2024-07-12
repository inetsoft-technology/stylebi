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
import { FormatInfoModel } from "../data/format-info-model";

const ALIGNMENT_LABEL_MAP: Map<string, string> = new Map([
  ["Left", "_#(js:Left)"],
  ["Center", "_#(js:Center)"],
  ["Right", "_#(js:Right)"],
  ["Top", "_#(js:Top)"],
  ["Middle", "_#(js:Middle)"],
  ["Bottom", "_#(js:Bottom)"]
]);
/**
 * Common format utility methods
 */
export namespace FormatTool {
   export function getFontString(format: FormatInfoModel): string {
      if(format == null || format.font == null) {
         return "Default 11";
      }

      const font = format.font;
      let fontStr: string = "";
      fontStr += font.fontFamily == null ? "Default" : font.fontFamily;
      fontStr += font.fontSize == null ? "-11" : ("-" + font.fontSize);

      if(font.fontWeight && font.fontWeight != "normal") {
         fontStr += "-" + font.fontWeight;
      }

      if(font.fontStyle && font.fontStyle != "normal") {
         fontStr += "-" + font.fontStyle;
      }

      if(font.fontUnderline && font.fontUnderline.indexOf("underline") >= 0) {
         fontStr += "-underline";
      }

      if(font.fontStrikethrough && font.fontStrikethrough.indexOf("strikethrough") >= 0) {
         fontStr += "-strikethrough";
      }

      return fontStr;
   }

   export function getAlignmentString(format: FormatInfoModel, henabled: boolean = true,
                                      venabled: boolean = true): string
   {
      if(format == null || format.align == null) {
         return "_#(js:Auto)";
      }

      let alignstr = "";

      if(format.align.halign != null && henabled) {
         alignstr += FormatTool.getAlignmentLabel(format.align.halign);
      }

      if(format.align.valign != null && venabled) {
         alignstr += " " + FormatTool.getAlignmentLabel(format.align.valign);
      }

      return alignstr.length == 0 ? "_#(js:Auto)" : alignstr;
   }

   export function getFormatString(format: FormatInfoModel): string {
      if(format == null || format.format == null) {
         return "_#(js:None)";
      }
      else if(format.format == "MessageFormat") {
         return "_#(js:Text)";
      }
      else if(format.format == "DecimalFormat") {
         return "_#(js:Number)";
      }
      else if(format.format == "DateFormat") {
         return "_#(js:Date)";
      }
      else if(format.format == "CurrencyFormat") {
         return "_#(js:Currency)";
      }
      else if(format.format == "PercentFormat") {
         return "_#(js:Percent)";
      }
      else if(format.format == "DurationFormat") {
         return "_#(js:Duration)";
      }

      return "_#(js:None)";
   }

   export function getAlignmentLabel(align: string): string {
      let alignLabel = ALIGNMENT_LABEL_MAP.get(align);

      return !!alignLabel ? alignLabel : "_#(js:Auto)";
   }
}
