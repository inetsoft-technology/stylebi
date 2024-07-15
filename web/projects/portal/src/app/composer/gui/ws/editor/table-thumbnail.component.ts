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
import {
   AfterViewInit,
   Component,
   ElementRef,
   EventEmitter,
   HostBinding,
   HostListener,
   Input,
   OnChanges,
   OnDestroy,
   Output,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { DragEvent } from "../../../../common/data/drag-event";
import { AssetType } from "../../../../../../../shared/data/asset-type";
import { Notification } from "../../../../common/data/notification";
import { Point } from "../../../../common/data/point";
import { AssetTreeService } from "../../../../widget/asset-tree/asset-tree.service";
import { DragService } from "../../../../widget/services/drag.service";
import { ModelService } from "../../../../widget/services/model.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { AbstractTableAssembly } from "../../../data/ws/abstract-table-assembly";
import { CompositeTableAssembly } from "../../../data/ws/composite-table-assembly";
import { ConcatenatedTableAssembly } from "../../../data/ws/concatenated-table-assembly";
import { MergeJoinTableAssembly } from "../../../data/ws/merge-join-table-assembly";
import { MirrorTableAssembly } from "../../../data/ws/mirror-table-assembly";
import { RelationalJoinTableAssembly } from "../../../data/ws/relational-join-table-assembly";
import { Worksheet } from "../../../data/ws/worksheet";
import { WSAssembly } from "../../../data/ws/ws-assembly";
import { WorksheetTableOperator } from "../../../data/ws/ws-table.operators";
import { WSTableActions } from "../action/ws-table.actions";
import { WSInsertColumnsEvent } from "../socket/ws-insert-columns/ws-insert-columns-event";
import { WSAssemblyThumbnail } from "./ws-assembly-thumbnail";
import { WSAssemblyThumbnailTitleComponent } from "./ws-assembly-thumbnail-title.component";
import {
   FeatureFlagsService,
   FeatureFlagValue
} from "../../../../../../../shared/feature-flags/feature-flags.service";

let timer: any = null;
const timerFunctions: (() => void)[] = [];

const RELATIONAL_JOIN_ICON = "⋈";
const CROSS_JOIN_ICON = "X";
const MERGE_JOIN_ICON = "➕";
const UNION_ICON = "⋃";
const INTERSECTION_ICON = "⋂";
const MINUS_ICON = "－";

/**
 * Component responsible for the table thumbnails within the graph pane.
 */
@Component({
   selector: "table-thumbnail",
   templateUrl: "table-thumbnail.component.html",
   styleUrls: [
      "thumbnail-base.scss",
      "table-thumbnail.component.scss",
      "../jsplumb/jsplumb-shared.scss"
   ]
})
export class TableThumbnailComponent extends WSAssemblyThumbnail
   implements OnChanges, AfterViewInit, OnDestroy
{
   /** Reference to the Worksheet Table */
   @Input() table: AbstractTableAssembly;
   @Input() worksheet: Worksheet;
   @Input() tableEndpoints: any[];
   @Input() sqlEnabled = true;
   @Input() freeFormSqlEnabled = true;
   @Output() onRemove = new EventEmitter<WSAssembly>();
   @Output() onDestroy = new EventEmitter<WSAssembly>();
   @Output() onCut = new EventEmitter<WSAssembly>();
   @Output() onCopy = new EventEmitter<WSAssembly>();
   @Output() onEditCompositionTable = new EventEmitter<AbstractTableAssembly>();
   @Output() onEditQuery = new EventEmitter<AbstractTableAssembly>();
   @Output() onRefreshAssembly = new EventEmitter<[string, any]>();
   @Output() onRegisterAssembly = new EventEmitter<[WSAssembly, string]>();
   @Output() onDragPasteAssemblies = new EventEmitter<Point>();
   @Output() onMoveAssemblies = new EventEmitter<Point>();
   @Output() onSetDraggable = new EventEmitter<[any, any]>();
   @Output() onStartEditName = new EventEmitter<void>();
   @Output() onEditName = new EventEmitter<string>();
   @Output() onNotify = new EventEmitter<Notification>();
   @Output() onToggleEndpoints = new EventEmitter<{element: HTMLElement}>();
   @Output() onHideEndpoints = new EventEmitter<{element: HTMLElement}>();
   @Output() onAddEndpoint = new EventEmitter<[any, any]>();
   @Output() onInsertColumns = new EventEmitter<WSInsertColumnsEvent>();
   @Output() onOpenAssemblyConditionDialog = new EventEmitter<string>();
   @Output() onOpenAggregateDialog = new EventEmitter<string>();
   @Output() onOpenSortColumnDialog = new EventEmitter<string>();
   @Output() onSelectDependent = new EventEmitter<void>();
   @ViewChild("thumbnailTitle") wsAssemblyThumbnailTitle: WSAssemblyThumbnailTitleComponent;

   dragColumnsOverlayDimensions: ClientRect;
   dragColumnsOverlayDropSide: "left" | "right";
   titleTooltip: string;
   private dragEndSub: Subscription;
   private editCompositionTableSub: Subscription;
   private editQuerySub: Subscription;

   constructor(private dragService: DragService,
               protected modalService: DialogService,
               protected ngbModal: NgbModal,
               protected modelService: ModelService,
               protected thumbnail: ElementRef,
               protected featureFlagsService: FeatureFlagsService)
   {
      super();
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.hasOwnProperty("table")) {
         super.onChange(changes["table"]);
         this.queueUpdateDragOverlayDimensions();
         this.cleanupActionSubs();
         this.initActionSubs();
      }
   }

   ngAfterViewInit() {
      super.ngAfterViewInit();
      this.endPointsInit();
      this.dragColumnsOverlayDimensions = this.thumbnail.nativeElement.getBoundingClientRect();
      this.dragEndSub = this.dragService.dragEndSubject
         .subscribe(() => this.clearOverlayDropSide());
   }

   ngOnDestroy() {
      super.ngOnDestroy();
      this.dragEndSub.unsubscribe();
      this.cleanupActionSubs();
   }

   @HostBinding("style.top.px")
   get top(): number {
      return this.table.top;
   }

   @HostBinding("style.left.px")
   get left(): number {
      return this.table.left;
   }

   @HostBinding("style.width.px")
   get styleWidth(): number {
      return this.width;
   }

   @HostBinding("attr.data-ws-assembly-name")
   get dataWSAssemblyName(): string {
      return this.table.name;
   }

   @HostListener("mouseup", ["$event"])
   mouseup(event: MouseEvent): void {
      if(event.button === 0) {
         this.toggleEndpoints();
      }
   }

   @HostListener("blur")
   blur(): void {
      this.hideEndpoints();
   }

   toggleEndpoints(): void {
      // Gives thumbnail focus capability as the jsPlumb library uses preventDefaults() on
      // draggables
      if(this.wsAssemblyThumbnailTitle.inputNameControl == null) {
         this.thumbnail.nativeElement.focus();
      }

      this.onToggleEndpoints.emit({element: this.thumbnail.nativeElement});
   }

   hideEndpoints(): void {
      this.onHideEndpoints.emit({element: this.thumbnail.nativeElement});
   }

   removeAssembly() {
      this.onRemove.emit(this.table);
   }

   cut() {
      this.onCut.emit(this.table);
   }

   copy() {
      this.onCopy.emit(this.table);
   }

   dragOver(event: DragEvent) {
      const canDropColumns =
         (this.dragService.get(AssetTreeService.getDragName(AssetType.COLUMN)) != null ||
         this.dragService.get(AssetTreeService.getDragName(AssetType.PHYSICAL_COLUMN)) != null) &&
            this.table.canDropAssetColumns;

      if(canDropColumns) {
         event.preventDefault();
         this.dragColumnsOverlayDropSide = this.getOverlayDropSide(event);
      }
   }

   clearOverlayDropSide() {
      this.dragColumnsOverlayDropSide = null;
   }

   dropColumns(event: DragEvent) {
      event.preventDefault();
      event.stopPropagation();
      let data = this.dragService.get(AssetTreeService.getDragName(AssetType.COLUMN)) ||
         this.dragService.get(AssetTreeService.getDragName(AssetType.PHYSICAL_COLUMN));
      let entries = JSON.parse(data);

      if(!(entries instanceof Array)) {
         entries = [entries];
      }

      let index = this.getOverlayDropSide(event) === "left" ? 0 : this.table.colCount;

      let insertColsEvent: WSInsertColumnsEvent = new WSInsertColumnsEvent();
      insertColsEvent.setName(this.table.name);
      insertColsEvent.setEntries(entries);
      insertColsEvent.setIndex(index);
      this.onInsertColumns.emit(insertColsEvent);
   }

   openAssemblyConditionDialog() {
      this.onOpenAssemblyConditionDialog.emit(this.table.name);
   }

   openAggregateDialog() {
      this.onOpenAggregateDialog.emit(this.table.name);
   }

   openSortColumnDialog() {
      this.onOpenSortColumnDialog.emit(this.table.name);
   }

   protected updateTitleTooltip() {
      let titleTooltip: string = this.table.name;

      if(this.table instanceof RelationalJoinTableAssembly) {
         const join = this.table as RelationalJoinTableAssembly;
         let subtbls = join.subtables;
         let tooltip = "";

         join.info.operatorGroups.forEach(ops => ops.forEach(op => {
            tooltip += op.ltable + " " + RELATIONAL_JOIN_ICON + " " + op.rtable + " ";
            subtbls = subtbls.filter(t => t != op.ltable && t != op.rtable);
         }));


         subtbls.forEach(t => {
            if(tooltip != "") {
               tooltip += CROSS_JOIN_ICON + " ";
            }

            tooltip += t;
         });

         titleTooltip += ": " + tooltip.trim();
      }
      else if(this.table instanceof CompositeTableAssembly) {
         const compositeTable = this.table as CompositeTableAssembly;

         for(let i = 0; i < compositeTable.subtables.length; i++) {
            const subtable = compositeTable.subtables[i];

            if(i === 0) {
               titleTooltip += `: ${subtable}`;
            }
            else {
               titleTooltip += ` ${this.getOperatorIcon(compositeTable, i)} ${subtable}`;
            }
         }
      }
      else if(this.table instanceof MirrorTableAssembly) {
         titleTooltip += `: ${this.table.info.mirrorInfo.mirrorName}`;
      }

      if(this.table.primary) {
         titleTooltip += " (_#(js:Primary))";
      }
      else if(!this.table.info.visibleTable) {
         titleTooltip += " (_#(js:Hidden))";
      }

      this.titleTooltip = titleTooltip.replace(/(OUTER_|_\d+)/g, "");
   }

   private queueUpdateDragOverlayDimensions() {
      timerFunctions.push(() => {
         if(!this.destroyed && this.thumbnail) {
            this.dragColumnsOverlayDimensions =
               this.thumbnail.nativeElement.getBoundingClientRect();
         }
      });

      if(timer == null) {
         timer = setTimeout(() => {
            timer = null;
            timerFunctions.forEach((fn) => fn());
            timerFunctions.splice(0, timerFunctions.length);
         });
      }
   }

   private endPointsInit(): void {
      const endpoints: any[] = this.tableEndpoints;

      for(const endpoint of endpoints) {
         this.onAddEndpoint.emit([this.thumbnail.nativeElement, endpoint]);
      }
   }

   private getOverlayDropSide(event: DragEvent) {
      const left = event.target["offsetLeft"];
      const right = left + event.target["clientWidth"];
      const rightSide: boolean = event.offsetX > (right - left) / 2;
      return rightSide ? "right" : "left";
   }

   /**
    * Get the operator icon of a table at the given subtable index.
    *
    * @param table the specified table
    * @param subtableIndex the index of the subtable to get the icon of
    *
    * @returns the matching icon for the subtable index
    */
   private getOperatorIcon(table: CompositeTableAssembly, subtableIndex: number): string {
      if(table instanceof MergeJoinTableAssembly) {
         return MERGE_JOIN_ICON;
      }
      else if(table instanceof ConcatenatedTableAssembly) {
         if(subtableIndex < 1 || subtableIndex >= table.subtables.length) {
            throw new RangeError(`Index out of bounds: ${subtableIndex}`);
         }

         const leftSubtable = table.subtables[subtableIndex - 1];
         const rightSubtable = table.subtables[subtableIndex];
         let operator = null;

         for(let ops of table.info.operatorGroups) {
            operator = ops.find(
               (op) => op.ltable === leftSubtable && op.rtable === rightSubtable);

            if(!!operator) {
               break;
            }
         }

         if(!operator) {
            operator = table.info.operatorGroups[subtableIndex - 1][0];
         }

         switch(operator.operation) {
            case WorksheetTableOperator.UNION:
               return UNION_ICON;
            case WorksheetTableOperator.INTERSECT:
               return INTERSECTION_ICON;
            case WorksheetTableOperator.MINUS:
               return MINUS_ICON;
            default:
               throw new TypeError(`Unhandled type: ${operator.operation}`);
         }
      }
      else {
         throw new TypeError(`Unhandled class type: ${table.tableClassType}`);
      }
   }

   private initActionSubs() {
      const actions = this.actions as WSTableActions;
      this.editCompositionTableSub = actions.editCompositionTable
         .subscribe((table) => this.onEditCompositionTable.emit(table));
      this.editQuerySub = actions.editQuery.subscribe((table) => this.onEditQuery.emit(table));
   }

   private cleanupActionSubs() {
      if(!!this.editCompositionTableSub && !this.editCompositionTableSub.closed) {
         this.editCompositionTableSub.unsubscribe();
      }

      if(!!this.editQuerySub && !this.editQuerySub.closed) {
         this.editQuerySub.unsubscribe();
      }
   }
}
