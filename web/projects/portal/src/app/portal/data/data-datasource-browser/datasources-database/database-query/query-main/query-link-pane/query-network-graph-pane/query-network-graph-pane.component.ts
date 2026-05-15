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
import { DOCUMENT } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import {
   AfterViewChecked,
   AfterViewInit,
   Component,
   ElementRef,
   EventEmitter,
   HostListener,
   Inject,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   Renderer2,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ConnectionMadeEventInfo } from "jsplumb";
import { Observable } from "rxjs";
import { AssetEntry } from "../../../../../../../../../../../shared/data/asset-entry";
import { Tool } from "../../../../../../../../../../../shared/util/tool";
import { AssemblyActionGroup } from "../../../../../../../../common/action/assembly-action-group";
import { Point } from "../../../../../../../../common/data/point";
import { Rectangle } from "../../../../../../../../common/data/rectangle";
import { ComponentTool } from "../../../../../../../../common/util/component-tool";
import { JSPlumbUtil } from "../../../../../../../../common/util/jsplumb-util";
import {
   AssemblyDragScrollHandler
} from "../../../../../../../../composer/gui/ws/editor/assembly-drag-scroll-handler";
import {
   DEPENDENCY_TYPE_OVERLAY_ID
} from "../../../../../../../../composer/gui/ws/jsplumb/jsplumb-dependency-type-overlays";
import { SelectionBoxEvent } from "../../../../../../../../widget/directive/selection-box.directive";
import { DomService } from "../../../../../../../../widget/dom-service/dom.service";
import {
   ActionsContextmenuComponent
} from "../../../../../../../../widget/fixed-dropdown/actions-contextmenu.component";
import { DropdownOptions } from "../../../../../../../../widget/fixed-dropdown/dropdown-options";
import {
   FixedDropdownService
} from "../../../../../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DebounceService } from "../../../../../../../../widget/services/debounce.service";
import { DragService } from "../../../../../../../../widget/services/drag.service";
import {
   AddQueryTableEvent
} from "../../../../../../model/datasources/database/events/add-query-table-event";
import {
   MoveGraphEvent
} from "../../../../../../model/datasources/database/events/move-graph-event";
import {
   RemoveGraphTableEvent
} from "../../../../../../model/datasources/database/events/remove-graph-table-event";
import {
   GraphModel
} from "../../../../../../model/datasources/database/physical-model/graph/graph-model";
import {
   GraphNodeModel
} from "../../../../../../model/datasources/database/physical-model/graph/graph-node-model";
import {
   GraphViewModel
} from "../../../../../../model/datasources/database/physical-model/graph/graph-view-model";
import {
   NodeConnectionInfo
} from "../../../../../../model/datasources/database/physical-model/graph/node-connection-info";
import {
   TableJoinInfo
} from "../../../../../../model/datasources/database/physical-model/graph/table-join-info";
import {
   joinMap
} from "../../../../../../model/datasources/database/physical-model/join-type.config";
import {
   JOIN,
   jspInitGraphMain,
   PHYSICAL_ENDPOINTS,
   TYPE_PHYSICAL_GRAPH_CONNECTION,
   TYPE_PHYSICAL_GRAPH_REVERSE_ARROW
} from "../../../../../../model/datasources/database/physical-model/jsplumb-physical-graph.config";
import { HighlightInfo } from "../../../../../../services/data-physical-model.service";
import {
   DataType
} from "../../../../common-components/join-thumbnail.service";
import { DataQueryModelService } from "../../../data-query-model.service";

const OPEN_JOIN_EDIT_PANE_URI = "../api/data/datasource/query/join-edit/open/";
const GRAPH_CLEAR_JOINS_URI = "../api/data/datasource/query/joins/";
const GRAPH_DELETE_JOINS_URI = "../api/data/datasource/query/joins/delete";
const GRAPH_CLEAR_TABLES_URI = "../api/data/datasource/query/table/";
const GRAPH_ADD_TABLES_URI = "../api/data/datasource/query/table/add";
const GRAPH_REMOVE_TABLES_URI = "../api/data/datasource/query/table/remove";
const GRAPH_MOVE_TABLES_URI = "../api/data/datasource/query/table/move";

