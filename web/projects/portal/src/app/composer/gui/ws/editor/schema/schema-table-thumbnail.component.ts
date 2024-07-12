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
import { DOCUMENT } from "@angular/common";
import {
   AfterViewInit,
   Component,
   ElementRef,
   EventEmitter,
   HostBinding,
   HostListener,
   Inject,
   Input,
   NgZone,
   OnDestroy,
   OnInit,
   Output,
   ViewChild
} from "@angular/core";
import { ColumnRef } from "../../../../../binding/data/column-ref";
import { AssemblyActionGroup } from "../../../../../common/action/assembly-action-group";
import { ActionsContextmenuComponent } from "../../../../../widget/fixed-dropdown/actions-contextmenu.component";
import { DropdownOptions } from "../../../../../widget/fixed-dropdown/dropdown-options";
import { FixedDropdownService } from "../../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { AbstractTableAssembly } from "../../../../data/ws/abstract-table-assembly";
import { CompositeTableAssembly } from "../../../../data/ws/composite-table-assembly";
import { RelationalJoinTableAssembly } from "../../../../data/ws/relational-join-table-assembly";
import { TableColumnPair } from "../../../../data/ws/table-column-pair";
import { WSTableActions } from "../../action/ws-table.actions";
import { WSResizeSchemaTableEvent } from "../../socket/ws-resize-schema-event";
import { SchemaThumbnailService } from "./schema-thumbnail.service";

export type SubtableInteractionEvent =
   MouseEvent & {subtable: AbstractTableAssembly, mousedown: boolean, click: boolean};

type ResizeActiveSide = "left" | "right" | null;

@Component({
   selector: "schema-table-thumbnail",
   templateUrl: "schema-table-thumbnail.component.html",
   styleUrls: ["schema-table-thumbnail.component.scss"]
})
export class SchemaTableThumbnailComponent implements OnInit, AfterViewInit, OnDestroy {
   @Input() schemaTable: AbstractTableAssembly;
   @Input() joinTable: RelationalJoinTableAssembly;
   @Input() columns: ColumnRef[];
   @Input() twoStepJoinColumn: TableColumnPair | null;
   @ViewChild("handle") handle: ElementRef;
   @Output() onSubtableInteraction = new EventEmitter<SubtableInteractionEvent>();
   @Output() onFocusCompositeTable = new EventEmitter<CompositeTableAssembly>();
   @Output() onDelete = new EventEmitter<void>();
   @Output() onResizeSchemaTable = new EventEmitter<WSResizeSchemaTableEvent>();
   private _selected: boolean;

   // Resize fields
   private readonly RESIZE_MIN_WIDTH = 40;
   private readonly moveResizeListener = this.moveResize.bind(this);
   private readonly endResizeListener = this.endResize.bind(this);
   private resizeXOrigin: number;
   resizeXOffset: number = 0;
   resizeActiveSide: ResizeActiveSide;

   @Input()
   set selected(selected: boolean) {
      if(!this._selected && selected) {
         this.schemaThumbnailService.addToDragSelection([this.schemaTable.name]);
      }
      else if(this._selected && !selected) {
         this.schemaThumbnailService.removeFromDragSelection([this.schemaTable.name]);
      }

      this._selected = selected;
   }

   get selected(): boolean {
      return this._selected;
   }

   constructor(public hostRef: ElementRef,
               private schemaThumbnailService: SchemaThumbnailService,
               private zone: NgZone,
               private fixedDropdownService: FixedDropdownService,
               @Inject(DOCUMENT) private document: any)
   {
   }

   ngOnInit(): void {
      this.schemaThumbnailService.registerTable(this.schemaTable.name, this.hostRef);
   }

   ngAfterViewInit(): void {
      let dragLeader: boolean = false;

      const updatePos = (params: any) => {
         if(dragLeader) {
            dragLeader = false;
            const offsetTop = params.pos[1] - this.position.top;
            const offsetLeft = params.pos[0] - this.position.left;

            if(offsetTop !== 0 || offsetLeft !== 0) {
               this.schemaThumbnailService.moveSchemas(offsetTop, offsetLeft);
            }
         }

         this.zone.run(() => {
            this.position = {left: params.pos[0], top: params.pos[1]};
         });
      };

      const checkDragLeader = (params: any) => {
         const event: MouseEvent = params.e;

         // If params has mouse event e, then this assembly should be the "leader"; this assembly
         // is the one being dragged.
         if(event) {
            dragLeader = true;
         }
      };

      this.zone.runOutsideAngular(() =>
         this.jsp.draggable(this.hostRef.nativeElement, {
            start: checkDragLeader,
            stop: updatePos,
            consumeStartEvent: false,
            handle: ".jsplumb-draggable-handle, .jsplumb-draggable-handle *"
         }));
   }

