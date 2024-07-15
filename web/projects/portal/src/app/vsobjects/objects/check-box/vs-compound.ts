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
import { ElementRef, EventEmitter, Input, NgZone, OnChanges, OnDestroy, Output, SimpleChanges, ViewChild, HostListener, Directive } from "@angular/core";
import { Observable ,  Subscription } from "rxjs";
import { DataPathConstants } from "../../../common/util/data-path-constants";
import { GuiTool } from "../../../common/util/gui-tool";
import { Tool } from "../../../../../../shared/util/tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ModelService } from "../../../widget/services/model.service";
import { ContextProvider } from "../../context-provider.service";
import { ChangeVSObjectTextEvent } from "../../event/change-vs-object-text-event";
import { VSCompoundModel } from "../../model/vs-compound-model";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { NavigationComponent } from "../abstract-nav-component";
import { NavigationKeys } from "../navigation-keys";
import { DataTipService } from "../data-tip/data-tip.service";
import { VSFormatModel } from "../../model/vs-format-model";

@Directive()
export abstract class VSCompound<T extends VSCompoundModel> extends NavigationComponent<T> implements OnChanges, OnDestroy
{
   @Input() submitted: Observable<boolean>;
   @Output() onTitleResizeMove: EventEmitter<number> = new EventEmitter<number>();
   @Output() onTitleResizeEnd: EventEmitter<any> = new EventEmitter<any>();
   @ViewChild("listBody") listBody: ElementRef;

   _selected: boolean = false;
   selectedCells: number[];
   previouslySelected: number = -1;
   resizingCell: number = -1;
   ocellHeight: number = -1;

   titleHAlign: string;
   titleVAlign: string;
   detailVAlign: string;
   defaultBorder: boolean = false;
   editingTitle: boolean = false;
   private longestLabelWidths: number[];

   submittedForm: Subscription;
   protected unappliedSelection = false;
   private readonly MINIMUM_CELL_HEIGHT = 5;

   @Input() set selected(value: boolean) {
      if(!value) {
         this.selectedCells = [];
         this.previouslySelected = -1;
         this.editingTitle = false;
      }

      this._selected = value;
   }

   get selected(): boolean {
      return this._selected;
   }

   constructor(protected socket: ViewsheetClientService,
               protected formDataService: CheckFormDataService,
               protected debounceService: DebounceService,
               protected context: ContextProvider,
               protected modelService: ModelService,
               protected dataTipService: DataTipService,
               zone: NgZone)
   {
      super(socket, zone, context, dataTipService);
   }

   ngOnChanges(changes: SimpleChanges) {
      this.calculateCheckBoxAlign();
      this.calculateTitleHAlign();
      this.calculateTitleVAlign();
      this.checkDefaultBorder();

      if(this.viewer && changes.submitted && this.submitted) {
         if(this.submittedForm) {
            this.submittedForm.unsubscribe();
            this.submittedForm = null;
         }

         this.submittedForm = this.submitted.subscribe((isSubmitted) => {
            if(isSubmitted && this.unappliedSelection) {
               this.applySelection();
            }
         });
      }
   }

   ngOnDestroy() {
      super.ngOnDestroy();

      if(this.submittedForm) {
         this.submittedForm.unsubscribe();
      }
   }

   getIndex(value: any): number {
      return this.model.values.map(v => v == null ? null : v + "").indexOf(value == null ? null : value + "");
   }

   getDataTopCSS(index: number): number {
      const dataHeight: number = this.model.detailFormat.height;
      const row: number = Math.floor(index / this.model.dataColCount);

      return dataHeight * row;
   }

   getDataLeftCSS(index: number): number {
      let dataWidth: number = this.model.detailFormat.width;
      let position: number = (index + 1) % this.model.dataColCount;

      if(position == 0) {
         return dataWidth * (this.model.dataColCount - 1);
      }
      else {
         return dataWidth * (position - 1);
      }
   }

