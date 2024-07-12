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
import { ViewsheetEvent } from "../../common/viewsheet-client/index";
import { SelectionStateModel } from "../objects/selection/selection-base-controller";

/**
 * Class that encapsulates an event sent to the server to instruct it to update the
 * selection in a selection list.
 */
export class ApplySelectionListEvent implements ViewsheetEvent {
   /**
    * The type of update to apply.
    */
   public type: ApplySelectionType;

   /**
    * The updated selections.
    */
   public values: SelectionStateModel[];

   public selectStart: number;
   public selectEnd: number;
   public eventSource: string;
   public toggle: boolean;
   public toggleAll: boolean;
   public toggleLevels: number[];

   public static APPLY: ApplySelectionType = "APPLY";
   public static REVERSE: ApplySelectionType = "REVERSE";

   /**
    * Creates a new instance of <tt>ApplySelectionListEvent</tt>.
    *
    * @param values the updated selections.
    * @param type   the type of update.
    */
   constructor(values: SelectionStateModel[] = null,
               type: ApplySelectionType = ApplySelectionListEvent.APPLY,
               selectStart: number = -1, selectEnd: number = -1, eventSource?: string,
               toggle?: boolean, toggleAll?: boolean, toggleLevels?: number[])
   {
      this.type = type;
      this.values = values;
      this.selectStart = selectStart;
      this.selectEnd = selectEnd;
      this.eventSource = eventSource;
      this.toggle = toggle;
      this.toggleAll = toggleAll;
      this.toggleLevels = toggleLevels;
   }
}

export type ApplySelectionType = "APPLY" | "REVERSE";
