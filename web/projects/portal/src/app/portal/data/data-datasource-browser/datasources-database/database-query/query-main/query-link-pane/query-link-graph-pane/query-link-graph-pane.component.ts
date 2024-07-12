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
import { HttpClient, HttpParams } from "@angular/common/http";
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from "@angular/core";
import { Subscription } from "rxjs";
import { Point } from "../../../../../../../../common/data/point";
import {
   GetGraphModelEvent
} from "../../../../../../model/datasources/database/events/get-graph-model-event";
import {
   JoinGraphModel
} from "../../../../../../model/datasources/database/physical-model/graph/join-graph-model";
import {
   TableJoinInfo
} from "../../../../../../model/datasources/database/physical-model/graph/table-join-info";
import { DataQueryModelService } from "../../../data-query-model.service";

const CLOSE_JOIN_EDIT_PANE_URI = "../api/data/datasource/query/join-edit/close";
const QUERY_GRAPH_PANE_MODEL_URI = "../api/data/datasource/query/graph";

@Component({
   selector: "query-link-graph-pane",
   templateUrl: "./query-link-graph-pane.component.html",
   styleUrls: ["./query-link-graph-pane.component.scss"]
})
export class QueryLinkGraphPaneComponent implements OnInit, OnDestroy {
   @Input() datasource: string;
   @Input() runtimeId: string;
   @Input() selectedGraphNodePath: string;
   @Output() onNodeSelected: EventEmitter<string> = new EventEmitter<string>();
   @Output() onQueryPropertiesChanged: EventEmitter<void> = new EventEmitter<void>();

   graphPaneModel: JoinGraphModel;
   loadingGraphPane: boolean = true;
   option: string;
   scrollPoint: Point = new Point();
   private subscriptions: Subscription = new Subscription();

   constructor(private httpClient: HttpClient,
               private queryModelService: DataQueryModelService)
   {
      this.subscriptions.add(this.queryModelService.graphViewChange.subscribe(() => {
         if(!!this.runtimeId) {
            this.onRefreshGraph();
         }
      }));
   }

   ngOnInit() {
      this.onRefreshGraph();
   }

   refresh() {
      this.onQueryPropertiesChanged.emit();
      this.onRefreshGraph();
   }

   onRefreshGraph(joinEditInfo: TableJoinInfo = null): void {
      let runtimeId = this.runtimeId;

      if(!!joinEditInfo) {
         runtimeId = joinEditInfo.runtimeId;
      }

      const event = new GetGraphModelEvent(this.datasource, runtimeId, null,
         null, joinEditInfo);
      this.loadingGraphPane = true;

      this.httpClient.post<JoinGraphModel>(QUERY_GRAPH_PANE_MODEL_URI, event)
         .subscribe(pgm => {
            this.loadingGraphPane = false;
            this.restoreJoinEditPaneModel(pgm, this.graphPaneModel);
            this.restoreGraphViewModel(pgm, this.graphPaneModel);
            this.graphPaneModel = pgm;
         }, () => this.loadingGraphPane = false);
   }

   restoreJoinEditPaneModel(newModel: JoinGraphModel, oldModel: JoinGraphModel): void {
      if(!!!oldModel || !oldModel.joinEdit || !newModel.joinEdit) {
         return;
      }

      // restore table bounds
      oldModel.joinEditPaneModel.tables.forEach((table) => {
         let findTable = newModel.joinEditPaneModel.tables.find((oldTable) => {
            return oldTable.name === table.name;
         });

         if(findTable) {
            findTable.bounds = table.bounds;
         }
      });
   }

   restoreGraphViewModel(newModel: JoinGraphModel, oldModel: JoinGraphModel): void {
      if(!!!oldModel || !oldModel.graphViewModel || !oldModel.graphViewModel.graphs ||
         !!!newModel || !newModel.graphViewModel || !newModel.graphViewModel.graphs)
      {
         return;
      }

      // restore table show columns status
      oldModel.graphViewModel.graphs.forEach((table) => {
         let findTable = newModel.graphViewModel.graphs.find((oldTable) => {
            return oldTable.node.id === table.node.id;
         });

         if(findTable) {
            findTable.showColumns = table.showColumns;
         }
      });
   }

   get editJoinRuntimeId(): string {
      return !!this.graphPaneModel && !!this.graphPaneModel.joinEditPaneModel ?
         this.graphPaneModel.joinEditPaneModel.runtimeID : null;
   }

   closeJoinEditPane(save: boolean): void {
      let params = new HttpParams()
         .set("originRuntimeId", this.runtimeId)
         .set("newRuntimeId", this.editJoinRuntimeId)
         .set("save", save + "");

      this.httpClient.get(CLOSE_JOIN_EDIT_PANE_URI, { params }).subscribe(() => {
         this.onRefreshGraph();
      });
   }

   get toolbarHeight(): number {
      return this.graphPaneModel.joinEdit ? 40 : 0;
   }

   get graphContainerHeight(): string {
      return this.graphPaneModel.joinEdit ? "calc(100% - 40px)" : "100%";
   }

   ngOnDestroy() {
      if(this.subscriptions) {
         this.subscriptions.unsubscribe();
         this.subscriptions = null;
      }
   }

   public isJoinEditView(): boolean {
      if(!!this.graphPaneModel) {
         return this.graphPaneModel.joinEdit;
      }

      return false;
   }
}
