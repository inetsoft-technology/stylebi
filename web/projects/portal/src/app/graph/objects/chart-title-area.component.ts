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
import { ChangeDetectionStrategy, Component, EventEmitter, Output, Input } from "@angular/core";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { ChartRegion } from "../model/chart-region";
import { Title } from "../model/title";
import { ChartService } from "../services/chart.service";
import { ChartObjectAreaBase } from "./chart-object-area-base";
import { GuiTool } from "../../common/util/gui-tool";

@Component({
   selector: "chart-title-area",
   templateUrl: "chart-title-area.component.html",
   styleUrls: ["chart-title-area.component.scss"],
   providers: [{
      provide: ChartObjectAreaBase,
      useExisting: ChartTitleArea
   }],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class ChartTitleArea extends ChartObjectAreaBase<Title> {
   @Output() selectRegion = new EventEmitter();
   @Output() enterTitle = new EventEmitter();
   @Output() leaveTitle = new EventEmitter();
   @Input() container: Element;

   isMouseDown: boolean = false;

   constructor(protected chartService: ChartService,
               scaleService: ScaleService)
   {
      super(chartService, scaleService);
   }

   onDown(event: MouseEvent): void {
      if(event.type === "pointerdown" ||
         event.type === "mousedown" && !GuiTool.supportPointEvent())
      {
         this.isMouseDown = true;
      }
   }

   onUp(event: MouseEvent): void {
      if(this.isMouseDown && (event.type === "pointerup" ||
         event.type === "mouseup" && !GuiTool.supportPointEvent()))
      {
         let clientRect = this.objectCanvas.nativeElement.getBoundingClientRect();
         let objLeft = clientRect.left;
         let objTop = clientRect.top;
         let x1 = (event.clientX - objLeft) / this.viewsheetScale;
         let y1 = (event.clientY - objTop) / this.viewsheetScale;
         let regions: ChartRegion[];

         regions = this.getTreeRegions(x1, y1, x1, y1);

         // Select Region
         this.selectRegion.emit({
            chartObject: this.chartObject,
            context: this.getContext(),
            canvasX: this.canvasX,
            canvasY: this.canvasY,
            regions: regions,
            isCtrl: event.ctrlKey
         });

         this.isMouseDown = false;
      }
   }

   public updateChartObject(): void {
      // do nothing
   }

   protected cleanup(): void {
      // no-op
   }

   public isVisible(): boolean {
      // if image is returned as null (see VGraphPair.getX2TitleGraphics.getX2TitleGraphic),
      // don't show an empty image which may cover the plot border.
      return this.chartObject && (this.chartObject.areaName != "x2_title" ||
         // should show top title other grid border may be missing. (57451)
                                  this.chartObject.layoutBounds.height >= 1);
   }
}
