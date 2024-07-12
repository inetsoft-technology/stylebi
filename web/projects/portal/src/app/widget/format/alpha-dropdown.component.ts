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
import { Component, Input, Output, EventEmitter, ChangeDetectorRef } from "@angular/core";
import { DebounceService } from "../services/debounce.service";

@Component({
   selector: "alpha-dropdown",
   templateUrl: "alpha-dropdown.component.html",
   styleUrls: ["./alpha-dropdown.component.scss"]
})
export class AlphaDropdown {
   @Input() alpha: number = 100;
   @Input() disabled: boolean = false;
   @Input() defaultAlpha: number = null;
   @Output() alphaChange: EventEmitter<number> = new EventEmitter<number>();
   @Output() alphaInvalid: EventEmitter<boolean> = new EventEmitter<boolean>();
   alphaOptions: number[] = [0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100];

   constructor(private changeRef: ChangeDetectorRef,
               private debounceService: DebounceService) {
   }

   changeAlpha(alpha: number) {
      this.debounceService.debounce("alpha-change", () => this.changeAlpha0(alpha), 500, []);
   }

   changeAlpha0(alpha: number) {
      const oalpha = alpha;
      alpha = alpha == null || alpha + "" == "" ? null : Math.min(Math.max(0, alpha), 100);
      this.alpha = alpha;

      // don't force empty alpha to become 0
      if(alpha != null || this.defaultAlpha != null) {
         this.alphaChange.emit(alpha);
      }

      if(alpha != oalpha) {
         this.alpha = null;
         this.changeRef.detectChanges();
         this.alpha = alpha;
      }

      if(this.alpha == null) {
         this.alphaInvalid.emit(this.defaultAlpha == null);
      }
      else if(!(this.alpha >= 0 && this.alpha <= 100)) {
         this.alphaInvalid.emit(true);
      }
      else {
         this.alphaInvalid.emit(false);
      }
   }
}
