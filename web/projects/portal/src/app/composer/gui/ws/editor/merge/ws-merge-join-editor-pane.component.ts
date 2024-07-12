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
import {
   ChangeDetectionStrategy,
   Component,
   EventEmitter,
   Input,
   OnDestroy,
   OnInit,
   Output
} from "@angular/core";
import { merge as observableMerge, Observable, Subscription } from "rxjs";
import { delay } from "rxjs/operators";
import { DragEvent } from "../../../../../common/data/drag-event";
import { AssemblyActionGroup } from "../../../../../common/action/assembly-action-group";
import { Tool } from "../../../../../../../../shared/util/tool";
import { FixedDropdownService } from "../../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DragService } from "../../../../../widget/services/drag.service";
import { AbstractTableAssembly } from "../../../../data/ws/abstract-table-assembly";
import { CompositeTableAssembly } from "../../../../data/ws/composite-table-assembly";
import { MergeJoinTableAssembly } from "../../../../data/ws/merge-join-table-assembly";
import { Worksheet } from "../../../../data/ws/worksheet";
import { WSTableActions } from "../../action/ws-table.actions";
import { WSDeleteSubtablesEvent } from "../../socket/ws-delete-sub-table-event";
import { WSMergeAddJoinTableEvent } from "../../socket/ws-join/ws-merge-add-join-table-event";
import { DRAG_TABLE_ID } from "../ws-composite-table-sidebar-pane.component";
import { ComponentTool } from "../../../../../common/util/component-tool";
import { WsChangeService } from "../ws-change.service";

const ADD_JOIN_TABLE_URI: string = "/events/composer/worksheet/add-join-table";
const REORDER_JOIN_TABLE_DRAG_KEY: string = "reorder-join-table";
const REORDER_JOIN_TABLE_URI: string = "/events/composer/worksheet/reorder-join-table";
const DELETE_SUBTABLE_URI: string = "/events/ws/joins/delete-sub-table";

class MergeJoinDropInfo {
   subtableCount: number;
   currentInsertionIndex: number;
   readonly width: number = 6;
   readonly totalMargin: number = 150;
}

