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
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from "@angular/core";
import { DashboardOption } from "./dashboard-option";

@Component({
   selector: "em-dashboards-settings-sortable-table-view",
   templateUrl: "./dashboards-settings-sortable-table-view.component.html",
   styleUrls: ["./dashboards-settings-sortable-table-view.component.scss"]
})
export class DashboardsSettingsSortableTableViewComponent implements OnChanges {
   @Input() dataSource: DashboardOption[];
   @Input() title: string = "_#(js:Dashboards)";
   @Output() dashboardSettingsChanged: EventEmitter<DashboardOption[]> = new EventEmitter();
   columnNames: string[] = ["name", "enable", "arrange"];

   ngOnChanges(changes: SimpleChanges) {
      if(changes.dataSource && this.dataSource != null || changes.reorder) {
         for(let row of this.dataSource) {
            row.arrange = true;
         }
      }
   }

   getDashboardLabel(dashboard: string) {
      return dashboard.replace("__GLOBAL", " _#(js:dashboard.globalLabel)");
   }

   upButtonEnabled(enable: boolean, index: number): boolean {
      let disableIndex = this.dataSource.findIndex((dashboard) => !dashboard.enable);
      disableIndex = disableIndex < 0 ? this.dataSource.length : disableIndex;
      return enable && index > 0 && index < disableIndex;
   }

   downButtonEnabled(enable: boolean, index: number): boolean {
      let disableIndex = this.dataSource.findIndex((dashboard) => !dashboard.enable);
      disableIndex = disableIndex < 0 ? this.dataSource.length : disableIndex;
      return enable && index >= 0 && index < disableIndex - 1;
   }

   moveUp(index: number) {
      const array = this.dataSource.slice(0);
      const element = array.splice(index, 1);
      array.splice(index - 1, 0, ...element);
      this.dataSource = array;
      this.fireDashboardSettingsChangedEvent();
   }

   moveDown(index: number) {
      const array = this.dataSource.slice(0);
      const element = array.splice(index, 1);
      array.splice(index + 1, 0, ...element);
      this.dataSource = array;
      this.fireDashboardSettingsChangedEvent();
   }

   changeVisible(enable: boolean, index: number) {
      const array = this.dataSource.slice(0);
      const element = array.splice(index, 1);

      if(enable) {
         let disableIndex = array.findIndex((dashboard) => !dashboard.enable);
         disableIndex  = disableIndex < 0 ? index : disableIndex;
         array.splice(disableIndex, 0, ...element);
      }
      else {
         let lastEnabledIndex = array
            .map((dashboard) => dashboard.enable)
            .lastIndexOf(true);
         array.splice(lastEnabledIndex + 1, 0, ...element);
      }

      this.dataSource = array;
      this.fireDashboardSettingsChangedEvent();
   }

   fireDashboardSettingsChangedEvent() {
      let array = this.dataSource.map((elem) => {
         return { name: elem.name, enable: elem.enable };
      });
      this.dashboardSettingsChanged.emit(array);
   }
}
