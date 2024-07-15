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
import { ViewsheetEvent } from "../../../common/viewsheet-client";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { ChartBindingModel } from "../../../binding/data/chart/chart-binding-model";
import { Tool } from "../../../../../../shared/util/tool";

export class UpdateVsWizardBindingEvent implements ViewsheetEvent {
   constructor(bindingModel: ChartBindingModel, selectedNodes: AssetEntry[],
               deleteFormatColumn?: string, autoOrder?: boolean)
   {
      this.bindingModel = Tool.shallowClone(bindingModel);
      // remove fields that are not used on the server side to reduce the transmission size
      if(this.bindingModel) {
         this.bindingModel.availableFields = [];
      }

      this.selectedNodes = selectedNodes;
      this.deleteFormatColumn = deleteFormatColumn;
      this.autoOrder = autoOrder;
   }

   selectedNodes: AssetEntry[];
   bindingModel: any;
   deleteFormatColumn?: string;
   autoOrder?: boolean;
}
