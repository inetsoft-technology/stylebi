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
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, ViewChild,
         ElementRef, OnChanges, SimpleChanges, ChangeDetectorRef } from "@angular/core";
import { ContextProvider } from "../../context-provider.service";
import { VSFormatModel } from "../../model/vs-format-model";
import { GuiTool } from "../../../common/util/gui-tool";

@Component({
   selector: "vs-title",
   templateUrl: "vs-title.component.html",
   styleUrls: ["vs-title.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class VSTitle implements OnChanges {
   @Input() titleContent: string;
   @Input() titleFormat: VSFormatModel;
   @Input() titleWidth: number;
   @Input() titleSelected: boolean;
   @Input() selected: boolean;
   @Input() titleVisible: boolean;
   @Input() zIndex: number;
   @Input() floating: boolean;
   @Input() formatPainterMode: boolean;
   @Input() textBackground: string = "white";
   @Output() selectTitle = new EventEmitter<MouseEvent>();
   @Output() changeTitle = new EventEmitter<string>();
   @Output() titleResizeMove = new EventEmitter<number>();
   @Output() titleResizeEnd = new EventEmitter<any>();
   editingTitle: boolean = false;
   rowResizeLabel: string = null;

   constructor(private contextProvider: ContextProvider,
               private changeRef: ChangeDetectorRef) {
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes["titleSelected"] && !this.titleSelected) {
         this.editingTitle = false;
      }
   }

   public get viewer() {
      return this.contextProvider.viewer;
   }

   public get preview() {
      return this.contextProvider.preview;
   }

   public get binding() {
      return this.contextProvider.binding;
   }

   public get vsWizard() {
      return this.contextProvider.vsWizard;
   }

   get vsWizardPreview(): boolean {
      return this.contextProvider.vsWizardPreview;
   }

   titleResizeMoving(event: any) {
      this.rowResizeLabel = Math.max(GuiTool.MINIMUM_TITLE_HEIGHT, event.rect.height) + "";
      this.titleResizeMove.emit(event);
   }

   titleResizeEnded() {
      this.rowResizeLabel = null;
      this.titleResizeEnd.emit();
   }

   getHTMLText(): string {
      return GuiTool.getHTMLText(this.titleContent);
   }
}
