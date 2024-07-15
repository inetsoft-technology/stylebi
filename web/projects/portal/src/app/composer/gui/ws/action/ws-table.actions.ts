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
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable, Subject } from "rxjs";
import { FeatureFlagsService } from "../../../../../../../shared/feature-flags/feature-flags.service";
import { Tool } from "../../../../../../../shared/util/tool";
import { AssemblyActionGroup } from "../../../../common/action/assembly-action-group";
import { ModelService } from "../../../../widget/services/model.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { AbstractJoinTableAssembly } from "../../../data/ws/abstract-join-table-assembly";
import { AbstractTableAssembly } from "../../../data/ws/abstract-table-assembly";
import { ConcatenatedTableAssembly } from "../../../data/ws/concatenated-table-assembly";
import { QueryNode } from "../../../data/ws/query-node";
import { SQLBoundTableAssembly } from "../../../data/ws/sql-bound-table-assembly";
import { TablePropertyDialogModel } from "../../../data/ws/table-property-dialog-model";
import { Worksheet } from "../../../data/ws/worksheet";
import { QueryPlanDialog } from "../../../dialog/ws/query-plan-dialog.component";
import { TablePropertyDialog } from "../../../dialog/ws/table-property-dialog.component";
import { TableUnpivotDialog } from "../../../dialog/ws/table-unpivot-dialog.component";
import { WSAssemblyEvent } from "../socket/ws-assembly-event";
import { WSUnpivotDialogEvent } from "../socket/ws-unpivot-dialog-event";
import { WSAssemblyActions } from "./ws-assembly.actions";
import { UnpivotTableAssemblyInfo } from "../../../data/ws/unpivot-table-assembly-info";

const TABLE_PROPERTY_DIALOG_REST_URI = "../api/composer/ws/dialog/table-property-dialog-model/";
const QUERY_PLAN_DIALOG_REST_URI = "../api/composer/ws/dialog/show-plan/";
const ROTATE_SOCKET_URI = "/events/composer/worksheet/rotate";
const MIRROR_SOCKET_URI = "/events/composer/worksheet/mirror";
const CONVERT_TO_EMBEDDED_TABLE_SOCKET_URI = "/events/composer/worksheet/convert-embedded";

export class WSTableActions extends WSAssemblyActions {
   private readonly onEditCompositionTable = new Subject<AbstractTableAssembly>();
   private readonly onEditQuery = new Subject<AbstractTableAssembly>();

   constructor(private table: AbstractTableAssembly,
               worksheet: Worksheet,
               modalService: DialogService,
               private ngbModal: NgbModal,
               private modelService: ModelService,
               private sqlEnabled: boolean = true,
               private freeFormSqlEnabled: boolean = true,
               featureFlagsService: FeatureFlagsService)
   {
      super(table, worksheet, modalService, featureFlagsService);
   }

   public static getEditCompositionLabel(table: AbstractTableAssembly): string {
      if(table instanceof AbstractJoinTableAssembly) {
         return "_#(js:Edit Join)";
      }
      else if(table instanceof ConcatenatedTableAssembly) {
         return "_#(js:Edit Concatenation)";
      }

      return "_#(js:Edit)";
   }

   public get editCompositionTable(): Observable<AbstractTableAssembly> {
      return this.onEditCompositionTable.asObservable();
   }

   public get editQuery(): Observable<AbstractTableAssembly> {
      return this.onEditQuery.asObservable();
   }

   /** @inheritDoc */
   protected createMenuActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      const showPlanVisible = !this.table.isWSEmbeddedTable();
      const convertToEmbeddedVisible = !this.table.isWSEmbeddedTable() ||
         this.table.isSnapshotTable();

      groups.push(new AssemblyActionGroup([
         {
            id: () => "worksheet table edit",
            label: () => WSTableActions.getEditCompositionLabel(this.table),
            icon: () => "fa fa-slider",
            enabled: () => true,
            visible: () => this.isCompositeTable(),
            action: () => this.onEditCompositionTable.next(this.table)
         },
         {
            id: () => "worksheet table edit-query",
            label: () => "_#(js:Edit Query)",
            icon: () => "edit-icon",
            enabled: () => true,
            visible: () => this.isQueryTable(),
            action: () => this.onEditQuery.next(this.table)
         },
         {
            id: () => "worksheet table properties",
            label: () => "_#(js:Properties)...",
            icon: () => "fa fa-slider",
            enabled: () => true,
            visible: () => true,
            action: () => this.showPropertiesDialog()
         },
         {
            id: () => "worksheet table show-plan",
            label: () => "_#(js:Show Plan)...",
            icon: () => "fa fa-database",
            enabled: () => true,
            visible: () => showPlanVisible,
            action: () => this.showPlanDialog()
         },
         {
            id: () => "worksheet table update-mirror",
            label: () => "_#(js:Update Mirror)",
            icon: () => "fa fa-refresh",
            enabled: () => true,
            visible: () => super.updateMirrorVisible(),
            action: () => super.updateMirror()
         }
      ]));

