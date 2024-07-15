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
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from "@angular/core";
import { DragEvent } from "../../../../../common/data/drag-event";
import { Tool } from "../../../../../../../../shared/util/tool";
import { DragService } from "../../../../../widget/services/drag.service";
import { DRAG_TABLE_ID } from "../ws-composite-table-sidebar-pane.component";
import { ConcatenationDropEvent } from "./concatenation-drop-event";
import { CONCAT_REORDER_SUBTABLE_ID } from "./ws-concatenation-editor-pane.component";

@Component({
   selector: "concatenation-pane-drop-target",
   templateUrl: "concatenation-pane-drop-target.component.html",
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class ConcatenationPaneDropTargetComponent {
   @Input() numTables: number;
   @Input() tableHeight: number;
   @Input() colWidth: number;
   @Input() connectorHeight: number;
   @Input() maxNumCols: number;
   @Output() onDrop: EventEmitter<ConcatenationDropEvent> =
      new EventEmitter<ConcatenationDropEvent>();
   y: number;
   private insertIndex: number;
   private concatenateWithLeftTable: boolean;

   constructor(private dragService: DragService) {
   }

   dragover(event: DragEvent) {
      if(this.dragService.get(DRAG_TABLE_ID) ||
         this.dragService.get(CONCAT_REORDER_SUBTABLE_ID))
      {
         event.preventDefault();
         this.setPosition(event.offsetY);
      }
   }

   drop(event: DragEvent) {
      event.preventDefault(); // Prevent page navigation in ff
      this.setPosition(event.offsetY);
      let tableName: string;
      let dropType: string;

      if((tableName = this.dragService.get(DRAG_TABLE_ID))) {
         dropType = DRAG_TABLE_ID;
      }
      else if((tableName = this.dragService.get(CONCAT_REORDER_SUBTABLE_ID))) {
         dropType = CONCAT_REORDER_SUBTABLE_ID;
      }

      this.onDrop.emit({
         tableName,
         insertIndex: this.insertIndex,
         concatenateWithLeftTable: this.concatenateWithLeftTable,
         dropType
      });
   }

   private setPosition(offsetY: number) {
      const tableConnectorPairSize = this.tableHeight + this.connectorHeight;
      const relativeOffset = Tool.mod(offsetY, tableConnectorPairSize);
      const tableConnectorPairOffset = Math.floor(offsetY / tableConnectorPairSize);
      this.insertIndex = tableConnectorPairOffset;

      if(relativeOffset < this.tableHeight / 2) {
         this.y = tableConnectorPairOffset * tableConnectorPairSize;
         this.concatenateWithLeftTable = false;
      }
      else if(relativeOffset < this.tableHeight + this.connectorHeight / 2) {
         this.y = tableConnectorPairOffset * tableConnectorPairSize + this.tableHeight;
         this.concatenateWithLeftTable = true;
         this.insertIndex++;
      }
      else {
         this.y = (tableConnectorPairOffset + 1) * tableConnectorPairSize;
         this.concatenateWithLeftTable = false;
         this.insertIndex++;
      }
   }
}