   changeTitle(title: string) {
      if(!this.viewer) {
         let event: ChangeVSObjectTextEvent = new ChangeVSObjectTextEvent(
            this.model.absoluteName, title);

         this.socket.sendEvent("/events/composer/viewsheet/objects/changeTitle", event);
      }
   }

   updateTitle() {
      if(!this.viewer) {
         let event: ChangeVSObjectTextEvent = new ChangeVSObjectTextEvent(
            this.model.absoluteName, this.model.title);

         this.socket.sendEvent("/events/composer/viewsheet/objects/changeTitle", event);
      }
   }

   selectCell(event: MouseEvent, index: number): void {
      // select vsobject before select parts
      if(!this.selected && !this.vsInfo.formatPainterMode || this.viewer) {
         return;
      }

      if(event.shiftKey && this.previouslySelected != -1) {
         let start: number = this.previouslySelected > index ? index : this.previouslySelected;
         let end: number = this.previouslySelected > index ? this.previouslySelected : index;

         for(let i = start; i <= end; i++) {
            this.selectedCells.push(i);
         }
      }
      else if(event.ctrlKey || event.shiftKey) {
         this.selectedCells.push(index);
      }
      else {
         this.selectedCells = [];
         this.model.selectedRegions = [];
         this.selectedCells.push(index);
      }

      this.addDataPath();
      this.previouslySelected = index;
   }

   isCellSelected(index: number): boolean {
      return this.selectedCells && this.selectedCells.indexOf(index) != -1;
   }

   selectTitle(event: MouseEvent): void {
      // select vsobject before select parts
      if(!this.selected && !this.vsInfo.formatPainterMode || this.viewer) {
         return;
      }

      if(!event.shiftKey && !event.ctrlKey) {
         this.selectedCells = [];
         this.model.selectedRegions = [];
         this.model.selectedRegions.push(DataPathConstants.TITLE);
      }
      else if(this.model.selectedRegions.indexOf(DataPathConstants.TITLE) == -1) {
         this.model.selectedRegions.push(DataPathConstants.TITLE);
      }
   }

   isTitleSelected(): boolean {
      return this.model.selectedRegions &&
         this.model.selectedRegions.indexOf(DataPathConstants.TITLE) != -1;
   }

   addDataPath(): void {
      if(this.model.selectedRegions &&
         this.model.selectedRegions.indexOf(DataPathConstants.DETAIL) === -1)
      {
         this.model.selectedRegions.push(DataPathConstants.DETAIL);
      }
   }

   titleResizeMove(event: any): void {
      this.onTitleResizeMove.emit(event.rect.height);
   }

   titleResizeEnd(): void {
      this.onTitleResizeEnd.emit();
   }

   detailResizeMove(event: any, idx: number): void {
      if(this.resizingCell != idx) {
         this.resizingCell = idx;
         this.ocellHeight = this.model.detailFormat.height;
      }

      const resizingRow = Math.floor(this.resizingCell / this.model.dataColCount);
      const bottom = this.ocellHeight * resizingRow + event.rect.height;
      const height = Math.round(bottom / (resizingRow + 1));
      this.model.detailFormat.height = Math.max(height, this.MINIMUM_CELL_HEIGHT);
   }

   detailResizeEnd(): void {
      this.resizingCell = -1;
      const url = "../api/composer/vs/setDetailHeight/" + this.model.absoluteName +
         "/" + this.model.detailFormat.height + "/" + Tool.byteEncode(this.socket.runtimeId);
      this.modelService.sendModel(url, null)
         .toPromise().then((res: any) => {});
   }

