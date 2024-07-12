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
import { VSColumnDndEvent } from "./vs-column-dnd-event";
import { TableAddColumnInfo } from "./table-add-column-info";
import { TableRemoveColumnInfo } from "./table-remove-column-info";

/**
 * Event for dnd.
 */
export class VSTableDndEvent extends VSColumnDndEvent {
   /**
    * Creates a new instance of <tt>VSDndEvent</tt>.
    */
   constructor(name: string, addInfo: TableAddColumnInfo,
      removeInfo: TableRemoveColumnInfo, confirmed: boolean = true,
      checkTrap: boolean = false, sourceChanged: boolean = false,
      table: string = null)
   {
      super(name, confirmed, checkTrap, sourceChanged, table);
      this.addInfo = addInfo;
      this.removeInfo = removeInfo;
   }

   private addInfo: TableAddColumnInfo;
   private removeInfo: TableRemoveColumnInfo;
}
