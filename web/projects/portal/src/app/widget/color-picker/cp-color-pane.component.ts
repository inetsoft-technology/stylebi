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
import { Component, EventEmitter, Input, Output, OnInit, TemplateRef,
         ViewChild } from "@angular/core";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { ColorPalette, RecentColorPalette } from "./color-classes";
import { DefaultPalette } from "./default-palette";
import { RecentColorService } from "./recent-color.service";
import { getColorHex } from "./color-utils";

@Component({
   selector: "cp-color-pane",
   templateUrl: "cp-color-pane.component.html",
   styleUrls: ["cp-color-pane.component.scss"]
})
export class ColorPane implements OnInit {
   @Input() color: string = "#000000";
   @Input() palette: ColorPalette = DefaultPalette.chart;
   @Input() recent: RecentColorPalette = null;
   @Input() showRecentColors: boolean = true;
   @Input() clearEnabled: boolean = false;
   @Output() colorChanged: EventEmitter<string> = new EventEmitter<string>();
   // clear and use default (default color emitted)
   @Output() colorCleared: EventEmitter<string> = new EventEmitter<string>();
   @Output() dialogOpened: EventEmitter<boolean> = new EventEmitter<boolean>();
   @ViewChild("colorEditorDialog") colorEditorDialog: TemplateRef<any>;
   colorValue: string;
   hidePane: boolean = false;
   allowNullColors: boolean = true;

   constructor(private modalService: NgbModal,
               private recentColorService: RecentColorService)
   {
   }

   ngOnInit(): void {
      if(this.color) {
         this.colorValue = getColorHex(this.color);
      }

      if(this.palette) {
         this.allowNullColors = this.palette.findIndex((row) => row.indexOf("") >= 0) >= 0;
      }
   }

   setColorValue(value: string) {
      if(!value) {
         setTimeout(() => this.selectColor(value), 0);
      }
      else {
         this.colorValue = getColorHex("#" + value);

         if(/^[0-9a-fA-F]{6}$/.test(value)) {
            this.color = "#" + value;
            this.recentColorService.colorSelected(this.color);
            setTimeout(() => this.colorChanged.emit(this.color), 0);
         }
      }
   }

   get recentColors(): RecentColorPalette {
      let palette: RecentColorPalette = this.recent ||
         this.recentColorService.recentColorPalette;

      if(!palette) {
         palette = ["", "", "", "", "", "", "", ""];
      }

      return palette;
   }

   selectColor(value: string) {
      if((!!value && value != "") || this.allowNullColors) {
         this.color = value;
         this.colorValue = value ? getColorHex(value) : null;
         this.colorChanged.emit(value);
         this.recentColorService.colorSelected(value);
      }
   }

   clearColor() {
      this.colorCleared.emit(this.palette[0][0]);
   }

   openColorEditor(): void {
      this.hidePane = true;
      this.dialogOpened.emit(true);

      let modalOptions: NgbModalOptions = {
         windowClass: "select-color-dialog"
      };
      this.modalService.open(this.colorEditorDialog, modalOptions).result.then(
         (result: string) => {
            this.color = result;
            this.colorChanged.emit(this.color);
            this.recentColorService.colorSelected(this.color);
            this.hidePane = false;
            this.dialogOpened.emit(false);
         },
         (reason: any) => {
            this.hidePane = false;
            this.dialogOpened.emit(false);
         }
      );
   }
}
