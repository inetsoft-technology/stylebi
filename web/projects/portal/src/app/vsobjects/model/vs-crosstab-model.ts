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
import { BaseTableModel } from "./base-table-model";
import { BaseTableCellModel } from "./base-table-cell-model";

export interface VSCrosstabModel extends BaseTableModel {
   readonly rowNames: string[];
   readonly colNames: string[];
   readonly aggrNames: string[];
   readonly sortTypeMap: {[field: string]: number};
   readonly dataTypeMap: {[field: string]: string};
   readonly sortOnHeader: boolean;
   readonly sortAggregate: boolean;
   readonly sortDimension: boolean;
   readonly containsFakeAggregate: boolean;
   readonly summarySideBySide?: boolean;
   readonly dateRangeNames: string[];
   readonly timeSeriesNames: string[];
   readonly hasHiddenColumn: boolean;
   cells?: BaseTableCellModel[][];
   tableHeaderCells?: BaseTableCellModel[][];

   // populate when table data is loaded - may be different than design
   runtimeRowHeaderCount?: number;
   runtimeColHeaderCount?: number;
   filterFields?: string[];
   customPeriod: boolean;
   dateComparisonDescription?: string;
   dateComparisonEnabled: boolean;
   dateComparisonDefined: boolean;
   appliedDateComparison: boolean;
   dcMergedColumn: string;
}
