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
import { ElementRef, Injectable, NgZone } from "@angular/core";
import { Observable, ReplaySubject } from "rxjs";
import { ColumnRef } from "../../../../../binding/data/column-ref";
import { AssetUtil } from "../../../../../binding/util/asset-util";
import { AssemblyActionGroup } from "../../../../../common/action/assembly-action-group";
import { Point } from "../../../../../common/data/point";
import { XConstants } from "../../../../../common/util/xconstants";
import { ViewsheetClientService } from "../../../../../common/viewsheet-client";
import { ActionsContextmenuComponent } from "../../../../../widget/fixed-dropdown/actions-contextmenu.component";
import { DropdownOptions } from "../../../../../widget/fixed-dropdown/dropdown-options";
import { FixedDropdownService } from "../../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { joinMap, operatorToJoinTitle } from "../../../../data/ws/inner-join.config";
import { RelationalJoinTableAssembly } from "../../../../data/ws/relational-join-table-assembly";
import { TableAssemblyOperator } from "../../../../data/ws/table-assembly-operator";
import { TableColumnPair } from "../../../../data/ws/table-column-pair";
import {
   jspInitGraphSchema,
   TYPE_COLUMN,
   TYPE_COLUMN_INTERACTION_TARGET,
   TYPE_CROSS
} from "../../jsplumb/jsplumb-graph-schema.config";
import { WSEquiJoinEvent } from "../../socket/ws-join/ws-equi-join-event-event";
import { WSInnerJoinEvent } from "../../socket/ws-join/ws-inner-join-event";
import { WSMoveSchemaTablesEvent } from "../../socket/ws-move/ws-move-schema-tables-event";
import {GuiTool} from "../../../../../common/util/gui-tool";

const EQUIJOIN_URI = "/events/composer/worksheet/equijoin";
const MOVE_SCHEMA_TABLES_URI = "/events/composer/worksheet/move-schemas";
const EDIT_INNER_JOIN_URI = "/events/ws/edit-inner-join";

@Injectable()
export class SchemaThumbnailService {
   private jsp: JSPlumb.JSPlumbInstance;
   private joinTable: RelationalJoinTableAssembly;
   private operators: TableAssemblyOperator[];
   private crossJoins: [string, string][];
   private watchPoint: MutationObserver;
   private readonly tableColumns: {[sourceIds: string]: TableColumnPair} = {};
   private readonly refMap: {[tableName: string]: ColumnRef[]} = {};
   private readonly tableMap: {[tableName: string]: any} = {};
   private readonly dragSchemaTables = new Set<string>();

   /** Object whose properties are a mapping between table names and html ids. */
   private readonly sourceIds = new Map<ColumnRef, string>();

   private readonly connectingColumnSubject = new ReplaySubject<TableColumnPair>(1);
   private readonly twoStepJoinColumnSubject = new ReplaySubject<TableColumnPair | null>(1);
   readonly focusColumnPairSubject = new ReplaySubject<ColumnRef[]>(1);

   constructor(private readonly zone: NgZone,
               private readonly worksheetClient: ViewsheetClientService,
               private readonly fixedDropdownService: FixedDropdownService)
   {
      this.zone.runOutsideAngular(() => {
         this.jsp = jspInitGraphSchema();

         this.jsp.bind("connectionDrag", (connection: any) => {
            const tableColumn = this.tableColumns[connection.sourceId];

            if(connection && connection.target) {
               this.watchPosition(connection.target);
            }

            this.connectingColumnSubject.next(tableColumn);
         });

         this.jsp.bind("connectionAborted", (connection: any) => {
            this.connectingColumnSubject.next(null);
            this.removeWatchPoint();
         });

         this.jsp.bind("connectionDetached", (connection: any) => {
            this.connectingColumnSubject.next(null);
            this.removeWatchPoint();
         });

         this.jsp.bind("connection", (info: any, originalEvent: any) => {
            this.removeWatchPoint();

            if(info.connection.hasType(TYPE_CROSS)) {
               return;
            }

            const sourceTableColumn = this.tableColumns[info.sourceId];
            const targetTableColumn = this.tableColumns[info.targetId];

            // User dragged column connection
            if(info.connection.hasType(TYPE_COLUMN) && originalEvent != null) {
               this.addNewJoinCondition(sourceTableColumn, targetTableColumn);
               this.jsp.deleteConnection(info.connection);
            }
         });
      });
   }

