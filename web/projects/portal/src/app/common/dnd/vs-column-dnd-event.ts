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
import { ViewsheetEvent } from "../viewsheet-client/index";

/**
 * Event for dnd.
 */
export class VSColumnDndEvent implements ViewsheetEvent {
   /**
    * Creates a new instance of <tt>VSDndEvent</tt>.
    */
   constructor(name: string, confirmed: boolean = true,
      checkTrap: boolean = false, sourceChanged: boolean = false,
      table: string = null)
   {
      this.name = name;
      this.confirmed = confirmed;
      this.checkTrap = checkTrap;
      this.sourceChanged = sourceChanged;
      this.table = table;
   }

   private name: string;
   private confirmed: boolean;
   public checkTrap: boolean;
   public sourceChanged: boolean;
   private table: string;
}