@Component({
   selector: "ws-merge-join-editor-pane",
   templateUrl: "ws-merge-join-editor-pane.component.html",
   styleUrls: ["ws-merge-join-editor-pane.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class WSMergeJoinEditorPaneComponent implements OnDestroy, OnInit {
   @Input() worksheet: Worksheet;
   @Output() onSelectSubtables = new EventEmitter<AbstractTableAssembly[]>();
   @Output() onFocusCompositeTable = new EventEmitter<CompositeTableAssembly>();
   subtables: AbstractTableAssembly[];
   dropInfo: MergeJoinDropInfo;
   draggingTable: Observable<boolean>;
   private _mergeTable: MergeJoinTableAssembly;
   private subscriptions = new Subscription();

   constructor(private dragService: DragService,
               private dropdownService: FixedDropdownService,
               private wsChangeService: WsChangeService)
   {
   }

   @Input()
   set mergeTable(mergeTable: MergeJoinTableAssembly) {
      this._mergeTable = mergeTable;
      this.populateDependedTables();
   }

   get mergeTable(): MergeJoinTableAssembly {
      return this._mergeTable;
   }

   ngOnInit() {
      this.draggingTable = observableMerge(
         this.dragService.registerDragDataListener(DRAG_TABLE_ID),
         this.dragService.registerDragDataListener(REORDER_JOIN_TABLE_DRAG_KEY)
      ).pipe(delay(0));

      this.subscriptions.add(this.wsChangeService.assemblyChanged.subscribe(name => {
         if(!this.mergeTable?.subtables) {
            return;
         }

         if(this.mergeTable.subtables.find(table => table == name)) {
            this.populateDependedTables();
         }
      }));
   }

   ngOnDestroy() {
      this.dragService.disposeDragDataListener(DRAG_TABLE_ID);
      this.dragService.disposeDragDataListener(REORDER_JOIN_TABLE_DRAG_KEY);
      this.subscriptions.unsubscribe();
   }

   focusMergeTable(event: MouseEvent) {
      if(event.target === event.currentTarget) {
         this.selectSubtable(this.mergeTable);
      }
   }

   selectSubtable(subtable: AbstractTableAssembly) {
      this.onSelectSubtables.emit([subtable]);
   }

   focusCompositeTable(subtable: AbstractTableAssembly) {
      if(subtable instanceof CompositeTableAssembly) {
         this.onFocusCompositeTable.emit(subtable);
      }
   }

   allowDropSubtable(event: DragEvent) {
      if(this.dragService.get(DRAG_TABLE_ID) ||
         this.dragService.get(REORDER_JOIN_TABLE_DRAG_KEY))
      {
         event.preventDefault();

         if(this.dropInfo == null) {
            this.dropInfo = new MergeJoinDropInfo();
            this.dropInfo.subtableCount = this.subtables.length;
         }

         this.updateDropInfo(event);
      }
   }

   dragSubtable(event: DragEvent, subtable: AbstractTableAssembly) {
      Tool.setTransferData(event.dataTransfer, {});
      this.dragService.put(REORDER_JOIN_TABLE_DRAG_KEY, subtable);
   }

   stopDrag() {
      this.dropInfo = null;
   }

   dropSubtable(e: DragEvent) {
      e.preventDefault(); // Prevent page navigation in ff
      this.updateDropInfo(e);
      const event = new WSMergeAddJoinTableEvent();
      event.setMergeTableName(this._mergeTable.name);
      event.setInsertionIndex(this.dropInfo.currentInsertionIndex);
      const addTable: string = this.dragService.get(DRAG_TABLE_ID);
      const reorderTable: AbstractTableAssembly = this.dragService.get(REORDER_JOIN_TABLE_DRAG_KEY);

      if(addTable && this.mergeTable.subtables.indexOf(addTable) < 0) {
         event.setNewTableName(addTable);
         this.worksheet.socketConnection.sendEvent(ADD_JOIN_TABLE_URI, event);
      }
      else if(addTable && this.mergeTable.subtables.indexOf(addTable) >= 0) {
         event.setNewTableName(addTable);
         this.worksheet.socketConnection.sendEvent(REORDER_JOIN_TABLE_URI, event);
      }
      else if(reorderTable) {
         event.setNewTableName(reorderTable.name);
         this.worksheet.socketConnection.sendEvent(REORDER_JOIN_TABLE_URI, event);
      }

      this.stopDrag();
   }

   showSubtableContextmenu(event: MouseEvent, subtable: AbstractTableAssembly) {
      const actions = [
         new AssemblyActionGroup([
            {
               id: () => "worksheet merge-table focus-composite-table",
               label: () => WSTableActions.getEditCompositionLabel(subtable),
               icon: () => null,
               enabled: () => true,
               visible: () => subtable instanceof CompositeTableAssembly,
               action: () => this.focusCompositeTable(subtable)
            },
            {
               id: () => "worksheet merge-table remove-table",
               label: () => "_#(js:Remove table)",
               icon: () => null,
               enabled: () => true,
               visible: () => true,
               action: () => this.deleteSubtable(subtable)
            }
         ])
      ];

      ComponentTool.openActionsContextmenu(this.dropdownService, actions, event);
   }

   deleteSubtable(subtable: AbstractTableAssembly) {
      const event = new WSDeleteSubtablesEvent();
      event.setJoinTable(this.mergeTable.name);
      event.setSubTables([subtable.name]);
      this.worksheet.socketConnection.sendEvent(DELETE_SUBTABLE_URI, event);
   }

   private updateDropInfo(event: DragEvent) {
      const x = event.offsetX;
      const fullWidth = this.dropInfo.width + this.dropInfo.totalMargin;
      const tableIndex = Math.min(Math.floor(x / fullWidth), this.dropInfo.subtableCount - 1);
      const relativeDragOffset = x - tableIndex * fullWidth;
      const indexOffset = relativeDragOffset > fullWidth / 2 ? 1 : 0;
      this.dropInfo.currentInsertionIndex = tableIndex + indexOffset;
   }

   private populateDependedTables() {
      this.subtables = [];

      for(let name of this.mergeTable.subtables) {
         let table = this.worksheet.getTableFromName(name);

         if(table != null) {
            this.subtables.push(table);
         }
         else {
            console.warn(`Could not resolve table: ${name}`); // should not happen
         }
      }
   }
}
