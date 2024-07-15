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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { QueryMonitoringTableModel } from "../query-monitoring-table-model";
import { ExpandableRowTableInfo } from "../../../common/util/table/expandable-row-table/expandable-row-table-info";

@Component({
   selector: "em-query-monitoring-view",
   templateUrl: "./query-monitoring-view.component.html",
   styleUrls: ["./query-monitoring-view.component.scss"]
})
export class QueryMonitoringViewComponent {
   @Input() dataSource: QueryMonitoringTableModel[];
   @Output() removeSelection = new EventEmitter();
   @Output() clickCell = new EventEmitter<any>();

   _monitoringTableInfo: ExpandableRowTableInfo;

   @Input() set monitoringTableInfo(monitoringTableInfo: ExpandableRowTableInfo) {
      this._monitoringTableInfo = monitoringTableInfo;
   }

   get monitoringTableInfo(): ExpandableRowTableInfo {
      return this._monitoringTableInfo;
   }

   timeCols: string[] = ["age"];
}
