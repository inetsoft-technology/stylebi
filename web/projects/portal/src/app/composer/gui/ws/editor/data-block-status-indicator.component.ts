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
   Output
} from "@angular/core";
import { AbstractTableAssembly } from "../../../data/ws/abstract-table-assembly";
import { ConcatenatedTableAssembly } from "../../../data/ws/concatenated-table-assembly";
import { Tool } from "../../../../../../../shared/util/tool";

@Component({
   selector: "data-block-status-indicator",
   templateUrl: "data-block-status-indicator.component.html",
   changeDetection: ChangeDetectionStrategy.OnPush,
   styleUrls: ["data-block-status-indicator.component.scss"]
})
export class DataBlockStatusIndicatorComponent {
   @Input() table: AbstractTableAssembly;
   @Output() onConditionIconClicked = new EventEmitter();
   @Output() onAggregateIconClicked = new EventEmitter();
   @Output() onSortIconClicked = new EventEmitter();

   concatenationWarning(): boolean {
      return this.table instanceof ConcatenatedTableAssembly &&
         this.table.concatenationWarning;
   }

   getColumnsLabel(): string {
      let visibleColCount = this.table?.headers?.length;
      visibleColCount = isNaN(visibleColCount) ? 0 : visibleColCount;

      let usePublicCols = this.table?.crosstab && (this.table?.mode == "default" ||
         this.table?.mode == "live");
      let totalColCount = usePublicCols ? this.table?.info?.publicSelection?.length :
         this.table?.info?.privateSelection?.length;
      totalColCount = isNaN(totalColCount) ? 0 : totalColCount;

      return "_#(js:cols) : " + Tool.formatCatalogString("_#(js:nOfTotal)", [visibleColCount, totalColCount]);
   }
}
