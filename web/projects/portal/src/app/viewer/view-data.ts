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
import { BaseTableModel } from "../vsobjects/model/base-table-model";
import { VSChartModel } from "../vsobjects/model/vs-chart-model";

export interface ViewData {
   queryParameters: Map<string, string[]>;
   assetId: string;
   linkUri?: string;
   runtimeId?: string;
   isMetadata?: boolean; // if vs use meta is true
   variableValues?: string[];
   tableModel?: BaseTableModel;
   chartModel?: VSChartModel;
   toolbarPermissions?: string[];
   portal?: boolean;
   dashboard?: boolean;
   dashboardName?: string;
   fullScreen?: boolean;
   fullScreenId?: string;
   previousSnapshots?: string[]; // json string of PreviousSnapshot
   collapseTree?: boolean;
   scaleToScreen?: boolean;
   fitToWidth?: boolean;
}
