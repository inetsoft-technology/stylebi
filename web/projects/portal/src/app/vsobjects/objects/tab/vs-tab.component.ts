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
   OnDestroy,
   ChangeDetectorRef,
   Component,
   ElementRef,
   HostListener,
   Input,
   NgZone,
   OnChanges,
   Renderer2,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { TinyColor } from "@ctrl/tinycolor";
import { Tool } from "../../../../../../shared/util/tool";
import { Border } from "../../../common/data/base-format-model";
import { DataPathConstants } from "../../../common/util/data-path-constants";
import { Subscription } from "rxjs";
import { GuiTool } from "../../../common/util/gui-tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client/viewsheet-client.service";
import { ContextProvider } from "../../context-provider.service";
import { ChangeTabStateEvent } from "../../event/change-tab-state-event";
import { VSFormatModel } from "../../model/vs-format-model";
import { VSTabModel } from "../../model/vs-tab-model";
import { VSChartModel } from "../../model/vs-chart-model";
import { NavigationComponent } from "../abstract-nav-component";
import { DataTipService } from "../data-tip/data-tip.service";
import { NavigationKeys } from "../navigation-keys";
import { VSTabService } from "../../util/vs-tab.service";

interface MaxBorderWidths {
   left: number;
   right: number;
   top: number;
   bottom: number;
}

@Component({
   selector: "vs-tab",
   templateUrl: "vs-tab.component.html",
   styleUrls: ["vs-tab.component.scss"]
})
export class VSTab extends NavigationComponent<VSTabModel> implements OnChanges, AfterViewInit, OnDestroy {
   @Input() changeEnabled = true;
   @Input() selected: boolean;
   @ViewChild("tabContainer") hostElement: ElementRef;
   private scrolling: any;
   leftScroll: boolean = false;
   rightScroll: boolean = false;
   private focusIndex: number = -1;
   maxBorderWidths: MaxBorderWidths;
   tabHovered = new Set<number>();
   hoverBorder: Border;

   get autoSize(): boolean {
      const _model = this.model as any;
      return !!_model && !!_model.autoSize;
   }

   constructor(private viewsheetClientService: ViewsheetClientService,
               private changeDetectionRef: ChangeDetectorRef,
               private renderer: Renderer2,
               protected context: ContextProvider,
               protected dataTipService: DataTipService,
               private tabService: VSTabService,
               zone: NgZone)
   {
      super(viewsheetClientService, zone, context, dataTipService);
   }

   ngOnChanges(changes: SimpleChanges) {
      if(this.updateScrolls()) {
         this.changeDetectionRef.detectChanges();
      }

      if(!changes || changes["model"]) {
         const left = Math.max(1, Tool.getMarginSize(this.model.objectFormat.border.left),
            Tool.getMarginSize(this.model.activeFormat.border.left));
         const right = Math.max(1, Tool.getMarginSize(this.model.objectFormat.border.right),
            Tool.getMarginSize(this.model.activeFormat.border.right));
         const top = Math.max(1, Tool.getMarginSize(this.model.objectFormat.border.top),
            Tool.getMarginSize(this.model.activeFormat.border.top));
         const bottom = Math.max(1, Tool.getMarginSize(this.model.objectFormat.border.bottom),
            Tool.getMarginSize(this.model.activeFormat.border.bottom));
         this.maxBorderWidths = {left: left, right: right, top: top, bottom: bottom};

         this.initHoverBorder();
      }
   }

   ngAfterViewInit() {
      this.ngOnChanges(null);
   }

   // do bfs on the children to list all the objects being set to active/inactive
   changeTab(name: string): void {
      if(this.changeEnabled) {
         let target = "/events/tab/changetab/" + this.model.absoluteName;
         let event = new ChangeTabStateEvent();
         this.tabService.deselect(this.model.selected);
         event.setTarget(name);
         this.vsInfo.vsObjects
            .filter(obj => obj.absoluteName == this.model.selected && obj.objectType == "VSChart")
            .forEach(obj => (<VSChartModel> obj).chartSelection.regions = []);
         console.log("change:", this.vsInfo);
         this.viewsheetClientService.sendEvent(target, event);
      }

      if(this.selected) {
         if(this.model.selected != name) {
            this.model.selectedRegions = [];
         }
         else {
            this.model.selectedRegions = [DataPathConstants.DETAIL];
         }
      }
   }

   updateScrolls(): boolean {
      const oleft = this.leftScroll;
      const oright = this.rightScroll;

      if(this.hostElement) {
         this.leftScroll = this.scrollLeft > 0;
         this.rightScroll = this.scrollWidth - this.offsetWidth - this.scrollLeft > 1;
      }

      return oleft != this.leftScroll || oright != this.rightScroll;
   }

   scrollToLeft(event: any): void {
      if(event.button == 0) {
         this.renderer.setProperty(this.hostElement.nativeElement, "scrollLeft",
                                   this.hostElement.nativeElement.scrollLeft - 4);
         this.updateScrolls();

         this.scrolling = setTimeout(() => {
            this.scrollToLeft(event);
         }, 50);
      }
   }

   scrollToRight(event: any): void {
      if(event.button == 0) {
         this.renderer.setProperty(this.hostElement.nativeElement, "scrollLeft",
                                   this.hostElement.nativeElement.scrollLeft + 4);
         this.updateScrolls();

         this.scrolling = setTimeout(() => {
            this.scrollToRight(event);
         }, 50);
      }
   }

