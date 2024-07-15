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
import { HttpClient, HttpParams } from "@angular/common/http";
import {
   AfterViewInit, Component, ElementRef, EventEmitter, HostBinding, HostListener, Input,
   Output, ViewChild,
} from "@angular/core";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { AssemblyActionGroup } from "../../../../../../common/action/assembly-action-group";
import { Point } from "../../../../../../common/data/point";
import { ComponentTool } from "../../../../../../common/util/component-tool";
import { InputNameDialog } from "../../../../../../widget/dialog/input-name-dialog/input-name-dialog.component";
import { FixedDropdownDirective } from "../../../../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { ActionsContextmenuComponent } from "../../../../../../widget/fixed-dropdown/actions-contextmenu.component";
import { DropdownOptions } from "../../../../../../widget/fixed-dropdown/dropdown-options";
import { FixedDropdownService } from "../../../../../../widget/fixed-dropdown/fixed-dropdown.service";
import {
   EditQueryTableEvent
} from "../../../../model/datasources/database/events/edit-query-table-event";
import { GraphModel } from "../../../../model/datasources/database/physical-model/graph/graph-model";
import { GraphNodeModel } from "../../../../model/datasources/database/physical-model/graph/graph-node-model";
import { DataPhysicalModelService } from "../../../../services/data-physical-model.service";
import { Tool } from "../../../../../../../../../shared/util/tool";
import { CheckTableAliasEvent } from "../../../../model/datasources/database/events/check-table-alias-event";
import {
   QueryTablePropertiesDialogComponent
} from "../../database-query/query-main/query-link-pane/query-table-properties-dialog/query-table-properties-dialog.component";
import { DataType } from "../join-thumbnail.service";

const CREATE_ALIAS_TABLE = "../api/data/physicalmodel/graph/alias";
const CHECK_ALIAS_HAS_DUPLICATE = "../api/data/physicalmodel/graph/alias/status";
const REFRESH_NODE = "../api/data/physicalmodel/graph/node/refresh";
const UPDATE_NODE_WIDTH = "../api/data/physicalmodel/graph/node/width/";
const EDIT_QUERY_TABLE_PROPERTIES = "../api/data/datasource/query/table/properties";

@Component({
   selector: "join-node-graph",
   templateUrl: "join-node-graph.component.html",
   styleUrls: ["join-node-graph.component.scss",
      "../../../../../../composer/gui/ws/jsplumb/jsplumb-shared.scss"]
})
export class JoinNodeGraphComponent implements AfterViewInit {
   @Input() runtimeId: string;
   @Input() graph: GraphModel;
   @Input() graphEndpoints: any[];
   @Input() selected = false;
   @Input() dataType = DataType.PHYSICAL;
   @Input() tableAliasCheck: (graphNode: GraphNodeModel, alias: string) => boolean;
   @Output() onModified: EventEmitter<boolean> = new EventEmitter<boolean>();
   @Output() onNodeSelected = new EventEmitter<MouseEvent>();
   @Output() onCreateAutoAlias = new EventEmitter<string>(); // qualifiedName
   @Output() onEditInlineView = new EventEmitter<string>(); // name
   @Output() onAddEndpoint = new EventEmitter<[any, any]>();
   @Output() onRemoveSelectedNodes = new EventEmitter<void>();
   @Output() onSetDraggable = new EventEmitter<[any, any]>();
   @Output() onShowEndpoints = new EventEmitter<{element: HTMLElement}>();
   @Output() onHideEndpoints = new EventEmitter<{element: HTMLElement}>();
   @Output() onMoveNodes = new EventEmitter<Point>();
   @Output() onRegisterNode = new EventEmitter<[GraphModel, any]>(); // node --> element
   @Output() onQueryPropertiesChanged = new EventEmitter<any>();
   @ViewChild(FixedDropdownDirective) dropdown: FixedDropdownDirective;

   constructor(private nodeGraph: ElementRef,
               private modalService: NgbModal,
               private http: HttpClient,
               private physicalModelService: DataPhysicalModelService,
               private readonly fixedDropdownService: FixedDropdownService)
   {
   }

   ngAfterViewInit(): void {
      this.setDraggable();
      this.endPointsInit();
      this.onRegisterNode.emit([this.graph, this.nodeGraph.nativeElement]);
      this.updateWidth(); // portal layout first.
   }

