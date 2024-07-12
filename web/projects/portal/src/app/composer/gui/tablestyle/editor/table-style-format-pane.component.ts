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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { AlignmentInfo, FontInfo } from "../../../../common/data/format-info-model";
import { TableStyleUtil } from "../../../../common/util/table-style-util";
import { BodyRegionFormat } from "../../../data/tablestyle/body-region-format";
import { FontService } from "../../../../widget/services/font.service";
import { BorderFormat } from "../../../data/tablestyle/border-format";
import { FormatTool } from "../../../../common/util/format-tool";

@Component({
   selector: "table-style-format-pane",
   templateUrl: "table-style-format-pane.component.html",
   styleUrls: ["table-style-format-pane.component.scss"]
})
export class TableStyleFormatPaneComponent implements OnInit {
   @Input() selectedRegion: string;
   @Input() selectedRegionLabel: string;
   @Input() isGroupTotal: boolean;
   @Input() regionFormatInfo: BodyRegionFormat;
   @Input() borderFormatInfo: BorderFormat;
   @Output() onChangeFormat = new EventEmitter();
   fonts: string[];
   alignments: Array<{ label: string, value: number }> = [];

   constructor(private fontService: FontService) {
   }

   ngOnInit() {
      this.alignments = TableStyleUtil.ALIGBNEBNT_STYLES;

      this.fontService.getAllFonts().subscribe(
         (fonts: string[]) => this.fonts = fonts
      );
   }

   changeRegionFormat(style: any, styleType: string) {
      switch(styleType) {
         case TableStyleUtil.BACKGROUND:
            this.regionFormatInfo.background = style;
            break;
         case TableStyleUtil.FOREGROUND:
            this.regionFormatInfo.foreground = style;
            break;
         case TableStyleUtil.ROW_BORDER_COLOR:
            this.regionFormatInfo.rowBorderColor = style;
            break;
         case TableStyleUtil.ROW_BORDER:
            this.regionFormatInfo.rowBorder = style;
            break;
         case TableStyleUtil.COL_BORDER_COLOR:
            this.regionFormatInfo.colBorderColor = style;
            break;
         case TableStyleUtil.COL_BORDER:
            this.regionFormatInfo.colBorder = style;
            break;
         case TableStyleUtil.COLOR:
            this.borderFormatInfo.borderColor = style;
            break;
         case TableStyleUtil.BORDER:
            this.borderFormatInfo.border = parseInt(style, 10);
            break;
      }

      this.onChangeFormat.emit();
   }

   getFontText(font: FontInfo): string {
      if(font == null) {
         return "Default 11";
      }

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

   getAlignmentLabel(align: AlignmentInfo): string {
      if(align == null) {
         return "_#(js:Auto)";
      }

      let alignstr = "";

      if(align.halign != null) {
         alignstr += FormatTool.getAlignmentLabel(align.halign);
      }

      if(align.valign != null) {
         alignstr += " " + FormatTool.getAlignmentLabel(align.valign);
      }

      return alignstr.length == 0 ? "_#(js:Auto)" : alignstr;
   }

   protected readonly TableStyleUtil = TableStyleUtil;
}
