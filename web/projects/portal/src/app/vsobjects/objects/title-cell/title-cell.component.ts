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
import { Component, EventEmitter, Output, Input, OnChanges, SimpleChanges } from "@angular/core";
import { ContextProvider } from "../../context-provider.service";
import { VSFormatModel } from "../../model/vs-format-model";
import { GuiTool } from "../../../common/util/gui-tool";

@Component({
   selector: "title-cell",
   templateUrl: "title-cell.component.html",
   styleUrls: ["title-cell.component.scss"]
})
export class TitleCell implements OnChanges {
   @Input() titleHeight: number;
   @Input() titleFormat: VSFormatModel;
   @Input() titleContent: string = "";
   @Input() selected: boolean = false;
   @Input() formatPainterMode: boolean;
   @Input() titleSelected: boolean = false;
   @Input() inactive: boolean = false;
   @Input() editable: boolean = true;
   @Input() editingTitle: boolean = false;
   @Input() resizable: boolean = true;
   @Input() forceResizable: boolean = false;
   @Input() inSelectionContainer: boolean = false;
   @Output() updateTitle: EventEmitter<string> = new EventEmitter<string>();
   @Output() cellClick: EventEmitter<MouseEvent> = new EventEmitter<MouseEvent>();
   @Output() onTitleResizeMove: EventEmitter<number> = new EventEmitter<number>();
   @Output() onTitleResizeEnd: EventEmitter<any> = new EventEmitter<any>();
   @Output() editingTitleChange: EventEmitter<boolean> = new EventEmitter<boolean>();

   rowResizeLabel: string = null;

   get viewer(): boolean {
      return this.context.viewer || this.context.preview;
   }

   get vsWizard(): boolean {
      return this.context.vsWizard;
   }

   get vsWizardPreview(): boolean {
      return this.context.vsWizardPreview;
   }

   constructor(private context: ContextProvider) {
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes["titleSelected"] && !this.titleSelected && this.editingTitle) {
         this.editingTitleChange.emit(this.editingTitle = false);
      }
   }

   changeEditing(editing: boolean) {
      if(this.editable) {
         this.editingTitle = editing;
         this.editingTitleChange.emit(editing);
      }
   }

   changeTitle(title: string): void {
      this.titleContent = title;
      this.updateTitle.emit(title);
   }

   handleClick(event: MouseEvent): void {
      if(!this.editingTitle && !this.viewer && (this.selected || this.formatPainterMode)) {
         this.cellClick.emit(event);
      }
   }

   titleResizeMove(event: any): void {
      this.rowResizeLabel = Math.max(GuiTool.MINIMUM_TITLE_HEIGHT, event.rect.height) + "";
      this.onTitleResizeMove.emit(event.rect.height);
   }

   titleResizeEnd(): void {
      this.rowResizeLabel = null;
      this.onTitleResizeEnd.emit();
   }

   getHTMLText(): string {
      return GuiTool.getHTMLText(this.titleContent);
   }

   get showResizeHandle(): boolean {
      return !this.editingTitle && !this.viewer &&
         (this.titleSelected || this.forceResizable) && this.resizable && !this.vsWizard;
   }
}
