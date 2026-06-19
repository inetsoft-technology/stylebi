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
import {
   AfterViewInit,
   Component,
   ElementRef,
   EventEmitter,
   Input,
   NgZone,
   OnDestroy,
   OnInit,
   Output,
   ViewChild
} from "@angular/core";
import { CdkDragDrop, moveItemInArray, transferArrayItem } from "@angular/cdk/drag-drop";
import { Tool } from "../../../../../../../shared/util/tool";
import { TextLayoutModel, TextLayoutRowModel, TextLayoutItemModel } from "../../../../common/data/visual-frame-model";
import { AestheticInfo } from "../../../data/chart/aesthetic-info";
import { ChartAggregateRef } from "../../../data/chart/chart-aggregate-ref";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { DragDropModule } from "@angular/cdk/drag-drop";
import { ChartAestheticMc } from "./chart-aesthetic-mc.component";
import { BlockMouseDirective } from "../../../../widget/mouse-event/block-mouse.directive";

@Component({
   selector: "text-layout-designer",
   templateUrl: "./text-layout-designer.component.html",
   styleUrls: ["./text-layout-designer.component.scss"],
   imports: [CommonModule, FormsModule, DragDropModule, ChartAestheticMc, BlockMouseDirective]
})
export class TextLayoutDesignerComponent implements OnInit, AfterViewInit, OnDestroy {
   @Input() layout: TextLayoutModel;
   @Input() textFields: AestheticInfo[] = []; // the real binding list FIELD items index into
   @Output() onCommit = new EventEmitter<TextLayoutModel>();
   @Output() onCancel = new EventEmitter<void>();
   @Output() onFormatField = new EventEmitter<string>(); // emits stub key (fullName or "_static:text")
   @Output() onPreSaveLayout = new EventEmitter<TextLayoutModel>(); // pre-save to create stubs before Format panel
   @Output() onAddField = new EventEmitter<{ field: AestheticInfo; insertRow: number; insertIndex: number }>();
   // Emitted when a FIELD chip is removed and its binding is no longer referenced, so the host can
   // drop the orphaned textFields entry at this index (the component has already compacted indices).
   @Output() onRemoveField = new EventEmitter<number>();
   // Emitted when a FIELD chip's binding is edited (aggregate/value-type/convert) so the host
   // can round-trip the binding model and persist the change to textFields.
   @Output() onFieldChange = new EventEmitter<void>();

   @ViewChild("layoutGrid") private layoutGridRef: ElementRef<HTMLElement>;

   workingRows: TextLayoutRowModel[] = [];

   selectedRowIndex: number = -1;
   selectedItemIndex: number = -1;

   readonly FIELD = 0;
   readonly STATIC = 1;
   readonly SPACING = 2;

   readonly PALETTE_LIST_ID = "palette-list";

   paletteItems: { type: number; label: string }[] = [];

   // Sentinel aggregate with classType="allaggregate" so chart-fieldmc emits onChangeAesthetic
   // instead of calling changeChartRef() to the server.
   readonly allChartSentinel: ChartAggregateRef = { classType: "allaggregate" } as ChartAggregateRef;

   // No-op drag-complete callback — chart-fieldmc requires this to handle dragging chips away.
   readonly fieldChipDragComplete = (_index: number): void => {};

   private gridDragoverHandler: (e: DragEvent) => void;
   private gridDragenterHandler: (e: DragEvent) => void;
   private gridDropHandler: (e: DragEvent) => void;

   constructor(private ngZone: NgZone) {}

   /** trackBy for the layout/palette *ngFor loops — track by position. */
   trackByIndex(index: number): number {
      return index;
   }

   ngOnInit(): void {
      if(this.layout?.rows?.length) {
         this.workingRows = Tool.clone(this.layout.rows);
      }

      this.paletteItems = [
         { type: this.STATIC, label: "_#(Text)" },
         { type: this.SPACING, label: "_#(Spacer)" }
      ];
   }