   private watchPosition(target: any): void {
      if(target == null) {
         return;
      }

      let container = this.jsp.getContainer();
      let containerRec = GuiTool.getElementRect(container);

      let observer = new MutationObserver(mutations => {
         mutations.forEach(mutation => {
            if(mutation.type == "attributes") {
               let top = parseInt(target.style.top, 10);
               let autoScroll = true;

               while(autoScroll) {
                  if(top - container.scrollTop + 25 >= containerRec.height) {
                     container.scrollTop += 25;
                  }
                  else if(top > 10 && top - container.scrollTop < 10) {
                     container.scrollTop = Math.max(0, container.scrollTop - 25);
                  }
                  else {
                     autoScroll = false;
                  }
               }
            }
         });
      });

      observer.observe(target, {
         attributes: true
      });

      this.setWatchPoint(observer);
   }

   private removeWatchPoint(): void {
      if(!!this.watchPoint) {
         this.watchPoint.disconnect();
         this.watchPoint = null;
      }
   }

   private setWatchPoint(observer: MutationObserver): void {
      this.removeWatchPoint();
      this.watchPoint = observer;
   }

   public get jsPlumbInstance(): JSPlumb.JSPlumbInstance {
      return this.jsp;
   }

   public getConnectingColumnSubject(): ReplaySubject<TableColumnPair> {
      return this.connectingColumnSubject;
   }

   public getTwoStepJoinColumnObs(): Observable<TableColumnPair | null> {
      return this.twoStepJoinColumnSubject;
   }

   public setCrossJoins(crossJoins: [string, string][]): void {
      this.crossJoins = crossJoins;
   }

   public setOperators(operatorGroups: TableAssemblyOperator[][]): void {
      this.operators = [];

      operatorGroups.forEach((operators) => {
         this.operators.push(...operators);
      });
   }

   public setJoinTable(joinTable: RelationalJoinTableAssembly): void {
      this.joinTable = joinTable;
      this.removeNonExistSelection();
   }

   public setContainer(container: any): void {
      this.zone.runOutsideAngular(() => this.jsp.setContainer(container));
   }

   public moveSchemas(offsetTop: number, offsetLeft: number): void {
      let event = new WSMoveSchemaTablesEvent();
      event.setJoinTableName(this.joinTable.name);
      event.setAssemblyNames(Array.from(this.dragSchemaTables));
      event.setOffsetTop(offsetTop);
      event.setOffsetLeft(offsetLeft);
      this.worksheetClient.sendEvent(MOVE_SCHEMA_TABLES_URI, event);
   }

   public cleanup(): void {
      this.jsp.reset();
   }

   public clear(): void {
      this.connectingColumnSubject.next(null);
      this.focusColumnPairSubject.next(null);
      this.jsp.deleteEveryConnection();
   }

   public initJoinConnections(): void {
      this.jsp.setSuspendDrawing(true);
      this.joinColumns();
      this.makeCrossJoins();
      this.jsp.setSuspendDrawing(false, true);
   }

   private makeCrossJoins(): void {
      this.crossJoins.forEach((cJoin) => {
         let leftTable: string = cJoin[0];
         let rightTable: string = cJoin[1];
         let leftEl = this.tableMap[leftTable];
         let rightEl = this.tableMap[rightTable];

         this.connectCrossJoin(leftEl, rightEl);
      });
   }

   private connectCrossJoin(leftEl: any, rightEl: any): void {
      this.jsp.connect({
         source: leftEl,
         target: rightEl,
         type: TYPE_CROSS,
         connector: ["Flowchart", {gap: 4, stub: 12}]
      });
   }

   private joinColumns(): void {
      for(let op of this.operators) {
         let leftId = this.getId(op.ltable, op.lref);
         let rightId = this.getId(op.rtable, op.rref);
         let overlayProps = {
            label:
               `<div class="overlay-operator"
                     title="${operatorToJoinTitle(op.operation)}">${joinMap(op.operation)}</div>`,
            id: "operator",
            location: 0.5
         };
         let overlays = [
            ["Label", overlayProps]
         ];

         this.connectColumns(leftId, rightId, op, overlays);
      }
   }