   private updateWidth(): void {
      if(this.dataType !== DataType.PHYSICAL) {
         return;
      }

      const width: number = Math.ceil(this.nodeGraph.nativeElement.clientWidth);

      let params = new HttpParams()
         .set("table", this.physicalModelService.getTableName(this.graph))
         .set("width", width + "");

      const alias = this.physicalModelService.getAutoAliasName(this.graph);

      if(!!alias) {
         params = params.set("alias", this.physicalModelService.getAutoAliasName(this.graph));
      }

      this.http.put(UPDATE_NODE_WIDTH + Tool.encodeURIComponentExceptSlash(this.runtimeId),
         null, {params}).subscribe((changed: boolean) => {
         if(changed) {
            this.graph.bounds.width = width;
         }
      });
   }

   @HostBinding("style.top.px")
   get top(): number {
      return this.graph.bounds.y;
   }

   @HostBinding("style.left.px")
   get left(): number {
      return this.graph.bounds.x;
   }

   @HostBinding("style.height.px")
   get height(): number {
      return this.graph.bounds.height;
   }

   showEndpoint(event: MouseEvent): void {
      if(event.button === 0) {
         // Gives thumbnail focus capability as the jsPlumb library uses preventDefaults() on
         // draggables
         this.nodeGraph.nativeElement.focus();
         this.onShowEndpoints.emit({element: this.nodeGraph.nativeElement});
      }
   }

   @HostListener("blur")
   blur(): void {
      this.hideEndpoints();
   }

   @HostListener("contextmenu", ["$event"])
   actions(event: MouseEvent): void {
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
      contextmenu.actions = this.getGraphNodeActions();
      contextmenu.sourceEvent = event;
   }

   private getGraphNodeActions(): AssemblyActionGroup[] {
      const actions: AssemblyActionGroup[] = [];

      if(this.dataType === DataType.PHYSICAL) {
         actions.push(new AssemblyActionGroup([
            {
               id: () => "physical view edit view",
               label: () => "_#(js:Edit View)",
               icon: () => null,
               enabled: () => true,
               visible: () => this.graph.sql && !this.graph.baseTable,
               action: () => this.onEditInlineView.emit(this.graph.node.name)
            },
            {
               id: () => "physical view edit alias",
               label: () => "_#(js:Edit Alias)",
               icon: () => "",
               enabled: () => true,
               visible: () => this.graph.designModeAlias && !this.graph.baseTable,
               action: () => this.editAlias()
            },
            {
               id: () => "physical view auto alias",
               label: () => "_#(js:Auto Alias)",
               icon: () => null,
               enabled: () => true,
               visible: () => !this.graph.designModeAlias &&
                  (!this.graph.alias || this.isAutoAliasNode()) && !this.graph.baseTable,
               action: () => this.onCreateAutoAlias.emit(this.isAutoAliasNode()
                  ? this.graph.node.aliasSource : this.graph.node.name)
            },
            {
               id: () => "physical-view refresh-table",
               label: () => "_#(js:Refresh)",
               icon: () => null,
               enabled: () => true,
               visible: () => this.graph.sql && !this.graph.baseTable,
               action: () => this.refreshTable()
            },
         ]));
      }

      actions.push(new AssemblyActionGroup([
         {
            id: () => "physical view create alias",
            label: () => "_#(js:Create Alias)",
            icon: () => "",
            enabled: () => true,
            visible: () => this.dataType === DataType.PHYSICAL,
            action: () => this.createAlias()
         },
         {
            id: () => "query table properties",
            label: () => "_#(js:Properties)...",
            icon: () => null,
            enabled: () => true,
            visible: () => this.dataType === DataType.QUERY,
            action: () => this.showQueryTablePropertiesDialog()
         },
         {
            id: () => "physical-view remove-table",
            label: () => "_#(js:Remove tables)",
            icon: () => null,
            enabled: () => true,
            visible: () => !this.graph.baseTable,
            action: () => this.removeSelectedNode()
         }
      ]));

      return actions;
   }

   isAutoAliasNode(): boolean {
      return !!this.graph && (this.graph.autoAlias || this.graph.autoAliasByOutgoing);
   }

   private removeSelectedNode(): void {
      this.onRemoveSelectedNodes.emit();
   }

   hideEndpoints(): void {
      this.onHideEndpoints.emit({element: this.nodeGraph.nativeElement});
   }

   private endPointsInit(): void {
      const endpoints: any[] = this.graphEndpoints;

      for(const endpoint of endpoints) {
         this.onAddEndpoint.emit([this.nodeGraph.nativeElement, endpoint]);
      }
   }

