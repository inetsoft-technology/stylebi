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
   HostListener,
   Input,
   Output,
} from "@angular/core";
import { ColumnRef } from "../../../../../binding/data/column-ref";
import { AbstractTableAssembly, } from "../../../../data/ws/abstract-table-assembly";
import { AssemblyActionGroup } from "../../../../../common/action/assembly-action-group";

@Component({
   selector: "concatenated-table-thumbnail",
   templateUrl: "concatenated-table-thumbnail.component.html",
   changeDetection: ChangeDetectionStrategy.OnPush,
   styleUrls: ["concatenated-table-thumbnail.component.scss"]
})
export class ConcatenatedTableThumbnailComponent {
   @Input() table: AbstractTableAssembly;
   @Input() subtableProblemColumns: Set<number>;
   @Input() colWidth: number;
   @Input() maxBoundNumCols: number;
   @Input() actions: AssemblyActionGroup[];
   @Output() onSelectTable: EventEmitter<AbstractTableAssembly> =
      new EventEmitter<AbstractTableAssembly>();

   @HostListener("mousedown")
   selectTable() {
      this.onSelectTable.emit(this.table);
   }

   tableHasCompositionError(): boolean {
      return this.subtableProblemColumns.size > 0;
   }

   columnHasCompositionError(columnIndex: number): boolean {
      return this.subtableProblemColumns.has(columnIndex);
   }

   getColumn(index: number): ColumnRef {
      return this.table.getPublicColumnSelection()[index];
   }

   getColumnHeader(index: number): string {
      const column = this.getColumn(index);
      return column.alias ? column.alias : column.dataRefModel.attribute;
   }
}
