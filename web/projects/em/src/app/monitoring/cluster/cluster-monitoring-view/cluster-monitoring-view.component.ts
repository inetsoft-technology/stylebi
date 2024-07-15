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
import { SelectionModel } from "@angular/cdk/collections";
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { TableInfo } from "../../../common/util/table/table-info";
import { TableModel } from "../../../common/util/table/table-model";
import { ReportClusterNodeModel } from "../cluster-monitoring-model/report-cluster-node-model";

@Component({
  selector: "em-cluster-monitoring-view",
  templateUrl: "./cluster-monitoring-view.component.html",
  styleUrls: ["./cluster-monitoring-view.component.scss"]
})
export class ClusterMonitoringViewComponent {
   @Input() dataSource: ReportClusterNodeModel[];
   @Input() monitoringTableInfo: TableInfo;
   @Input() trackByProp: string;
   @Output() selectionChanged = new EventEmitter<SelectionModel<TableModel>>();
}
