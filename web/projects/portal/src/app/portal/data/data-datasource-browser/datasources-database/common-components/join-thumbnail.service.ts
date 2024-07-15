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
import { HttpClient } from "@angular/common/http";
import { Injectable, NgZone, OnDestroy } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable, ReplaySubject, timer as observableTimer } from "rxjs";
import { Tool } from "../../../../../../../../shared/util/tool";
import { AssetUtil } from "../../../../../binding/util/asset-util";
import { AssemblyActionGroup } from "../../../../../common/action/assembly-action-group";
import { Point } from "../../../../../common/data/point";
import { ComponentTool } from "../../../../../common/util/component-tool";
import { DependencyType } from "../../../../../composer/data/ws/dependency-type";
import { JSPlumbDependencyTypeOverlays } from "../../../../../composer/gui/ws/jsplumb/jsplumb-dependency-type-overlays";
import {
   jspInitGraphSchema, PHYSICAL_VIEW_TYPE_COLUMN, TYPE_COLUMN_INTERACTION_TARGET
} from "../../../../../composer/gui/ws/jsplumb/jsplumb-graph-schema.config";
import { ActionsContextmenuComponent } from "../../../../../widget/fixed-dropdown/actions-contextmenu.component";
import { DropdownOptions } from "../../../../../widget/fixed-dropdown/dropdown-options";
import { FixedDropdownService } from "../../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { EditJoinEvent } from "../../../model/datasources/database/events/edit-join-event";
import { TableJoinInfo } from "../../../model/datasources/database/physical-model/graph/table-join-info";
import { JoinEditPaneModel } from "../../../model/datasources/database/physical-model/graph/join-edit-pane-model";
import { TableDetailJoinInfo } from "../../../model/datasources/database/physical-model/graph/table-detail-join-info";
import { GraphColumnInfo } from "../../../model/datasources/database/physical-model/graph/graph-column-info";
import { JoinModel } from "../../../model/datasources/database/physical-model/join-model";
import {
   joinMap, operatorToJoinTitle
} from "../../../model/datasources/database/physical-model/join-type.config";
import { EditJoinDialog } from "../database-physical-model/physical-model-edit-table/physical-table-joins/edit-join-dialog/edit-join-dialog.component";
import getOverlayLabel = JSPlumbDependencyTypeOverlays.getOverlayLabel;
import { JSPlumbUtil } from "../../../../../common/util/jsplumb-util";

const TABLE_COLUMN_SEPARATOR = "-";
const PHYSICAL_JOIN_URI = "../api/data/physicalmodel/join";
const PHYSICAL_DELETE_JOIN_URI = PHYSICAL_JOIN_URI + "/delete";
const QUERY_JOIN_URI = "../api/data/datasource/query/join";
const QUERY_DELETE_JOIN_URI = QUERY_JOIN_URI + "/delete";

const HEARTBEAT_DELAY_TIME: number = 0;
const HEARTBEAT_INTERVAL_TIME: number = 20000;

export enum DataType {
   PHYSICAL = "physical",
   QUERY = "query"
}

@Injectable()
export class JoinThumbnailService implements OnDestroy {
   private jsp: JSPlumb.JSPlumbInstance;
   private model: JoinEditPaneModel;

   private readonly connectingColumnSubject = new ReplaySubject<GraphColumnInfo>(1);
   readonly focusColumnPairSubject = new ReplaySubject<GraphColumnInfo[]>(1);
   readonly refreshGraph = new ReplaySubject<TableJoinInfo>(1);
   heartbeat: Observable<number> = observableTimer(HEARTBEAT_DELAY_TIME, HEARTBEAT_INTERVAL_TIME);

   private readonly tableColumns: {[sourceIds: string]: GraphColumnInfo} = {};
   private readonly sourceIds = new Map<string, string>(); // column info --> element id
   private _dataType: string = DataType.PHYSICAL;

