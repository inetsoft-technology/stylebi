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

/**
 * Class that encapsulates an event sent to the server to instruct it to move a
 * selection child in a selection container.
 */
export class MoveSelectionChildEvent implements ViewsheetEvent {
   public fromIndex: number;
   public toIndex: number;
   public currentSelection: boolean;

   /**
    * Creates a new instance of <tt>MoveSelectionChildEvent</tt>.
    *
    * @param fromIndex           the index to move from.
    * @param toIndex             the index to move to.
    * @param currentSelection    if it is a current selection.
    */
   constructor(fromIndex: number, toIndex: number, currentSelection: boolean)
   {
      this.fromIndex = fromIndex;
      this.toIndex = toIndex;
      this.currentSelection = currentSelection;
   }
}