   private getId(tableName: string, ref: ColumnRef): string {
      let column = this.refMap[tableName].find((col) => ColumnRef.equalName(ref, col));
      return this.sourceIds.get(column);
   }

   private connectColumns(source: string, target: string,
                          op: TableAssemblyOperator, overlays: any): void
   {
      const connCol = {
         source: source,
         target: target,
         type: TYPE_COLUMN,
         connector: ["Straight", {gap: 4, stub: 35}]
      };

      if((<any[]> this.jsp.getConnections(connCol)).length === 0 &&
         source != null && target != null && source !== target)
      {
         this.jsp.connect(connCol);
         const connInteractionTarget = {
            ...connCol,
            type: TYPE_COLUMN_INTERACTION_TARGET,
            overlays: overlays
         };
         const c = this.jsp.connect(connInteractionTarget);

         c.bind("mouseover", () => {
            let sourceColumn = this.tableColumns[c.sourceId].column;
            let targetColumn = this.tableColumns[c.targetId].column;
            this.zone.run(() => this.focusColumnPairSubject.next([sourceColumn, targetColumn]));
         });
         c.bind("mouseout", () => {
            this.zone.run(() => this.focusColumnPairSubject.next(null));
         });
         c.bind("click", (connection: any, event: MouseEvent) => {
            this.zone.run(() => this.showJoinOperatorsActions(event, op));
         });
      }
   }

   public registerTable(tableName: string, ref: ElementRef): void {
      this.refMap[tableName] = [];
      this.tableMap[tableName] = ref.nativeElement;
   }

   public deregisterTable(tableName: string): void {
      const el = this.tableMap[tableName];
      this.jsp.remove(el);
      delete this.refMap[tableName];
      delete this.tableMap[tableName];
   }

   public registerColumn(tableColumn: TableColumnPair, sourceId: string): void {
      this.tableColumns[sourceId] = tableColumn;
      this.sourceIds.set(tableColumn.column, sourceId);
      let tableName = tableColumn.table;
      this.refMap[tableName].push(tableColumn.column);
   }

   public unregisterColumn(tableColumn: TableColumnPair, sourceId: string): void {
      delete this.tableColumns[sourceId];
      this.sourceIds.delete(tableColumn.column);
   }

   public addToDragSelection(tableNames: string[]): void {
      tableNames.forEach((name) => this.dragSchemaTables.add(name));
      const elements = tableNames.map((name) => this.tableMap[name]);
      this.jsp.addToDragSelection(elements);
   }

   public removeFromDragSelection(tableNames: string[]): void {
      tableNames.forEach((name) => this.dragSchemaTables.delete(name));
      const elements = tableNames.map((name) => this.tableMap[name]);
      this.jsp.removeFromDragSelection(elements);
   }

   public removeNonExistSelection() {
      if(!this.joinTable || !this.dragSchemaTables || this.dragSchemaTables.size == 0) {
         return;
      }

      let schemaTableInfos = this.joinTable.info.schemaTableInfos;

      for(let dragSchemaTable of this.dragSchemaTables) {
         if(!schemaTableInfos[dragSchemaTable]) {
            this.removeFromDragSelection([dragSchemaTable]);
         }
      }
   }

   private sendInnerJoinEvent(operators: TableAssemblyOperator[]): void {
      let event = new WSInnerJoinEvent();
      event.setTableName(this.joinTable.name);
      event.setOperators(operators);
      this.worksheetClient.sendEvent(EDIT_INNER_JOIN_URI, event);
   }

