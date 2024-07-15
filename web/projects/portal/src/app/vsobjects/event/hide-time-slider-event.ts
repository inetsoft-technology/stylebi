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
import { ViewsheetEvent } from "../../common/viewsheet-client/index";

/**
 * Class that encapsulates an event sent to the server to instruct it to update the
 * selection in a selection list.
 */
export class HideTimeSliderEvent implements ViewsheetEvent {
   /**
    * The type of update to apply.
    */
   public hide: boolean;

   /**
    * Creates a new instance of <tt>ApplySelectionListEvent</tt>.
    *
    * @param values the updated selections.
    * @param type   the type of update.
    */
   constructor(hide: boolean)
   {
      this.hide = hide;
   }
}