   ngAfterViewInit(): void {
      this.gridDragoverHandler = (event: DragEvent) => {
         event.preventDefault();
         if(event.dataTransfer) {
            event.dataTransfer.dropEffect = "move";
         }
      };

      this.gridDropHandler = (event: DragEvent) => {
         const rowIndex = this.getRowIndexFromEvent(event);
         if(rowIndex >= 0) {
            this.ngZone.run(() => this.onBindingTreeDrop(event, rowIndex, true));
         }
      };

      this.gridDragenterHandler = (event: DragEvent) => {
         event.preventDefault();
         if(event.dataTransfer) {
            event.dataTransfer.dropEffect = "move";
         }
      };

      this.ngZone.runOutsideAngular(() => {
         const el = this.layoutGridRef.nativeElement;
         el.addEventListener("dragenter", this.gridDragenterHandler);
         el.addEventListener("dragover", this.gridDragoverHandler);
         el.addEventListener("drop", this.gridDropHandler);
      });
   }

   ngOnDestroy(): void {
      const el = this.layoutGridRef?.nativeElement;
      if(el) {
         el.removeEventListener("dragenter", this.gridDragenterHandler);
         el.removeEventListener("dragover", this.gridDragoverHandler);
         el.removeEventListener("drop", this.gridDropHandler);
      }
   }

   private getRowIndexFromEvent(event: DragEvent): number {
      let el = event.target as HTMLElement;
      const grid = this.layoutGridRef.nativeElement;
      while(el && el !== grid) {
         const idx = el.dataset?.rowIndex;
         if(idx !== undefined) return parseInt(idx, 10);
         el = el.parentElement;
      }
      return -1;
   }

   /** Resolve the bound AestheticInfo for a FIELD item from its fieldIndex. */
   getFieldRef(item: TextLayoutItemModel): AestheticInfo | null {
      if(item?.fieldIndex == null) {
         return null;
      }

      return this.textFields?.[item.fieldIndex] ?? null;
   }

   getFieldLabel(item: TextLayoutItemModel): string {
      const di = this.getFieldRef(item)?.dataInfo as any;
      return di?.fullName ?? di?.attribute ?? "Field";
   }

   // ── Selection ────────────────────────────────────────────────────────────

   get selectedItem(): TextLayoutItemModel | null {
      if(this.selectedRowIndex < 0 || this.selectedItemIndex < 0) return null;
      return this.workingRows[this.selectedRowIndex]?.items[this.selectedItemIndex] ?? null;
   }

   getSelectedRow(): TextLayoutRowModel | null {
      return this.selectedRowIndex >= 0 ? (this.workingRows[this.selectedRowIndex] ?? null) : null;
   }

   selectItem(ri: number, ii: number): void {
      this.selectedRowIndex = ri;
      this.selectedItemIndex = ii;
   }

   selectRow(ri: number): void {
      this.selectedRowIndex = ri;
      this.selectedItemIndex = -1;
   }

   isItemSelected(ri: number, ii: number): boolean {
      return this.selectedRowIndex === ri && this.selectedItemIndex === ii;
   }

   isRowSelected(ri: number): boolean {
      return this.selectedRowIndex === ri;
   }

   // ── Row alignment ────────────────────────────────────────────────────────

   setRowAlign(align: string): void {
      const row = this.getSelectedRow();
      if(row) {
         row.horizontalAlign = align === "left" ? undefined : align;
      }
   }

   getRowAlign(): string {
      return this.getSelectedRow()?.horizontalAlign ?? "left";
   }

   // ── CDK drop-list IDs ────────────────────────────────────────────────────

   /** IDs of the row item lists only — palette is NOT included so row items can't drop onto it. */
   get rowItemListIds(): string[] {
      return this.workingRows.map((_, i) => `items-${i}`);
   }

   // ── Drag-and-drop handlers ───────────────────────────────────────────────

   onItemDroppedInRow(event: CdkDragDrop<any[]>, rowIndex: number): void {
      if(event.previousContainer.id === this.PALETTE_LIST_ID) {
         // Palette drag: create a new item — do NOT transfer (palette chips stay)
         const template = event.previousContainer.data[event.previousIndex];
         const newItem: TextLayoutItemModel = template.type === this.STATIC
            ? { type: this.STATIC, text: "", fontSize: 10, bold: false, italic: false }
            : { type: this.SPACING, spacingAmount: 10 };
         this.workingRows[rowIndex].items.splice(event.currentIndex, 0, newItem);
         // Restore palette data (CDK may have moved it — reset to keep chips always present)
         this.paletteItems = [
            { type: this.STATIC, label: "_#(Text)" },
            { type: this.SPACING, label: "_#(Spacer)" }
         ];
         if(newItem.type === this.STATIC) {
            this.selectedRowIndex = rowIndex;
            this.selectedItemIndex = event.currentIndex;
         }
      }
      else if(event.previousContainer === event.container) {
         moveItemInArray(event.container.data, event.previousIndex, event.currentIndex);
      }
      else {
         transferArrayItem(
            event.previousContainer.data,
            event.container.data,
            event.previousIndex,
            event.currentIndex
         );
         this.workingRows = this.workingRows.filter(r => r.items.length > 0);
         this.selectedRowIndex = -1;
         this.selectedItemIndex = -1;
      }
   }

