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
import { Component, Input, OnInit } from "@angular/core";
import { Range } from "../../../common/data/range";
import { MultiSelectList } from "../../../common/util/multi-select-list";
import { FiltersPaneModel } from "../../data/vs/filters-pane-model";

@Component({
   selector: "filters-pane",
   templateUrl: "filters-pane.component.html",
   styleUrls: ["filters-pane.component.scss"],
})
export class FiltersPane implements OnInit {
   @Input() model: FiltersPaneModel;
   private readonly filtersSelection = new MultiSelectList();
   private readonly sharedFiltersSelection = new MultiSelectList();

   ngOnInit(): void {
      for(let filter of this.model.sharedFilters) {
         let index: number = -1;

         for(let i: number = 0; i < this.model.filters.length; i++) {
            if(filter.column === this.model.filters[i].column) {
               index = i;
               break;
            }
         }

         if(index !== -1) {
            this.model.filters.splice(index, 1);
         }
      }

      this.filtersSelection.setSize(this.model.filters.length);
      this.sharedFiltersSelection.setSize(this.model.sharedFilters.length);

      if(this.filtersSelection.size() > 0) {
         this.filtersSelection.select(0);
      }

      if(this.sharedFiltersSelection.size() > 0) {
         this.sharedFiltersSelection.select(0);
      }
   }

   selectFilter(index: number, event: MouseEvent): void {
      this.filtersSelection.selectWithEvent(index, event);
   }

   isFilterSelected(index: number): boolean {
      return this.filtersSelection.isSelected(index);
   }

   selectSharedFilter(index: number, event: MouseEvent): void {
      this.sharedFiltersSelection.selectWithEvent(index, event);
   }

   isSharedFilterSelected(index: number): boolean {
      return this.sharedFiltersSelection.isSelected(index);
   }

   add(): void {
      const selectRangeStart = this.model.sharedFilters.length;
      let maxSelectedIndex = -1;
      let isSelected = false;

      this.model.filters
         .filter((_, i) => {
            isSelected = this.filtersSelection.isSelected(i);

            if(isSelected && i > maxSelectedIndex) {
               maxSelectedIndex = i;
            }

            return isSelected;
         })
         .forEach((filter, i, arr) => {
            filter.filterId = filter.column;
            this.model.sharedFilters.push(filter);
            let index: number = this.model.filters.indexOf(filter);
            this.model.filters.splice(index, 1);

            if(i != arr.length - 1) {
               maxSelectedIndex--;
            }
         });

      this.filtersSelection.setSize(this.model.filters.length);

      if(maxSelectedIndex >= this.model.filters.length) {
         maxSelectedIndex = this.model.filters.length - 1;
      }

      if(maxSelectedIndex >= 0 && this.model.filters.length >= 0) {
         this.filtersSelection.selectRange(new Range(maxSelectedIndex, maxSelectedIndex));
      }

      const selectRangeEnd = this.model.sharedFilters.length - 1;
      this.sharedFiltersSelection.setSize(this.model.sharedFilters.length);
      this.sharedFiltersSelection.selectRange(new Range(selectRangeStart, selectRangeEnd));
   }

   isAddEnabled(): boolean {
      return this.filtersSelection.hasSelection();
   }

   remove(): void {
      const selectRangeStart = this.model.filters.length;
      let maxRemoveIndex = -1;
      let isSelected = false;

      this.model.sharedFilters.filter((_, i) => {
            isSelected = this.sharedFiltersSelection.isSelected(i);

            if(isSelected && i > maxRemoveIndex) {
               maxRemoveIndex = i;
            }

            return isSelected;
         })
         .forEach((filter, i, arr) => {
            let index: number = this.model.sharedFilters.indexOf(filter);
            this.model.sharedFilters.splice(index, 1);
            this.model.filters.push(filter);

            if(i != arr.length - 1) {
               maxRemoveIndex--;
            }
         });

      const selectRangeEnd = this.model.filters.length - 1;

      this.filtersSelection.setSize(this.model.filters.length);
      this.filtersSelection.selectRange(new Range(selectRangeStart, selectRangeEnd));
      this.sharedFiltersSelection.setSize(this.model.sharedFilters.length);

      if(maxRemoveIndex >= this.model.sharedFilters.length) {
         maxRemoveIndex = this.model.sharedFilters.length - 1;
      }

      if(maxRemoveIndex >= 0 && this.model.sharedFilters.length >= 0) {
         this.sharedFiltersSelection.selectRange(new Range(maxRemoveIndex, maxRemoveIndex));
      }
   }

   isRemoveEnabled(): boolean {
      return this.sharedFiltersSelection.hasSelection();
   }
}
