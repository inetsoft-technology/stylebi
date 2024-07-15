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
   ChangeDetectionStrategy,
   Component,
   EventEmitter,
   Input,
   Output
} from "@angular/core";

import { AbstractTableAssembly } from "../../../../data/ws/abstract-table-assembly";

@Component({
   selector: "merge-join-subtable",
   templateUrl: "merge-join-subtable.component.html",
   styleUrls: ["merge-join-subtable.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})

export class MergeJoinSubtableComponent {
   @Input() subtable: AbstractTableAssembly;
   @Input() selected: boolean = false;
   @Output() onSelectTable: EventEmitter<boolean> = new EventEmitter<boolean>();
   @Output() onFocusCompositeTable: EventEmitter<null> = new EventEmitter<null>();

   selectTable(event: MouseEvent) {
      if(event.ctrlKey) {
         this.onSelectTable.emit(!this.selected);
      }
      else {
         this.onSelectTable.emit(true);
      }
   }

   focusCompositeTable(event: MouseEvent) {
      event.stopPropagation();
      this.onFocusCompositeTable.emit();
   }
}