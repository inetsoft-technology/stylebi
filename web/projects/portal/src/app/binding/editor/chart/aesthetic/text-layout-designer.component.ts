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
import { CdkDragDrop, moveItemInArray } from "@angular/cdk/drag-drop";
import { Tool } from "../../../../../../../shared/util/tool";
import { TextLayoutModel, TextLayoutRowModel, TextLayoutItemModel } from "../../../../common/data/visual-frame-model";
import { AestheticInfo } from "../../../data/chart/aesthetic-info";
import { ChartAggregateRef } from "../../../data/chart/chart-aggregate-ref";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { DragDropModule } from "@angular/cdk/drag-drop";
import { ChartAestheticMc } from "./chart-aesthetic-mc.component";
import { BlockMouseDirective } from "../../../../widget/mouse-event/block-mouse.directive";

// Prefix for the index-based Format-panel key ("_layoutfield:<index>"). Keying by index (not the
// client-side, not-yet-aggregated field name) is what fixes the "format reverts on first open" bug
// (#75474). Must match LAYOUT_FIELD_PREFIX on the Java side (FormatPainterService).
export const LAYOUT_FIELD_PREFIX = "_layoutfield:";

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
   @Output() onFormatField = new EventEmitter<string>(); // emits format key ("_layoutfield:<index>" or "_static:text")
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

   // Drop indicator for native HTML5 drags (binding-tree field, item reorder, palette
   // insert): which row and insert position the green line is shown at. -1 = none.
   dropRowIndex: number = -1;
   dropItemIndex: number = -1;

   // Active internal (within-designer) native drag — distinguishes an item reorder or
   // palette insert from a binding-tree field drop (which has no internalDrag). null = none.
   private internalDrag:
      | { kind: "item"; rowIndex: number; itemIndex: number }
      | { kind: "palette"; paletteType: number }
      | null = null;

   readonly FIELD = 0;
   readonly STATIC = 1;
   readonly SPACING = 2;

   paletteItems: { type: number; label: string }[] = [];

   // Sentinel aggregate with classType="allaggregate" so chart-fieldmc emits onChangeAesthetic
   // instead of calling changeChartRef() to the server.
   readonly allChartSentinel: ChartAggregateRef = { classType: "allaggregate" } as ChartAggregateRef;

   // No-op drag-complete callback — chart-fieldmc requires this to handle dragging chips away.
   readonly fieldChipDragComplete = (_index: number): void => {};

   private gridDragoverHandler: (e: DragEvent) => void;
   private gridDragenterHandler: (e: DragEvent) => void;
   private gridDropHandler: (e: DragEvent) => void;
   private gridDragleaveHandler: (e: DragEvent) => void;

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
         const drag = this.internalDrag;
         const targetRow = this.dropRowIndex;
         const targetIndex = this.dropItemIndex;

         this.ngZone.run(() => {
            if(drag) {
               // Internal reorder / palette insert. Prefer the indicator position; if the drop
               // landed off every divider (e.g. directly on a chip), fall back to the row under
               // the cursor + append — consistent with the binding-tree branch below, so the
               // drop is never silently swallowed.
               const rowIndex = targetRow >= 0 ? targetRow : this.getRowIndexFromEvent(event);

               if(rowIndex >= 0) {
                  event.preventDefault();
                  const insertIndex = targetIndex >= 0
                     ? targetIndex : this.workingRows[rowIndex].items.length;

                  if(drag.kind === "palette") {
                     this.insertPaletteItem(drag.paletteType, rowIndex, insertIndex);
                  }
                  else {
                     this.moveItem(drag.rowIndex, drag.itemIndex, rowIndex, insertIndex);
                  }
               }
            }
            else {
               // Field dragged in from the binding tree. Prefer the indicator position;
               // fall back to the row under the cursor + append if no divider was entered.
               const rowIndex = targetRow >= 0 ? targetRow : this.getRowIndexFromEvent(event);

               if(rowIndex >= 0) {
                  const insertIndex = targetRow >= 0 && targetIndex >= 0
                     ? targetIndex : this.workingRows[rowIndex].items.length;
                  this.onBindingTreeDrop(event, rowIndex, insertIndex);
               }
            }

            this.internalDrag = null;
            this.clearDropIndicator();
         });
      };

      this.gridDragenterHandler = (event: DragEvent) => {
         event.preventDefault();
         if(event.dataTransfer) {
            event.dataTransfer.dropEffect = "move";
         }
      };

      // Clear the insertion line when the drag truly leaves the grid — not when the cursor
      // merely moves between child elements inside it (relatedTarget still within the grid).
      this.gridDragleaveHandler = (event: DragEvent) => {
         const related = event.relatedTarget as Node | null;
         if(!related || !this.layoutGridRef.nativeElement.contains(related)) {
            this.ngZone.run(() => this.clearDropIndicator());
         }
      };

      this.ngZone.runOutsideAngular(() => {
         const el = this.layoutGridRef.nativeElement;
         el.addEventListener("dragenter", this.gridDragenterHandler);
         el.addEventListener("dragover", this.gridDragoverHandler);
         el.addEventListener("drop", this.gridDropHandler);
         el.addEventListener("dragleave", this.gridDragleaveHandler);
      });
   }

   ngOnDestroy(): void {
      const el = this.layoutGridRef?.nativeElement;
      if(el) {
         el.removeEventListener("dragenter", this.gridDragenterHandler);
         el.removeEventListener("dragover", this.gridDragoverHandler);
         el.removeEventListener("drop", this.gridDropHandler);
         el.removeEventListener("dragleave", this.gridDragleaveHandler);
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

   // ── Drop insertion indicator (native binding-tree drag) ───────────────────

   /** True when the green insertion line should show before item `k` of row `ri`. */
   isDropTarget(ri: number, k: number): boolean {
      return this.dropRowIndex === ri && this.dropItemIndex === k;
   }

   /** Record where a dragged binding-tree field would be inserted (drives the line). */
   onDividerDragEnter(ri: number, k: number): void {
      this.dropRowIndex = ri;
      this.dropItemIndex = k;
   }

   private clearDropIndicator(): void {
      this.dropRowIndex = -1;
      this.dropItemIndex = -1;
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

   // ── Native drag-and-drop: items + palette ─────────────────────────────────
   // Items and palette chips use native HTML5 drag (not CDK) so existing chips never
   // shift during a drag — only the thin insertion line moves between them, exactly
   // like dragging a field in from the binding tree. (Rows still reorder via CDK from
   // the hamburger handle; CDK only intercepts native drag from that handle, so item
   // drags inside a row are unaffected.)

   onItemDragStart(event: DragEvent, rowIndex: number, itemIndex: number): void {
      this.internalDrag = { kind: "item", rowIndex, itemIndex };

      if(event.dataTransfer) {
         event.dataTransfer.effectAllowed = "move";
         // Firefox requires data on the transfer for the drag to start.
         event.dataTransfer.setData("text", "");
      }
   }

   onPaletteDragStart(event: DragEvent, chip: { type: number }): void {
      this.internalDrag = { kind: "palette", paletteType: chip.type };

      if(event.dataTransfer) {
         event.dataTransfer.effectAllowed = "move";
         event.dataTransfer.setData("text", "");
      }
   }

   onInternalDragEnd(): void {
      this.internalDrag = null;
      this.clearDropIndicator();
   }

   /** True while this item is the one being dragged (so the template can dim it). */
   isDraggingItem(rowIndex: number, itemIndex: number): boolean {
      return this.internalDrag?.kind === "item"
         && this.internalDrag.rowIndex === rowIndex
         && this.internalDrag.itemIndex === itemIndex;
   }

   /** Move an existing item to a new row/position (insert-before `toIndex`). */
   private moveItem(fromRow: number, fromIndex: number, toRow: number, toIndex: number): void {
      const src = this.workingRows[fromRow];
      const dst = this.workingRows[toRow];   // capture before any row removal

      if(!src || !dst) {
         return;
      }

      const [item] = src.items.splice(fromIndex, 1);

      if(!item) {
         return;
      }

      // Removing from earlier in the same row shifts the target position down by one.
      let target = toIndex;

      if(src === dst && fromIndex < toIndex) {
         target = toIndex - 1;
      }

      target = Math.max(0, Math.min(target, dst.items.length));
      dst.items.splice(target, 0, item);

      // Drop an emptied source row (never the row we just inserted into).
      if(src !== dst && src.items.length === 0) {
         this.workingRows.splice(fromRow, 1);
      }

      this.selectedRowIndex = -1;
      this.selectedItemIndex = -1;
   }

   /** Insert a new STATIC/SPACING item from the palette at `toIndex`. */
   private insertPaletteItem(type: number, toRow: number, toIndex: number): void {
      const row = this.workingRows[toRow];

      if(!row) {
         return;
      }

      const newItem: TextLayoutItemModel = type === this.STATIC
         ? { type: this.STATIC, text: "", fontSize: 10, bold: false, italic: false }
         : { type: this.SPACING, spacingAmount: 10 };

      const idx = Math.max(0, Math.min(toIndex, row.items.length));
      row.items.splice(idx, 0, newItem);

      if(newItem.type === this.STATIC) {
         this.selectedRowIndex = toRow;
         this.selectedItemIndex = idx;
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

   onBindingTreeDrop(event: DragEvent, rowIndex: number, insertIndex: number = -1): void {
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

      // Clamp the requested insert position to the row's current item count. A negative
      // insertIndex is the "append" sentinel (the default), preserving the original intent.
      const itemCount = this.workingRows[rowIndex].items.length;
      const clampedIndex = Math.max(
         0, Math.min(insertIndex < 0 ? itemCount : insertIndex, itemCount));

      // Build the field client-side and add it synchronously (the host appends it to textFields
      // and passes the new array back via [textFields]). No server round-trip, so the new field's
      // index is simply the current textFields length — and the chip resolves immediately.
      const newIndex = this.textFields?.length ?? 0;

      this.workingRows[rowIndex].items.splice(
         clampedIndex, 0, { type: this.FIELD, fieldIndex: newIndex });

      // Optimistically reflect the append in our own [textFields] so a second rapid drop computes
      // the next index correctly — the host re-passes [textFields] only on the next change-detection
      // cycle, so we cannot rely on the @Input being current between two synchronous drops. The host
      // appends this same field object, so the eventual @Input replacement is consistent.
      this.textFields = [...(this.textFields ?? []), field];

      this.onAddField.emit({ field, insertRow: rowIndex, insertIndex: clampedIndex });
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
      // Key the Format panel by field INDEX, not name — a designer-added field's aggregated name
      // isn't known client-side until a round-trip, so a name key fails on first open (#75474).
      if(item?.fieldIndex == null) {
         return;
      }

      this.onPreSaveLayout.emit({ rows: this.workingRows });
      this.onFormatField.emit(LAYOUT_FIELD_PREFIX + item.fieldIndex);
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
