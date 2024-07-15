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
   Component, Input, Output, EventEmitter, OnInit, ViewChild,
   AfterViewInit, SimpleChanges, OnChanges, ChangeDetectorRef
} from "@angular/core";
import { FontInfo } from "../../common/data/format-info-model";
import { FontService } from "../services/font.service";
import { DebounceService } from "../services/debounce.service";

let scrollPos: any;

@Component({
   selector: "font-pane",
   templateUrl: "font-pane.component.html",
   styleUrls: ["font-pane.component.scss"]
})
export class FontPane implements OnInit, OnChanges {
   @Input() fontModel: FontInfo;
   @Input() isOpen: boolean;
   @Input() fonts: string[] = [];
   @Output() onFontChange: EventEmitter<boolean> = new EventEmitter<boolean>();
   @Output() onApply: EventEmitter<boolean> = new EventEmitter<boolean>();
   @ViewChild("scrollBar") scrollBar: any;
   _font: FontInfo;
   private pending: number = 0;

   get font_family(): string {
      if(this._font == null || this._font.fontFamily == null) {
         return "Default";
      }

      return this._font.fontFamily;
   }

   get font_size(): string {
      if(this._font == null) {
         return "11";
      }

      if(this._font.fontSize == null) {
         this._font.fontSize = "11";
      }

      return Math.max(1, +this._font.fontSize) + "";
   }

   set font_size(val: string) {
      if(this._font != null) {
         let size: string = String(val);

         if(size.length > 0 && !isNaN(+size)) {
            this._font.fontSize = Math.max(1, +size) + "";
         }
      }
   }

   constructor(private fontService: FontService,
               private changeRef: ChangeDetectorRef,
               private debounceService: DebounceService) {
   }

   ngOnInit(): void {
      this._font = this.fontModel;

      if(!this.fonts || this.fonts.length == 0) {
         this.getFonts();
      }
   }

   ngOnChanges(changes: SimpleChanges) {
      if(this.scrollBar) {
         this.scrollBar.nativeElement.scrollTop = scrollPos;
      }

      if(changes.fontModel) {
         // if a user clicks on size up/down arrow repeatedly, each request would
         // trigger an update of the current format. if we populate the format
         // between the new size change and the next response, the size would 'jump'
         // back to previous value. we should wait until the action stops todo
         // update the model. since the model is already up-to-date in this case,
         // it should not cause any out of sync problem
         if(this.pending > 0 && this._font) {
            this.debounceService.debounce("font-pane", () => {
               this._font = this.fontModel;
               this.changeRef.detectChanges();
            }, 1000, []);
         }
         else {
            this._font = this.fontModel;
            this.changeRef.detectChanges();
         }

         this.pending--;
      }
   }

   getFonts(): void {
      this.fonts = [];

      this.fontService.getAllFonts().subscribe((data: string[]) => {
         this.fonts = data;
      });
   }

   get defaultFont(): string {
      return this.fontService.defaultFont;
   }

   changeFontFamily(family: string): void {
      scrollPos = this.scrollBar.nativeElement.scrollTop;
      this._font.fontFamily = family;
      this.fireChangeEvent();
   }

   toggleWeight(): void {
      this._font.fontWeight = this._font.fontWeight == "bold" ? "normal" : "bold";
      this.checkFontFamily();
      this.fireChangeEvent();
   }

   toggleStyle(): void {
      this._font.fontStyle = this._font.fontStyle == "italic" ? "normal" : "italic";
      this.checkFontFamily();
      this.fireChangeEvent();
   }

   toggleUnderline(): void {
      this._font.fontUnderline = this._font.fontUnderline == "underline" ?
         "normal" : "underline";
      this.checkFontFamily();
      this.fireChangeEvent();
   }

   toggleStrikethrough(): void {
      this._font.fontStrikethrough = this._font.fontStrikethrough == "strikethrough" ?
         "normal" : "strikethrough";
      this.checkFontFamily();
      this.fireChangeEvent();
   }

   changeFontSize(): void {
      this.debounceService.debounce("font-pane-size", () => {
         this.checkFontFamily();
         this.fireChangeEvent();
      }, 300, []);
   }

   // make sure the font family is set
   checkFontFamily(): void {
      if(this._font && !this._font.fontFamily) {
         this._font.fontFamily = "Default";
      }
   }

   private fireChangeEvent() {
      for(let key in this._font) {
         if(this._font.hasOwnProperty(key)) {
            this.fontModel[key] = this._font[key];
         }
      }

      this.pending++;
      this.onFontChange.emit(true);
   }
}
