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
import { Dimension } from "../../../common/data/dimension";
import { GuiTool } from "../../../common/util/gui-tool";
import { ViewsheetEvent } from "../../../common/viewsheet-client/index";
import { BaseTableModel } from "../../model/base-table-model";

/**
 * Event for toggle max mode.
 */
export class MaxObjectEvent implements ViewsheetEvent {
   private maxSize: Dimension;
   private assemblyName: string;

   constructor(absoluteName: string, container: any = null) {
      this.assemblyName = absoluteName;

      if(!!container) {
         this.maxSize = GuiTool.getChartMaxModeSize(container);
      }
   }
}