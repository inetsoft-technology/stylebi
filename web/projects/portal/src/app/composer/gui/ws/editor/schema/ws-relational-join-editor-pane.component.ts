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
   AfterViewChecked,
   AfterViewInit,
   Component,
   ElementRef,
   EventEmitter,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   QueryList,
   SimpleChanges,
   ViewChild,
   ViewChildren
} from "@angular/core";
import { Observable, Subscription } from "rxjs";
import { Tool } from "../../../../../../../../shared/util/tool";
import { ColumnRef } from "../../../../../binding/data/column-ref";
import { DragEvent } from "../../../../../common/data/drag-event";
import { Notification } from "../../../../../common/data/notification";
import { Rectangle } from "../../../../../common/data/rectangle";
import { SelectionBoxEvent } from "../../../../../widget/directive/selection-box.directive";
import { DragService } from "../../../../../widget/services/drag.service";
import { AbstractTableAssembly } from "../../../../data/ws/abstract-table-assembly";
import { CompositeTableAssembly } from "../../../../data/ws/composite-table-assembly";
import { RelationalJoinTableAssembly } from "../../../../data/ws/relational-join-table-assembly";
import { TableAssemblyOperator } from "../../../../data/ws/table-assembly-operator";
import { TableColumnPair } from "../../../../data/ws/table-column-pair";
import { Worksheet } from "../../../../data/ws/worksheet";
import { WSDeleteSubtablesEvent } from "../../socket/ws-delete-sub-table-event";
import { WSDropTableIntoJoinSchemaEvent } from "../../socket/ws-join/ws-drop-table-into-join-schema-event";
import { WSResizeSchemaTableEvent } from "../../socket/ws-resize-schema-event";
import { DRAG_TABLE_ID } from "../ws-composite-table-sidebar-pane.component";
import {
   SchemaTableThumbnailComponent,
   SubtableInteractionEvent
} from "./schema-table-thumbnail.component";
import { SchemaThumbnailService } from "./schema-thumbnail.service";
import { reorderColumns, TableColumnsPair } from "./sort-schema-column.controller";
import { WsChangeService } from "../ws-change.service";

const DELETE_SUBTABLE_URI = "/events/ws/joins/delete-sub-table";
const DROP_TABLE_URI = "/events/composer/worksheet/drop-table-into-join-schema";
const RESIZE_SCHEMA_TABLE_URI = "/events/composer/worksheet/resize-schema-table";

