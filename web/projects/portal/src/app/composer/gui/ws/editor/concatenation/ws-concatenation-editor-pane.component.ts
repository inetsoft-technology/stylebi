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
import { HttpParams } from "@angular/common/http";
import {
   ChangeDetectionStrategy,
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import { merge as observableMerge, Observable, Subscription } from "rxjs";
import { delay } from "rxjs/operators";
import { AssetUtil } from "../../../../../binding/util/asset-util";
import { DragEvent } from "../../../../../common/data/drag-event";
import { AssemblyActionGroup } from "../../../../../common/action/assembly-action-group";
import { Tool } from "../../../../../../../../shared/util/tool";
import { XConstants } from "../../../../../common/util/xconstants";
import { FixedDropdownService } from "../../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DragService } from "../../../../../widget/services/drag.service";
import { ModelService } from "../../../../../widget/services/model.service";
import { DialogService } from "../../../../../widget/slide-out/dialog-service.service";
import { AbstractTableAssembly } from "../../../../data/ws/abstract-table-assembly";
import { CompositeTableAssembly } from "../../../../data/ws/composite-table-assembly";
import { ConcatenatedTableAssembly } from "../../../../data/ws/concatenated-table-assembly";
import { ConcatenationTypeDialogModel } from "../../../../data/ws/concatenation-type-dialog-model";
import { Worksheet } from "../../../../data/ws/worksheet";
import { ConcatenationTypeDialog } from "../../../../dialog/ws/concatenation-type-dialog.component";
import { WSTableActions } from "../../action/ws-table.actions";
import { ReorderSubtablesEvent } from "../../socket/reorder-subtables-event";
import { WSConcatAddSubtableEvent } from "../../socket/ws-concat-add-subtable-event";
import { WSDeleteSubtablesEvent } from "../../socket/ws-delete-sub-table-event";
import { DRAG_TABLE_ID } from "../ws-composite-table-sidebar-pane.component";
import { ConcatenationDropEvent } from "./concatenation-drop-event";
import { ComponentTool } from "../../../../../common/util/component-tool";
import { WsChangeService } from "../ws-change.service";

export const CONCAT_REORDER_SUBTABLE_ID: string = "reorder subtable";
const CONCAT_ADD_TABLE_URI: string = "/events/composer/worksheet/concatenate/add-table";
const CONCAT_TYPE_URI: string = "../api/composer/ws/concatenation-type-dialog/";
const DELETE_SUBTABLE_URI: string = "/events/ws/joins/delete-sub-table";
const REORDER_SUBTABLES_URI = "/events/composer/worksheet/reorder-subtables";

@Component({
   selector: "ws-concatenation-editor-pane",
   templateUrl: "ws-concatenation-editor-pane.component.html",
   styleUrls: ["ws-concatenation-editor-pane.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class WSConcatenationEditorPane implements OnChanges, OnInit, OnDestroy {
   @Input() worksheet: Worksheet;
   @Input() concatTable: ConcatenatedTableAssembly;
   @Output() onSelectSubtables = new EventEmitter<AbstractTableAssembly[]>();
   @Output() onFocusCompositeTable = new EventEmitter<CompositeTableAssembly>();
   readonly colWidth: number = 150;
   readonly tableHeight: number = 60;
   readonly connectorHeight: number = 60;
   tables: AbstractTableAssembly[];
   maxBoundNumCols: number = 0;
   draggingTable: Observable<string>;
   subtableProblemColumns: Map<string, Set<number>>;
   allProblemColumns: Set<number>;
   private subscriptions = new Subscription();

   constructor(private dragService: DragService,
               private modelService: ModelService,
               private dialogService: DialogService,
               private dropdownService: FixedDropdownService,
               private wsChangeService: WsChangeService)
   {
   }

   ngOnInit() {
      this.draggingTable = observableMerge(
         this.dragService.registerDragDataListener(DRAG_TABLE_ID),
         this.dragService.registerDragDataListener(CONCAT_REORDER_SUBTABLE_ID)
      ).pipe(delay(0)); // Delay so that the drop overlay does not interfere with dragging

      this.subscriptions.add(this.wsChangeService.assemblyChanged.subscribe(name => {
         if(!this.concatTable?.subtables) {
            return;
         }

         if(this.concatTable.subtables.find(table => table == name)) {
            this.populateDependedTables();
         }
      }));
   }

   ngOnChanges(changes: SimpleChanges) {
      this.populateDependedTables();
      this.calculateMaxBoundNumCols();
      this.populateMergeProblemTables(this.tables);
   }

   ngOnDestroy() {
      this.dragService.disposeDragDataListener(DRAG_TABLE_ID);
      this.dragService.disposeDragDataListener(CONCAT_REORDER_SUBTABLE_ID);
      this.dialogService.objectDelete("ws-concatenation-editor");
      this.subscriptions.unsubscribe();
   }

   focusConcatTable(event: MouseEvent) {
      if(event.target === event.currentTarget) {
         this.selectTableInView(this.concatTable);
      }
   }

   get errorTableHeight(): number {
      return this.tableHeight * this.tables.length +
         this.connectorHeight * (this.tables.length - 1);
   }

   focusCompositeTable(subtable: AbstractTableAssembly) {
      if(subtable instanceof CompositeTableAssembly) {
         this.onFocusCompositeTable.emit(subtable);
      }
   }

   calculateMaxBoundNumCols() {
      this.maxBoundNumCols = 0;
      this.tables.forEach((table) => {
         this.maxBoundNumCols = Math.max(this.maxBoundNumCols,
            table.getPublicColumnSelection().length);
      });
   }

   selectTableInView(table: AbstractTableAssembly) {
      this.onSelectSubtables.emit([table]);
   }

   showConcatenationTypeDialog(leftTable: string, rightTable: string) {
      const params = new HttpParams()
         .set("concatenatedTable", this.concatTable.name)
         .set("leftTable", leftTable)
         .set("rightTable", rightTable);

      this.modelService.getModel<ConcatenationTypeDialogModel>(CONCAT_TYPE_URI + Tool.byteEncode(this.worksheet.runtimeId), params)
         .subscribe((model) => {
               const options = {
                  windowClass: "property-dialog-window",
                  objectId: "ws-concatenation-editor"
               };
               const dialog = ComponentTool.showDialog(this.dialogService, ConcatenationTypeDialog,
                  (resolve) => {
                     if(resolve) {
                        this.worksheet.socketConnection
                           .sendEvent(resolve.controller, resolve.model);
                     }
                  }, options);

               dialog.model = model;
            },
            () => {
               console.error("Could not fetch concatenation type info.");
            }
         );
   }

   getOperation(leftTable: string, rightTable: string): number {
      const operatorGroup = this.concatTable.info.operatorGroups
         .find((group) => group[0].ltable === leftTable && group[0].rtable === rightTable);

      if(operatorGroup) {
         return operatorGroup[0].operation;
      }

      // concat table don't stop left/right table after save and re-open, the op
      // is stored sequentially as the subtables
      for(let i = 0; i < this.concatTable.subtables.length - 1; i++) {
         if(this.concatTable.subtables[i] == leftTable &&
            this.concatTable.subtables[i + 1] == rightTable)
         {
            return this.concatTable.info.operatorGroups[i][0].operation;
         }
      }

      return null;
   }

   dropTable(dropEvent: ConcatenationDropEvent) {
      if(dropEvent.dropType === DRAG_TABLE_ID) {
         this.concatenateTable(dropEvent);
      }
      else if(dropEvent.dropType === CONCAT_REORDER_SUBTABLE_ID) {
         this.reorderSubtables(dropEvent);
      }
      else {
         throw new Error(`Unknown drop type: ${dropEvent.dropType}`);
      }
   }

   showSubtableContextmenu(event: MouseEvent, subtable: AbstractTableAssembly) {
      const actions = this.createActions(subtable);

      ComponentTool.openActionsContextmenu(this.dropdownService, actions, event);
   }

   createActions(subtable: AbstractTableAssembly) {
      const actions = [
         new AssemblyActionGroup([
            {
               id: () => "worksheet concat-table focus-composite-table",
               label: () => WSTableActions.getEditCompositionLabel(subtable),
               icon: () => null,
               enabled: () => true,
               visible: () => subtable instanceof CompositeTableAssembly,
               action: () => this.focusCompositeTable(subtable)
            },
            {
               id: () => "worksheet concat-table remove-table",
               label: () => "_#(js:Remove table)",
               icon: () => null,
               enabled: () => true,
               visible: () => true,
               action: () => this.deleteSubtable(subtable)
            }
         ])
      ];

      return actions;
   }

   deleteSubtable(subtable: AbstractTableAssembly) {
      const event = new WSDeleteSubtablesEvent();
      event.setJoinTable(this.concatTable.name);
      event.setSubTables([subtable.name]);
      this.worksheet.socketConnection.sendEvent(DELETE_SUBTABLE_URI, event);
   }

   dragSubtable(event: DragEvent, subtable: AbstractTableAssembly) {
      Tool.setTransferData(event.dataTransfer, {}); // Prevent page navigation in ff
      this.dragService.put(CONCAT_REORDER_SUBTABLE_ID, subtable.name);
   }

   private populateDependedTables() {
      this.tables = [];

      for(let name of this.concatTable.subtables) {
         const table = this.worksheet.getTableFromName(name);

         if(table != null) {
            this.tables.push(table);
         }
         else {
            console.warn(`Could not resolve table: ${name}`);
         }
      }
   }

   private populateMergeProblemTables(subtables: AbstractTableAssembly[]) {
      this.allProblemColumns = new Set<number>();
      this.subtableProblemColumns = new Map<string, Set<number>>();
      const baseSubtable = subtables[0];
      // base will never have problem columns
      this.subtableProblemColumns.set(baseSubtable.name, new Set<number>());

      for(let i = 1; i < subtables.length; i++) {
         const subtable = subtables[i];
         const subtableProblemColumns = new Set<number>();

         for(let j = 0; j < baseSubtable.getPublicColumnSelection().length ||
                        j < subtable.getPublicColumnSelection().length; j++)
         {
            const baseColumnRef = baseSubtable.getPublicColumnSelection()[j];
            const subtableColumnRef = subtable.getPublicColumnSelection()[j];

            if(baseColumnRef == null || subtableColumnRef == null ||
               !AssetUtil.isMergeable(baseColumnRef.dataType, subtableColumnRef.dataType))
            {
               this.allProblemColumns.add(j);
               subtableProblemColumns.add(j);
            }
         }

         this.subtableProblemColumns.set(subtable.name, subtableProblemColumns);
      }
   }

   private concatenateTable(dropEvent: ConcatenationDropEvent) {
      const event = new WSConcatAddSubtableEvent();
      event.setConcatTableName(this.concatTable.name);
      event.setNewTableName(dropEvent.tableName);
      event.setOperator(XConstants.UNION);
      event.setIndex(dropEvent.insertIndex);
      event.setConcatenateWithLeftTable(dropEvent.concatenateWithLeftTable);
      this.worksheet.socketConnection.sendEvent(CONCAT_ADD_TABLE_URI, event);
   }

   private reorderSubtables(dropEvent: ConcatenationDropEvent) {
      const initialIndex = this.concatTable.subtables.indexOf(dropEvent.tableName);

      if(initialIndex === -1) {
         throw new Error("Invalid subtable");
      }

      // Subtable index is unchanged
      if(initialIndex === dropEvent.insertIndex ||
         (initialIndex + 1 === dropEvent.insertIndex && initialIndex < dropEvent.insertIndex))
      {
         return;
      }

      let subtables: string[];

      if(initialIndex < dropEvent.insertIndex) {
         subtables = [
            ...this.concatTable.subtables.slice(0, initialIndex),
            ...this.concatTable.subtables.slice(initialIndex + 1, dropEvent.insertIndex),
            dropEvent.tableName,
            ...this.concatTable.subtables.slice(dropEvent.insertIndex)
         ];
      }
      else {
         subtables = [
            ...this.concatTable.subtables.slice(0, dropEvent.insertIndex),
            dropEvent.tableName,
            ...this.concatTable.subtables.slice(dropEvent.insertIndex, initialIndex),
            ...this.concatTable.subtables.slice(initialIndex + 1)
         ];
      }

      const event = new ReorderSubtablesEvent();
      event.setParentTable(this.concatTable.name);
      event.setSubtables(subtables);
      this.worksheet.socketConnection.sendEvent(REORDER_SUBTABLES_URI, event);
   }
}
