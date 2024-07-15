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
   ChangeDetectorRef,
   Component,
   EventEmitter,
   HostBinding,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import { FormatInfoModel } from "../../common/data/format-info-model";
import { VSObjectFormatInfoModel } from "../../common/data/vs-object-format-info-model";
import { ColorDropdown } from "../../widget/color-picker/color-dropdown.component";
import { ComboMode } from "../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { FontService } from "../../widget/services/font.service";

@Component({
   selector: "formats-pane",
   templateUrl: "formats-pane.component.html",
   styleUrls: ["formats-pane.component.scss"],
})
export class FormatsPane implements OnInit, OnChanges {
   @HostBinding("hidden")
   @Input() inactive: boolean;
   @Input() composerPane: boolean;
   @Input() focusedAssemblies: any[] = [];
   @Input() variableValues: string[] = [];
   @Input() vsId: string;
   @Input() formatPaneDisabled: boolean = false;
   @Output() changeFormat: EventEmitter<FormatInfoModel> = new EventEmitter<FormatInfoModel>();
   fonts: string[];
   staticString: string = ColorDropdown.STATIC;
   _format: FormatInfoModel;
   vsObjectFormat: VSObjectFormatInfoModel = <VSObjectFormatInfoModel> {};
   alphaInvalid: boolean = false;

   @Input()
   set format(format: FormatInfoModel | VSObjectFormatInfoModel) {
      this._format = format;

      if(format && (<any>format).type &&
         (<any>format).type.indexOf("VSObjectFormatInfoModel") != -1)
      {
         this.vsObjectFormat = <VSObjectFormatInfoModel> format;
      }
   }

   constructor(private changeDetectorRef: ChangeDetectorRef, private fontService: FontService) {
   }

   ngOnInit(): void {
      this.fontService.getAllFonts().subscribe(
         (fonts: string[]) => this.fonts = fonts
      );
   }

   ngOnChanges(changes: SimpleChanges) {
      if(this.inactive) {
         this.changeDetectorRef.detach();
      }
      else {
         this.changeDetectorRef.reattach();
      }
   }

   getFont(): string {
      if(this._format == null || this._format.font == null) {
         return "Default 11";
      }

      let fontStr: string = "";
      fontStr += this._format.font.fontFamily == null ? "Default" :
         this._format.font.fontFamily;
      fontStr += this._format.font.fontSize == null ? " 11" :
         (" " + this._format.font.fontSize);

      return fontStr;
   }

   getAlignment(): string {
      if(this._format == null || this._format.align == null) {
         return "Auto";
      }

      let alignstr = "";

      if(this._format.align.halign != null) {
         alignstr += this._format.align.halign;
      }

      if(this._format.align.valign != null) {
         alignstr += " " + this._format.align.valign;
      }

      return alignstr.length == 0 ? "Auto" : alignstr;
   }

   getFormat(): string {
      if(this._format == null || this._format.format == null) {
         return "None";
      }
      else if(this._format.format == "MessageFormat") {
         return "Text";
      }
      else if(this._format.format == "DecimalFormat") {
         return "Number";
      }
      else if(this._format.format == "DateFormat") {
         return "Date";
      }
      else if(this._format.format == "CurrencyFormat") {
         return "Currency";
      }
      else if(this._format.format == "PercentFormat") {
         return "Percent";
      }

      return "None";
   }

   changeColor(color: string, colorType: string) {
      if("color" === colorType) {
         this._format.color = color;
      }
      else {
         this._format.backgroundColor = color;
      }

      this.changeFormat.emit(this._format);
   }

   updateFormat(change: boolean = true): void {
      if(change && this.composerPane) {
         this.changeFormat.emit(this._format);
      }

      if(!this.composerPane) {
         this.changeFormat.emit(this._format);
      }
   }

   fixColor(type: ComboMode, pane: string): void {
      if(type == ComboMode.VALUE) {
         if("color" == pane) {
            this.vsObjectFormat.colorType = this.staticString;
         }
         else {
            this.vsObjectFormat.backgroundColorType = this.staticString;
         }

         this.updateFormat();
      }
      else {
         if("color" == pane) {
            this.vsObjectFormat.colorType = "";
         }
         else {
            this.vsObjectFormat.backgroundColorType = "";
         }
      }
   }

   changeAlphaWarning(event) {
      this.alphaInvalid = event;
   }

   onFormatChanged(model: FormatInfoModel) {
      this.changeFormat.emit(model);
   }
}