   onRowDropped(event: CdkDragDrop<TextLayoutRowModel[]>): void {
      moveItemInArray(this.workingRows, event.previousIndex, event.currentIndex);
      this.selectedRowIndex = -1;
      this.selectedItemIndex = -1;
   }

   addRow(): void {
      this.workingRows.push({ items: [] });
   }

   removeItem(rowIndex: number, itemIndex: number): void {
      this.workingRows[rowIndex].items.splice(itemIndex, 1);
      if(this.workingRows[rowIndex].items.length === 0) {
         this.workingRows.splice(rowIndex, 1);
      }
      this.selectedRowIndex = -1;
      this.selectedItemIndex = -1;
   }

   commit(): void {
      this.onCommit.emit({ rows: this.workingRows });
   }

   cancel(): void {
      this.onCancel.emit();
   }

   onBindingTreeDrop(event: DragEvent, rowIndex: number, insertAtEnd: boolean = true): void {
      event.preventDefault();
      event.stopPropagation();

      const raw = event.dataTransfer?.getData("text");
      let data: any;
      try {
         data = JSON.parse(raw);
      }
      catch {
         return;
      }

      const entries: any[] = data?.column ?? data?.columns;
      if(!entries?.length) return;

      const entry = entries[0];
      const field = this.buildAestheticInfo(entry);

      if(!field) {
         return;
      }

      const insertIndex = insertAtEnd ? this.workingRows[rowIndex].items.length : 0;

      // Build the field client-side and add it synchronously (the host appends it to textFields
      // and passes the new array back via [textFields]). No server round-trip, so the new field's
      // index is simply the current textFields length — and the chip resolves immediately.
      const newIndex = this.textFields?.length ?? 0;

      this.workingRows[rowIndex].items.splice(
         insertIndex, 0, { type: this.FIELD, fieldIndex: newIndex });

      // Optimistically reflect the append in our own [textFields] so a second rapid drop computes
      // the next index correctly — the host re-passes [textFields] only on the next change-detection
      // cycle, so we cannot rely on the @Input being current between two synchronous drops. The host
      // appends this same field object, so the eventual @Input replacement is consistent.
      this.textFields = [...(this.textFields ?? []), field];

      this.onAddField.emit({ field, insertRow: rowIndex, insertIndex });
   }

   /**
    * Build an AestheticInfo from a dropped data-tree entry, client-side. The backend turns the
    * dataInfo into a real aggregated ref via pasteAestheticRef on commit; classType is the Jackson
    * discriminator ("aggregate" for measures, "dimension" otherwise).
    */
   private buildAestheticInfo(entry: any): AestheticInfo | null {
      const props = entry?.properties || {};
      const attr: string = props.attribute
         ?? (entry?.path ? entry.path.split("/").pop() : null)
         ?? entry?.name;

      if(!attr) {
         return null;
      }

      const dtype: string = props.dtype;
      const caption: string = props.caption ?? attr;
      const refType: number = props.refType ? parseInt(props.refType, 10) : 0;
      const measureTypes = ["integer", "double", "float", "long", "short", "byte", "decimal", "number"];
      const isMeasure = !!dtype && measureTypes.indexOf(dtype.toLowerCase()) >= 0;

      const dataInfo: any = isMeasure
         ? {
            classType: "aggregate", columnValue: attr, caption, measure: true,
            formula: "Sum", aggregated: true, refType, refConvertEnabled: true,
            dataType: dtype, fullName: caption, view: caption, name: attr
         }
         : {
            classType: "dimension", columnValue: attr, caption, measure: false,
            refType, refConvertEnabled: true, dataType: dtype,
            fullName: caption, view: caption, name: attr
         };

      // The AestheticInfo wrapper's Jackson discriminator is the class name "AestheticInfo"
      // (@JsonTypeInfo NAME, no subtypes). The "aggregate"/"dimension" discriminator belongs on
      // dataInfo (ChartRefModel). Getting the wrapper classType wrong makes Jackson drop the whole
      // textFields list on the backend.
      return { classType: "AestheticInfo", fullName: caption, dataInfo, frame: null };
   }