   /** Set thumbnails as draggable and have them update assembly pos on drag end. */
   private setDraggable(): void {
      // do not allow edit base table.
      if(this.graph.baseTable) {
         return;
      }

      // Drag happens for all selected draggables, so dragLeader allows to
      // discriminate on the main one
      let dragLeader: boolean = false;

      const updatePos = (params: any) => {
         if(dragLeader) {
            const x: number = params.pos[0];
            const y: number = params.pos[1];
            const offsetTop = y - this.graph.bounds.y;
            const offsetLeft = x - this.graph.bounds.x;
            const offset = new Point(offsetLeft, offsetTop);

            if(offsetTop !== 0 || offsetLeft !== 0) {
               this.onMoveNodes.emit(offset);
            }

            dragLeader = false;
         }
      };

      const checkDragLeader = (params: any) => {
         const event: MouseEvent = params.e;

         // If params has mouse event e, then this assembly should be the "leader"; this assembly
         // is the one being dragged.
         if(event) {
            dragLeader = true;
         }
      };

      this.onSetDraggable.emit([this.nodeGraph.nativeElement, {
         start: checkDragLeader,
         stop: updatePos,
         consumeStartEvent: false,
         handle: ".jsplumb-draggable-handle, .jsplumb-draggable-handle *"
      }]);
   }

   stopPropagation(event: MouseEvent): void {
      event.preventDefault();
      event.stopPropagation();
   }

   selectNode(event: MouseEvent): void {
      this.onNodeSelected.emit(event);
   }

   selectNodeByIcon(event: MouseEvent): void {
      this.stopPropagation(event);
      this.graph.showColumns = !this.graph.showColumns;
      this.selectNode(event);
   }

   /* change to allow more than one table to be expanded so a user can view the columns
      at the same time.
   @HostListener("mouseleave")
   hideColumns() {
      this.graph.showColumns = false;
   }
   */

   private createAlias(): void {
      this.showAliasDialog();
   }

   private editAlias(): void {
      let alias = this.graph.node.name;

      if(this.graph.autoAliasByOutgoing && !!this.graph.outgoingAutoAliasSource) {
         alias = this.graph.outgoingAutoAliasSource;
      }

      this.showAliasDialog(alias);
   }

   private showAliasDialog(oldAlias?: string): void {
      const dialog = ComponentTool.showDialog(this.modalService, InputNameDialog, (alias) => {
         let params = new HttpParams()
            .set("runtimeId", this.runtimeId)
            .set("table", this.isAutoAliasNode() ? this.graph.node.aliasSource : this.graph.node.name)
            .set("alias", alias);

         if(!!oldAlias) {
            params = params.set("oldAlias", oldAlias);
         }

         this.http.post(CREATE_ALIAS_TABLE, null, {params})
            .subscribe((inValidMsg: any) =>
         {
            if(!!inValidMsg && !!inValidMsg.body) {
               ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                  inValidMsg.body);
            }
            else {
               this.physicalModelService.emitModelChange();
            }
         });
      });

      dialog.value = oldAlias;
      dialog.title = "_#(js:Table Alias)";
      dialog.label = "_#(js:data.physicalmodel.tableAliasName)";
      dialog.validators = this.physicalModelService.aliasValidators;
      dialog.validatorMessages = this.physicalModelService.aliasValidatorMessages;
      dialog.helpLinkKey = "TableAlias";
      dialog.hasDuplicateCheck = (alias: string) => {
         let event = new CheckTableAliasEvent(this.runtimeId, alias);
         return this.http.put<boolean>(CHECK_ALIAS_HAS_DUPLICATE, event);
      };
      dialog.duplicateMessage = "_#(js:designer.qb.aliasExists)";
   }

   private refreshTable(): void {
      let params = new HttpParams()
         .set("runtimeId", this.runtimeId)
         .set("table", this.graph.node.name);

      this.http.get(REFRESH_NODE, { params }).subscribe(() => {
         this.physicalModelService.emitModelChange();
      });
   }

   private showQueryTablePropertiesDialog(): void {
      let modalOptions: NgbModalOptions = {
         backdrop: "static",
      };
      const dialog = ComponentTool.showDialog(this.modalService, QueryTablePropertiesDialogComponent,
         (data) => {
            const table = {name: data.name, alias: data.alias};
            const event = new EditQueryTableEvent(this.runtimeId, [table], this.graph.node.name);
            this.http.post(EDIT_QUERY_TABLE_PROPERTIES, event)
               .subscribe(() => {
                  this.onModified.emit(true);
                  this.onQueryPropertiesChanged.emit(true);
               });
         }, modalOptions);

      dialog.runtimeId = this.runtimeId;
      dialog.graphNode = this.graph.node;
      dialog.tableAliasCheck = this.tableAliasCheck;
   }
}
