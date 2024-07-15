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
import { Rectangle } from "../../common/data/rectangle";

/**
 * Event for common parameters for composer object events.
 */
export class ModifyTableLayoutEvent implements ViewsheetEvent {
   /**
    * Creates a new instance of <tt>GetTableModelEvent</tt>.
    *
    * @param chartName the name of the viewsheet object.
    */
   constructor(name: string, op: string, num: number, selection: Rectangle) {
      this.name = name;
      this.op = op;
      this.num = num;
      this.selection = selection;
   }

   private name: string;
   private op: string;
   private num: number;
   private selection: Rectangle;
}
