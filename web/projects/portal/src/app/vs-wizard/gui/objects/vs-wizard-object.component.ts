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
import {
   ChangeDetectorRef,
   Component,
   EventEmitter,
   Input,
   OnDestroy,
   OnInit,
   Output,
   ViewChild,
   Renderer2
} from "@angular/core";
import { GuiTool } from "../../../common/util/gui-tool";
import { VSObjectModel } from "../../../vsobjects/model/vs-object-model";
import { Viewsheet } from "../../../composer/data/vs/viewsheet";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { Subscription } from "rxjs";
import { Rectangle } from "../../../common/data/rectangle";
import { VSFormatModel } from "../../../vsobjects/model/vs-format-model";
import { VSTextModel } from "../../../vsobjects/model/output/vs-text-model";
import { FollowDirection } from "../wizard-pane/follow-direction";
import { DragMoveStartOptions } from "../wizard-pane/drag-move-start-options";
import { SelectableObject } from "../../../vsobjects/objects/selectable-object";

@Component({
   selector: "vs-wizard-object",
   templateUrl: "./vs-wizard-object.component.html",
   styleUrls: ["./vs-wizard-object.component.scss"]
})
export class VsWizardObjectComponent implements OnInit, OnDestroy {
   @Input() vsObject: VSObjectModel;
   @Input() viewsheet: Viewsheet;
   @Input() heightIncrement: number = 1;
   @Input() widthIncrement: number = 1;
   @Input() maxHeight: number;
   @Input() maxWidth: number;
   @Input() willFollow: boolean = false;
   @Output() onResize: EventEmitter<VSObjectModel> = new EventEmitter<VSObjectModel>();
   @Output() onMove: EventEmitter<any> = new EventEmitter<any>();
   @Output() onEdit: EventEmitter<string> = new EventEmitter();
   @Output() onRemove: EventEmitter<string> = new EventEmitter();
   @Output() onDragResizeStart = new EventEmitter<DragMoveStartOptions>();
   @Output() onDragResizeEnd = new EventEmitter<any>();
   @Output() onRowsChanged: EventEmitter<VSObjectModel | number> = new EventEmitter();
   @Output() onColsChanged: EventEmitter<VSObjectModel | number> = new EventEmitter();
   @Output() onChangeFollowDirection: EventEmitter<FollowDirection> = new EventEmitter();
   @Output() onMouseIn = new EventEmitter<any>();
   @ViewChild("objectComponent") objectComponent: any;
   selected: boolean = false;
   interactionResizable: boolean;
   interactionDraggable: boolean;

   miniToolbarActions: AssemblyActionGroup[] = [];
   private subscriptions = new Subscription();
   private originalRectangle: Rectangle = new Rectangle(0, 0, 0, 0);

   constructor(private changeDetectorRef: ChangeDetectorRef,
               private renderer: Renderer2) {
   }

   ngOnInit() {
      this.miniToolbarActions.push(new AssemblyActionGroup([
         {
            id: () => "Edit VS Wizard Object",
            label: () => "_#(js:Edit)",
            icon: () => "edit-icon",
            enabled: () => true,
            visible: () => this.canEdit(),
            action: () => this.editObject()
         },
         {
            id: () => "Delete VS Wizard Object",
            label: () => "_#(js:Delete)",
            icon: () => "shape-cross-icon",
            enabled: () => true,
            visible: () => true,
            action: () => this.onRemove.emit(this.vsObject.absoluteName)
         }
      ]));

      this.subscriptions.add(this.viewsheet.focusedAssemblies.subscribe((focusedAssemblies) => {
         if(this.viewsheet.isAssemblyFocused(this.vsObject.absoluteName)) {
            this.selected = true;
            this.updateInteractable(true);
         }
         else {
            if(this.selected) {
               if(this.vsObject.objectType === "VSTable" ||
                  this.vsObject.objectType === "VSCrosstab" ||
                  this.vsObject.objectType === "VSCalcTable" ||
                  this.vsObject.objectType === "VSChart")
               {
                  (this.objectComponent as SelectableObject).clearSelection();
               }

               this.vsObject.selectedRegions = [];
            }

            this.selected = false;
            this.updateInteractable(false);
         }

         this.changeDetectorRef.detectChanges();
      }));
   }

   toEditMode() {
      if(this.selected && (this.vsObject.objectType === "VSText" ||
         this.vsObject.objectType === "VSImage"))
      {
         this.updateInteractable(false);
      }
   }