   addNewJoinCondition(leftTableColumn: TableColumnPair, rightTableColumn: TableColumnPair): void {
      if(!AssetUtil.isMergeable(leftTableColumn.column.dataType,
         rightTableColumn.column.dataType) || leftTableColumn.table === rightTableColumn.table)
      {
         return;
      }

      let operation = XConstants.INNER_JOIN;

      for(let op2 of this.operators) {
         if(op2.ltable === leftTableColumn.table && (op2.operation & XConstants.LEFT_JOIN) === XConstants.LEFT_JOIN) {
            operation |= XConstants.LEFT_JOIN;
         }
         if(op2.rtable === rightTableColumn.table && (op2.operation & XConstants.RIGHT_JOIN) === XConstants.RIGHT_JOIN) {
            operation |= XConstants.RIGHT_JOIN;
         }
      }

      let op: TableAssemblyOperator = {
         distinct: true,
         operation: operation,
         lref: leftTableColumn.column,
         ltable: leftTableColumn.table,
         rref: rightTableColumn.column,
         rtable: rightTableColumn.table,
      };

      this.sendInnerJoinEvent([...this.operators, op]);
   }

   editJoinCondition(oldOp: TableAssemblyOperator, newOp: TableAssemblyOperator): void {
      let i = this.operators.indexOf(oldOp);

      if(newOp.operation === XConstants.INNER_JOIN) {
         for(let op of this.operators) {
            if(op.ltable === newOp.ltable && (op.operation & XConstants.LEFT_JOIN) === XConstants.LEFT_JOIN) {
               newOp.operation |= XConstants.LEFT_JOIN;
            }
            if(op.rtable === newOp.rtable && (op.operation & XConstants.RIGHT_JOIN) === XConstants.RIGHT_JOIN) {
               newOp.operation |= XConstants.RIGHT_JOIN;
            }
         }
      }

      this.operators[i] = newOp;
      this.sendInnerJoinEvent(this.operators);
   }

   /**
    * Toggles the outer join state of a RelationalJoinTableAssembly operator.
    *
    * @param table the join table
    * @param oldOp the old join operator
    * @param operation the new equijoin operation
    */
   setEquiJoinType(table: RelationalJoinTableAssembly,
                   oldOp: TableAssemblyOperator,
                   operation: number): void
   {
      if(oldOp.operation === operation) {
         return;
      }

      const event = new WSEquiJoinEvent(table.name, oldOp.ltable, oldOp.rtable, operation);
      this.worksheetClient.sendEvent(EQUIJOIN_URI, event);
   }

   removeJoinCondition(operator: TableAssemblyOperator): void {
      const index = this.operators.indexOf(operator);

      if(index >= 0) {
         this.sendInnerJoinEvent([...this.operators.slice(0, index),
            ...this.operators.slice(index + 1)]);
      }
   }

   public startTwoStepJoin(tableColumn: TableColumnPair): void {
      this.sourceIds.forEach((id) => this.jsp.setSourceEnabled(id, false, TYPE_COLUMN));
      this.connectingColumnSubject.next(tableColumn);
      this.twoStepJoinColumnSubject.next(tableColumn);
   }

   public stopTwoStepJoin(): void {
      this.sourceIds.forEach((id) => this.jsp.setSourceEnabled(id, true, TYPE_COLUMN));
      this.connectingColumnSubject.next(null);
      this.twoStepJoinColumnSubject.next(null);
   }

   /**
    * Shows the join operator actions.
    *
    * @param event the mouse event causing the actions to show
    * @param op the operator to show the actions of
    */
   private showJoinOperatorsActions(event: MouseEvent, op: TableAssemblyOperator): void {
      event.preventDefault();
      event.stopPropagation();

      const dropdownOptions: DropdownOptions = {
         autoClose: true,
         closeOnOutsideClick: true,
         contextmenu: true,
         position: new Point(event.clientX, event.clientY),
         closeOnWindowResize: true
      };

      const contextmenu: ActionsContextmenuComponent = this.fixedDropdownService.open(
         ActionsContextmenuComponent, dropdownOptions).componentInstance;

      contextmenu.actions = this.getJoinOperatorActions(this.joinTable, op);
      contextmenu.sourceEvent = event;
   }

