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
import { Tool } from "../../../../../shared/util/tool";

@Component({
    selector: "generic-multi-select-list",
    templateUrl: "generic-multi-select-list.component.html",
    styleUrls: ["./generic-multi-select-list.component.scss"],
    imports: []
})
export class GenericMultiSelectList {
   @Input() labels: string[] = [];
   @Input() values: any[] = [];
   @Input() selected: any[] = [];
   @Input() disabled: boolean = false;
   @Input() dataTruncated: boolean = false;
   @Output() selectedChange: EventEmitter<any[]> = new EventEmitter<any[]>();

   isSelected(value: any): boolean {
      return (this.selected ?? []).some(v => Tool.isEquals(v, value));
   }

   toggle(value: any): void {
      if(this.disabled) {
         return;
      }

      const next = this.isSelected(value)
         ? (this.selected ?? []).filter(v => !Tool.isEquals(v, value))
         : [...(this.selected ?? []), value];

      this.selectedChange.emit(next);
   }
}
