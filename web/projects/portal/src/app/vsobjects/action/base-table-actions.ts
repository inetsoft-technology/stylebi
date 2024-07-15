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
import { XConstants } from "../../common/util/xconstants";
import { ContextProvider } from "../context-provider.service";
import { BaseTableModel } from "../model/base-table-model";
import { AbstractVSActions } from "./abstract-vs-actions";
import { ActionStateProvider } from "./action-state-provider";
import { AnnotatableActions } from "./annotatable-actions";
import { TableDataPath } from "../../common/data/table-data-path";
import { Tool } from "../../../../../shared/util/tool";
import { DataTipService } from "../objects/data-tip/data-tip.service";
import { PopComponentService } from "../objects/data-tip/pop-component.service";
import { MiniToolbarService } from "../objects/mini-toolbar/mini-toolbar.service";

export abstract class BaseTableActions<T extends BaseTableModel> extends AbstractVSActions<T>
   implements AnnotatableActions
{
   constructor(model: T, contextProvider: ContextProvider, securityEnabled: boolean,
               protected stateProvider: ActionStateProvider,
               dataTipService: DataTipService = null,
               popService: PopComponentService = null,
               miniToolbarService: MiniToolbarService = null)
   {
      super(model, contextProvider, securityEnabled, stateProvider,
            dataTipService, popService, miniToolbarService);
   }

   protected get cellSelected(): boolean {
      return this.headerCellSelected || this.dataCellSelected;
   }

   protected get headerCellSelected(): boolean {
      return !!this.model.selectedHeaders && this.model.selectedHeaders.size > 0;
   }

   protected get dataCellSelected(): boolean {
      return !!this.model.selectedData && this.model.selectedData.size > 0;
   }

   protected get oneCellSelected(): boolean {
      if(this.model.selectedHeaders && this.model.selectedData) {
         return false;
      }

      const map: Map<number, number[]> = this.model.selectedHeaders || this.model.selectedData;

      if(map && map.size === 1) {
         const columns = map.values().next().value;
         return columns && columns.length == 1;
      }

      return false;
   }

   protected get hasHighlight(): boolean {
      const selectedCell: TableDataPath = Object.assign( {}, this.model.selectedRegions[0]);

      if(selectedCell) {
         const paths: TableDataPath[] = this.model.highlightedCells;

         for(let i = 0; i < paths.length; i++) {
            selectedCell.dataType = paths[i].dataType;
            if(Tool.isEquals(paths[i], selectedCell)) {
               return true;
            }
         }
      }

      return false;
   }

   protected get sortIcon(): string {
      let icon: string;
      const sortValue: number = this.model.sortInfo ? this.model.sortInfo.sortValue : 0;

      switch(sortValue) {
      case XConstants.SORT_ASC:
         icon = "sort-asc-icon";
         break;
      case XConstants.SORT_DESC:
         icon = "sort-desc-icon";
         break;
      case XConstants.SORT_VALUE_ASC:
         icon = "sort-val-asc-icon";
         break;
      case XConstants.SORT_VALUE_DESC:
         icon = "sort-val-desc-icon";
         break;
      default:
         icon = "sort-icon";
         break;
      }

      return icon;
   }
}
