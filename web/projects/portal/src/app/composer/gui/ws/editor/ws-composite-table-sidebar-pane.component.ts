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
import { HttpParams } from "@angular/common/http";
import {
   ChangeDetectionStrategy,
   ChangeDetectorRef,
   Component,
   Input,
   OnChanges,
   OnDestroy,
   SimpleChanges
} from "@angular/core";
import { Subscription } from "rxjs";
import { DragEvent } from "../../../../common/data/drag-event";
import { Tool } from "../../../../../../../shared/util/tool";
import { DragService } from "../../../../widget/services/drag.service";
import { ModelService } from "../../../../widget/services/model.service";
import { AbstractJoinTableAssembly } from "../../../data/ws/abstract-join-table-assembly";
import { AbstractTableAssembly } from "../../../data/ws/abstract-table-assembly";
import { CompositeTableAssembly } from "../../../data/ws/composite-table-assembly";
import { ConcatenatedTableAssembly } from "../../../data/ws/concatenated-table-assembly";
import { Worksheet } from "../../../data/ws/worksheet";

export const DRAG_TABLE_ID = "Add table";
const CONCAT_COMPATIBLE_INSERTION_TABLE_URI = "../api/composer/worksheet/concat/compatible-insertion-tables/";
const JOIN_COMPATIBLE_INSERTION_TABLE_URI = "../api/composer/worksheet/join/compatible-insertion-tables/";

@Component({
   selector: "ws-composite-table-sidebar-pane",
   templateUrl: "ws-composite-table-sidebar-pane.component.html",
   styleUrls: ["schema/sidebar-pane/subtable-list.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class WSCompositeTableSidebarPane implements OnChanges, OnDestroy {
   @Input() worksheet: Worksheet;
   @Input() compositeTable: CompositeTableAssembly;
   compatibleTables: AbstractTableAssembly[];
   private modelSub: Subscription;

   constructor(private modelService: ModelService,
               private cd: ChangeDetectorRef,
               private dragService: DragService)
   {
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.hasOwnProperty("compositeTable")) {
         this.closeSubscription();
         this.compatibleTables = null;

         if(this.worksheet) {
            if(this.isJoinTable()) {
               this.updateCompatibleTables("joinTable", JOIN_COMPATIBLE_INSERTION_TABLE_URI);
            }
            else if(this.isConcatTable()) {
               this.updateCompatibleTables("concatTable", CONCAT_COMPATIBLE_INSERTION_TABLE_URI);
            }
         }
      }
   }

   ngOnDestroy(): void {
      this.closeSubscription();
   }

   dragCompatibleTable(event: DragEvent, table: string): void {
      Tool.setTransferData(event.dataTransfer, {});
      this.dragService.put(DRAG_TABLE_ID, table);
   }

   private getCompatibleTables(compatibleTables: string[]): AbstractTableAssembly[] {
      return this.worksheet.tables.filter((t) => compatibleTables.indexOf(t.name) >= 0);
   }

   private closeSubscription(): void {
      if(this.modelSub && !this.modelSub.closed) {
         this.modelSub.unsubscribe();
      }
   }

   private updateCompatibleTables(tableNameKey: string, uriPrefix: string): void {
      const params = new HttpParams().set(tableNameKey, this.compositeTable.name);
      const uri = uriPrefix + Tool.byteEncode(this.worksheet.runtimeId);

      this.modelSub = this.modelService.getModel(uri, params)
         .subscribe((compatibleTables: string[]) => {
            this.compatibleTables = this.getCompatibleTables(compatibleTables);
            this.cd.markForCheck();
         });
   }

   private isJoinTable(): boolean {
      return this.worksheet.selectedCompositeTable instanceof AbstractJoinTableAssembly;
   }

   private isConcatTable(): boolean {
      return this.worksheet.selectedCompositeTable instanceof ConcatenatedTableAssembly;
   }

   isMergeJoinTable(): boolean {
      return this.worksheet.selectedCompositeTable != null &&
             this.worksheet.selectedCompositeTable.tableClassType === "MergeJoinTableAssembly";
   }
}
