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
import { Component, Inject } from "@angular/core";
import { MAT_BOTTOM_SHEET_DATA, MatBottomSheetRef } from "@angular/material/bottom-sheet";
import { SortOptions } from "../../../../../../../shared/util/sort/sort-options";
import { SortTypes } from "../../../../../../../shared/util/sort/sort-types";
import { Tool } from "../../../../../../../shared/util/tool";

@Component({
   selector: "em-base-query-result",
   templateUrl: "./base-query-result.component.html"
})
export class BaseQueryResult {
   queryResult: string[];
   label: string = "_#(js:Query Result)";
   selectable = false;

   constructor(private bottomSheetRef: MatBottomSheetRef<BaseQueryResult>,
               @Inject(MAT_BOTTOM_SHEET_DATA) public data: any)
   {
      this.queryResult = Tool.sortObjects(data.queryResult,
      new SortOptions([], SortTypes.ASCENDING));
      this.label = data.label ?? this.label;
      this.selectable = !!data.selectable;
   }

   ok(event: MouseEvent, item: string): void {
      event.preventDefault();
      event.stopPropagation();

      if(this.selectable) {
         this.bottomSheetRef.dismiss(item);
      }
   }
}