   @HostListener("document: mouseup", ["$event"])
   stopScrolling(event: any): void {
      clearTimeout(this.scrolling);
   }

   private get scrollWidth(): number {
      return this.hostElement.nativeElement.scrollWidth;
   }

   private get offsetWidth(): number {
      return this.hostElement.nativeElement.offsetWidth;
   }

   private get scrollLeft(): number {
      return this.hostElement.nativeElement.scrollLeft;
   }

   getFlexVAlign(tabID: number): string {
      return GuiTool.getFlexVAlign(this.getFormat(tabID).vAlign);
   }

   /**
    * Keyboard navigation for this component.
    * @param {NavigationKeys} key
    */
   protected navigate(key: NavigationKeys): void {
      let index: number = this.focusIndex;

      if(key == NavigationKeys.RIGHT) {
         index++;
      }
      else if(key == NavigationKeys.LEFT) {
         index--;
      }
      else if(key == NavigationKeys.SPACE) {
         this.changeTab(this.model.childrenNames[this.focusIndex]);
         return;
      }
      else {
         return;
      }

      if(index >= 0 && this.hostElement && this.hostElement.nativeElement.children &&
         this.hostElement.nativeElement.children.length > index)
      {
         this.hostElement.nativeElement.children[index].focus();
         this.focusIndex = index;
      }
   }

   /**
    * Clear selection make by navigating.
    */
   protected clearNavSelection(): void {
      this.focusIndex = -1;
   }

   get disableEvents(): boolean {
      return this.viewer && !this.model.enabled;
   }

   getFormat(tabID: number): VSFormatModel {
      if(this.model.selected == this.model.childrenNames[tabID]) {
         return this.model.activeFormat;
      }

      return this.model.objectFormat;
   }

   getBorder(tabID: number): Border {
      if(this.tabHovered.has(tabID)) {
         return this.hoverBorder;
      }

      // make left and right border transparent instead when set to 'none'
      // to create space between tabs
      let border = Tool.clone(this.getFormat(tabID).border);

      for(let key of ["left", "right"]) {
         if(border.hasOwnProperty(key)) {
            let borderStr = border[key];

            if(Tool.getBorderStyle(borderStr) == "none") {
               border[key] = "1px solid transparent";
            }
         }
         else {
            border[key] = "1px solid transparent";
         }
      }

      return border;
   }

   /**
    * The purpose of this margin is to keep the text centered across the tabs that
    * may have different border widths and to prevent the text jumping up/down when switching
    * between active/non-active tab.
    */
   getMargin(tabID: number, type: string): number {
      const border = this.getBorder(tabID);

      if(type === "left") {
         return this.maxBorderWidths.left - Tool.getMarginSize(border.left);
      }
      else if(type === "right") {
         return this.maxBorderWidths.right - Tool.getMarginSize(border.right);
      }
      else if(type === "top") {
         return this.maxBorderWidths.top - Tool.getMarginSize(border.top);
      }
      else {
         return this.maxBorderWidths.bottom - Tool.getMarginSize(border.bottom);
      }
   }

   getBottomBorder(): string {
      // if round corner is set then don't display this border since it doesn't look good
      return !this.model.roundTopCornersOnly && (this.model.objectFormat?.roundCorner > 0 ||
         this.model.activeFormat?.roundCorner > 0) ?
         "" : this.model.objectFormat.border.bottom;
   }

   /**
    * A workaround to keep the bottom border corners square
    */
   getTabItemBottomBorderStyle(tabID: number) {
      const border = this.getBorder(tabID);

      if(Tool.getBorderStyle(border.bottom) == "none") {
         return null;
      }

      return {
         "left": -Tool.getMarginSize(border.left) + "px",
         "bottom": -Tool.getMarginSize(border.bottom) + "px",
         "width": "calc(100% + " +
            (Tool.getMarginSize(border.left) + Tool.getMarginSize(border.right)) + "px)",
         "height": "calc(100% + " +
            (Tool.getMarginSize(border.bottom) + Tool.getMarginSize(border.top)) + "px)"
      };
   }

   private initHoverBorder() {
      let hoverColor: string;

      // try to derive the color from non-active tab borders
      if(this.model.objectFormat.border) {
         const borders = [this.model.objectFormat.border.bottom, this.model.objectFormat.border.left,
            this.model.objectFormat.border.top, this.model.objectFormat.border.right];

         for(let border of borders) {
            if(border) {
               let color = border.split(" ")[2];

               if(color) {
                  hoverColor = new TinyColor(color).tint(20).toRgbString();
                  break;
               }
            }
         }
      }

      // try to use the background color
      if(!hoverColor && this.model.objectFormat.background) {
         let color = new TinyColor(this.model.objectFormat.background);

         if(color.isValid && color.isLight()) {
            hoverColor = GuiTool.getSelectedColor("white");
         }
         else {
            hoverColor = GuiTool.getSelectedColor("black");
         }
      }

      if(!hoverColor) {
         hoverColor = GuiTool.getSelectedColor("white");
      }

      this.hoverBorder = {
         left: this.maxBorderWidths.left + "px solid " + hoverColor,
         right: this.maxBorderWidths.right + "px solid " + hoverColor,
         top: this.maxBorderWidths.top + "px solid " + hoverColor,
         bottom: this.maxBorderWidths.bottom + "px solid " + hoverColor
      };
   }
}