   ngOnDestroy(): void {
      this.schemaThumbnailService.deregisterTable(this.schemaTable.name);
   }

   @HostBinding("style.top.px")
   public get top(): number {
      return this.position.top;
   }

   @HostBinding("style.left.px")
   public get left(): number {
      return this.position.left;
   }

   @HostBinding("style.width.px")
   public get width(): number {
      return this.joinTable.info.schemaTableInfos[this.schemaTable.name].width;
   }

   @HostListener("contextmenu", ["$event"])
   showContextmenu(event: MouseEvent): void {
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

   deleteSubtables(): void {
      this.onDelete.emit();
   }

   interactWithSubtable(event: MouseEvent, mousedown: boolean): void {
      this.onSubtableInteraction
         .emit(Object.assign(event, {subtable: this.schemaTable, mousedown, click: !mousedown}));
   }

   focusCompositeTable(): void {
      if(this.schemaTable instanceof CompositeTableAssembly) {
         this.onFocusCompositeTable.emit(this.schemaTable);
      }
   }

   /**
    * Start resizing this schema table thumbnail.
    */
   startResize(event: MouseEvent, side: ResizeActiveSide): void {
      this.document.addEventListener("mousemove", this.moveResizeListener);
      this.document.addEventListener("mouseup", this.endResizeListener);
      this.resizeXOrigin = event.pageX;
      this.resizeActiveSide = side;
   }

   /**
    * Update the resize field(s) according to the mousemove event.
    */
   private moveResize(event: MouseEvent): void {
      let xOffset = event.pageX - this.resizeXOrigin;
      const offsetLocation = this.isResizeOffsetLocation(this.resizeActiveSide);
      const width = this.getResizeWidth(xOffset, offsetLocation);
      // Prevent xOffset from making width smaller than min width.
      const minWidthBuffer = Math.max(0, this.RESIZE_MIN_WIDTH - width);

      if(offsetLocation) {
         // Prevent width expanding from causing the left position to go negative.
         if(width > this.width) {
            xOffset = Math.max(this.width - width, -this.left);
         }
         else {
            xOffset -= minWidthBuffer;
         }
      }
      else {
         xOffset += minWidthBuffer;
      }

      this.resizeXOffset = xOffset;
   }

   /**
    * End resizing the schema table thumbnail.
    */
   private endResize(event: MouseEvent): void {
      this.moveResize(event);
      const offsetLocation = this.isResizeOffsetLocation(this.resizeActiveSide);
      const width = this.getResizeWidth(this.resizeXOffset, offsetLocation);
      this.resizeSchemaTable(width, offsetLocation);

      this.document.removeEventListener("mousemove", this.moveResizeListener);
      this.document.removeEventListener("mouseup", this.endResizeListener);
      this.resizeXOrigin = null;
      this.resizeXOffset = null;
      this.resizeActiveSide = null;
   }

   private resizeSchemaTable(width: number, offsetLocation: boolean): void {
      const event = new WSResizeSchemaTableEvent();
      event.setJoinTableName(this.joinTable.name);
      event.setSchemaTableName(this.schemaTable.name);
      event.setWidth(width);
      event.setOffsetLocation(offsetLocation);
      this.onResizeSchemaTable.emit(event);
   }

   private getResizeWidth(resizeXOffset: number, offsetLocation: boolean): number {
      const offset = offsetLocation ? -resizeXOffset : resizeXOffset;
      return offset + this.width;
   }

   /**
    * Return true if the schema table thumbnail will have to have its location offset as a result
    * of the width resizing.
    */
   private isResizeOffsetLocation(side: ResizeActiveSide): boolean {
      return side === "left";
   }

   public createActions(): AssemblyActionGroup[] {
      return [
         new AssemblyActionGroup([
            {
               id: () => "worksheet schema-table focus-composite-table",
               label: () => WSTableActions.getEditCompositionLabel(this.schemaTable),
               icon: () => null,
               enabled: () => true,
               visible: () => this.schemaTable instanceof CompositeTableAssembly,
               action: () => this.focusCompositeTable()
            },
            {
               id: () => "worksheet schema-table remove-tables",
               label: () => "_#(js:Remove tables)",
               icon: () => null,
               enabled: () => true,
               visible: () => true,
               action: () => this.deleteSubtables()
            }
         ])
      ];
   }

   private get jsp(): JSPlumb.JSPlumbInstance {
      return this.schemaThumbnailService.jsPlumbInstance;
   }

   private set position(pos: {left: number, top: number}) {
      const oldInfo = this.joinTable.info.schemaTableInfos[this.schemaTable.name];
      this.joinTable.info.schemaTableInfos[this.schemaTable.name] = {...oldInfo, ...pos};
   }

   private get position(): {left: number, top: number} {
      return this.joinTable.info.schemaTableInfos[this.schemaTable.name];
   }
}
