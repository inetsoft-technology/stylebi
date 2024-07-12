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

/**
 * Event for common parameters for composer object events.
 */
export class RefreshBindingTreeEvent implements ViewsheetEvent {
   /**
    * Creates a new instance of <tt>RefreshBindingTreeEvent</tt>.
    *
    * @param assembly name of the tree used by.
    */
   constructor(name: string, layoutMode?: boolean) {
      this.name = name;
      this.layoutMode = layoutMode;
   }

   private name: string;
   private layoutMode: boolean;
}