   constructor(private readonly zone: NgZone,
               private readonly http: HttpClient,
               private readonly modalService: NgbModal,
               private readonly fixedDropdownService: FixedDropdownService)
   {
      this.zone.runOutsideAngular(() => {
         this.jsp = jspInitGraphSchema();

         this.jsp.bind("connectionDrag", (connection: any) => {
            const tableColumn = this.tableColumns[connection.sourceId];
            this.connectingColumnSubject.next(tableColumn);
         });

         this.jsp.bind("contextmenu", (info: any, event: MouseEvent) => {
            this.zone.run(() => {
               this.showJoinActions(event, info);
            });
         });

         this.jsp.bind("connectionAborted", (connection: any) => {
            this.connectingColumnSubject.next(null);
         });

         this.jsp.bind("connectionDetached", (connection: any) => {
            this.connectingColumnSubject.next(null);
         });

         // 1, can not connect column to self.
         // 2, can not connect table to self.
         this.jsp.bind("beforeDrop", (info: any) => {
            const sourceTableColumn: GraphColumnInfo = this.tableColumns[info.sourceId];
            const targetTableColumn: GraphColumnInfo = this.tableColumns[info.targetId];

            if(info.targetId === info.sourceId || !sourceTableColumn || !targetTableColumn ||
               sourceTableColumn.table === targetTableColumn.table)
            {
               ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)",
                  "_#(js:designer.binding.selfjoin)");

               return false;
            }

            if(!AssetUtil.isMergeable(sourceTableColumn.type, targetTableColumn.type)) {
               ComponentTool.showMessageDialog(this.modalService, "_#(js:Warning)",
                  "_#(js:data.query.graph.incompatibilityColumnType)");

               return false;
            }

            return true;
         });

         this.jsp.bind("connection", (info: any, originalEvent: any) => {
            if(!!!originalEvent) {
               // init connection.
               return;
            }

            const sourceTableColumn: GraphColumnInfo = this.tableColumns[info.sourceId];
            const targetTableColumn: GraphColumnInfo = this.tableColumns[info.targetId];

            // User dragged column connection
            if(info.connection.hasType(PHYSICAL_VIEW_TYPE_COLUMN)) {
               this.addNewJoinCondition(sourceTableColumn, targetTableColumn)
                  .subscribe((message) => {
                     this.refreshGraph.next({
                        sourceTable: sourceTableColumn.table,
                        targetTable: targetTableColumn.table,
                        runtimeId: this.model.runtimeID
                     });

                     this.jsp.deleteConnection(info.connection);
               }, (error) => {
                     // TODO show create join error message
                     console.log(error);
                  });
            }
         });
      });
   }

   ngOnDestroy(): void {
      this.stopHeartbeat();
   }

   setDataType(type: string) {
      this._dataType = type;
   }

   getDataType() {
      return this._dataType;
   }

   get joinUri(): string {
      return this._dataType === DataType.PHYSICAL ? PHYSICAL_JOIN_URI : QUERY_JOIN_URI;
   }

   get deleteJoinUri(): string {
      return this._dataType === DataType.PHYSICAL ? PHYSICAL_DELETE_JOIN_URI : QUERY_DELETE_JOIN_URI;
   }

   private addNewJoinCondition(sourceTableColumn: GraphColumnInfo,
                               targetTableColumn: GraphColumnInfo): Observable<string>
   {
      const joinInfo = {
            runtimeId: this.model.runtimeID,
            sourceTable: sourceTableColumn.table,
            targetTable: targetTableColumn.table,
            sourceColumn: sourceTableColumn.name,
            targetColumn: targetTableColumn.name
         };
      return this.http.post<string>(this.joinUri, joinInfo);
   }

   public get jsPlumbInstance(): JSPlumb.JSPlumbInstance {
      return this.jsp;
   }

   public setContainer(container: any): void {
      this.zone.runOutsideAngular(() => this.jsp.setContainer(container));
   }

   public getConnectingColumnSubject(): ReplaySubject<GraphColumnInfo> {
      return this.connectingColumnSubject;
   }

   public registerColumn(col: GraphColumnInfo, sourceId: string): void {
      this.sourceIds.set(col.id, sourceId);
      this.tableColumns[sourceId] = col;
   }

   public getSourceId(col: GraphColumnInfo): string {
      return this.sourceIds.get(col.id);
   }

   public initJoinConnections(): void {
      this.jsp.setSuspendDrawing(true);
      this.joinColumns();
      this.jsp.setSuspendDrawing(false, true);
   }

   private joinColumns(): void {
      let overlayProps = {
         label: getOverlayLabel(DependencyType.JOIN),
         id: "join",
         location: 0.5,
         cssClass: "dependency-type-overlay-container"
      };
      let overlays = [
         ["Label", overlayProps]
      ];

      this.model.tables.forEach(table => {
         table.joins.forEach(join => {
            const leftId = this.sourceIds
               .get(table.name + TABLE_COLUMN_SEPARATOR + join.column);
            const rightId = this.sourceIds
               .get(join.foreignTable + TABLE_COLUMN_SEPARATOR + join.foreignColumn);

            overlayProps.label = `<div class="overlay-operator"
                     title="${operatorToJoinTitle(join.type)}">${joinMap(join.type)}</div>`;

            this.connectColumns(leftId, rightId, overlays, join.weak, join.cycle);
         });
      });
   }

   private connectColumns(source: string, target: string,
                          overlays: (string | { label: string; id: string })[][],
                          weakJoin = false, cycle = false)
   {
      const connCol = {
         source,
         target,
         type: PHYSICAL_VIEW_TYPE_COLUMN,
         overlays: overlays,
         connector: ["Straight", {gap: 4, stub: 35}]
      };

      if(weakJoin) {
         JSPlumbUtil.makeWeakJoinConnection(connCol);
      }

      if(cycle) {
         JSPlumbUtil.makeCycleJoinConnection(connCol);
      }

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
            let sourceColumn = this.tableColumns[c.sourceId];
            let targetColumn = this.tableColumns[c.targetId];
            this.zone.run(() => this.focusColumnPairSubject.next([sourceColumn, targetColumn]));
         });
         c.bind("mouseout", () => {
            this.zone.run(() => this.focusColumnPairSubject.next(null));
         });
         c.bind("mouseup", (info: any, event: MouseEvent) => {
            if(event.button === 0 && !this.isBaseJoin(c)) { // left
               this.zone.run(() => {
                  this.showEditJoinPropertiesDialog(c);
               });
            }
         });
      }
   }

   private showEditJoinPropertiesDialog(connection: any): void {
      const sourceId = connection.sourceId;
      const targetId = connection.targetId;
      const sourceCol: GraphColumnInfo = this.tableColumns[sourceId];
      const targetCol: GraphColumnInfo = this.tableColumns[targetId];

      const table = this.model.tables.find(tableModel => tableModel.name === sourceCol.table);

      const join = table.joins.find(joinModel => joinModel.column === sourceCol.name
         && joinModel.foreignTable === targetCol.table
         && joinModel.foreignColumn === targetCol.name);

      if(!!!join) {
         return;
      }

      const dialog = ComponentTool.showDialog(this.modalService, EditJoinDialog,
         (joinModel: JoinModel) => {
            const joinInfo: TableDetailJoinInfo = {
               runtimeId: this.model.runtimeID,
               sourceTable: sourceCol.table,
               targetTable: targetCol.table,
               sourceColumn: sourceCol.name,
               targetColumn: targetCol.name
            };

            const nextInfo = {
               runtimeId: this.model.runtimeID,
               sourceTable: sourceCol.table,
               targetTable: targetCol.table,
            };

            if(joinModel.delete) {
               this.http.post(this.deleteJoinUri, joinInfo).subscribe(() => {
                  this.refreshGraph.next(nextInfo);
               });

               return;
            }

            const event: EditJoinEvent = {
               joinModel,
               detailJoinInfo: joinInfo
            };

            this.http.put(this.joinUri, event).subscribe(() => {
               this.refreshGraph.next(nextInfo);
            });
         });

      dialog.joinModel = Tool.clone(join);
      dialog.dataType = this.getDataType();
   }

   /**
    * Shows the join actions.
    */
   private showJoinActions(event: MouseEvent, connectionInfo: any): void {
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

      contextmenu.actions = this.getJoinActions(connectionInfo);
      contextmenu.sourceEvent = event;
   }

   private getJoinActions(connectionInfo: any): AssemblyActionGroup[] {
      const actions: AssemblyActionGroup[] = [];

      actions.push(new AssemblyActionGroup([
         {
            id: () => "physical-view remove-join-condition",
            label: () => "_#(js:Remove Join Condition)",
            icon: () => null,
            enabled: () => true,
            visible: () => !this.isBaseJoin(connectionInfo),
            action: () => this.removeJoinCondition(connectionInfo)
         }
      ]));

      return actions;
   }

   private removeJoinCondition(connectionInfo: any): void {
      if(!!!connectionInfo) {
         return;
      }

      const joinInfo = this.getCompositeInfo(connectionInfo);

      this.http.post(this.deleteJoinUri, joinInfo).subscribe(() => {
         this.refreshGraph.next(this.getCompositeInfo(connectionInfo, false));
      });
   }

   /**
    * Whether connection is base join
    * @param connectionInfo
    */
   private isBaseJoin(connectionInfo: any): boolean {
      const sourceCol: GraphColumnInfo = this.tableColumns[connectionInfo.sourceId];
      const targetCol: GraphColumnInfo = this.tableColumns[connectionInfo.targetId];

      if(!sourceCol || !targetCol) {
         return false;
      }

      let sourceTable = this.model.tables.find((table) =>
         table && table.name === sourceCol.table);

      if(sourceTable && sourceTable.joins) {
         let isBase = sourceTable.joins.some((join) => join &&
            join.foreignTable === targetCol.table && join.foreignColumn === targetCol.name &&
            join.table == sourceCol.table && join.column == sourceCol.name &&
            join.baseJoin);

         if(isBase) {
            return true;
         }
      }

      return false;
   }

   private getCompositeInfo(connection: any, isJoin = true): TableJoinInfo {
      const sourceId = connection.sourceId;
      const targetId = connection.targetId;
      const sourceCol: GraphColumnInfo = this.tableColumns[sourceId];
      const targetCol: GraphColumnInfo = this.tableColumns[targetId];

     return isJoin ? {
         runtimeId: this.model.runtimeID,
         sourceTable: sourceCol.table,
         targetTable: targetCol.table,
         sourceColumn: sourceCol.name,
         targetColumn: targetCol.name
      } as TableDetailJoinInfo
      : {
           runtimeId: this.model.runtimeID,
           sourceTable: sourceCol.table,
           targetTable: targetCol.table
     };
   }

   public unregisterColumn(col: GraphColumnInfo, sourceId: string): void {
      delete this.tableColumns[sourceId];
      this.sourceIds.delete(col.id);
   }

   public setJoinEditPaneModel(m: JoinEditPaneModel): void {
      this.model = m;
   }

   public getJoinEditPaneModel(): JoinEditPaneModel {
      return this.model;
   }

   public cleanup(): void {
      this.jsp.reset();
   }

   public clear(): void {
      this.connectingColumnSubject.next(null);
      this.focusColumnPairSubject.next(null);
      this.jsp.deleteEveryConnection();
   }

   private stopHeartbeat(): void {
      if(!!this.heartbeat) {
         this.heartbeat = null;
      }
   }
}
