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
import { AfterViewInit, Component, ElementRef, Input } from "@angular/core";

declare const window;

@Component({
   selector: "w-elided-cell",
   templateUrl: "./elided-cell.component.html",
   styleUrls: ["./elided-cell.component.scss"]
})
export class ElidedCellComponent implements AfterViewInit {
   content = "";
   width = "auto";
   private _text: string;
   private initialized = false;

   constructor(private element: ElementRef) {
   }

   @Input()
   set text(value: string) {
      this._text = value;

      if(this.initialized) {
         this.content = value;
      }
   }

   get text(): string {
      return this._text;
   }

   ngAfterViewInit(): void {
      this.initialized = true;
      setTimeout(() =>  {
         const td = this.element.nativeElement.parentElement;
         const style = window.getComputedStyle(td);
         let width = td.clientWidth;
         let padding = style.paddingLeft;

         if(padding) {
            width -= parseFloat(padding);
         }

         padding = style.paddingRight;

         if(padding) {
            width -= parseFloat(padding);
         }

         this.width = `${width}px`;
         this.content = this.text;
      }, 0);
   }
}
