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
import { Dimension } from "../../../common/data/dimension";
import { GuiTool } from "../../../common/util/gui-tool";
import { ViewsheetEvent } from "../../../common/viewsheet-client/index";
import { BaseTableModel } from "../../model/base-table-model";

/**
 * Event for common parameters for chart user actions.
 */
export class MaxTableEvent implements ViewsheetEvent {
   private maxSize: Dimension;
   private tableName: string;

   /**
    * The viewport width of the browser.
    */
   public width: number;

   /**
    * The viewport height of the browser.
    */
   public height: number;

   /**
    * Creates a new instance of <tt>VSChartEvent</tt>.
    */
   constructor(model: BaseTableModel, maxMode: boolean = model.maxMode, container: any = null,
               w: number = 0, h: number = 0)
   {
      this.tableName = model.absoluteName;

      if(maxMode) {
         this.maxSize = GuiTool.getChartMaxModeSize(container);
      }

      this.width = w;
      this.height = h;
   }
}