      groups.push(new AssemblyActionGroup([
            {
               id: () => "worksheet table rotate",
               label: () => "_#(js:Rotate)",
               icon: () => "fa fa-rotate-right",
               enabled: () => true,
               visible: () => true,
               action: () => this.rotate()
            },
            {
               id: () => "worksheet table unpivot",
               label: () => "_#(js:Unpivot)...",
               icon: () => "fa fa-rotate-right",
               enabled: () => true,
               visible: () => true,
               action: () => this.unpivot()
            },
            {
               id: () => "edit worksheet table pivot level",
               label: () => "_#(js:Edit Pivot Level)...",
               icon: () => "fa fa-rotate-right",
               enabled: () => true,
               visible: () => this.isUnpivotTable(),
               action: () => this.unpivotLevel()
            },
            {
               id: () => "worksheet table mirror",
               label: () => "_#(js:Mirror)",
               icon: () => "fa fa-copy",
               enabled: () => true,
               visible: () => true,
               action: () => this.mirror()
            },
            {
               id: () => "worksheet table convert-to-embedded",
               label: () => "_#(js:Convert To Embedded Table)",
               icon: () => "fa fa-copy",
               enabled: () => true,
               visible: () => convertToEmbeddedVisible,
               action: () => this.convertToEmbeddedTable()
            },
         ]));

      return super.createMenuActions(groups);
   }

   private showPropertiesDialog(): void {
      const params = new HttpParams().set("table", this.table.name);

      this.modelService.getModel(TABLE_PROPERTY_DIALOG_REST_URI +
                                 Tool.byteEncode(this.worksheet.runtimeId), params)
         .subscribe((model: TablePropertyDialogModel) => {
               const dialog =
                  this.showDialog(TablePropertyDialog, {objectId: this.table.name},
                     (resolve) => {
                        if(resolve) {
                           this.worksheet.socketConnection
                              .sendEvent(resolve.controller, resolve.model);
                        }
                     },
                  );
               dialog.worksheet = this.worksheet;
               dialog.table = this.table;
               dialog.model = model;
            },
            () => {
               console.error("Error fetching table properties from the server");
            }
         );
   }

   private showPlanDialog(): void {
      const params = new HttpParams().set("table", Tool.byteEncode(this.table.name));

      this.modelService.getModel<QueryNode>(QUERY_PLAN_DIALOG_REST_URI + Tool.byteEncode(this.worksheet.runtimeId), params)
         .subscribe((data) => {
               const dialog = this.showDialog(QueryPlanDialog, {objectId: this.table.name});
               data.description = Tool.transformDate(data.description);
               dialog.focusedQueryNode = data;
            },
            () => {
               console.error("Could not fetch table plan.");
            }
         );
   }

   private rotate(): void {
      const event = new WSAssemblyEvent();
      event.setAssemblyName(this.table.name);
      this.worksheet.socketConnection.sendEvent(ROTATE_SOCKET_URI, event);
   }

   private unpivot(): void {
      this.showDialog(TableUnpivotDialog, {
            objectId: this.table.name,
            popup: true,
            backdrop: "static"
         },
         (resolve) => {
            if(resolve) {
               const event = new WSUnpivotDialogEvent();
               event.setAssemblyName(this.table.name);
               event.setModel(resolve.model);
               this.worksheet.socketConnection.sendEvent(resolve.controller, event);
            }
         });
   }

   private unpivotLevel(): void {
      let dialog = this.showDialog(TableUnpivotDialog, {
            objectId: this.table.name,
            popup: true,
            backdrop: "static"
         },
         (resolve) => {
            if(resolve) {
               const event = new WSUnpivotDialogEvent();
               event.setAssemblyName(this.table.name);
               event.setModel(resolve.model);
               this.worksheet.socketConnection.sendEvent(resolve.controller, event);
            }
         });

      dialog.edit = true;
      dialog.level = (<UnpivotTableAssemblyInfo> this.table.info).headerColumns;
   }

   private mirror(): void {
      const event = new WSAssemblyEvent();
      event.setAssemblyName(this.table.name);
      this.worksheet.socketConnection.sendEvent(MIRROR_SOCKET_URI, event);
   }

   private convertToEmbeddedTable(): void {
      const event = new WSAssemblyEvent();
      event.setAssemblyName(this.table.name);
      this.worksheet.socketConnection.sendEvent(CONVERT_TO_EMBEDDED_TABLE_SOCKET_URI, event);
   }

   private isCompositeTable(): boolean {
      return this.table.tableClassType === "RelationalJoinTableAssembly" ||
         this.table.tableClassType === "MergeJoinTableAssembly" ||
         this.table.tableClassType === "ConcatenatedTableAssembly";
   }

   private isQueryTable(): boolean {
      if(this.table.tableClassType === "SQLBoundTableAssembly") {
         if(!this.sqlEnabled) {
            return false;
         }

         return !((this.assembly as SQLBoundTableAssembly).sqlEdited && !this.freeFormSqlEnabled);
      }

      return this.table.tableClassType === "TabularTableAssembly";
   }

   private isUnpivotTable() {
      return this.table.tableClassType === "UnpivotTableAssembly";
   }
}
