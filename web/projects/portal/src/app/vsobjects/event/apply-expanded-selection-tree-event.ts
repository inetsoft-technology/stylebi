/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import { ViewsheetEvent } from "../../common/viewsheet-client";

/**
 * Class that encapsulates an event sent to the server to instruct it to update the
 * expanded values in a selection tree
 */
export class ApplyExpandedSelectionTreeEvent implements ViewsheetEvent {
   public expandedValues: String[];

   /**
    * Creates a new instance of <tt>ApplyExpandedSelectionTreeEvent</tt>.
    *
    * @param visibleValues the updated expanded values.
    */
   constructor(expandedValues: String[])
   {
      this.expandedValues = expandedValues;
   }
}