   private calculateCheckBoxAlign(): void {
      const detailHAlign = this.model.detailFormat.hAlign;
      this.detailVAlign = GuiTool.getFlexVAlign(this.model.detailFormat.vAlign);
      this.longestLabelWidths = [];

      if(detailHAlign == "center" || detailHAlign == "right") {
         const labelws: Array<number> = this.model.labels
            .map(lbl => GuiTool.measureText(lbl, this.model.detailFormat.font));

         for(let i = 0; i < labelws.length; i++) {
            const col = i % this.model.dataColCount;
            // column width is the widest checkbox (label + checkbox + margin)
            const colws = Math.min(this.model.detailFormat.width, labelws[i] + 13 + 3);

            if(this.longestLabelWidths[col] == null) {
               this.longestLabelWidths[col] = colws;
            }
            else {
               this.longestLabelWidths[col] = Math.max(colws, this.longestLabelWidths[col]);
            }
         }
      }
   }

   getColWidth(i: number): number {
      const col = i % this.model.dataColCount;

      if(!this.longestLabelWidths || this.longestLabelWidths[col] == null) {
         return null;
      }

      return this.longestLabelWidths[col];
   }

   private calculateTitleHAlign(): void {
      this.titleHAlign = GuiTool.getFlexHAlign(this.model.titleFormat.hAlign);
   }

   private calculateTitleVAlign(): void {
      this.titleVAlign = GuiTool.getFlexVAlign(this.model.titleFormat.vAlign);
   }

   protected abstract applySelection(): void;

   private checkDefaultBorder(): void {
      if(!this.model.titleFormat.border.bottom && !this.model.titleFormat.border.top
         && !this.model.titleFormat.border.left && !this.model.titleFormat.border.right
         && !this.model.objectFormat.border.bottom && !this.model.objectFormat.border.top
         && !this.model.objectFormat.border.left && !this.model.objectFormat.border.bottom
         && !this.model.titleFormat.padding)
      {
         this.defaultBorder = true;
      }
      else {
         this.defaultBorder = false;
      }
   }

   /**
    * How to perform a selection when space is pressed.
    * @param {number} index
    */
   protected abstract onSpace(index: number): void;

   /**
    * Keyboard navigation for this component type.
    * @param {NavigationKeys} key
    */
   protected navigate(key: NavigationKeys): void {
      let index: number = !!this.selectedCells && this.selectedCells.length > 0 ?
         this.selectedCells[0] : -1;

      const step: number = this.model.dataColCount;
      const size: number = this.model.values.length;

      if(index == -1) {
         index = 0;
      }
      else if(key == NavigationKeys.DOWN) {
         if(index + step < size) {
            index += step;
         }
      }
      else if(key == NavigationKeys.UP) {
         if(index - step >= 0) {
            index -= step;
         }
      }
      else if(key == NavigationKeys.LEFT) {
         if(index - 1 >= 0) {
            index--;
         }
      }
      else if(key == NavigationKeys.RIGHT) {
         if(index + 1 < size) {
            index++;
         }
      }
      else if(key == NavigationKeys.SPACE) {
         this.onSpace(index);
      }

      this.selectedCells = [index];

      if(!!this.listBody && !!this.listBody.nativeElement.children &&
         this.listBody.nativeElement.children.length > index)
      {
         this.listBody.nativeElement.children[index].focus();
      }
   }

   /**
    * Clear selection make by navigating.
    */
   protected clearNavSelection(): void {
      this.selectedCells = [];
   }

   protected ctrlDown: boolean = false;
   protected pendingChange: boolean = false;

   @HostListener("document: keyup", ["$event"])
   onKeyUp(event: KeyboardEvent) {
      if(event.keyCode == 17) {
         this.ctrlDown = false;

         if(this.pendingChange) {
            this.applySelection();
            this.pendingChange = false;
         }
      }
   }

   @HostListener("document: keydown", ["$event"])
   onKeyDown(event: KeyboardEvent) {
      if(event.keyCode == 17) {
         this.ctrlDown = true;
      }
   }

   getCellFormat(index: number): VSFormatModel {
      if(!this.model?.formats || !this.model.formatIndexes) {
         return this.model.detailFormat;
      }

      return this.model.formats[this.model?.formatIndexes[index]];
   }
}
