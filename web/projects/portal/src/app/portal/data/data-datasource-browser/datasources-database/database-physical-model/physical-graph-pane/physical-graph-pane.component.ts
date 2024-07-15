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
import {
   Component, Input, Output, EventEmitter, OnDestroy, OnInit, HostListener, ViewChild, ElementRef,
   AfterViewChecked
} from "@angular/core";
import { fromEvent, Subscription } from "rxjs";
import { Tool } from "../../../../../../../../../shared/util/tool";
import { Rectangle } from "../../../../../../common/data/rectangle";
import { DebounceService } from "../../../../../../widget/services/debounce.service";
import { GetGraphModelEvent } from "../../../../model/datasources/database/events/get-graph-model-event";
import { GetModelEvent } from "../../../../model/datasources/database/events/get-model-event";
import { TableJoinInfo } from "../../../../model/datasources/database/physical-model/graph/table-join-info";
import { JoinGraphModel } from "../../../../model/datasources/database/physical-model/graph/join-graph-model";
import {
   DataPhysicalModelService, HighlightInfo
} from "../../../../services/data-physical-model.service";
import { ZoomOptions } from "../../../../../../vsobjects/model/layout/zoom-options";
import { GraphModel } from "../../../../model/datasources/database/physical-model/graph/graph-model";
import { Point } from "../../../../../../common/data/point";
import { GraphViewModel } from "../../../../model/datasources/database/physical-model/graph/graph-view-model";
import { GraphNodeModel } from "../../../../model/datasources/database/physical-model/graph/graph-node-model";

const CLOSE_JOIN_EDIT_PANE_URI = "../api/data/physicalmodel/join-edit/close";
const PHYSICAL_GRAPH_PANE_MODEL_URI = "../api/data/physicalmodel/graph";
const PHYSICAL_GRAPH_AUTO_LAYOUT_URI = "../api/data/physicalmodel/graph/layout/";
const UPDATE_GRAPH_PANE_SIZE = "../api/data/physicalmodel/graph/size/";

@Component({
   selector: "physical-graph-pane",
   templateUrl: "physical-graph-pane.component.html",
   styleUrls: ["physical-graph-pane.component.scss"]
})
export class PhysicalGraphPane implements OnInit, AfterViewChecked, OnDestroy {
   @Input() physicalView: string;
   @Input() datasource: string;
   @Input() runtimeId: string;
   @Input() selectedGraphNode: GraphNodeModel;

   @Output() onCreateAutoAlias = new EventEmitter<string>();
   @Output() onEditInlineView = new EventEmitter<string>();
   @Output() onJoinEditing: EventEmitter<boolean> = new EventEmitter<boolean>();
   @Output() onPhysicalGraph: EventEmitter<GraphViewModel> = new EventEmitter<GraphViewModel>();
   @Output() onModified: EventEmitter<boolean> = new EventEmitter<boolean>();
   @Output() onNodeSelected: EventEmitter<string[]> = new EventEmitter<string[]>();
   @Output() onRemoveTable: EventEmitter<GraphModel[]> = new EventEmitter<GraphModel[]>();
   @Output() onZoom: EventEmitter<number> = new EventEmitter<number>();

   @ViewChild("graphContainer") graphContainer: ElementRef<HTMLDivElement>;
   @ViewChild("visContainer") visContainer: ElementRef<HTMLDivElement>;

   physicalGraph: JoinGraphModel;
   private subscription: Subscription;
   public fullScreenView = false;
   public ZoomOptions = ZoomOptions;
   scale: number = 1.0;
   highlightConnections: HighlightInfo[] = null;
   scrollPoint: Point = new Point();
   loadingGraphPane: boolean = true;
   oldViewport: any;
   option: string;

   get modelInitializing(): boolean {
      return this.physicalModelService.loadingModel;
   }

   constructor(private httpClient: HttpClient,
               private debounceService: DebounceService,
               private physicalModelService: DataPhysicalModelService)
   {
      this.subscription = this.physicalModelService.modelChange.subscribe((graphTrigger) => {
         if(!graphTrigger) {
            this.refreshPhysicalGraphModel();
         }
      });

      this.subscription.add(this.physicalModelService.onHighlightConnections.subscribe(infos => {
         if(!Tool.isEquals(this.highlightConnections, infos)) {
            this.highlightConnections = infos;
            this.refreshPhysicalGraphModel();
         }
      }));
   }

   ngOnInit(): void {
      this.refreshPhysicalGraphModel();

      this.subscription.add(fromEvent(window, "resize")
         .subscribe((event) => {
            this.updateGraphPaneSize();
         }));
   }

   ngAfterViewChecked(): void {
      this.updateGraphPaneSize();
   }

   ngOnDestroy(): void {
      if(!!this.subscription) {
         this.subscription.unsubscribe();
         this.subscription = null;
      }

      // always close full screen
      this.physicalModelService.onFullScreen.next(false);
   }

   get viewport(): any {
      return {
         width: !!this.graphContainer
            ? this.graphContainer.nativeElement.clientWidth
            : 0,
         height: !!this.graphContainer
            ? this.graphContainer.nativeElement.clientHeight
            : 0
      };
   }

