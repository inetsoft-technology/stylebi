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
import {
   Component,
   Input,
   Output,
   EventEmitter,
   ChangeDetectionStrategy
} from "@angular/core";

export interface NumericRange {
   top: number;
   bottom: number;
   key: string;
   label: string;
}

@Component({
   selector: "value-range-selectable-list",
   templateUrl: "value-range-selectable-list.component.html",
   styleUrls: ["value-range-selectable-list.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class ValueRangeSelectableList {
   @Input() rangeList: NumericRange[];
   @Input() selectedIndex: number;
   @Output() selectedIndexChange: EventEmitter<number> = new EventEmitter<number>();
   @Output() labelChange: EventEmitter<[number, string]> = new EventEmitter<[number, string]>();

   select(index: number) {
      this.selectedIndex = index;
      this.selectedIndexChange.emit(index);
   }

   editLabel(index: number, label: string) {
      this.labelChange.emit([index, label]);
   }

   focusInput(input: HTMLInputElement) {
      input.select();
   }
}
