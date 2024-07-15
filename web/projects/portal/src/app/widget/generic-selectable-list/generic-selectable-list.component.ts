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
import { Component, Input, Output, EventEmitter } from "@angular/core";

@Component({
   selector: "generic-selectable-list",
   templateUrl: "generic-selectable-list.component.html",
   styleUrls: ["./generic-selectable-list.component.scss"]
})
export class GenericSelectableList {
   @Input() elementToString: (x: any) => string;
   @Input() itemList: any[];
   @Input() selectedIndex: number = -1;
   @Input() disabled: boolean = false;
   @Output() selectedIndexChange: EventEmitter<number> = new EventEmitter<number>();

   select(index: number): void {
      this.selectedIndex = index;
      this.selectedIndexChange.emit(index);
   }

   get hasSelectedElement(): boolean {
      return this.selectedIndex != -1;
   }
}