   private updateGraphPaneSize(): void {
      if(!!!this.graphContainer || Tool.isEquals(this.oldViewport, this.viewport)) {
         return;
      }

      const vp = Tool.clone(this.viewport);

      this.debounceService.debounce("update-graph-pane-size", () => {
         const bounds = new Rectangle(0, 0, vp.width, vp.height);

         this.httpClient.put(UPDATE_GRAPH_PANE_SIZE +
            Tool.encodeURIComponentExceptSlash(this.runtimeId), bounds)
            .subscribe(() => {
               this.oldViewport = vp;
            });
      }, 200, null);
   }

   public refreshPhysicalGraphModel(joinEditInfo: TableJoinInfo = null): void {
      let runtimeId = this.runtimeId;

      if(!!joinEditInfo) {
         runtimeId = joinEditInfo.runtimeId;
      }

      const event = new GetGraphModelEvent(this.datasource, runtimeId, this.physicalView,
         null, joinEditInfo);
      this.loadingGraphPane = true;

      this.httpClient.post<JoinGraphModel>(PHYSICAL_GRAPH_PANE_MODEL_URI, event)
         .subscribe(pgm => {
            this.loadingGraphPane = false;
            this.restoreJoinEditPaneModel(pgm, this.physicalGraph);
            this.restoreGraphViewModel(pgm, this.physicalGraph);
            this.physicalGraph = pgm;
            this.onPhysicalGraph.emit(pgm.graphViewModel);
            this.onJoinEditing.emit(pgm.joinEdit);

            if(pgm.joinEdit) {
               this.physicalModelService.refreshWarning(runtimeId);
            }
         }, () => this.loadingGraphPane = false);
   }

   private restoreGraphViewModel(newModel: JoinGraphModel,
                                 oldModel: JoinGraphModel): void
   {

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

   private restoreJoinEditPaneModel(newModel: JoinGraphModel,
                                    oldModel: JoinGraphModel): void
   {
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

   toggleFullScreen(): void {
      this.fullScreenView = !this.fullScreenView;
      this.physicalModelService.onFullScreen.next(this.fullScreenView);
   }

   zoomLayout(zoom: ZoomOptions): void {
      if(zoom == ZoomOptions.ZOOM_OUT) {
         this.zoom(true);
      }
      else if(zoom == ZoomOptions.ZOOM_IN) {
         this.zoom();
      }
      else {
         this.updateScale(zoom);
      }

      this.refreshPhysicalGraphModel();
   }

   zoom(zoomOut: boolean = false) {
      if((this.scale <= 0.2 && zoomOut) || (this.scale >= 2.0 && !zoomOut)) {
         return;
      }

      this.updateScale(Tool.numberCalculate(this.scale, 0.2, zoomOut));
   }

   updateScale(scale: number): void {
      this.scale = scale;
      this.onZoom.emit(scale);
   }

   isAutoLayoutSelected(option: string): boolean {
      return this.option == option;
   }

   isZoomItemSelected(zoom: ZoomOptions): boolean {
      return this.scale == zoom;
   }

   zoomOutEnabled(): boolean {
      return Number(this.scale.toFixed(2)) > 0.2 &&
         Number(this.scale.toFixed(2)) <= 2.0;
   }

   zoomInEnabled(): boolean {
      return Number(this.scale.toFixed(2)) >= 0.2 &&
         Number(this.scale.toFixed(2)) < 2.0;
   }

   @HostListener("keydown", ["$event"])
   keydown(event: KeyboardEvent): void {
      if(this.fullScreenView && ("Esc" == event.key || "Escape" == event.key)) {
         event.preventDefault();
         event.stopPropagation();

         this.toggleFullScreen();
      }
   }

   autoLayout(colPriority = false): void {
      colPriority ? this.option = "horizontal" : this.option = "vertical";
      this.graphContainer.nativeElement.scrollTop = 0;
      this.graphContainer.nativeElement.scrollLeft = 0;
      this.loadingGraphPane = true;
      const event = new GetModelEvent(this.datasource, this.physicalView);

      this.httpClient.put<number>(
         PHYSICAL_GRAPH_AUTO_LAYOUT_URI + colPriority + "/" + this.runtimeId, event)
         .subscribe(() => {
            this.refreshPhysicalGraphModel();
            this.onModified.emit(true);
      }, () => this.loadingGraphPane = false);
   }

   get editJoinRuntimeId(): string {
      return !!this.physicalGraph && !!this.physicalGraph.joinEditPaneModel
         ? this.physicalGraph.joinEditPaneModel.runtimeID
         : null;
   }

   closeJoinEditPane(save: boolean): void {
      let params = new HttpParams()
         .set("originRuntimeId", this.runtimeId)
         .set("newRuntimeId", this.editJoinRuntimeId)
         .set("save", save + "");

      this.httpClient.get(CLOSE_JOIN_EDIT_PANE_URI, { params }).subscribe(() => {
         this.refreshPhysicalGraphModel();
         this.physicalModelService.refreshWarning(this.runtimeId);

         if(save) {
            this.physicalModelService.emitModelChange(true);
            this.onModified.emit(true);
         }
      });
   }

   get fullScreenTooltip(): string {
      return this.fullScreenView ? "_#(js:Exit Full Screen)" : "_#(js:Toggle Full Screen)";
   }
}
