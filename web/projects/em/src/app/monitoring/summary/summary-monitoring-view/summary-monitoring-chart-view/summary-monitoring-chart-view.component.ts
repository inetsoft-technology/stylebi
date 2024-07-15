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
   ElementRef,
   EventEmitter,
   Input,
   NgZone,
   OnChanges,
   Output,
   ViewChild
} from "@angular/core";
import {MonitorLevelService} from "../../../monitor-level.service";
import {ChartInfo} from "../../summary-monitoring-page/summary-monitoring-page.component";
import {SummaryChartInfo} from "./summary-chart-info";
import {SummaryChartLegend} from "./summary-chart-legend";
import {Tool} from "../../../../../../../shared/util/tool";

@Component({
   selector: "em-summary-monitoring-chart-view",
   templateUrl: "./summary-monitoring-chart-view.component.html",
   styleUrls: ["./summary-monitoring-chart-view.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class SummaryMonitoringChartViewComponent implements OnChanges {
   @ViewChild("chartContainer", { static: true }) chart: ElementRef;
   @ViewChild("chartImg", { static: true }) chartImg: ElementRef;
   @Input() info: SummaryChartInfo;
   @Input() legends: SummaryChartLegend[];
   @Input() timestamp: number;
   @Input() clusterEnabled: boolean;
   @Input() chartLink: string;
   @Output() onLinkClick: EventEmitter<ChartInfo> = new EventEmitter<ChartInfo>();
   showLegends: boolean = true;
   imageError = false;
   monitorLevelLabel: string | null = null;
   _selectedClusterNode: string;

   @Input()
   set selectedClusterNode(value: string) {
      if(this._selectedClusterNode != value) {
         this.imageError = false;
      }

      this._selectedClusterNode = value;
   }

   get selectedClusterNode(): string {
      return this._selectedClusterNode;
   }

   constructor(private zone: NgZone,
               private levelService: MonitorLevelService)
   {
   }

   ngOnChanges(): void {
      if(this.levelService.getMonitorLevel() <= this.info.monitorLevel) {
         this.monitorLevelLabel = this.levelService.getMonitorLevelLabel(this.info.monitorLevel);
      }
      else {
         this.monitorLevelLabel = null;
      }
   }

   get chartUri(): string {
      if(!this.timestamp) {
         return "";
      }

      let bounds = this.chart.nativeElement.getBoundingClientRect();

      let url = "../em/getSummaryImage/" + this.info.name + "/" + bounds.width + "/" +
         bounds.height + "?timestamp=" + this.timestamp;

      if(this.clusterEnabled && this.selectedClusterNode) {
         url += "&clusterNode=" + encodeURIComponent(this.selectedClusterNode);
      }

      if(this.levelService.getMonitorLevel() <= this.info.monitorLevel) {
         return "";
      }

      return url;
   }

   updateChartUri(): void {
      this.chartImg.nativeElement.src = this.chartUri;
   }

   onImageError(): void {
      this.showLegends = false;
      this.imageError = true;
   }

   getMonitorLevelErrorMessage(level: string): string {
      return Tool.formatCatalogString("_#(js:monitor.dashboard.imageInvisible)", [level]);
   }
}