   removeFieldItem(rowIndex: number, itemIndex: number): void {
      const removed = this.workingRows[rowIndex]?.items[itemIndex];
      const fi = removed?.type === this.FIELD ? removed.fieldIndex : -1;

      this.workingRows[rowIndex].items.splice(itemIndex, 1);
      if(this.workingRows[rowIndex].items.length === 0) {
         this.workingRows.splice(rowIndex, 1);
      }

      // A FIELD item indexes into textFields. Once removed, drop the now-orphaned binding so it
      // doesn't round-trip to the backend as a dead aesthetic ref — unless another FIELD item still
      // references the same entry (e.g. the same column dropped twice). Removing the entry shifts
      // every higher index down by one, so compact the remaining FIELD items' fieldIndex to match,
      // and tell the host to drop the same index from its authoritative textFields list.
      if(fi >= 0) {
         const stillReferenced = this.workingRows.some(
            r => r.items.some(it => it.type === this.FIELD && it.fieldIndex === fi));

         if(!stillReferenced) {
            this.removeOrphanedField(fi);
         }
      }

      this.selectedRowIndex = -1;
      this.selectedItemIndex = -1;
   }

   /**
    * Drop the orphaned textFields entry at index fi: remove the binding, compact every higher
    * FIELD item's fieldIndex down by one to match, and tell the host to drop the same index from
    * its authoritative textFields list. Shared by removeFieldItem and removeRow so the orphan
    * contract stays in one place.
    */
   private removeOrphanedField(fi: number): void {
      this.textFields = (this.textFields ?? []).filter((_, idx) => idx !== fi);

      for(const r of this.workingRows) {
         for(const it of r.items) {
            if(it.type === this.FIELD && it.fieldIndex > fi) {
               it.fieldIndex = it.fieldIndex - 1;
            }
         }
      }

      this.onRemoveField.emit(fi);
   }

   /**
    * Delete an entire row and all of its items. Rows below shift up automatically. Each FIELD item
    * in the row drops its backing textFields entry — unless another remaining row still references
    * the same binding (e.g. the same column placed twice). Orphaned indices are processed highest
    * first so each onRemoveField emit (which splices the host's authoritative textFields list) does
    * not shift the indices still pending removal; the remaining FIELD items' fieldIndex is compacted
    * to match. Mirrors removeFieldItem's single-item orphan/compaction logic.
    */
   removeRow(rowIndex: number): void {
      const row = this.workingRows[rowIndex];

      if(!row) {
         return;
      }

      // Remove the row first; remaining rows shift up via splice.
      this.workingRows.splice(rowIndex, 1);

      // Unique FIELD indices that were in the removed row, now orphaned only if no surviving row
      // still references them. Sort descending so removals don't invalidate pending indices.
      const orphaned = Array.from(new Set(
         (row.items ?? [])
            .filter(it => it.type === this.FIELD && it.fieldIndex != null)
            .map(it => it.fieldIndex)))
         .filter(fi => !this.workingRows.some(
            r => r.items.some(it => it.type === this.FIELD && it.fieldIndex === fi)))
         .sort((a, b) => b - a);

      for(const fi of orphaned) {
         this.removeOrphanedField(fi);
      }

      this.selectedRowIndex = -1;
      this.selectedItemIndex = -1;
   }

   openFieldFormat(item: TextLayoutItemModel): void {
      const fullName = (this.getFieldRef(item)?.dataInfo as any)?.fullName;

      if(fullName) {
         this.onPreSaveLayout.emit({ rows: this.workingRows });
         this.onFormatField.emit(fullName);
      }
   }

   /**
    * A FIELD chip's binding was edited (change aggregate / value type / convert dim-measure).
    * chart-fieldmc mutates the bound AestheticInfo.dataInfo in place; tell the host to round-trip
    * the binding model so the edited textFields ref persists.
    */
   onFieldChipChanged(): void {
      this.onFieldChange.emit();
   }

   openStaticItemFormat(item: TextLayoutItemModel): void {
      const key = "_static:" + (item.text ?? "");
      this.onPreSaveLayout.emit({ rows: this.workingRows });
      this.onFormatField.emit(key);
   }
}