@Component({
   selector: "query-network-graph-pane",
   templateUrl: "./query-network-graph-pane.component.html",
   styleUrls: [
      "./query-network-graph-pane.component.scss",
      "../../../../../../../../composer/gui/ws/jsplumb/jsplumb-shared.scss"
   ]
})
export class QueryNetworkGraphPaneComponent implements OnInit, AfterViewInit,
   AfterViewChecked, OnChanges, OnDestroy
{
   @Input() runtimeId: string;
   @Input() graphViewModel: GraphViewModel;
   @Input() highlightConnections: HighlightInfo[];
   @Input() scrollPoint: Point = new Point();
   @Input() selectedGraphNodePath: string;
   @Output() onRefreshGraph = new EventEmitter<TableJoinInfo>();
   @Output() onNodeSelected: EventEmitter<string> = new EventEmitter<string>();
   @Output() onRemoveTable: EventEmitter<GraphModel[]> = new EventEmitter<GraphModel[]>();
   @Output() onQueryPropertiesChanged = new EventEmitter<any>();
   @ViewChild("jspContainerMain") jspContainerMain: ElementRef<HTMLDivElement>;
   @ViewChild("graphPane") graphPane: ElementRef<HTMLDivElement>;

   jsp: JSPlumb.JSPlumbInstance;
   readonly dataType = DataType.QUERY;
   private readonly dsHandler: AssemblyDragScrollHandler;
   private readonly dragScrollMargin = 15;
   private readonly dragScrollRate = 20;
   private repaintTimeout: any;
   private isDragging: boolean = false; // drag endpoint
   private nodeMoving = false; // drag node
   private dragNodes: GraphModel[] = [];
   private nodes: {[sourceIds: string]: GraphModel} = {}; // element id --> node model
   private sourceIds: {[nodeId: string]: string} = {}; // node id --> element id

   constructor(private readonly zone: NgZone,
               private readonly http: HttpClient,
               private readonly renderer: Renderer2,
               private readonly modalService: NgbModal,
               private debounceService: DebounceService,
               private domService: DomService,
               @Inject(DOCUMENT) private document: Document,
               private queryModelService: DataQueryModelService,
               private readonly fixedDropdownService: FixedDropdownService,
               private dragService: DragService)
   {
      this.zone.runOutsideAngular(() => {
         this.jsp = jspInitGraphMain();

         this.zone.run(() => {
            this.jsp.bind("connectionDrag", function(connection: any): void {
               this.isDragging = true;

               if(connection.scope === JOIN) {
                  // show all anchors
                  this.jsp.selectEndpoints({scope: JOIN}).setVisible(true, true, true);
                  this.checkJoinCompatibility(this.nodes[connection.sourceId].node);
               }
               else {
                  for(let id in this.dragNodes) {
                     if(id === connection.targetId) {
                        continue;
                     }

                     if(!this.jsp.isSource(id)) {
                        this.jsp.makeSource(id);
                     }
                  }
               }
            }.bind(this));

            this.jsp.bind("contextmenu", (connectInfo: any, originalEvent: MouseEvent): void => {
               this.zone.run(() => {
                  this.showJoinActions(originalEvent, connectInfo);
               });
            });

            this.jsp.bind("mousedown", (info: any, originalEvent: MouseEvent): void => {
               if(this.isDragging || !!!originalEvent || originalEvent.button !== 0) {
                  return;
               }

               const sourceId = info.sourceId;
               const targetId = info.targetId;
               const source = this.nodes[sourceId];
               const target = this.nodes[targetId];

               this.openJoinEditPane().subscribe((newRuntimeId) => {
                  const joinEditInfo: TableJoinInfo = {
                     sourceTable: source.node.id,
                     targetTable: target.node.id,
                     runtimeId: newRuntimeId
                  };
                  this.onRefreshGraph.emit(joinEditInfo);
               });
            });

            this.jsp.bind("connectionAborted", function(connection: any): void {
               this.isDragging = false;

               this.refreshAnchors(connection);
            }.bind(this));

            // should not create join when source and target is one table in design mode.
            this.jsp.bind("beforeDrop", (info: ConnectionMadeEventInfo) => {
               const sourceId = info.sourceId;
               const targetId = info.targetId;
               const source = this.nodes[sourceId];
               const target = this.nodes[targetId];

               if(!source || !target) {
                  return false;
               }

               if(source.autoAlias && target.autoAlias &&
                  source.node.aliasSource === target.node.aliasSource)
               {
                  return false;
               }

               if(source.autoAliasByOutgoing && target.autoAliasByOutgoing &&
                  source.node.outgoingAliasSource === source.node.outgoingAliasSource)
               {
                  return false;
               }

               return true;
            });

            this.jsp.bind("connection", (info: ConnectionMadeEventInfo, originalEvent: Event) => {
               if(originalEvent == undefined) {
                  // init connection.
                  return;
               }

               this.isDragging = false;
               const sourceId = info.sourceId;
               const targetId = info.targetId;
               const source = this.nodes[sourceId];
               const target = this.nodes[targetId];

               this.openJoinEditPane().subscribe((newRuntimeId) => {
                  const joinEditInfo: TableJoinInfo = {
                     sourceTable: source.node.id,
                     targetTable: target.node.id,
                     runtimeId: newRuntimeId,
                     autoCreateColumnJoin: true
                  };
                  this.onRefreshGraph.emit(joinEditInfo);
                  this.refreshAnchors(info.connection);
                  // Delete the connection and switch to join edit page,
                  // because whether to create a join is
                  // determined by `Done` or `Cancel` of the join edit pane.
                  this.jsp.deleteConnection(info.connection);
               });
            });
         });
      });

      this.dsHandler = new AssemblyDragScrollHandler(this.dragScrollMargin, this.dragScrollRate);
      this.dsHandler.renderer = renderer;
   }

   ngOnInit(): void {
      this.setRepaintTimer(); // waiting jsPlumb auto setting graph id.
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.hasOwnProperty("graphViewModel")) {
         this.sourceIds = {};
         this.nodes = {};
         this.jsp.deleteEveryConnection();
         this.jsp.deleteEveryEndpoint();
         this.updateUnjoinedTables();
      }
   }

   ngAfterViewChecked(): void {
      if(this.jsp.isSuspendDrawing()) {
         this.jsp.setSuspendDrawing(false, true);
      }
   }

   ngAfterViewInit(): void {
      if(this.jspContainerMain) {
         this.setContainer(this.jspContainerMain.nativeElement);
      }

      if(this.graphPane) {
         this.graphPane.nativeElement.scrollTo(this.scrollPoint.x, this.scrollPoint.y);
      }
   }

   ngOnDestroy(): void {
      this.jsp.deleteEveryConnection({fireEvent: false});
      this.jsp.reset();
      this.jsp = null;
   }

   scrollPosition() {
      this.domService.requestRead(() => {
         this.scrollPoint.x = this.graphPane ? this.graphPane.nativeElement.scrollLeft : 0;
         this.scrollPoint.y = this.graphPane ? this.graphPane.nativeElement.scrollTop : 0;
      });

      this.dsHandler.onScroll();
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
            visible: () => this.connectionDeletable(connectionInfo),
            action: () => this.removeJoinCondition(connectionInfo)
         }
      ]));

      return actions;
   }

   /**
    *  Whether connection can be deleted.
    * @param connectionInfo
    */
   private connectionDeletable(connectionInfo: any): boolean {
      const sourceNode = this.nodes[connectionInfo.sourceId];
      const targetNode = this.nodes[connectionInfo.targetId];

      if(sourceNode && sourceNode.node && targetNode && targetNode.node) {
         let sourceId = sourceNode.node.id;
         let targetId = targetNode.node.id;

         if(this.hasBaseJoin(sourceNode.edge.input, sourceId, targetId) ||
            this.hasBaseJoin(sourceNode.edge.output, sourceId, targetId) ||
            this.hasBaseJoin(targetNode.edge.input, sourceId, targetId) ||
            this.hasBaseJoin(targetNode.edge.output, sourceId, targetId))
         {
            return false;
         }
      }

      return true;
   }

   /**
    * Whether have base join.
    * @param conInfos
    * @param checkId
    */
   private hasBaseJoin(conInfos: NodeConnectionInfo[], table1: string, table2: string): boolean {
      if(!conInfos || !table1 || !table2) {
         return false;
      }

      for(let con of conInfos){
         if(!con) {
            continue;
         }

         if((con.id === table1 && con.joinModel && con.joinModel.foreignTable === table2) ||
            (con.id === table2 && con.joinModel && con.joinModel.foreignTable === table1))
         {
            if(con.joinModel.baseJoin) {
               return true;
            }
         }
      }

      return false;
   }

   private removeJoinCondition(connectionInfo: any): void {
      if(!!!connectionInfo) {
         return;
      }

      const sourceId = connectionInfo.sourceId;
      const targetId = connectionInfo.targetId;
      const sourceNode = this.nodes[sourceId];
      const targetNode = this.nodes[targetId];

      const info: TableJoinInfo = {
         runtimeId: this.runtimeId,
         sourceTable: sourceNode.node.name,
         targetTable: targetNode.node.name
      };

      this.http.post(GRAPH_DELETE_JOINS_URI, info).subscribe(() => {
         this.onRefreshGraph.emit();
         this.queryModelService.emitModelChange();
      });
   }

   private deleteNode(sourceId: string) {
      let conns = <any[]>this.jsp.getConnections({target: sourceId});

      for (let conn of conns) {
         this.jsp.deleteConnection(conn);
      }
   }

   private openJoinEditPane(): Observable<string> {
      return this.http.get<string>(OPEN_JOIN_EDIT_PANE_URI
         + Tool.encodeURIComponentExceptSlash(this.runtimeId));
   }

   private refreshAnchors(connection: any): void {
      // hidden all anchors
      // this.jsp.selectEndpoints({scope: connection.scope})
      //    .setVisible(false, true, true);

      // show self anchor
      this.jsp.selectEndpoints({source: connection.sourceId})
         .setVisible(true, true, true);
      this.jsp.unmakeEverySource();
   }

   private setRepaintTimer(): void {
      this.zone.runOutsideAngular(() => {
         if(this.repaintTimeout == null) {
            this.repaintTimeout = setTimeout(() => {
               this.repaintTimeout = null;

               if(this.jsp.isSuspendDrawing()) {
                  this.jsp.setSuspendDrawing(false, true);
               }
            });
         }
      });
   }

   private setContainer(container: any): void {
      this.zone.runOutsideAngular(() => this.jsp.setContainer(container));
      this.dsHandler.container = container;
   }

   private checkJoinCompatibility(sourceTable: GraphNodeModel): void {
      this.jsp.selectEndpoints({scope: JOIN}).setVisible(true, true, true);

      for(const sourceId of Object.keys(this.nodes)) {
         let table = this.nodes[sourceId].node;

         if(!(table.id === sourceTable.id)) {
            return;
         }
      }
   }

   public get graphEndpoints(): Object[] {
      return PHYSICAL_ENDPOINTS;
   }

   private refreshDragSelection(): void {
      this.jsp.clearDragSelection();
      const elements: string[] = [];

      this.dragNodes
         .forEach((graph, index) => {
            const id = graph.node.id;
            const elemId = this.sourceIds[id];
            const g = this.nodes[elemId];

            if(g === graph) {
               elements.push(elemId);
            }
            else {
               this.dragNodes.splice(index, 1);
            }
         });

      this.jsp.addToDragSelection(elements);
   }

   addEndpoint(element: any, endpoint: any): void {
      this.jsp.addEndpoint(element, endpoint).setVisible(true, true, true);
   }

   showEndpoints(params: {element: HTMLElement, [prop: string]: any}): void {
      params.scope = [JOIN];
      const endpoints = this.jsp.selectEndpoints(params);

      endpoints.each(function(endpoint): void {
         if(endpoint.scope === JOIN) {
            endpoint.canvas.title = "_#(js:common.worksheet.graph.join)";
         }
      });

      if(endpoints.length > 0 && !endpoints.get(0).isVisible()) {
         endpoints.setVisible(true, true, true);
      }
   }

   hideEndpoints(params: {element: HTMLElement, [prop: string]: any}): void {
      // params.scope = [JOIN];
      // const endpoints = this.jsp.selectEndpoints(params);
      //endpoints.setVisible(false, true, true);
   }

   /**
    * jsPlumb will setting element id if element without id attribute
    */
   setDraggable(element: any, options: any): void {
      const oldStart = options.start;
      options.start = (params) => {
         this.zone.run(() => {
            this.nodeMoving = true;
         });

         oldStart(params);
         this.dsHandler.addElement({el: params.el});

         if(params.e != null) {
            this.dsHandler.addScrollCallback(() => this.jsp.repaintEverything());
         }
      };

      options.drag = (params: any) => {
         this.zone.run(() => {
            this.nodeMoving = true;
         });

         const event: MouseEvent = params.e;

         if(event != null) {
            this.dsHandler.onDrag(event);
         }
      };

      const oldStop = options.stop;
      options.stop = (params: any) => {
         // this.zone.run(() => {
         //    this.nodeMoving = false;
         // });

         oldStop(params);
         this.dsHandler.reset();
      };

      // Limit the area where graph node can be dragged.
      options.containment = true;

      this.zone.runOutsideAngular(() => this.jsp.draggable(element, options));
   }

   moveNodes(offset: Point): void {
      this.debounceService.debounce("physical-model-graph-move-node", () => {
         this.dragNodes.forEach((graph) => {
            const id = this.sourceIds[graph.node.id];
            const el = this.document.getElementById(id);

            if(el != null) {
               const left = Math.max(graph.bounds.x + offset.x, 0);
               const top = Math.max(graph.bounds.y + offset.y, 0);
               graph.bounds.x = left;
               graph.bounds.y = top;

               let event = new MoveGraphEvent(this.runtimeId, graph.node.name,
                  null, graph.bounds);
               // save position to server
               this.http.put(GRAPH_MOVE_TABLES_URI, event).subscribe(() => {});

               this.renderer.setStyle(el, "left", left + "px");
               this.renderer.setStyle(el, "top", top + "px");
            }
         });
      }, 200, null);
   }

   public registerNode(graph: GraphModel, element: any): void {
      let sourceId = element.id;
      this.nodes[sourceId] = graph;
      this.sourceIds[graph.node.id] = sourceId;
      const edge = graph.edge;

      edge.input.forEach(inputInfo => {
         this.connectNode(inputInfo.id, inputInfo.joinModel.foreignTable, inputInfo, edge.input);
      });

      edge.output.forEach(outputInfo => {
         this.connectNode(outputInfo.id, outputInfo.joinModel.foreignTable, outputInfo, edge.output);
      });

      this.refreshDragSelection();
   }

   selectNode(event: MouseEvent, graph: GraphModel) {
      if(this.dragNodes.some(g => g.node.id === graph.node.id)) {
         // missing id or already selected
         return;
      }
      else if(event.ctrlKey || event.shiftKey) {
         this.dragNodes.push(graph);
      }
      else {
         this.dragNodes = [graph];
      }

      this.fireSelectedNodesChanged();
      this.refreshDragSelection();
   }

   private fireSelectedNodesChanged(): void {
      this.selectedGraphNodePath = null;
      let selectedPath: string;

      if(this.dragNodes && this.dragNodes.length > 0) {
         selectedPath = this.dragNodes[this.dragNodes.length - 1].node.treeLink;
      }

      if(!!selectedPath) {
         this.onNodeSelected.emit(selectedPath);
      }
   }

   @HostListener("contextmenu", ["$event"])
   contextMenu(event: MouseEvent): void {
      event.preventDefault();
      event.stopPropagation();

      const options: DropdownOptions = {
         position: {x: event.clientX, y: event.clientY},
         contextmenu: true,
         autoClose: true,
         closeOnOutsideClick: true,
         closeOnWindowResize: true
      };

      const component: ActionsContextmenuComponent =
         this.fixedDropdownService.open(ActionsContextmenuComponent, options).componentInstance;
      component.sourceEvent = event;
      component.actions = this.createActions();
   }

   private createActions(): AssemblyActionGroup[] {
      return [
         new AssemblyActionGroup([
            {
               id: () => "physical-view clear table",
               label: () => "_#(js:Clear Table)",
               icon: () => null,
               enabled: () => this.clearTableEnabled,
               visible: () => true,
               action: () => this.clearTable()
            },
            {
               id: () => "physical-view clear join",
               label: () => "_#(js:Clear Join)",
               icon: () => null,
               enabled: () => this.clearJoinEnabled,
               visible: () => true,
               action: () => this.clearJoin()
            }
         ])
      ];
   }

   get clearTableEnabled(): boolean {
      return this.graphViewModel.graphs.length > 0;
   }

   get clearJoinEnabled(): boolean {
      return this.graphViewModel.graphs
         .some(graph => graph.edge.input.length > 0 || graph.edge.output.length > 0);
   }

   private clearTable(): void {
      this.http.delete(GRAPH_CLEAR_TABLES_URI + Tool.encodeURIComponentExceptSlash(this.runtimeId))
         .subscribe(() => {
            this.onRefreshGraph.emit();
            this.queryModelService.emitModelChange();
         });
   }

   private clearJoin(): void {
      this.http.delete(GRAPH_CLEAR_JOINS_URI + Tool.encodeURIComponentExceptSlash(this.runtimeId))
         .subscribe(() => {
            this.onRefreshGraph.emit();
            this.queryModelService.emitModelChange();
         });
   }

   @HostListener("keydown", ["$event"])
   keydown(event: KeyboardEvent): void {
      if(event.ctrlKey && event.key == "a") {
         event.preventDefault();
         event.stopPropagation();
         this.selectAll();
      }
      else if(event.key == "Delete") {
         event.preventDefault();
         event.stopPropagation();

         this.removeSelectTables();
      }

      // Don't stop propagation anything
   }

   removeSelectTables(): void {
      const tables = [];
      let showConfirm = false;

      this.dragNodes.filter(node => node && !node.baseTable)
         .forEach(node => {
            if(node.edge.output.length > 0 || node.edge.input.length > 0) {
               showConfirm = true;
            }

            tables.push({
               tableName: node.node.tableName,
               fullName: node.node.name
            });
         });

      const removeTableEvent: RemoveGraphTableEvent = {
         runtimeId: this.runtimeId,
         tables
      };

      if(showConfirm) {
         const message: string = "_#(js:data.physicalmodel.confirmRemoveTable)";
         ComponentTool.showConfirmDialog(this.modalService, "_#(js:Warning)", message)
            .then((result) => {
               if("ok" === result) {
                  this.doRemove(removeTableEvent);
               }
            });
      }
      else {
         this.doRemove(removeTableEvent);
      }
   }

   doRemove(event: RemoveGraphTableEvent) {
      this.http.post(GRAPH_REMOVE_TABLES_URI, event).subscribe(() => {
         this.onRefreshGraph.emit();
         this.queryModelService.emitModelChange();
         this.onRemoveTable.emit(this.dragNodes);
         this.dragNodes = [];
         this.fireSelectedNodesChanged();
      });
   }

   selectAll(): void {
      this.dragNodes = this.graphViewModel.graphs;
      this.fireSelectedNodesChanged();
      this.refreshDragSelection();
   }

   onSelectionBox(event: SelectionBoxEvent): void {
      this.dragNodes = this.graphViewModel.graphs.filter((graph) => {
         return new Rectangle(graph.bounds.x, graph.bounds.y,
            graph.bounds.width, graph.bounds.height)
            .intersects(event.box);
      });

      this.fireSelectedNodesChanged();
      this.refreshDragSelection();
   }

   clearSelection(event: MouseEvent): void {
      this.dragNodes = [];
      this.fireSelectedNodesChanged();
      this.jsp.setSuspendDrawing(false, true);
   }

   getThumbnailClasses(graph: GraphModel): {[className: string]: boolean} {
      const containsNode = this.dragNodes.some(n => n.node.id === graph.node.id);

      return {
         "ws-assembly-graph-element--selected": containsNode,
         "ws-assembly-graph-element--dimmed": this.nodeMoving && !containsNode
      };
   }

   private connectNode(sourceTableName: string, targetTableName: string,
                       joinInfo: NodeConnectionInfo, joinInfos: NodeConnectionInfo[]): void
   {
      const sourceId = this.sourceIds[sourceTableName];
      const targetId = this.sourceIds[targetTableName];

      if(!!!sourceId || !!!targetId) {
         // if source node or target node has not been registed
         return;
      }

      if(this.jsp.getAllConnections()
         .some(conn => conn.sourceId === sourceId && conn.targetId === targetId
            || conn.sourceId === targetId && conn.targetId === sourceId))
      {
         // has connected
         return;
      }

      if(!joinInfo || !joinInfo.joinModel || !this.isColumnExist(sourceTableName, joinInfo.joinModel.column) ||
         !this.isColumnExist(targetTableName, joinInfo.joinModel.foreignColumn))
      {
         return;
      }

      const tooltip = this.getJoinTooltip(sourceId, targetId);

      const overlays = [[
         "Label", {
            // label becomes innerhtml of overlay
            label: `<div class="join-icon cursor-pointer icon-size-medium"
                      title="${tooltip}"></div>`,
            id: DEPENDENCY_TYPE_OVERLAY_ID,
            cssClass: "physical-graph-type-overlay-container"
         }
      ]];

      const connection = {
         source: sourceId,
         target: targetId,
         type: TYPE_PHYSICAL_GRAPH_CONNECTION,
         overlays
      };

      const sourceNode = this.nodes[sourceId];
      const targetNode = this.nodes[targetId];

      // Highlight Join Connection
      if(this.isHighlight(this.highlightConnections, sourceNode, targetNode)) {
         JSPlumbUtil.makeHighlightJoinConnection(connection);
      }

      const cycle = joinInfos.filter(connInfo => connInfo.id === sourceTableName
         && connInfo.joinModel.foreignTable === targetTableName
         || connInfo.id === targetTableName
         && connInfo.joinModel.foreignTable === sourceTableName)
         .map(connInfo => connInfo.joinModel)
         .some(joinModel => joinModel.cycle);

      if(cycle) {
         JSPlumbUtil.makeCycleJoinConnection(connection);
      }

      const conns = sourceNode.edge.output.filter(outJoin => outJoin.id === sourceTableName
         && outJoin.joinModel.foreignTable === targetTableName);

      // use dash line connect node only some connections is weak
      if(!!conns && conns.length > 0 && conns.some(connInfo => connInfo.joinModel.weak)) {
         JSPlumbUtil.makeWeakJoinConnection(connection);
      }

      // Use double-headed arrow indicates that both nodes are each other's targets:
      // A-->B and B-->A
      if(targetNode.edge.output
         .some((join) => join.joinModel.foreignTable === sourceTableName))
      {
         connection.type += ` ${TYPE_PHYSICAL_GRAPH_REVERSE_ARROW}`;
      }

      this.jsp.connect(connection);
   }

   private isColumnExist(tableName: string, column: string): boolean {
      if(!this.nodes) {
         return false;
      }

      for(let key of Object.keys(this.nodes)) {
         let node = this.nodes[key];

         if(!!node && !!node.node && node.node.name === tableName) {
            if(!node.cols || node.cols.length == 0) {
               return false;
            }

            for(let col of node.cols) {
               if(!!col && col.name == column) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private isHighlight(infos: HighlightInfo[], sourceNode: GraphModel,
                       targetNode: GraphModel): boolean
   {
      return infos?.some(info => {
         if(!!!info || !!!info.sourceTable || !!!info.targetTable) {
            return false;
         }

         let sourceNodeName = sourceNode.node.name;
         let targetNodeName = targetNode.node.name;

         if(sourceNode.autoAlias || sourceNode.autoAliasByOutgoing) {
            sourceNodeName = sourceNode.autoAlias ? sourceNode.node.aliasSource :
               sourceNode.node.outgoingAliasSource;
         }

         if(targetNode.autoAlias || targetNode.autoAliasByOutgoing) {
            targetNodeName = targetNode.autoAlias ? targetNode.node.aliasSource :
               targetNode.node.outgoingAliasSource;
         }

         return info.sourceTable === sourceNodeName && info.targetTable === targetNodeName
            || info.targetTable === sourceNodeName && info.sourceTable === targetNodeName;
      });
   }

   private getJoinTooltip(sourceId: string, targetId: string): string {
      const sourceNode: GraphModel = this.nodes[sourceId];
      const targetNode: GraphModel = this.nodes[targetId];
      let tooltip: string = null;
      let singleJoin = "";

      sourceNode.edge.output.map(joinInfo => joinInfo.joinModel).forEach(join => {
         if(join.foreignTable == targetNode.node.name) {
            singleJoin = sourceNode.node.name + "." + join.column
               + " " + joinMap(join.type) + " "
               + targetNode.node.name + "." + join.foreignColumn;
            tooltip = !!tooltip ? tooltip + "\n" + singleJoin : singleJoin;
         }
      });

      targetNode.edge.output.map(joinInfo => joinInfo.joinModel).forEach(join => {
         if(join.foreignTable == sourceNode.node.name) {
            singleJoin = targetNode.node.name + "." + join.column
               + " " + joinMap(join.type) + " "
               + sourceNode.node.name + "." + join.foreignColumn;
            tooltip = !!tooltip ? tooltip + "\n" + singleJoin : singleJoin;
         }
      });

      if(!!tooltip) {
         tooltip = this.convertToHTMLCharacterEntity(tooltip);
      }

      return tooltip;
   }

   /**
    * Encode the ", <, >, ' to html entity.
    * @param str
    */
   private convertToHTMLCharacterEntity(str: string): string {
      let ret: string = "";

      for(let ch of str) {
         if(ch == "<") {
            ch = "&lt;";
         }
         else if(ch == ">") {
            ch = "&gt;";
         }
         else if(ch == "\"") {
            ch = "&quot;";
         }
         else if(ch == "'") {
            ch = "&apos;";
         }

         ret += ch;
      }

      return ret;
   }

   getSelectedNode(currentNode: GraphNodeModel): boolean {
      if(this.selectedGraphNodePath) {
         return currentNode.treeLink === this.selectedGraphNodePath;
      }

      return this.dragNodes.some(node => node.node.id === currentNode.id);
   }

   drop(event: DragEvent): void {
      event.preventDefault();
      event.stopPropagation();

      this.addTables({
         position: {
            x: event.offsetX,
            y: event.offsetY
         },
         data: this.dragService.getDragDataValues(event)[0]
      });
   }

   addTables(payload: any): void {
      const position = payload.position;
      const selectedNodesData = payload.data;

      if(!!selectedNodesData && selectedNodesData.length > 0) {
         const tables: AssetEntry[] = selectedNodesData;
         const event: AddQueryTableEvent = {
            id: this.runtimeId,
            tables: tables,
            position: position
         };
         this.http.post(GRAPH_ADD_TABLES_URI, event)
            .subscribe(() => {
               this.onRefreshGraph.emit();
               this.queryModelService.emitModelChange();
            });
      }
   }

   isDuplicateTableAlias = (graphNode: GraphNodeModel, alias: string) => {
      if(!!this.graphViewModel && !!this.graphViewModel.graphs && !!graphNode) {
         return this.graphViewModel.graphs
            .filter(graph => graph.node.aliasSource != graphNode.aliasSource ||
               graph.node.name != graphNode.name)
            .some(graph => graph.node.name == alias);
      }

      return true;
   };

   updateUnjoinedTables(): void {
      this.queryModelService.setUnjoinedTables(this.getUnjoinedTables());
   }

   getUnjoinedTables(): string[] {
      if(this.graphViewModel?.graphs) {
         if(this.graphViewModel.graphs.length <= 1) {
            return [];
         }

         return this.graphViewModel.graphs
            .filter(graph => graph.edge.input?.length == 0 && graph.edge.output?.length == 0)
            .map(table => table.node.id);
      }

      return [];
   }
}