   private getJoinOperatorActions(
      table: RelationalJoinTableAssembly, oldOp: TableAssemblyOperator): AssemblyActionGroup[]
   {
      const actions: AssemblyActionGroup[] = [];

      actions.push(new AssemblyActionGroup([
         {
            id: () => "worksheet table-assembly-operator remove-join-condition",
            label: () => "_#(js:Remove Join Condition)",
            icon: () => null,
            enabled: () => true,
            visible: () => true,
            action: () => this.removeJoinCondition(oldOp)
         }
      ]));

      if(oldOp.operation === XConstants.INNER_JOIN || oldOp.operation === XConstants.LEFT_JOIN ||
         oldOp.operation === XConstants.RIGHT_JOIN || oldOp.operation === XConstants.FULL_JOIN)
      {
         actions.push(new AssemblyActionGroup([
            {
               id: () => "worksheet table-assembly-operator inner-join",
               label: () => "_#(js:Inner Join)",
               icon: () => null,
               enabled: () => true,
               visible: () => true,
               action: () => this.setEquiJoinType(table, oldOp, XConstants.INNER_JOIN),
               classes: () => oldOp.operation === XConstants.INNER_JOIN ? "dropdown-item--check-right" : ""
            },
            {
               id: () => "worksheet table-assembly-operator left-outer-join",
               label: () => "_#(js:Left Outer Join)",
               icon: () => null,
               enabled: () => true,
               visible: () => true,
               action: () => this.setEquiJoinType(table, oldOp, XConstants.LEFT_JOIN),
               classes: () => oldOp.operation === XConstants.LEFT_JOIN ? "dropdown-item--check-right" : ""
            },
            {
               id: () => "worksheet table-assembly-operator right-outer-join",
               label: () => "_#(js:Right Outer Join)",
               icon: () => null,
               enabled: () => true,
               visible: () => true,
               action: () => this.setEquiJoinType(table, oldOp, XConstants.RIGHT_JOIN),
               classes: () => oldOp.operation === XConstants.RIGHT_JOIN ? "dropdown-item--check-right" : ""
            },
            {
               id: () => "worksheet table-assembly-operator full-outer-join",
               label: () => "_#(js:Full Outer Join)",
               icon: () => null,
               enabled: () => true,
               visible: () => true,
               action: () => this.setEquiJoinType(table, oldOp, XConstants.FULL_JOIN),
               classes: () => oldOp.operation === XConstants.FULL_JOIN ? "dropdown-item--check-right" : ""
            }
         ]));
      }

      actions.push(new AssemblyActionGroup([
         {
            id: () => "worksheet table-assembly-operator inner-join",
            label: () => "=",
            icon: () => null,
            enabled: () => true,
            visible: () => true,
            action: () => this.editJoinCondition(oldOp, {
               ...oldOp,
               operation: XConstants.INNER_JOIN
            })
         },
         {
            id: () => "worksheet table-assembly-operator greater-join",
            label: () => ">",
            icon: () => null,
            enabled: () => true,
            visible: () => true,
            action: () => this.editJoinCondition(oldOp, {
               ...oldOp,
               operation: XConstants.GREATER_JOIN
            })
         },
         {
            id: () => "worksheet table-assembly-operator less-join",
            label: () => "<",
            icon: () => null,
            enabled: () => true,
            visible: () => true,
            action: () => this.editJoinCondition(oldOp, {...oldOp, operation: XConstants.LESS_JOIN})
         },
         {
            id: () => "worksheet table-assembly-operator greater-equal-join",
            label: () => ">=",
            icon: () => null,
            enabled: () => true,
            visible: () => true,
            action: () => this.editJoinCondition(oldOp, {
               ...oldOp,
               operation: XConstants.GREATER_EQUAL_JOIN
            })
         },
         {
            id: () => "worksheet table-assembly-operator less-equal-join",
            label: () => "<=",
            icon: () => null,
            enabled: () => true,
            visible: () => true,
            action: () => this.editJoinCondition(oldOp, {
               ...oldOp,
               operation: XConstants.LESS_EQUAL_JOIN
            })
         },
         {
            id: () => "worksheet table-assembly-operator not-equal-join",
            label: () => "<>",
            icon: () => null,
            enabled: () => true,
            visible: () => true,
            action: () => this.editJoinCondition(oldOp, {
               ...oldOp,
               operation: XConstants.NOT_EQUAL_JOIN
            })
         }
      ]));

      return actions;
   }
}
