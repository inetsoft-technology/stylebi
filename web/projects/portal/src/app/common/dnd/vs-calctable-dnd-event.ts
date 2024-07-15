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
import { VSColumnDndEvent } from "./vs-column-dnd-event";
import { CalcTableAddColumnInfo } from "./calctable-add-column-info";
import { CalcTableRemoveColumnInfo } from "./calctable-remove-column-info";

/**
 * Event for dnd.
 */
export class VSCalcTableDndEvent extends VSColumnDndEvent {
   /**
    * Creates a new instance of <tt>VSCalcTableDndEvent</tt>.
    */
   constructor(name: string, addInfo: CalcTableAddColumnInfo,
      removeInfo: CalcTableRemoveColumnInfo, confirmed: boolean = true,
      checkTrap: boolean = false, sourceChanged: boolean = false,
      table: string = null)
   {
      super(name, confirmed, checkTrap, sourceChanged, table);
      this.addInfo = addInfo;
      this.removeInfo = removeInfo;
   }

   private addInfo: CalcTableAddColumnInfo;
   private removeInfo: CalcTableRemoveColumnInfo;
}