   updateInteractable(enabled: boolean) {
      this.interactionDraggable = enabled;
      this.interactionResizable = enabled;
   }

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }

   private editObject(): void {
      if(this.vsObject.objectType == "VSText" &&
         !(<VSTextModel>this.vsObject).defaultAnnotationContent)
      {
         (this.vsObject as VSTextModel).editing = true;
         this.toEditMode();
      }
      else {
         this.onEdit.emit(this.vsObject.absoluteName);
      }
   }

   private saveOriginalBounds() {
      this.originalRectangle.x = this.vsObject.objectFormat.left;
      this.originalRectangle.y = this.vsObject.objectFormat.top;
      this.originalRectangle.height = this.vsObject.objectFormat.height;
      this.originalRectangle.width = this.vsObject.objectFormat.width;
   }

   private updateFollowPositions() {
      const xdiff = this.vsObject.objectFormat.left - this.originalRectangle.x;
      const ydiff = this.vsObject.objectFormat.top - this.originalRectangle.y;

      this.viewsheet.vsObjects
         .filter(obj => obj.absoluteName != this.vsObject.absoluteName)
         .forEach(obj => {
            const div = document.getElementById(obj.absoluteName);

            if(div) {
               if(div.classList.contains("follow-object")) {
                  this.renderer.setStyle(div, "left", (obj.objectFormat.left + xdiff) + "px");
                  this.renderer.setStyle(div, "top", (obj.objectFormat.top + ydiff) + "px");

                  if(obj.objectFormat.top + ydiff + obj.objectFormat.height > this.maxHeight) {
                     this.onRowsChanged.emit(obj.objectFormat.top + ydiff + obj.objectFormat.height);
                  }

                  if(obj.objectFormat.left + xdiff + obj.objectFormat.width > this.maxWidth) {
                     this.onColsChanged.emit(obj.objectFormat.left + xdiff + obj.objectFormat.width);
                  }
               }
               else {
                  this.renderer.setStyle(div, "left", obj.objectFormat.left + "px");
                  this.renderer.setStyle(div, "top", obj.objectFormat.top + "px");
               }
            }
         });
   }

   isBoundsChanged(): boolean {
      if(!this.originalRectangle) {
         return false;
      }

      let format: VSFormatModel = this.vsObject.objectFormat;

      return format.left != this.originalRectangle.x || format.top != this.originalRectangle.y ||
         format.width != this.originalRectangle.width ||
         format.height != this.originalRectangle.height;
   }

   onResizeStart(event: any) {
      this.saveOriginalBounds();
      this.onDragResizeStart.emit(new DragMoveStartOptions(false, this.vsObject, false));
   }

   onResizeMove(event: any) {
      let currentHeight = this.vsObject.objectFormat.height;
      let currentWidth = this.vsObject.objectFormat.width;
      let top = this.vsObject.objectFormat.top;
      let left = this.vsObject.objectFormat.left;

      if(event.deltaRect.top) {
         top = Math.max(0, top + event.deltaRect.top);
         currentHeight -= event.deltaRect.top;
      }

      if(event.deltaRect.left) {
         left = Math.max(0, left + event.deltaRect.left);
         currentWidth -= event.deltaRect.left;
      }

      if(event.deltaRect.right) {
         currentWidth += event.deltaRect.right;
      }

      if(event.deltaRect.bottom) {
         currentHeight += event.deltaRect.bottom;
      }

      this.vsObject.objectFormat.left = left;
      this.vsObject.objectFormat.top = top;
      this.vsObject.objectFormat.width = currentWidth;
      this.vsObject.objectFormat.height = currentHeight;
      this.objectComponent.resized();
      this.onRowsChanged.emit(this.vsObject);
      this.onColsChanged.emit(this.vsObject);
   }

   onResizeEnd() {
      let currentHeight = this.vsObject.objectFormat.height;
      let currentWidth = this.vsObject.objectFormat.width;
      let left = this.vsObject.objectFormat.left;
      let top = this.vsObject.objectFormat.top;

      if((left % this.widthIncrement) != 0) {
         left = Math.round(left / this.widthIncrement) * this.widthIncrement;
         this.vsObject.objectFormat.left = left;
      }

      if((top % this.heightIncrement) != 0) {
         top = Math.round(top / this.heightIncrement) * this.heightIncrement;
         this.vsObject.objectFormat.top = top;
      }

      if((currentHeight % this.heightIncrement) != 0) {
         currentHeight = Math.round(currentHeight / this.heightIncrement) * this.heightIncrement;
      }

      if((currentWidth % this.widthIncrement) != 0) {
         currentWidth = Math.round(currentWidth / this.widthIncrement) * this.widthIncrement;
      }

      if(currentHeight >= this.heightIncrement) {
         this.vsObject.objectFormat.height = currentHeight;
      }
      else {
         this.vsObject.objectFormat.height = this.heightIncrement;
      }

      if(currentWidth >= this.widthIncrement) {
         this.vsObject.objectFormat.width = currentWidth;
      }
      else {
         this.vsObject.objectFormat.width = this.widthIncrement;
      }

      if(this.isBoundsChanged()) {
         this.onResize.emit(this.vsObject);
      }

      this.onDragResizeEnd.emit();
   }

   onDragStart(event: any) {
      this.saveOriginalBounds();
      this.onDragResizeStart.emit(new DragMoveStartOptions(true, this.vsObject,
         event.shiftKey));
      let followDir: FollowDirection = new FollowDirection();
      let bounds: Rectangle = new Rectangle(
         (this.originalRectangle.x + this.originalRectangle.width / 2) - 10,
         (this.originalRectangle.y + this.originalRectangle.height / 2) - 10,
         20, 20);
      followDir.bounds = bounds;
      this.onChangeFollowDirection.emit(followDir);
   }

   onDragMove(event: any) {
      let dx = event.dx;
      let dy = event.dy;
      let currentLeft = this.vsObject.objectFormat.left;
      let currentTop = this.vsObject.objectFormat.top;

      if(dx != 0) {
         currentLeft += dx;
      }

      if(dy != 0) {
         currentTop += dy;
      }

      this.vsObject.objectFormat.left = currentLeft;
      this.vsObject.objectFormat.top = currentTop;
      this.onRowsChanged.emit(this.vsObject);
      this.onColsChanged.emit(this.vsObject);
      let followDir: FollowDirection = new FollowDirection();
      let originalDX = Math.abs(currentLeft - this.originalRectangle.x);
      let originalDY = Math.abs(currentTop - this.originalRectangle.y);

      if(originalDY == 0 && originalDX == 0 ||
         this.viewsheet.currentFocusedAssemblies.length != 1)
      {
         followDir.direction = "none";
      }
      else if(originalDX >= originalDY) {
         followDir.direction = "right";
      }
      else {
         followDir.direction = "bottom";
      }

      this.updateFollowPositions();
      this.onChangeFollowDirection.emit(followDir);
      this.onMove.emit({event: event, model: this.vsObject});
   }

   onDragEnd(event: any) {
      this.onChangeFollowDirection.emit(new FollowDirection());
      let currentLeft = this.vsObject.objectFormat.left;
      let currentTop = this.vsObject.objectFormat.top;
      currentTop = Math.round(currentTop / this.heightIncrement) * this.heightIncrement;
      currentLeft = Math.round(currentLeft / this.widthIncrement) * this.widthIncrement;
      this.vsObject.objectFormat.left = currentLeft < 0 ? 0 : currentLeft;
      this.vsObject.objectFormat.top = currentTop < 0 ? 0 : currentTop;
      this.updateFollowPositions();

      if(this.isBoundsChanged()) {
         this.onMove.emit({event: event, model: this.vsObject});
      }

      this.onDragResizeEnd.emit();
      this.changeDetectorRef.detectChanges();
   }

   select(event: MouseEvent) {
      if(!this.selected) {
         if(!event.ctrlKey && !event.metaKey) {
            this.viewsheet.clearFocusedAssemblies();
         }

         this.viewsheet.selectAssembly(this.vsObject);
      }
      else if(this.selected && event.ctrlKey) {
         this.viewsheet.deselectAssembly(this.vsObject);
      }
   }

   isVSWizardObject() {
      return this.vsObject.objectType === "VSText" || this.vsObject.objectType === "VSImage" ||
         this.vsObject.objectType === "VSGauge" || this.vsObject.objectType === "VSCalendar" ||
         this.vsObject.objectType === "VSChart" || this.vsObject.objectType === "VSSelectionContainer" ||
         this.vsObject.objectType === "VSSelectionList" || this.vsObject.objectType === "VSSelectionTree" ||
         this.vsObject.objectType === "VSTable" || this.vsObject.objectType === "VSCrosstab" ||
         this.vsObject.objectType === "VSRangeSlider";
   }

   private canEdit(): boolean {
      return !!this.vsObject && !this.vsObject.hasDynamic;
   }

   onMouseover(event: MouseEvent) {
      this.onMouseIn.emit(event);
   }
}
