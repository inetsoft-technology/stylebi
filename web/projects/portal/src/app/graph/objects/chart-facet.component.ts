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
import { ChangeDetectionStrategy, Component, Input } from "@angular/core";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { Facet } from "../model/facet";
import { ChartService } from "../services/chart.service";
import { ChartObjectAreaBase } from "./chart-object-area-base";

@Component({
   selector: "chart-facet-area",
   templateUrl: "./chart-facet.component.html",
   styleUrls: ["./chart-facet.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class ChartFacetArea extends ChartObjectAreaBase<Facet> {
   @Input() container: Element;
   constructor(chartService: ChartService,
               scaleService: ScaleService)
   {
      super(chartService, scaleService);
   }

   /** @inheritDoc */
   public updateChartObject(): void {
      // no-op
   }

   protected cleanup(): void {
      // no-op
   }
}
