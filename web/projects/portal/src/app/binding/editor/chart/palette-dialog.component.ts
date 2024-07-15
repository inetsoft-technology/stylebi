/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, Input, Output, EventEmitter } from "@angular/core";
import { CategoricalColorModel } from "../../../common/data/visual-frame-model";

@Component({
   selector: "palette-dialog",
   templateUrl: "palette-dialog.component.html",
   styleUrls: ["palette-dialog.component.scss"]
})

export class PaletteDialog {
   @Input() currPalette: CategoricalColorModel;
   @Input() colorPalettes: CategoricalColorModel[];
   @Output() onCommit: EventEmitter<CategoricalColorModel> =
      new EventEmitter<CategoricalColorModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   _selectedIndex: number = -1;
   _reversed: boolean = false;

   get displayPalette(): CategoricalColorModel {
      if(!this.colorPalettes) {
         return null;
      }

      if(this._selectedIndex == -1) {
         this._selectedIndex = this.getPaletteIndex();
      }

      if(this._selectedIndex == -1) {
         return null;
      }

      if(!this._reversed) {
         return this.colorPalettes[this._selectedIndex];
      }

      let cateModel: CategoricalColorModel = new CategoricalColorModel();
      let colors: string[] = this.colorPalettes[this._selectedIndex].colors.slice();
      cateModel.colors = colors.reverse();

      return cateModel;
   }

   private getPaletteIndex() {
      if(!this.colorPalettes || !this.currPalette) {
         return 0;
      }

      let matchcnt = 0;
      let idx = 0;

      for(let i = 0; i < this.colorPalettes.length; i++) {
         let pt: CategoricalColorModel = this.colorPalettes[i];
         let cnt = 0;

         for(let j = 0; j < pt.colors.length; j++) {
            if(pt.colors[j] != this.currPalette.colors[j]) {
               break;
            }

            cnt++;
         }

         if(cnt == pt.colors.length && cnt > matchcnt) {
            idx = i;
            matchcnt = cnt;
            this._reversed = false;
         }
         else {
            for(let j = 0; j < pt.colors.length; j++) {
               if(pt.colors[pt.colors.length - 1 - j] != this.currPalette.colors[j]) {
                  break;
               }

               cnt++;
            }

            if(cnt == pt.colors.length && cnt > matchcnt) {
               idx = i;
               matchcnt = cnt;
               this._reversed = true;
            }
         }
      }

      return idx;
   }

   cancelChanges(event: MouseEvent): void {
      this.onCancel.emit("cancel");
   }

   saveChanges(event: MouseEvent): void {
      let npalette: CategoricalColorModel = new CategoricalColorModel();
      npalette.colors = this.currPalette.colors;
      npalette.colors.splice(0, this.displayPalette.colors.length);
      npalette.colors = this.displayPalette.colors.concat(npalette.colors);
      this.onCommit.emit(npalette);
   }

   openColorOption(event: MouseEvent) {
      event.stopPropagation();
   }
}
