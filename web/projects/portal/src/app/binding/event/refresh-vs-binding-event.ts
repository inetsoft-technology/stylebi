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
import { ViewsheetEvent } from "../../common/viewsheet-client/index";
import { Dimension } from "../../common/data/dimension";
import { GuiTool } from "../../common/util/gui-tool";

/**
 * Event for common parameters for composer object events.
 */
export class RefreshVSBindingEvent implements ViewsheetEvent {
   /**
    * Creates a new instance of <tt>GetVSBindingModelEvent</tt>.
    *
    * @param chartName the name of the viewsheet object.
    */
   constructor(vsId: string, name: string) {
      this.vsId = vsId;
      this.name = name;
   }

   private name: string;
   private vsId: string;
}
