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
import {
   AfterContentInit,
   AfterViewChecked,
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
import { Subscription } from "rxjs";
import { AssetEntry } from "../../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../../shared/data/asset-type";
import { Tool } from "../../../../../../../shared/util/tool";
import { AssemblyActionGroup } from "../../../../common/action/assembly-action-group";
import { DragEvent } from "../../../../common/data/drag-event";
import { Notification } from "../../../../common/data/notification";
import { Point } from "../../../../common/data/point";
import { Rectangle } from "../../../../common/data/rectangle";
import { ComponentTool } from "../../../../common/util/component-tool";
import { GuiTool } from "../../../../common/util/gui-tool";
import { CommandProcessor, ViewsheetClientService } from "../../../../common/viewsheet-client";
import { AssetTreeService } from "../../../../widget/asset-tree/asset-tree.service";
import { SelectionBoxEvent } from "../../../../widget/directive/selection-box.directive";
import { ActionsContextmenuComponent } from "../../../../widget/fixed-dropdown/actions-contextmenu.component";
import { DropdownOptions } from "../../../../widget/fixed-dropdown/dropdown-options";
import { FixedDropdownService } from "../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DragService } from "../../../../widget/services/drag.service";
import { ModelService } from "../../../../widget/services/model.service";
import { AbstractTableAssembly } from "../../../data/ws/abstract-table-assembly";
import { CompositeTableAssembly } from "../../../data/ws/composite-table-assembly";
import { ConcatenatedTableAssembly } from "../../../data/ws/concatenated-table-assembly";
import { Worksheet } from "../../../data/ws/worksheet";
import { WSAssembly } from "../../../data/ws/ws-assembly";
import { WSDependency } from "../../../data/ws/ws-dependency";
import { WorksheetTableOperator } from "../../../data/ws/ws-table.operators";
import {
   DEPENDENCY_TYPE_OVERLAY_ID,
   JSPlumbDependencyTypeOverlays
} from "../jsplumb/jsplumb-dependency-type-overlays";
import {
   CONCAT,
   endpointType,
   JOIN,
   jspInitGraphMain,
   TABLE_ENDPOINTS,
   TYPE_ASSEMBLY_CONNECTION,
   TYPE_COLUMN_SOURCE,
   TYPE_CONCATENATION_WARNING,
   TYPE_DIMMED,
   TYPE_INDETERMINATE,
   TYPE_INVALID,
   TYPE_VALID
} from "../jsplumb/jsplumb-graph-main.config";
import { ConcatCompatibilityCommand } from "../socket/concat-compatibility/concat-compatibility-command";
import { ConcatCompatibilityEvent } from "../socket/concat-compatibility/concat-compatibility-event";
import { OpenAssetEvent } from "../socket/open-asset/open-asset-event";
import { OpenAssetEventValidator } from "../socket/open-asset/open-asset-event-validator";
import { WSChangeDependencyEvent } from "../socket/ws-change-dependency-event";
import { WSConcatenateEvent } from "../socket/ws-concatenate-event";
import { WSDragColumnsEvent } from "../socket/ws-drag-columns-event";
import { WSInsertColumnsEvent } from "../socket/ws-insert-columns/ws-insert-columns-event";
import { WSJoinTablePairEvent } from "../socket/ws-join/ws-join-table-pair-event";
import { WSMoveAssembliesEvent } from "../socket/ws-move/ws-move-assemblies-event";
import { WSRelocateAssembliesEvent } from "../socket/ws-move/ws-relocate-assemblies.event";
import { WSPasteAssembliesEvent } from "../socket/ws-paste-assemblies-event";
import { WSRemoveAssembliesEvent } from "../socket/ws-remove-assemblies-event";
import { WSRenameAssemblyEvent } from "../socket/ws-rename-assembly-event";
import { AssemblyDragScrollHandler } from "./assembly-drag-scroll-handler";
import { HttpParams } from "@angular/common/http";

const CONCAT_DIALOG_URI = "/events/composer/worksheet/concatenate-tables";
const CONCAT_COMPATIBILITY_URI = "/events/composer/worksheet/concat/compatibility";
const OFFSET_ASSEMBLIES_URI = "/events/composer/worksheet/move-assemblies/offset";
const RELOCATE_ASSEMBLIES_URI = "/events/composer/worksheet/move-assemblies/relocate";
const JOIN_TABLES_URI = "/events/composer/worksheet/join-tables";
const CHANGE_DEPENDENCY_URI = "/events/composer/worksheet/change-dependency";
const OPEN_ASSET_URI = "/events/composer/worksheet/open-asset";
const OPEN_ASSET_CHECK_TRAP_URI = "../api/composer-worksheet/open-asset/check-trap/";
const CONTROLLER_REMOVE_ASSEMBLIES_CHECK = "../api/composer/worksheet/remove-assemblies/check-dependency/";
const REMOVE_ASSEMBLIES_URI = "/events/composer/worksheet/remove-assemblies";
const DRAG_COLUMNS_URI = "/events/composer/worksheet/drag-columns";
const DRAG_PASTE_URI = "/events/composer/worksheet/paste-assemblies";
const RENAME_ASSEMBLY_URI = "/events/composer/worksheet/rename-assembly";
const START_DRAG_COPY_OFFSET_DISTANCE = 10;
const ARROW_KEY_MOVE_FACTOR_BASE = 2;
const ARROW_KEY_MOVE_FACTOR_COARSE = 10;
const ARROW_KEY_MOVE_FACTOR_FINE = 0.5;

/**
 * The graph pane of the worksheet composer.
 * <p>It utilizes the jsPlumb library to display a graph visualization of
 * the worksheet and provide a gui for table operations.
 */
@Component({
   selector: "ws-assembly-graph-pane",
   templateUrl: "ws-assembly-graph-pane.component.html",
   styleUrls: ["ws-assembly-graph-pane.component.scss", "../jsplumb/jsplumb-shared.scss"]
})
export class WSAssemblyGraphPaneComponent
   extends CommandProcessor
   implements OnInit, AfterContentInit, OnDestroy, AfterViewChecked, OnChanges
{
   @Input() worksheet: Worksheet;
   @Input() pasteEnabled: boolean;
   @Input() sqlEnabled = true;
   @Input() freeFormSqlEnabled = true;
   @Input() columnSourceTable: AbstractTableAssembly | null;
   @Output() onCopy = new EventEmitter<Worksheet>();
   @Output() onCut = new EventEmitter<Worksheet>();
   @Output() onPaste = new EventEmitter<[Worksheet, Point]>();
   @Output() onConcatenateTables =
      new EventEmitter<[AbstractTableAssembly, AbstractTableAssembly]>();
   @Output() onSelectCompositeTable = new EventEmitter<CompositeTableAssembly>();
   @Output() onInsertColumns = new EventEmitter<WSInsertColumnsEvent>();
   @Output() onOpenAssemblyConditionDialog = new EventEmitter<string>();
   @Output() onOpenAggregateDialog = new EventEmitter<string>();
   @Output() onOpenSortColumnDialog = new EventEmitter<string>();
   @Output() onEditQuery = new EventEmitter<AbstractTableAssembly>();
   @Output() onNotification = new EventEmitter<Notification>();
   @Output() onToggleAutoUpdate = new EventEmitter<WSAssembly>();
   @Output() onEditJoin = new EventEmitter<void>();
   @ViewChild("jspContainerMain", {static: true}) jspContainerMain: ElementRef<HTMLDivElement>;

   /** jsPlumb instance */
   jsp: JSPlumb.JSPlumbInstance;

   /** The non-dimmed assemblies in the composer. */
   nonDimmedAssemblies: Set<string> = new Set<string>();

   /**
    * Object whose property names create a mapping between table html ids and the
    * corresponding assembly.
    */
   private assemblies: {[sourceIds: string]: WSAssembly} = {};

   /** Object whose properties are a mapping between table names and html ids. */
   private sourceIds: {[assemblyName: string]: string} = {};

   /** True if currently dragging a connection, false otherwise. */
   private isDragging: boolean = false;

   /** Assemblies which are part of the drag selection. */
   private dragAssemblies: WSAssembly[];

   /** If flag is true, an assembly has been selected, but click event has not yet happened on it. */
   private selectAssemblyFlag: boolean = false;

   /**
    * A map of currently blocking depended assemblies. The keys are the blocking assemblies' names,
    * and the values are the list of blocked assemblies.
    */
   private blockingDependeds: Map<string, string[]> = new Map<string, string[]>();

   /** Drag-copy assembly fields */
   private dragCopyElements: {element: HTMLElement, assembly: WSAssembly}[];
   private mousemoveListener: () => void;
   private mouseupFn: EventListenerObject;

   private focusedSub: Subscription;
   private focusedAssembliesSub: Subscription;
   private repaintTimeout: any;
   private readonly lastClick = new Point();

   private readonly dragScrollMargin = 15;
   private readonly dragScrollRate = 20;
   private readonly dsHandler;

   constructor(private zone: NgZone,
               private worksheetClient: ViewsheetClientService,
               private dragService: DragService,
               private dropdownService: FixedDropdownService,
               private modalService: NgbModal,
               private modelService: ModelService,
               private renderer: Renderer2,
               @Inject(DOCUMENT) private document: Document)
   {
      super(worksheetClient, zone, true);

      this.zone.runOutsideAngular(() => {
         this.jsp = jspInitGraphMain();

         this.zone.run(() => {
            this.jsp.bind("connectionDrag", function(connection: any): void {
               this.isDragging = true;

               if(connection.scope === CONCAT) {
                  this.jsp.selectEndpoints({scope: CONCAT}).setType(TYPE_VALID);
                  this.checkConcatCompatibility(this.assemblies[connection.sourceId]);
               }
               else if(connection.scope === JOIN) {
                  this.jsp.selectEndpoints({scope: JOIN}).setVisible(true, true, true);
                  this.checkJoinCompatibility(this.assemblies[connection.sourceId]);
               }
               else {
                  for(let id in this.assemblies) {
                     if(id === connection.targetId) {
                        continue;
                     }

                     if(!this.jsp.isSource(id)) {
                        this.jsp.makeSource(id);
                     }
                  }
               }
            }.bind(this));

            this.jsp.bind("connectionAborted", function(connection: any): void {
               this.isDragging = false;

               if(connection.scope === CONCAT) {
                  this.jsp.selectEndpoints({scope: connection.scope})
                     .setVisible(false, true, true).setType(TYPE_VALID);
               }
               else {
                  this.jsp.selectEndpoints({scope: connection.scope})
                     .setVisible(false, true, true);
               }

               this.jsp.selectEndpoints({source: connection.sourceId})
                  .setVisible(true, true, true);
               this.jsp.unmakeEverySource();
            }.bind(this));

            this.jsp.bind("connectionDragStop", function(info: any, originalEvent: any): void {
               this.isDragging = false;
               this.jsp.unmakeEverySource();
            }.bind(this));

            this.jsp.bind("beforeDrop", function(info: any): boolean {
               this.jsp.unmakeEverySource();
               let connection = info.connection;

               // Dragging existing connection source from one assembly to another.
               if(connection.suspendedElementId) {
                  let oldSource: string = this.assemblies[connection.suspendedElementId].name;
                  let target: string = this.assemblies[connection.targetId].name;
                  let newSource: string = this.assemblies[connection.sourceId].name;

                  let event = new WSChangeDependencyEvent();
                  event.setOldDepended(oldSource);
                  event.setTarget(target);
                  event.setNewDepended(newSource);
                  this.worksheetClient.sendEvent(CHANGE_DEPENDENCY_URI, event);
                  return false;
               }

               return true;
            }.bind(this));

            this.jsp.bind("connection", function(info: any, originalEvent: any): void {
               this.isDragging = false;
               this.jsp.selectEndpoints({scope: JOIN}).setVisible(false, true, true);
               this.jsp.selectEndpoints({scope: CONCAT}).setVisible(false, true, true);
               const endpoints = this.jsp.selectEndpoints({scope: info.connection.scope});

               switch(<endpointType> info.connection.scope) {
                  case JOIN:
                     // if the target is a join table with expression columns, prompt users to
                     // join with the table or add to join. otherwise the expression columns
                     // will not be available as join columns. (58273)
                     if(this.isJoinWithExpr(this.assemblies[info.targetId])) {
                        ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
                                                        "_#(js:common.worksheet.joinTarget)",
                                                        {"yes": "Yes", "no": "No"})
                           .then((result: string) => {
                              return result === "yes";
                           }).then(result => {
                              this.join(this.assemblies[info.sourceId],
                                        this.assemblies[info.targetId], result);
                           });
                     }
                     else {
                        this.join(this.assemblies[info.sourceId], this.assemblies[info.targetId]);
                     }

                     this.jsp.deleteConnection(info.connection);
                     break;
                  case CONCAT:
                     if(info.targetEndpoint.hasType(TYPE_INVALID)) {
                        this.causeConcatRejection(this.assemblies[info.sourceId],
                           this.assemblies[info.targetId]);
                     }
                     else {
                        this.concat(this.assemblies[info.sourceId],
                           this.assemblies[info.targetId]);
                     }

                     endpoints.setType(TYPE_VALID, {}, true);
                     this.jsp.deleteConnection(info.connection);
                     break;
                  default:
                  // programmatic connection
               }
            }.bind(this));
         });
      });

      this.dsHandler = new AssemblyDragScrollHandler(this.dragScrollMargin, this.dragScrollRate);
      this.dsHandler.renderer = renderer;
   }

   private isJoinWithExpr(tbl: AbstractTableAssembly): boolean {
      return tbl.tableClassType == "RelationalJoinTableAssembly" &&
         tbl.colInfos.filter(c => c.ref.expression).length > 0;
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.hasOwnProperty("worksheet")) {
         this.subscribeToFocus();
      }

      if(changes.hasOwnProperty("columnSourceTable")) {
         this.refreshColumnSourceConnectionTypes();
      }
   }

   ngOnInit(): void {
      this.worksheet.jspAssemblyGraph = this.jsp;
      this.subscribeToFocus();
      this.setRepaintTimer();
   }

   ngAfterContentInit(): void {
      this.setContainer(this.jspContainerMain.nativeElement);
   }

   ngOnDestroy(): void {
      this.cleanup();
   }

   ngAfterViewChecked(): void {
      this.setRepaintTimer();
   }

   oozKeyDown(event: KeyboardEvent): void {
      if(this.worksheet.isFocused && !Tool.isEventTargetTextEditor(event)) {
         const keyCode = Tool.getKeyCode(event);

         if(!event.repeat && keyCode === 46) { // Delete
            this.zone.run(() => {
               this.removeFocusedAssemblies();
            });
         }
         else if(keyCode === 65 && event.ctrlKey) { // ctrl-a
            event.preventDefault();

            if(!event.repeat) {
               this.zone.run(() => {
                  this.worksheet.currentFocusedAssemblies = this.worksheet.assemblies();
               });
            }
         }
         else if(this.isArrowKey(keyCode)) {
            event.preventDefault();

            this.zone.run(() => {
               this.arrowKeyMove(event);
            });
         }
      }

      //fix IE 11 cannot Listen the "document: paste" event
      if(this.worksheet.isFocused && !(event.target instanceof HTMLInputElement) &&
         event.keyCode == 86 && event.ctrlKey &&
         !(event.target instanceof HTMLTextAreaElement))
      {
         this.onPaste.emit([this.worksheet, new Point(this.lastClick.x, this.lastClick.y)]);
      }
   }

   @HostListener("keyup", ["$event"])
   onKeyUp(event: KeyboardEvent): void {
      if(this.worksheet.isFocused && !Tool.isEventTargetTextEditor(event)) {
         const keyCode = Tool.getKeyCode(event);

         if(this.isArrowKey(keyCode)) {
            this.arrowKeyMoveEnd();
         }
      }
   }

   getAssemblyName(): string {
      return null;
   }

   cleanup(): void {
      super.cleanup();
      this.jsp.reset();

      if(!!this.focusedAssembliesSub) {
         this.focusedAssembliesSub.unsubscribe();
      }

      if(!!this.focusedSub) {
         this.focusedSub.unsubscribe();
      }

      clearTimeout(this.repaintTimeout);

      if(!!this.mousemoveListener) {
         this.mousemoveListener();
      }

      this.cleanupMouseupListener();
      this.dsHandler.reset();
   }

   private setContainer(container: any): void {
      this.zone.runOutsideAngular(() => this.jsp.setContainer(container));
      this.dsHandler.container = container;
   }

   trackByFn(index: number, assembly: WSAssembly): string {
      return assembly.name;
   }

   getThumbnailClasses(assembly: WSAssembly): {[className: string]: boolean} {
      return {
         "ws-assembly-graph-element--selected": this.worksheet.isAssemblyFocused(assembly),
         "ws-assembly-graph-element--dimmed": !this.isDragging &&
                                               this.nonDimmedAssemblies.size > 0 &&
                                              !this.nonDimmedAssemblies.has(assembly.name),
         "ws-assembly-graph-element--primary": assembly.primary,
         "ws-assembly-graph-element--uneditable": assembly.classType !== "TableAssembly" &&
                        (!assembly.info.editable ||
                           (!!assembly.info.mirrorInfo && assembly.info.mirrorInfo.outerMirror)),
         "ws-assembly-graph-element--selection-adjacent":
         !this.worksheet.isAssemblyFocused(assembly) && this.nonDimmedAssemblies.has(assembly.name)
         && this.nonDimmedAssemblies.size > 0
      };
   }

   /**
    * Inform the component and details service that an assembly was selected
    *
    * @param event the event which invoked this method
    * @param assembly the selected assembly
    */
   selectAssembly(event: MouseEvent, assembly: WSAssembly): void {
      /** Don't push if current table is already focused */
      if(this.worksheet.isAssemblyFocused(assembly)) {
         return;
      }
      else if(event.ctrlKey || event.shiftKey) {
         this.worksheet.selectAssembly(assembly);
      }
      else {
         this.worksheet.currentFocusedAssemblies = [assembly];
      }

      this.selectAssemblyFlag = true;
   }

   /** Handler for assembly click. */
   clickAssembly(event: MouseEvent, assembly: WSAssembly): void {
      // Deselect component if the previous mousedown did not select the assembly.
      if(this.selectAssemblyFlag) {
         this.selectAssemblyFlag = false;
      }
      else if(event.ctrlKey && this.worksheet.isAssemblyFocused(assembly)) {
         this.worksheet.deselectAssembly(assembly);
      }
   }

   onSelectionBox(event: SelectionBoxEvent): void {
      this.worksheet.currentFocusedAssemblies = this.worksheet.assemblies().filter((assembly) => {
         const assemblyRect = new Rectangle(assembly.left, assembly.top,
            assembly.width, assembly.height);

         return assemblyRect.intersects(event.box);
      });
   }

   /** Clear focused assemblies. */
   clearSelection(event: MouseEvent): void {
      if(event.target === event.currentTarget &&
         this.worksheet.currentFocusedAssemblies.length > 0)
      {
         this.worksheet.clearFocusedAssemblies();
      }
   }

   updateLastClick(event: MouseEvent): void {
      if(event.currentTarget === event.target) {
         this.lastClick.x = event.offsetX + this.jspContainerMain.nativeElement.scrollLeft;
         this.lastClick.y = event.offsetY + this.jspContainerMain.nativeElement.scrollTop;
      }
   }

   allowDrop(event: DragEvent): void {
      // If drop target is not jsp container, then positions offsets will be wrong.
      // TODO consider better solution that allows drop anywhere in the view.
      if(event.target === this.jspContainerMain.nativeElement) {
         event.preventDefault();
      }
   }

   /**
    * Drop event handler. Will try to open Queries, Tables, Columns, and Worksheet Primaries
    * in the current worksheet.
    * @param dragEvent the drag event
    */
   drop(dragEvent: DragEvent): void {
      dragEvent.preventDefault();
      GuiTool.clearDragImage();

      if(this.worksheet && this.worksheet.assemblies() &&
         this.worksheet.assemblies().length === 0)
      {
         this.notify({
            type: "success",
            message: "_#(js:composer.ws.graphPane.checkDataBlock)"
         });
      }

      if(this.dragService.get("selectedColumnIndices") &&
         this.dragService.get("columnsOfTable"))
      {
         this.handleDragColumnsEvent(dragEvent,
            this.dragService.get("columnsOfTable"),
            this.dragService.get("selectedColumnIndices"));
         return;
      }

      let entriesString =
         this.dragService.get(AssetTreeService.getDragName(AssetType.WORKSHEET)) ||
         this.dragService.get(AssetTreeService.getDragName(AssetType.QUERY)) ||
         this.dragService.get(AssetTreeService.getDragName(AssetType.TABLE)) ||
         this.dragService.get(AssetTreeService.getDragName(AssetType.COLUMN)) ||
         this.dragService.get(AssetTreeService.getDragName(AssetType.PHYSICAL_TABLE)) ||
         this.dragService.get(AssetTreeService.getDragName(AssetType.PHYSICAL_COLUMN));
      let entries: AssetEntry[];

      if(entriesString) {
         entries = JSON.parse(entriesString);
      }

      if(entries && entries.length > 0) {
         dragEvent.stopPropagation();

         for(let entry of entries) {
            if(entry.type === AssetType.WORKSHEET && entry.identifier === this.worksheet.id ||
               entry.type === AssetType.COLUMN && (entry.properties || {}).ws === this.worksheet.id)
            {
               console.warn("Cannot use self.");
               return;
            }
         }

         this.openAssets(entries, dragEvent);
      }
   }

   /**
    * Open assets dragged into this pane.
    *
    * @param {AssetEntry[]} entries the entries to open
    * @param {DragEvent} dragEvent the drag event this is caused by
    */
   private openAssets(entries: AssetEntry[], dragEvent: DragEvent): void {
      const jspContainer = this.jspContainerMain.nativeElement;
      const dy = jspContainer.scrollTop;
      const dx = jspContainer.scrollLeft;

      const openAssetEvent = new OpenAssetEvent();
      openAssetEvent.setEntries(entries);
      openAssetEvent.setTop(dragEvent.offsetY + dy);
      openAssetEvent.setLeft(dragEvent.offsetX + dx);
      const uri = OPEN_ASSET_CHECK_TRAP_URI + Tool.byteEncode(this.worksheet.runtimeId);

      this.modelService.sendModel<OpenAssetEventValidator>(uri, openAssetEvent)
         .subscribe((res) => {
            if(!!res.body) {
               const validator = res.body;

               ComponentTool.showTrapAlert(this.modalService, false, validator.trapMessage,
                  {backdrop: "static"})
                  .then((buttonClicked) => {
                     if(buttonClicked === "yes") {
                        this.worksheetClient.sendEvent(OPEN_ASSET_URI, openAssetEvent);
                     }
                  });
            }
            else {
               this.worksheetClient.sendEvent(OPEN_ASSET_URI, openAssetEvent);
            }
         });
   }

   private handleDragColumnsEvent(
      dragEvent: DragEvent, table: AbstractTableAssembly, columnIndices: number[]): void
   {
      const jspContainer = this.jspContainerMain.nativeElement;
      const dy = jspContainer.scrollTop;
      const dx = jspContainer.scrollLeft;

      const privateColIndices: number[] = [];

      for(let columnIndex of columnIndices) {
         const colInfo = table.colInfos[columnIndex];
         const privateColIndex =
            table.info.privateSelection.findIndex(col => col.name === colInfo.name);

         if(privateColIndex === -1) {
            throw new Error(`Could not match column ${colInfo.name} to a column in the private
            column selection.`);
         }

         privateColIndices.push(privateColIndex);
      }

      const event = new WSDragColumnsEvent();
      event.setTableName(table.name);
      event.setColumnIndices(privateColIndices);
      event.setTop(dragEvent.offsetY + dy);
      event.setLeft(dragEvent.offsetX + dx);
      this.worksheetClient.sendEvent(DRAG_COLUMNS_URI, event);
   }

   selectCompositeTable(table: AbstractTableAssembly, event?: MouseEvent): void {
      this.onEditJoin.emit();

      if(event != null) {
         event.stopPropagation();
      }

      if(table instanceof CompositeTableAssembly) {
         this.onSelectCompositeTable.emit(table);
      }
   }

   removeFocusedAssemblies(): void {
      let names = this.worksheet.currentFocusedAssemblies.map((a: WSAssembly) => a.name);
      let event = new WSRemoveAssembliesEvent();
      event.setAssemblyNames(names);
      let promise = Promise.resolve(true);

      let primary = this.worksheet.currentFocusedAssemblies.find((a: WSAssembly) => a.primary);

      if(primary) {
         // TODO common.PrimaryWarning
         let message = "_#(js:common.PrimaryWarning)" + "_*" + primary.name;

         promise = ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", message,
            {"delete": "_#(js:Delete)", "cancel": "_#(js:Cancel)"})
            .then((buttonClicked) => buttonClicked === "delete");
      }

      promise.then((val) => {
         if(val) {
            this.removeAssemblies(event);
         }
      });
   }

   removeAssemblies(event: WSRemoveAssembliesEvent) {
      let params = new HttpParams()
         .set("all", "false");
      let encodeRuntimeId = Tool.encodeURIPath(this.worksheetClient.runtimeId);

      this.modelService.sendModel(CONTROLLER_REMOVE_ASSEMBLIES_CHECK
         + encodeRuntimeId, event, params).subscribe(msg =>
      {
         if(msg.body === "true") {
            ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
               "_#(js:common.confirmColumnDependency)",
               {"yes": "_#(js:Yes)",
                  "no": "_#(js:No)",
                  "cancel": "_#(js:Cancel)"}).then(result =>
            {
               if(result == "yes") {
                  params = params.set("all", "true");

                  this.modelService.sendModel(CONTROLLER_REMOVE_ASSEMBLIES_CHECK
                     + encodeRuntimeId, event, params).subscribe(msg2 =>
                  {
                     ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
                        msg2.body as string).then(result2 =>
                     {
                        if(result2 == "ok") {
                           this.worksheetClient.sendEvent(REMOVE_ASSEMBLIES_URI, event);
                        }
                     });
                  });
               }
               else if(result == "no") {
                  this.worksheetClient.sendEvent(REMOVE_ASSEMBLIES_URI, event);
               }
            });
         }
         else {
            this.worksheetClient.sendEvent(REMOVE_ASSEMBLIES_URI, event);
         }
      });
   }

   destroyAssembly(assembly: WSAssembly): void {
      this.blockingDependeds.delete(assembly.name);
      let sourceId = this.sourceIds[assembly.name];
      this.jsp.removeAllEndpoints(sourceId);
      delete this.assemblies[sourceId];
      delete this.sourceIds[assembly.name];
   }

   cutAssembly(assembly: WSAssembly): void {
      this.onCut.emit(this.worksheet);
   }

   copyAssembly(assembly: WSAssembly): void {
      this.onCopy.emit(this.worksheet);
   }

   toggleAutoUpdate(assembly: WSAssembly): void {
      this.onToggleAutoUpdate.emit(assembly);
   }

   public get tableEndpoints(): Object[] {
      return TABLE_ENDPOINTS;
   }

   public registerAssembly(assembly: WSAssembly, sourceId: string): void {
      this.assemblies[sourceId] = assembly;
      this.sourceIds[assembly.name] = sourceId;

      for(const depended of assembly.dependeds) {
         this.connectAssemblies(assembly.name, depended);
      }

      let blockedDependings = this.blockingDependeds.get(assembly.name);
      let dependings: string[];

      if(blockedDependings != null) {
         this.blockingDependeds.delete(assembly.name);
         dependings = Tool.uniq([...assembly.dependings, ...blockedDependings]);
      }
      else {
         dependings = assembly.dependings;
      }

      for(const dependingName of dependings) {
         const source = this.sourceIds[dependingName];
         const dependingAssembly = this.assemblies[source];

         if(dependingAssembly != null) {
            const wsDependency = dependingAssembly.dependeds
               .find((dep) => dep.assemblyName === assembly.name);

            if(wsDependency != null) {
               this.connectAssemblies(dependingName, wsDependency);
            }
         }
      }

      this.refreshDragSelection();
   }

   public startEditName(assembly: WSAssembly): void {
      this.worksheet.selectOnlyAssembly(assembly);
   }

   public editName(assembly: WSAssembly, newName: string): void {
      const event = new WSRenameAssemblyEvent(assembly.name, newName);
      this.worksheet.socketConnection.sendEvent(RENAME_ASSEMBLY_URI, event);
   }

   private connectAssemblies(targetName: string, dependency: WSDependency): void {
      const sourceName = dependency.assemblyName;
      const source = this.sourceIds[sourceName];

      if(source == null) {
         let dependings = this.blockingDependeds.get(sourceName);

         if(dependings == null) {
            dependings = [];
            this.blockingDependeds.set(sourceName, dependings);
         }

         dependings.push(targetName);
         return;
      }

      const target = this.sourceIds[targetName];
      const overlay = JSPlumbDependencyTypeOverlays.getOverlays(dependency.types);
      const connection = {source, target, type: TYPE_ASSEMBLY_CONNECTION, overlays: [overlay]};

      const dimmed = this.nonDimmedAssemblies.size > 0 &&
         (!this.nonDimmedAssemblies.has(sourceName) ||
            !this.nonDimmedAssemblies.has(targetName));
      const targetAssembly = this.assemblies[target];

      if(targetAssembly instanceof ConcatenatedTableAssembly &&
         targetAssembly.concatenationWarning)
      {
         connection.type += ` ${TYPE_CONCATENATION_WARNING}`;
      }

      this.createConnection(connection, dimmed);
   }

   private createConnection(conn: {source: string, target: string, [prop: string]: any},
                            dimmed: boolean): void
   {
      if((<any[]> this.jsp.getConnections(conn)).length === 0 &&
         conn.source != undefined && conn.target != undefined && conn.source !== conn.target)
      {
         const connection = this.jsp.connect(conn);

         if(dimmed) {
            this.dimConnection(connection);
         }
      }
   }

   public refreshAssembly(oldName: string, newAssembly: WSAssembly): void {
      this.jsp.setSuspendDrawing(true);
      let sourceId = this.sourceIds[oldName];

      if(sourceId == undefined) {
         return;
      }

      delete this.sourceIds[oldName];
      let conns = <any[]> this.jsp.getConnections({target: sourceId});

      for(let conn of conns) {
         this.jsp.deleteConnection(conn);
      }

      this.registerAssembly(newAssembly, sourceId);
   }

   setDraggable(element: any, options: any): void {
      const oldStart = options.start;
      options.start = (params) => {
        oldStart(params);
        this.dsHandler.addElement({el: params.el});

         if(params.e != null) {
            this.dsHandler.addScrollCallback(() => this.jsp.repaintEverything());
         }
      };

      options.drag = (params: any) => {
         const event: MouseEvent = params.e;

         if(event != null) {
            this.dsHandler.onDrag(event);
         }
      };

      const oldStop = options.stop;
      options.stop = (params: any) => {
         oldStop(params);
         this.dsHandler.reset();
      };

      this.zone.runOutsideAngular(() => this.jsp.draggable(element, options));
   }

   oozScroll(): void {
      this.dsHandler.onScroll();
   }

   toggleEndpoints(params: {element: HTMLElement, [prop: string]: any}): void {
      params.scope = [JOIN, CONCAT];
      const endpoints = this.jsp.selectEndpoints(params);

      endpoints.each(function(endpoint): void {
         if(endpoint.scope === CONCAT) {
            endpoint.canvas.title = "_#(js:common.worksheet.graph.concatenation)";
         }
         else {
            endpoint.canvas.title = "_#(js:common.worksheet.graph.join)";
         }
      });

      if(endpoints.length > 0) {
         const visible = endpoints.get(0).isVisible();
         endpoints.setVisible(!visible, true, true);
      }
   }

   hideEndpoints(params: {element: HTMLElement, [prop: string]: any}): void {
      params.scope = [JOIN, CONCAT];
      const endpoints = this.jsp.selectEndpoints(params);
      endpoints.setVisible(false, true, true);
   }

   addEndpoint(element: any, endpoint: any): void {
      this.jsp.addEndpoint(element, endpoint).setVisible(false, true, true);
   }

   notify(notification: Notification): void {
      this.onNotification.emit(notification);
   }

   private checkJoinCompatibility(sourceTable: AbstractTableAssembly): void {
      this.jsp.selectEndpoints({scope: JOIN}).setVisible(true, true, true);

      for(const sourceId of Object.keys(this.assemblies)) {
         let table = this.assemblies[sourceId];

         if(table instanceof AbstractTableAssembly && !(table.name === sourceTable.name)) {
            return;
         }
      }

      this.notify({
         type: "warning",
         message: "There are no other tables to join with."
      });
   }

   private checkConcatCompatibility(sourceTable: AbstractTableAssembly): void {
      this.jsp.selectEndpoints({scope: CONCAT})
         .setType(TYPE_INDETERMINATE, {}, true).setVisible(true, true, true);
      const otherTables: string[] = [];

      for(let sourceId in this.assemblies) {
         if(!(this.assemblies[sourceId] instanceof AbstractTableAssembly)) {
            continue;
         }

         const table = this.assemblies[sourceId];

         if(table.name === sourceTable.name) {
            this.jsp.selectEndpoints({
               element: sourceId,
               scope: CONCAT
            }).setType(TYPE_VALID, {}, true);
            continue;
         }

         otherTables.push(table.name);
      }

      let event = new ConcatCompatibilityEvent();
      event.setSourceTable(sourceTable.name);
      event.setOtherTables(otherTables);
      this.worksheetClient.sendEvent(CONCAT_COMPATIBILITY_URI, event);
   }

   private processConcatCompatibilityCommand(command: ConcatCompatibilityCommand): void {
      // Not currently dragging, so command does not do anything.
      if(!this.isDragging) {
         return;
      }

      this.jsp.selectEndpoints({scope: CONCAT}).setType(TYPE_VALID, {}, true);

      for(let tableName of command.invalidTables) {
         let sourceId = this.sourceIds[tableName];
         let endpoint: any = this.jsp.selectEndpoints({
            element: sourceId,
            scope: CONCAT
         });

         endpoint.setType(TYPE_INVALID, {}, true);
      }
   }

   private causeConcatRejection(sourceTable: AbstractTableAssembly,
                                targetTable: AbstractTableAssembly): void
   {
      let event = new WSConcatenateEvent();
      event.setOperator(WorksheetTableOperator.INTERSECT);
      event.setTables([targetTable.name, sourceTable.name]);
      this.worksheetClient.sendEvent(CONCAT_DIALOG_URI, event);
   }

   private join(sourceTable: AbstractTableAssembly, targetTable: AbstractTableAssembly,
                joinTarget: boolean): void
   {
      let event = new WSJoinTablePairEvent();
      event.setLeftTable(sourceTable.name);
      event.setRightTable(targetTable.name);
      event.setJoinTarget(joinTarget);
      this.worksheetClient.sendEvent(JOIN_TABLES_URI, event);
   }

   private concat(sourceTable: AbstractTableAssembly, targetTable: AbstractTableAssembly): void {
      this.onConcatenateTables.emit([targetTable, sourceTable]);
   }

   private refreshDragSelection(): void {
      this.jsp.clearDragSelection();
      const elements: string[] = [];
      this.dragAssemblies = [];

      this.worksheet.getCurrentFocusedAssemblies()
         .filter(a => !!a)
         .forEach(a => {
            const name = a.name;
            const id = this.sourceIds[name];
            const assembly = this.assemblies[id];

            if(assembly === a) {
               elements.push(id);
               this.dragAssemblies.push(assembly);
            }
         });

      this.jsp.addToDragSelection(elements);
   }

   dragPasteAssemblies(initialDragPoint: Point): void {
      this.dragCopyElements = null;
      this.cleanupMouseupListener();
      this.mouseupFn = this.windowMouseup.bind(this, initialDragPoint);
      this.mousemoveListener = this.renderer.listen("window", "mousemove",
         (event) => this.windowMousemove(initialDragPoint, event));
      window.addEventListener("mouseup", this.mouseupFn, true);
   }

   moveAssemblies(offset: Point): void {
      let event = new WSMoveAssembliesEvent();
      let assemblyNames = this.dragAssemblies.map((el) => el.name);

      this.dragAssemblies.forEach((assembly) => {
         const id = this.sourceIds[assembly.name];
         const el = this.document.getElementById(id);

         if(el != null) {
            const left = Math.max(assembly.left + offset.x, 0);
            const top = Math.max(assembly.top + offset.y, 0);
            assembly.left = left;
            assembly.top = top;
            this.renderer.setStyle(el, "left", left + "px");
            this.renderer.setStyle(el, "top", top + "px");
         }
      });

      if(assemblyNames.length > 0) {
         event.setAssemblyNames(assemblyNames);
         event.setOffsetTop(offset.y);
         event.setOffsetLeft(offset.x);
         this.worksheetClient.sendEvent(OFFSET_ASSEMBLIES_URI, event);
      }
   }

   private subscribeToFocus(): void {
      if(this.focusedAssembliesSub != null) {
         this.focusedAssembliesSub.unsubscribe();
         this.focusedAssembliesSub = null;
      }

      if(this.worksheet.focusedAssemblies != null) {
         this.focusedAssembliesSub = this.worksheet.focusedAssemblies.subscribe(
            (focusedAssemblies: WSAssembly[]) => {
               this.refreshDragSelection();
               this.dimUnfocusedAssemblies(focusedAssemblies);
            });
      }

      if(this.focusedSub != null) {
         this.focusedSub.unsubscribe();
      }

      this.focusedSub = this.worksheet.focused.subscribe((focused) => {
         // Cause jsplumb to repaint as it may be in a bad state after being focused.
         if(focused) {
            this.jsp.setSuspendDrawing(true);
         }
      });
   }

   private refreshColumnSourceConnectionTypes(): void {
      const focusedTable = this.worksheet.focusedTable;
      const sourceTable = this.columnSourceTable;

      this.jsp.getAllConnections().forEach((conn) => {
         if(conn.hasType(TYPE_COLUMN_SOURCE)) {
            conn.removeType(TYPE_COLUMN_SOURCE);
            conn.endpoints.forEach((endpt) => endpt.removeType(TYPE_COLUMN_SOURCE));
         }
      });

      if(focusedTable != null && sourceTable != null) {
         const focusedTableId = this.sourceIds[focusedTable.name];
         const sourceTableId = this.sourceIds[sourceTable.name];
         const conns = <any[]> this.jsp.getConnections(
            {source: sourceTableId, target: focusedTableId});

         conns.forEach((conn) => {
            conn.addType(TYPE_COLUMN_SOURCE);
            conn.endpoints.forEach((endpt) => endpt.addType(TYPE_COLUMN_SOURCE));
         });
      }
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

   openContextMenu(event: MouseEvent): void {
      event.preventDefault();
      event.stopPropagation();

      let container = this.worksheet.jspAssemblyGraph.getContainer();
      let dy = container.scrollTop;
      let dx = container.scrollLeft;

      let pastePosition = {
         x: event.offsetX + dx,
         y: event.offsetY + dy
      };

      let options: DropdownOptions = {
         position: {x: event.clientX, y: event.clientY},
         contextmenu: true
      };

      let contextmenu: ActionsContextmenuComponent =
         this.dropdownService.open(ActionsContextmenuComponent, options).componentInstance;
      contextmenu.sourceEvent = event;
      contextmenu.actions = [
         new AssemblyActionGroup([
            {
               id: () => "worksheet assembly-graph-pane paste",
               label: () => "_#(js:Paste)",
               icon: () => "fa fa-paste",
               enabled: () => this.pasteEnabled,
               visible: () => true,
               action: (e: MouseEvent) => this.paste(pastePosition)
            }
         ])
      ];
   }

   paste(position: Point): void {
      this.onPaste.emit([this.worksheet, position]);
   }

   /**
    * Dim unfocused assemblies. If no assembly is focused, then dim no assemblies.
    */
   private dimUnfocusedAssemblies(focusedAssemblies: WSAssembly[]): void {
      this.nonDimmedAssemblies = new Set(focusedAssemblies.map((a) => a.name));

      for(const focusedAssembly of focusedAssemblies) {
         focusedAssembly.dependeds.forEach(
            (dep) => this.nonDimmedAssemblies.add(dep.assemblyName));
      }

      if(focusedAssemblies.length > 0) {
         for(const assembly of this.worksheet.assemblies()) {
            if(this.nonDimmedAssemblies.has(assembly.name) || assembly.dependeds.length === 0) {
               continue;
            }

            // Assembly dependings could be out of date, so need to check dependeds of other
            // assemblies.
            const nonDimmed = focusedAssemblies.some(
               (focusedAssembly) => assembly.dependeds
                  .findIndex((dep) => focusedAssembly.name === dep.assemblyName) >= 0);

            if(nonDimmed) {
               this.nonDimmedAssemblies.add(assembly.name);
            }
         }

      }

      this.setDimmedTypes();
   }

   private setDimmedTypes(): void {
      this.jsp.setSuspendDrawing(true);
      const allConns = this.jsp.getAllConnections();

      if(this.nonDimmedAssemblies.size > 0) {
         allConns.forEach((conn) => {
            const source = this.assemblies[conn.sourceId];
            const target = this.assemblies[conn.targetId];

            if(!this.nonDimmedAssemblies.has(source.name) ||
               !this.nonDimmedAssemblies.has(target.name))
            {
               this.dimConnection(conn);
            }
            else {
               this.undimConnection(conn);
            }
         });
      }
      else {
         allConns.forEach((conn) => {
            this.undimConnection(conn);
         });
      }
   }

   private dimConnection(conn: any): void {
      conn.addType(TYPE_DIMMED);
      conn.endpoints.forEach((endpt) => endpt.addType(TYPE_DIMMED));
      conn.hideOverlay(DEPENDENCY_TYPE_OVERLAY_ID);
   }

   private undimConnection(conn: any): void {
      conn.removeType(TYPE_DIMMED);
      conn.endpoints.forEach((endpt) => endpt.removeType(TYPE_DIMMED));
      conn.showOverlay(DEPENDENCY_TYPE_OVERLAY_ID);
   }

   private windowMousemove(initialDragPoint: Point, mousemoveEvent: MouseEvent): void {
      const xOffset = mousemoveEvent.clientX - initialDragPoint.x;
      const yOffset = mousemoveEvent.clientY - initialDragPoint.y;
      const distOffset = Math.sqrt(Math.pow(xOffset, 2) + Math.pow(yOffset, 2));

      // If offset is large enough, create the copy-drag elements.
      if(this.dragCopyElements == null && distOffset > START_DRAG_COPY_OFFSET_DISTANCE) {
         this.dragCopyElements = this.dragAssemblies.map((assembly) => {
            const id = this.sourceIds[assembly.name];
            const originalEl = this.document.getElementById(id);
            const parent = originalEl.parentElement;
            const elClone = originalEl.cloneNode(true) as HTMLElement;
            this.renderer.removeAttribute(elClone, "id");
            const parentClone = parent.cloneNode(false) as HTMLElement;
            parentClone.appendChild(elClone);
            this.renderer.appendChild(this.jspContainerMain.nativeElement, parentClone);
            this.renderer.addClass(elClone, "ws-graph-thumbnail--drag-copy");
            return {element: elClone, assembly};
         });
      }

      if(this.dragCopyElements != null) {
         this.dragCopyElements.forEach((el) => {
            const top = el.assembly.top + yOffset;
            const left = el.assembly.left + xOffset;
            this.renderer.setStyle(el.element, "top", top + "px");
            this.renderer.setStyle(el.element, "left", left + "px");
         });
      }
   }

   private windowMouseup(initialDragPoint: Point, mouseupEvent: MouseEvent): void {
      this.cleanupMouseupListener();

      if(!!this.mousemoveListener) {
         this.mousemoveListener();
      }

      if(this.dragCopyElements != null) {
         this.dragCopyElements.forEach((el) => {
            this.renderer.removeChild(this.jspContainerMain.nativeElement, el.element.parentElement);
         });

         const xOffset = mouseupEvent.clientX - initialDragPoint.x;
         const yOffset = mouseupEvent.clientY - initialDragPoint.y;
         this.sendDragPasteEvent(xOffset, yOffset);
      }
   }

   private cleanupMouseupListener(): void {
      window.removeEventListener("mouseup", this.mouseupFn, true);
   }

   private sendDragPasteEvent(xOffset: number, yOffset: number): void {
      const assemblies = this.worksheet.getCurrentFocusedAssemblies();

      if(assemblies.length === 0) {
         return;
      }

      const topLeft = new Point(Infinity, Infinity);

      assemblies.forEach((a) => {
         topLeft.y = Math.min(topLeft.y, a.top);
         topLeft.x = Math.min(topLeft.x, a.left);
      });

      const targetPoint = new Point(topLeft.x + xOffset, topLeft.y + yOffset);
      const event = new WSPasteAssembliesEvent();
      event.setAssemblies(assemblies.map((a) => a.name));
      event.setCut(false);
      event.setTop(Math.max(targetPoint.y, 0));
      event.setLeft(Math.max(targetPoint.x, 0));
      event.setSourceRuntimeId(this.worksheet.runtimeId);
      this.worksheet.socketConnection.sendEvent(DRAG_PASTE_URI, event);
   }

   private isArrowKey(keyCode: number): boolean {
      return keyCode >= 37 && keyCode <= 40;
   }

   private arrowKeyMove(event: KeyboardEvent): void {
      const focusedAssemblies = this.worksheet.getCurrentFocusedAssemblies();

      if(focusedAssemblies.length === 0) {
         return;
      }

      let topOffset = 0;
      let leftOffset = 0;
      const keyCode = Tool.getKeyCode(event);

      switch(keyCode) {
         case 37: // left arrow key
            leftOffset = -1;
            break;
         case 39: // right arrow key
            leftOffset = 1;
            break;
         case 38: // up arrow key
            topOffset = -1;
            break;
         case 40: // down arrow key
            topOffset = 1;
            break;
         default:
            console.error(keyCode);
      }

      const factor = this.getArrowKeyMoveFactor(event);
      topOffset *= factor;
      leftOffset *= factor;

      focusedAssemblies.forEach((a) => {
         a.top = Math.max(a.top + topOffset, 0);
         a.left = Math.max(a.left + leftOffset, 0);
      });

      this.jsp.setSuspendDrawing(true);
   }

   private getArrowKeyMoveFactor(event: KeyboardEvent): number {
      let factor = ARROW_KEY_MOVE_FACTOR_BASE;

      if(event.shiftKey) {
         factor *= ARROW_KEY_MOVE_FACTOR_COARSE;
      }

      if(event.altKey) {
         factor *= ARROW_KEY_MOVE_FACTOR_FINE;
      }

      return factor;
   }

   private arrowKeyMoveEnd(): void {
      const assemblies = this.worksheet.assemblies();

      if(assemblies.length === 0) {
         return;
      }

      const names = assemblies.map((a) => a.name);
      const tops = assemblies.map((a) => a.top);
      const lefts = assemblies.map((a) => a.left);
      const event = new WSRelocateAssembliesEvent(names, tops, lefts);
      this.worksheetClient.sendEvent(RELOCATE_ASSEMBLIES_URI, event);
   }

   isWorksheetEmpty(): boolean {
      return this.worksheet && this.worksheet.tables.length === 0
         && this.worksheet.variables.length === 0 && this.worksheet.groupings.length === 0;
   }

   /**
    * Select all highlighted dependencies
    */
   selectDependent() {
      const focusedAssemblies = this.worksheet.getCurrentFocusedAssemblies();
      const selectAssemblies = new Set<WSAssembly>();

      for(let assembly of this.worksheet.assemblies()) {
         // currently focused then get all dependent assemblies
         if(focusedAssemblies.some((a) => a === assembly)) {
            for(let dep of assembly.dependeds) {
               selectAssemblies.add(
                  this.worksheet.assemblies().find((a) => a.name === dep.assemblyName));
            }
         }
         // if not focused then check if it depends on the focused assembly
         else if(assembly.dependeds.some(
            (dep) => focusedAssemblies.some(
               (a) => a.name === dep.assemblyName)))
         {
            selectAssemblies.add(assembly);
         }
      }

      // select the assemblies here
      for(let assembly of selectAssemblies) {
         this.worksheet.selectAssembly(assembly);
      }
   }
}
