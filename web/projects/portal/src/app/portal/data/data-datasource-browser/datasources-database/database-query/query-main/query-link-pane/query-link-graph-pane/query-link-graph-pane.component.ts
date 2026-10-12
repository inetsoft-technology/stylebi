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
import { LoadingIndicatorPaneComponent } from "../../../../common-components/loading-indicator-pane/loading-indicator-pane.component";
import { QueryJoinEditPane } from "../query-join-editor-pane/query-join-edit-pane.component";
import { QueryNetworkGraphPaneComponent } from "../query-network-graph-pane/query-network-graph-pane.component";
import {
   GraphNodeMove
} from "../../../../database-physical-model/physical-model-network-graph/physical-model-network-graph.component";


const CLOSE_JOIN_EDIT_PANE_URI = "../api/data/datasource/query/join-edit/close";
const QUERY_GRAPH_PANE_MODEL_URI = "../api/data/datasource/query/graph";

@Component({
    selector: "query-link-graph-pane",
    templateUrl: "./query-link-graph-pane.component.html",
    styleUrls: ["./query-link-graph-pane.component.scss"],
    imports: [QueryNetworkGraphPaneComponent, QueryJoinEditPane, LoadingIndicatorPaneComponent]
})
export class QueryLinkGraphPaneComponent implements OnInit, OnDestroy {
   @Input() datasource: string;
   @Input() runtimeId: string;
   @Input() selectedGraphNodePath: string;
   @Output() onNodeSelected: EventEmitter<string> = new EventEmitter<string>();
   @Output() onQueryPropertiesChanged: EventEmitter<void> = new EventEmitter<void>();
   @Output() editingJoinChanged = new EventEmitter<boolean>();

   graphPaneModel: JoinGraphModel;
   loadingGraphPane: boolean = true;
   option: string;
   scrollPoint: Point = new Point();
   private subscriptions: Subscription = new Subscription();
   // incremented when a graph request is sent
   private graphRequestSeq = 0;
   // the graphRequestSeq of the latest graph response applied
   private lastAppliedSeq = 0;
   // node id --> the latest move of the node, and the graphRequestSeq when its PUT was sent.
   // A graph request sent before then was built without the move.
   private movedNodes = new Map<string, {move: GraphNodeMove, sentAt: number}>();

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
      const requestSeq = ++this.graphRequestSeq;
      this.loadingGraphPane = true;

      this.httpClient.post<JoinGraphModel>(QUERY_GRAPH_PANE_MODEL_URI, event)
         .subscribe(pgm => {
            // the responses can arrive out of order: a response to an older request than the
            // one applied last has an older table set and positions, so it is discarded
            if(requestSeq < this.lastAppliedSeq) {
               return;
            }

            this.lastAppliedSeq = requestSeq;
            this.clearLoading(requestSeq);
            this.applyMovedNodes(pgm, requestSeq);
            this.restoreJoinEditPaneModel(pgm, this.graphPaneModel);
            this.restoreGraphViewModel(pgm, this.graphPaneModel);
            this.graphPaneModel = pgm;
            this.editingJoinChanged.emit(this.graphPaneModel.joinEdit);
         }, () => this.clearLoading(requestSeq));
   }

   /**
    * The loading mask stays until the latest graph request completes.
    */
   private clearLoading(requestSeq: number): void {
      if(requestSeq === this.graphRequestSeq) {
         this.loadingGraphPane = false;
      }
   }

   /**
    * Track a node move sent by the network graph, see applyMovedNodes.
    */
   nodeMoved(move: GraphNodeMove): void {
      if(move.saved == null) {
         this.movedNodes.set(move.nodeId, {move, sentAt: this.graphRequestSeq});
      }
      // the server kept the old position; unless a later move of the node replaced this one
      else if(!move.saved && this.movedNodes.get(move.nodeId)?.move === move) {
         this.movedNodes.delete(move.nodeId);
      }
   }

   /**
    * A graph request sent before a node move was sent (e.g. by a table drop the user started
    * just before dragging) returns the node's old position. It still has the change of the
    * action, so apply it, but with the moved position. A request sent after the move reaches
    * the server after it, so its response has the move or a later server position, and the
    * move is no longer tracked.
    */
   private applyMovedNodes(pgm: JoinGraphModel, requestSeq: number): void {
      this.movedNodes.forEach((entry, nodeId) => {
         if(requestSeq > entry.sentAt) {
            this.movedNodes.delete(nodeId);
            return;
         }

         const graph = pgm?.graphViewModel?.graphs?.find(g => g?.node?.id === nodeId);

         if(graph?.bounds) {
            graph.bounds.x = entry.move.bounds.x;
            graph.bounds.y = entry.move.bounds.y;
         }
      });
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