@Component({
   selector: "ws-relational-join-editor-pane",
   templateUrl: "ws-relational-join-editor-pane.component.html",
   styleUrls: [
      "ws-relational-join-editor-pane.component.scss",
      "../../jsplumb/jsplumb-shared.scss"
   ],
   providers: [SchemaThumbnailService]
})
export class WSRelationalJoinEditorPaneComponent
   implements OnChanges, OnInit, AfterViewInit, AfterViewChecked, OnDestroy
{
   @Input() joinTable: RelationalJoinTableAssembly;
   @Input() worksheet: Worksheet;
   @Output() onSelectSubtables = new EventEmitter<AbstractTableAssembly[]>();
   @Output() onFocusCompositeTable = new EventEmitter<CompositeTableAssembly>();
   @Output() onNotification = new EventEmitter<Notification>();
   @Output() onCrossJoinsChanged = new EventEmitter<[string, string][]>();
   @ViewChild("jspContainerSchema") jspContainerSchema: ElementRef;
   @ViewChildren(SchemaTableThumbnailComponent) thumbnails: QueryList<SchemaTableThumbnailComponent>;
   tableColumnsPairs: TableColumnsPair[];
   sortedOperatorGroups: TableAssemblyOperator[][];
   private populated: boolean = false;
   private selectSubtableFlag: boolean;
   private subscriptions = new Subscription();

   constructor(private schemaThumbnailService: SchemaThumbnailService,
               private dragService: DragService,
               private zone: NgZone,
               private wsChangeService: WsChangeService)
   {
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.hasOwnProperty("joinTable")) {
         this.schemaThumbnailService.setJoinTable(this.joinTable);
         this.populateDependedTables();
         this.reorderColumns();
         this.sortOperators();
         const crossJoins =
            this.createCrossJoins(this.joinTable.subtables, this.sortedOperatorGroups);
         this.schemaThumbnailService.setCrossJoins(crossJoins);
         this.onCrossJoinsChanged.emit(crossJoins);
         this.schemaThumbnailService.setOperators(this.sortedOperatorGroups);
         this.populated = true;
      }
   }

   ngOnInit(): void {
      this.worksheet.jspSchemaGraph = this.schemaThumbnailService.jsPlumbInstance;

      this.subscriptions.add(this.wsChangeService.assemblyChanged.subscribe(name => {
         if(!this.joinTable?.subtables) {
            return;
         }

         if(this.joinTable.subtables.find(table => table == name)) {
            this.populateDependedTables();
            this.reorderColumns();
            this.populated = true;
         }
      }));
   }

   ngAfterViewInit(): void {
      this.schemaThumbnailService.setContainer(this.jspContainerSchema.nativeElement);
      this.onNotification.emit({
         type: "success",
         message: "_#(js:viewer.worksheet.createJoins)"
      });
   }

   ngAfterViewChecked(): void {
      if(this.jsp.isSuspendDrawing()) {
         this.jsp.makeSource("schema-column");
         this.jsp.setSuspendDrawing(false, true);
      }

      if(this.populated) {
         this.populated = false;
         // Wait a tick for elements to get created, then connect them.
         setTimeout(() => {
            this.schemaThumbnailService.clear();
            this.schemaThumbnailService.initJoinConnections();
         }, 0);
      }
   }

   ngOnDestroy(): void {
      this.jsp.deleteEveryConnection({fireEvent: false});
      this.schemaThumbnailService.cleanup();
      this.subscriptions.unsubscribe();
   }

   oozKeydown(event: KeyboardEvent): void {
      if(this.worksheet.isFocused) {
         // Delete
         if(!event.repeat && Tool.getKeyCode(event) === 46 && !!this.worksheet.selectedSubtables
            && this.worksheet.selectedSubtables.length > 0 &&
            !(event.target instanceof HTMLInputElement) &&
            !(event.target instanceof HTMLTextAreaElement))
         {
            this.deleteSubtables();
         }
         // ctrl-a
         else if(Tool.getKeyCode(event) === 65 && event.ctrlKey) {
            event.preventDefault();

            if(!event.repeat) {
               this.zone.run(() => {
                  this.worksheet.selectedSubtables = this.tableColumnsPairs
                     .map((pair) => pair.table);
               });
            }
         }
      }
   }

   deleteSubtables(): void {
      const event = new WSDeleteSubtablesEvent();
      event.setJoinTable(this.joinTable.name);
      event.setSubTables(this.worksheet.selectedSubtables.map((t) => t.name));
      this.worksheet.socketConnection.sendEvent(DELETE_SUBTABLE_URI, event);
   }

   resizeSchemaTable(event: WSResizeSchemaTableEvent): void {
      this.worksheet.socketConnection.sendEvent(RESIZE_SCHEMA_TABLE_URI, event);
   }

   allowDrop(event: DragEvent): void {
      // If drop target is not jsp container, then positions offsets will be wrong.
      // TODO consider better solution that allows drop anywhere in the view.
      if(this.dragService.get(DRAG_TABLE_ID) &&
         event.target == this.jspContainerSchema.nativeElement)
      {
         event.preventDefault();
      }
   }

   dropSchemaTable(dragEvent: DragEvent): void {
      dragEvent.preventDefault();
      let draggedTable = <string> this.dragService.get(DRAG_TABLE_ID);
      let dy = this.jspContainerSchema.nativeElement.scrollTop;
      let dx = this.jspContainerSchema.nativeElement.scrollLeft;

      let event = new WSDropTableIntoJoinSchemaEvent();
      event.setJoinTable(this.joinTable.name);
      event.setDroppedTable(draggedTable);
      event.setTop(dragEvent.offsetY + dy);
      event.setLeft(dragEvent.offsetX + dx);
      this.worksheet.socketConnection.sendEvent(DROP_TABLE_URI, event);
   }

   selectSubtable(event: SubtableInteractionEvent): void {
      if(event.click && this.selectSubtableFlag) {
         this.selectSubtableFlag = false;
         return;
      }

      let selectedSubtables: AbstractTableAssembly[] = null;
      const index = this.worksheet.selectedSubtables.indexOf(event.subtable);

      if(event.ctrlKey || event.metaKey) {

         if(index >= 0 && event.click) {
            selectedSubtables = [...this.worksheet.selectedSubtables];
            selectedSubtables.splice(index, 1);
         }
         else if(index === -1 && event.mousedown) {
            selectedSubtables = [...this.worksheet.selectedSubtables, event.subtable];
         }
      }
      else if(index === -1) {
         selectedSubtables = [event.subtable];
      }

      if(selectedSubtables !== null) {
         this.onSelectSubtables.emit(selectedSubtables);

         if(event.mousedown) {
            this.selectSubtableFlag = true;
         }
      }
   }

   jsplumbContainerMousedown(event: MouseEvent): void {
      if(this.jspContainerSchema.nativeElement === event.target) {
         this.onSelectSubtables.emit([]);
         this.schemaThumbnailService.stopTwoStepJoin();
      }
   }

   focusCompositeTable(table: CompositeTableAssembly): void {
      this.onFocusCompositeTable.emit(table);
   }

   onSelectionBox(event: SelectionBoxEvent): void {
      const box = Rectangle.fromClientRect(event.clientRect);
      const selectedSubtables: AbstractTableAssembly[] = [];

      for(const thumbnail of this.thumbnails.toArray()) {
         const clientRect = thumbnail.hostRef.nativeElement.getBoundingClientRect();
         const rect = Rectangle.fromClientRect(clientRect);

         if(rect.intersects(box)) {
            selectedSubtables.push(thumbnail.schemaTable);
         }
      }

      if(selectedSubtables.length > 0) {
         this.onSelectSubtables.emit(selectedSubtables);
      }
   }

   getTwoStepJoinColumnObs(): Observable<TableColumnPair | null> {
      return this.schemaThumbnailService.getTwoStepJoinColumnObs();
   }

   private populateDependedTables(): void {
      this.tableColumnsPairs = [];

      for(let name of this.joinTable.subtables) {
         let table = this.worksheet.getTableFromName(name);

         if(table != null) {
            this.tableColumnsPairs.push({table: table});
         }
         else {
            console.warn(`Could not resolve table: ${name}`); // should not happen
         }
      }
   }

   private reorderColumns(): void {
      this.tableColumnsPairs = reorderColumns(this.tableColumnsPairs.map((p) => p.table),
         this.joinTable.info.operatorGroups);
   }

   /**
    * Sort operators so that their order corresponds to table pair column order.
    * This is so that when adding connections using jsPlumb, they are added in order
    * so that element layering becomes ordered.
    */
   private sortOperators(): void {
      this.sortedOperatorGroups = [];

      for(let operators of this.joinTable.info.operatorGroups) {
         let sortedOperators: TableAssemblyOperator[] = [];
         let leftTable = operators[0].ltable;
         let pair = this.tableColumnsPairs.find((tip) => tip.table.name === leftTable);

         // TODO remove if proves robust
         if(!pair) {
            console.error("Operators were not resolved.");
            return;
         }

         pair.columns.forEach((ref) => {
            let correspondingOperators = operators
               .filter((op) => ColumnRef.equalName(op.lref, ref));

            if(correspondingOperators.length === 0) {
               return;
            }

            sortedOperators.push(...correspondingOperators);
         });

         // TODO remove if proves robust
         if(sortedOperators.length !== operators.length) {
            console.error("Sorted operators were not resolved.");
         }

         this.sortedOperatorGroups.push(sortedOperators);
      }
   }

   private createCrossJoins(
      subTables: string[], operatorGroups: TableAssemblyOperator[][]): [string, string][]
   {
      let connMatrix: boolean[][] = this.getConnectionMatrix(subTables, operatorGroups);
      let tableRoots: string[] = [...subTables];

      for(let i = 0; i < subTables.length; i++) {
         let root = tableRoots[i];
         let connections: boolean[] = connMatrix[i];

         connections.forEach((areConnected, j) => {
            if(areConnected) {
               tableRoots[j] = root;
            }
         });
      }

      tableRoots = Tool.uniq(tableRoots);
      let crossJoins: [string, string][] = [];

      for(let i = 1; i < tableRoots.length; i++) {
         crossJoins.push([tableRoots[i - 1], tableRoots[i]]);
      }

      return crossJoins;
   }

   private new2DBooleanArray(size: number): boolean[][] {
      return Array.from({length: size}, () => Array(size).fill(false));
   }

   private getConnectionMatrix(
      subTables: string[], operatorGroups: TableAssemblyOperator[][]): boolean[][]
   {
      let connMatrix: boolean[][] = this.new2DBooleanArray(subTables.length);

      for(let group of operatorGroups) {
         let leftTable: string = group[0].ltable;
         let rightTable: string = group[0].rtable;
         let leftIndex: number = subTables.indexOf(leftTable);
         let rightIndex: number = subTables.indexOf(rightTable);

         if(leftIndex >= 0 && rightIndex >= 0) {
            connMatrix[leftIndex][rightIndex] = true;
            connMatrix[rightIndex][leftIndex] = true;
         }
      }

      return connMatrix;
   }

   private get jsp(): JSPlumb.JSPlumbInstance {
      return this.schemaThumbnailService.jsPlumbInstance;
   }
}
