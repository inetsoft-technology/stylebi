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
   ChangeDetectionStrategy,
   Component,
   EventEmitter,
   Input,
   Output,
   OnInit,
   OnChanges,
   SimpleChanges
} from "@angular/core";
import { Rectangle } from "../../common/data/rectangle";
import { GuiTool } from "../../common/util/gui-tool";
import {
   InteractArea,
   InteractInfo,
   ResizeOptions
} from "../../widget/resize/element-interact.directive";
import { ContextProvider } from "../../vsobjects/context-provider.service";
import { LegendContainer } from "../model/legend-container";
import { LegendOption } from "../model/legend-option";
import { LegendResizeInfo } from "../model/legend-resize-info";
import { ChartAreaName } from "../model/chart-area-name";

@Component({
   selector: "chart-legend-container",
   templateUrl: "chart-legend-container.component.html",
   styleUrls: ["chart-legend-container.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class ChartLegendContainer implements OnInit, OnChanges {
   @Input() plotRegion: Rectangle;
   @Input() legend: LegendContainer;
   @Input() legendOption: LegendOption;
   @Input() chartContainer: Element;
   @Input() mouseoverLegendRegion: ChartAreaName;
   @Input() resizeEnable: boolean;
   @Input() moveEnable: boolean;
   @Output() onLegendMove = new EventEmitter<InteractInfo>();
   @Output() onLegendResizeStart = new EventEmitter<LegendResizeInfo>();
   @Output() detectChanges = new EventEmitter();

   isMoving: boolean = false;
   isResizing: boolean = false;
   outlineHeight: number;
   outlineLeft: number;
   outlineTop: number;
   outlineWidth: number;
   private draggedDistance: number = 0;
   private eventXdown: number;
   private eventYdown: number;
   private legendBounds: ClientRect;
   private resizeOption: number = 0;
   private chartRect: ClientRect;
   private scaleContainer: Element;
   background: string = null;

   constructor(private contextProvider: ContextProvider) {
   }

   ngOnInit() {
      this.ngOnChanges(null);
   }

   ngOnChanges(changes: SimpleChanges) {
      if(!changes || changes.chartContainer) {
         this.scaleContainer = GuiTool.closest(this.chartContainer, ".scale-container");
      }

      if(!changes || changes.legend) {
         if(this.legend && this.legend.legendObjects) {
            const legends = this.legend.legendObjects;
            this.background = legends.length > 0 ? legends[legends.length - 1].background : null;
         }
      }
   }

   /**
    * Logic to outline the dragging chartLegendArea based on how LegendOptions were
    * set in VSChartComponent
    */
   private buildOutlineRectangle(clientX: number, clientY: number): InteractInfo {
      let legendWidth = this.legend.bounds.width;
      let legendHeight = this.legend.bounds.height;
      // the new left position of the legend
      let newLeft = clientX - this.eventXdown + this.legendBounds.left;
      // the new top position of the legend
      let newTop = clientY - this.eventYdown + this.legendBounds.top;
      // Distance from left side of legend to left side of chart
      let x =  newLeft - this.chartRect.left;

      // Distance from top of legend to top of chart
      let y = newTop - this.chartRect.top;
      let area: InteractArea;

      // Determine the new position of the legend. If it's on the edges then draw
      // a rectangle there to indicate that the legend will be pinned to that side
      if(newTop < this.chartRect.top ) {
         area = InteractArea.TOP_EDGE;
         this.outlineTop = this.chartRect.top;
         this.outlineLeft = this.chartRect.left;
         this.outlineWidth = this.chartRect.width;
         this.outlineHeight = this.chartRect.height / 10;
      }
      else if(newLeft + legendWidth > this.chartRect.left + this.chartRect.width) {
         area = InteractArea.RIGHT_EDGE;
         this.outlineTop = this.chartRect.top;
         this.outlineLeft = this.chartRect.left + this.plotRegion.x + this.plotRegion.width
                            - this.chartRect.width / 10;
         this.outlineWidth = this.chartRect.width / 10;
         this.outlineHeight = this.chartRect.height;
      }
      else if(newTop + legendHeight > this.chartRect.top + this.chartRect.height) {
         area = InteractArea.BOTTOM_EDGE;
         this.outlineTop = this.chartRect.top + this.chartRect.height
                           - this.chartRect.height / 10;
         this.outlineLeft = this.chartRect.left;
         this.outlineWidth = this.chartRect.width;
         this.outlineHeight = this.chartRect.height / 10;
      }
      else if(newLeft < this.chartRect.left) {
         area = InteractArea.LEFT_EDGE;
         this.outlineTop = this.chartRect.top;
         this.outlineLeft = this.chartRect.left;
         this.outlineWidth = this.chartRect.width / 10;
         this.outlineHeight = this.chartRect.height;
      }
      else {
         area = InteractArea.CENTER;
         this.outlineLeft = newLeft;
         this.outlineTop = newTop;
         this.outlineWidth = legendWidth;
         this.outlineHeight = legendHeight;
      }

      const viewer = this.contextProvider.viewer || this.contextProvider.preview;

      // IE fixed position is always relative to viewport, but others are relative to
      // transformed container (scale-container)
      if(viewer && this.scaleContainer && !GuiTool.isIE()) {
         const paneOffsetX = this.scaleContainer.getBoundingClientRect().left;
         const paneOffsetY = this.scaleContainer.getBoundingClientRect().top;

         this.outlineTop -= paneOffsetY;
         this.outlineLeft -= paneOffsetX;
      }

      return {
         x: x,
         y: y,
         area: area
      };
   }

   /**
    * Get the edges on this legend container that are resizable. This depends on where
    * the legend is attached to the chart since you can only resize on the edge
    * closest to the plot area
    *
    * @returns {number} a bit set used by the element interact directive
    *                   to determine enabled edges
    */
   getResizeEdges(): number {
      this.resizeOption = 0b0000;

      switch(this.legendOption) {
         case LegendOption.TOP:
            this.resizeOption |= ResizeOptions.BOTTOM;
            break;
         case LegendOption.RIGHT:
            this.resizeOption |= ResizeOptions.LEFT;
            break;
         case LegendOption.BOTTOM:
            this.resizeOption |= ResizeOptions.TOP;
            break;
         case LegendOption.LEFT:
            this.resizeOption |= ResizeOptions.RIGHT;
            break;
         case LegendOption.IN_PLACE:
            this.resizeOption |= ResizeOptions.RIGHT | ResizeOptions.BOTTOM ;
            break;
         default:
            break;
      }

      return this.resizeOption;
   }

   /**
    * Distance mouse pointer from its origin mousedown event, the value in comparison
    * is customizable
    * @returns {boolean}
    */
   draggedEnoughDistance(): boolean {
      return this.draggedDistance >= 3;
   }

   startMoveOrResize(event: InteractInfo, legendContainer: Element, isMoving: boolean): void {
      if(!this.moveEnable && !this.resizeEnable) {
         return;
      }

      if(!isMoving) {
         this.isResizing = true;
         this.onLegendResizeStart.emit(this.createLegendResizeInfo(event.area));
         return;
      }

      this.isMoving = isMoving;
      this.legendBounds = legendContainer.getBoundingClientRect();
      this.chartRect = this.chartContainer.getBoundingClientRect();
      this.eventXdown = event.x;
      this.eventYdown = event.y;
   }

   endMoveOrResize(info: InteractInfo): void {
      if(this.isResizing) {
         this.isResizing = false;
         return;
      }

      if(this.draggedEnoughDistance() && info.area === InteractArea.CENTER) {
         const newInfo = this.buildOutlineRectangle(info.x, info.y);
         this.onLegendMove.emit(newInfo);
      }

      this.draggedDistance = 0;
      this.isMoving = false;
   }

   /**
    * Show outline rectangle when dragging on legend
    */
   onMove(event: MouseEvent): void {
      // Manhattan distance the mouse has traveled
      this.draggedDistance = Math.abs(this.eventXdown - event.x) +
         Math.abs(this.eventYdown - event.y);

      if(this.draggedEnoughDistance()) {
         this.buildOutlineRectangle(event.clientX, event.clientY);
      }
   }

   private createLegendResizeInfo(area: InteractArea): LegendResizeInfo {
      const boundingRect: ClientRect = this.chartContainer.getBoundingClientRect();
      let min: number = 0;
      let max: number = boundingRect.width;
      const vertical = area == InteractArea.LEFT_EDGE || area == InteractArea.RIGHT_EDGE;

      switch(this.legendOption) {
      case LegendOption.TOP:
         min = this.legend.minSize.height;
         max = boundingRect.height / 2;
         break;
      case LegendOption.RIGHT:
         min = boundingRect.width / 2;
         max = boundingRect.width - this.legend.minSize.width;
         break;
      case LegendOption.BOTTOM:
         min = boundingRect.height / 2;
         max = boundingRect.height - this.legend.minSize.height;
         break;
      case LegendOption.LEFT:
         min = this.legend.minSize.width;
         max = boundingRect.width / 2;
         break;
      default:
         break;
      }

      return {
         chartObject: null,
         regions: null,
         minPosition: min,
         maxPosition: max,
         vertical: vertical,
         type: "legend",
         legend: this.legend
      };
   }
}
