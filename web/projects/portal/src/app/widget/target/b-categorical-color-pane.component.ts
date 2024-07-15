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

import { HttpClient } from "@angular/common/http";
import {
   Component,
   EventEmitter,
   Input,
   OnInit,
   Output,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { CategoricalColorModel } from "../../common/data/visual-frame-model";

@Component({
   selector: "b-categorical-color-pane",
   templateUrl: "b-categorical-color-pane.component.html",
   styleUrls: ["b-categorical-color-pane.component.scss"]
})
export class BCategoricalColorPane implements OnInit {
   @ViewChild("paletteDialog") paletteDialog: TemplateRef<any>;
   @Input() colorModel: CategoricalColorModel;
   @Output() colorListChange: EventEmitter<CategoricalColorModel> =
   new EventEmitter<CategoricalColorModel>();
   COLORS_IN_VIEW = 6;
   currentViewIndex = 0;
   currentViewIndices: number[] = this.newView();
   colorPalettes: CategoricalColorModel[] = [];

   constructor(private modalService: NgbModal, private http: HttpClient) {
   }

   ngOnInit() {
   }

   get viewAtBeginning(): boolean {
      return this.currentViewIndex == 0;
   }

   get viewAtEnd(): boolean {
      return this.currentViewIndex + this.COLORS_IN_VIEW >= this.colorModel.colors.length;
   }

   shift(n: number): void {
      this.currentViewIndex += n;
      this.currentViewIndices = this.newView();
   }

   newView(): number[] {
      return Array.from(new Array(this.COLORS_IN_VIEW), (x, i) => i + this.currentViewIndex);
   }

   reset(): void {
      if(!this.colorModel || !this.colorModel.colors) {
         return;
      }

      for(let i = 0; i < this.colorModel.colors.length; i++) {
         this.colorModel.colors[i] = this.colorModel.cssColors[i] ?
            this.colorModel.cssColors[i] : this.colorModel.defaultColors[i];
      }
   }

   apply(): void {
      // TODO
      this.colorListChange.emit(this.colorModel);
   }

   openPalette() {
      this.modalService.open(this.paletteDialog, {size: "sm"}).result.then(
         (result: CategoricalColorModel) => {
            this.colorModel.colors = result.colors;
         },
         (reject) => {});
   }
}
