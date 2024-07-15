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
   Input,
   OnChanges,
   SimpleChanges,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AbstractTableAssembly } from "../../../../../data/ws/abstract-table-assembly";
import { CompositeTableAssembly } from "../../../../../data/ws/composite-table-assembly";
import { Worksheet } from "../../../../../data/ws/worksheet";
import { ReorderSubtablesDialogComponent } from "../../../../../dialog/ws/reorder-subtables-dialog.component";
import { ReorderSubtablesEvent } from "../../../socket/reorder-subtables-event";

const REORDER_SUBTABLES_URI = "/events/composer/worksheet/reorder-subtables";

@Component({
   selector: "subtable-list",
   templateUrl: "subtable-list.component.html",
   styleUrls: ["subtable-list.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class SubtableListComponent implements OnChanges {
   @Input() compositeTable: CompositeTableAssembly;
   @Input() worksheet: Worksheet;
   @ViewChild("reorderSubtablesDialog") reorderSubtablesDialog:
      TemplateRef<ReorderSubtablesDialogComponent>;
   subtables: AbstractTableAssembly[];

   constructor(private modalService: NgbModal) {
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(this.compositeTable && this.worksheet) {
         this.subtables = this.compositeTable.subtables
            .map((tableName) => {
               const index = this.worksheet.tables.findIndex((t) => t.name === tableName);

               if(index >= 0) {
                  return this.worksheet.tables[index];
               }

               return null;
            });
      }
   }

   reorderTables(): void {
      this.modalService.open(this.reorderSubtablesDialog, {backdrop: "static", size: "lg"})
         .result.then(
         (subtables: string[]) => {
            const event = new ReorderSubtablesEvent();
            event.setParentTable(this.compositeTable.name);
            event.setSubtables(subtables);
            this.worksheet.socketConnection.sendEvent(REORDER_SUBTABLES_URI, event);
         }, () => {
         });
   }
}
