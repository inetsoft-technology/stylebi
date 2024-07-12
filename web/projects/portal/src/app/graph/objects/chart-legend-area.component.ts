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
import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from "@angular/core";
import { Tool } from "../../../../../shared/util/tool";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { ChartRegion } from "../model/chart-region";
import { ChartTool } from "../model/chart-tool";
import { Legend, } from "../model/legend";
import { ChartService } from "../services/chart.service";
import { ChartObjectAreaBase } from "./chart-object-area-base";
import { TooltipInfo } from "../model/tooltip-info";

@Component({
   selector: "chart-legend-area",
   templateUrl: "chart-legend-area.component.html",
   styleUrls: ["chart-legend-area.component.scss"],
   providers: [{
      provide: ChartObjectAreaBase,
      useExisting: ChartLegendArea
   }],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class ChartLegendArea extends ChartObjectAreaBase<Legend> {
   @Input() maxHeight: number;
   @Input() container: Element;
   @Output() selectRegion = new EventEmitter();
   @Output() brushChart = new EventEmitter();
   @Output() showTooltip = new EventEmitter<TooltipInfo>();
   @Output() mouseoverLegendRegion = new EventEmitter();
   isMouseDown: boolean = false;
   selectionWidth: number;
   selectionHeight: number;
   selectionTop: number;
   selectionLeft: number;
   eventXdown: number;
   eventYdown: number;

   constructor(protected chartService: ChartService,
               scaleService: ScaleService)
   {
      super(chartService, scaleService);
   }

   onDown(event: MouseEvent): void {
      this.isMouseDown = true;
      this.eventYdown = this.selectionTop = event.clientY;
      this.eventXdown = this.selectionLeft = event.clientX;
   }

   onUp(event: MouseEvent): void {
      if(this.isMouseDown && event.clientX == this.eventXdown && event.clientY == this.eventYdown) {
         // Chart legend items region points are based on the whole legend area,
         // this includes the title and content
         let clientRect = this.objectCanvas.nativeElement.getBoundingClientRect();
         let objLeft = clientRect.left;
         let objTop = clientRect.top;
         let x1 = (this.eventXdown - objLeft) / this.viewsheetScale;
         let y1 = (this.eventYdown - objTop) / this.viewsheetScale;
         let x2 = (event.clientX - objLeft) / this.viewsheetScale;
         let y2 = (event.clientY - objTop) / this.viewsheetScale;
         let regions: ChartRegion[];

         regions = this.getTreeRegions(x1, y1, x2, y2)
            .filter((region) => !!region && !region.noselect);

         // Don't do anything when right clicking on a selected region.
         if(event.which != 3 ||
            !Tool.isEquals(this.chartObject, this.chartSelection.chartObject) ||
            !ChartTool.isRegionSelected(this.chartSelection,
               this.chartObject.areaName, regions[0]))
         {
            // Select Region
            this.selectRegion.emit({
               chartObject: this.chartObject,
               context: this.getContext(),
               canvasX: this.canvasX,
               canvasY: this.canvasY,
               regions: regions,
               isCtrl: event.ctrlKey || event.metaKey
            });
         }

         this.selectionWidth = 0;
         this.selectionHeight = 0;
         this.isMouseDown = false;
      }
   }

   onDblClick(event: MouseEvent): void {
      this.brushChart.emit();
   }

   onMove(event: MouseEvent): void {
      let eventX = event.offsetX;
      let eventY = event.offsetY;
      let regions = this.getTreeRegions(eventX, eventY);

      this.emitTooltip(regions);
      this.mouseoverLegendRegion.emit(this.chartObject.areaName);
   }

   protected cleanup(): void {
      // no-op
   }

   private emitTooltip(regions: ChartRegion[]): void {
      let tipInfo = regions
         .map(region => ({
            tipIndex: region.tipIdx == -1 ? ChartTool.dimIdx(this.model, region) : region.tipIdx,
            region: region
         }))
         .find(ti => ti.tipIndex >= 0);
      this.showTooltip.emit(tipInfo);
   }

   public updateChartObject(): void {
      // do nothing
   }

   hasTitle(): boolean {
      return this.chartObject.areaName != "legend_title" && this.chartObject.titleVisible;
   }
}
