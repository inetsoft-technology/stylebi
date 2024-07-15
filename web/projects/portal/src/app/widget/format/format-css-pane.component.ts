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
   Component,
   Input,
   Output,
   EventEmitter,
   OnChanges,
   SimpleChanges
} from "@angular/core";
import {
   FeatureFlagsService,
   FeatureFlagValue
} from "../../../../../shared/feature-flags/feature-flags.service";

@Component({
   selector: "format-css-pane",
   templateUrl: "format-css-pane.component.html",
   styleUrls: ["format-css-pane.component.scss"]
})
export class FormatCSSPane implements OnChanges {
   @Input() cssID: string;
   @Input() cssClass: string;
   @Input() cssIDs: string[] = [];
   @Input() cssClasses: string[] = [];
   @Input() cssType: string;
   @Output() cssIDChange: EventEmitter<string> = new EventEmitter<string>();
   @Output() cssClassChange: EventEmitter<string> = new EventEmitter<string>();
   @Output() onApply: EventEmitter<boolean> = new EventEmitter<boolean>();
   selectedCSSIDIndex: number = -1;
   selectedCSSClassIndexes: number[] = [];
   selectedCSSClassIndex: number = -1;
   readonly FeatureFlagValue = FeatureFlagValue;

   constructor(private featureFlagsService: FeatureFlagsService) {
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(!!this.cssIDs && this.cssIDs.length > 0) {
         this.selectedCSSIDIndex = this.cssIDs.indexOf(this.cssID);
      }

      if(!!this.cssClasses && this.cssClasses.length > 0) {
         let selectedCSSClasses = !!this.cssClass ? this.cssClass.split(",") : [];
         this.selectedCSSClassIndexes = selectedCSSClasses.map((val) => this.cssClasses.indexOf(val));
      }
   }

   selectCSSID(choice: string, index: number): void {
      this.selectedCSSIDIndex = index;
      this.cssID = choice;
      this.cssIDChange.emit(choice);
   }

   selectCSSClass(choice: string, index: number): void {
      if(index == -1) {
         this.selectedCSSClassIndexes = [];
      }
      else {
         if(this.selectedCSSClassIndexes.indexOf(index) >= 0) {
            this.selectedCSSClassIndexes.splice(this.selectedCSSClassIndexes.indexOf(index), 1);
         }
         else {
            this.selectedCSSClassIndexes.push(index);
         }
      }

      this.cssClass = this.selectedCSSClassIndexes.filter((i) => i >= 0)
         .map((i) => this.cssClasses[i]).join(",");
      this.cssClassChange.emit(this.cssClass);
   }
}
