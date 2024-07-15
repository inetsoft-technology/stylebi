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
import { ViewsheetEvent } from "../viewsheet-client/index";
import { DataTransfer, DropTarget } from "../data/dnd-transfer";
import { AssetEntry } from "../../../../../shared/data/asset-entry";

/**
 * Event for dnd.
 */
export class VSDndEvent implements ViewsheetEvent {
   /**
    * Creates a new instance of <tt>VSDndEvent</tt>.
    *
    * @param transfer the transfer of the viewsheet object.
    */
   constructor(name: string, transfer: DataTransfer, dropTarget: DropTarget,
      entries?: AssetEntry[], table?: string, confirmed?: boolean, checkTrap?: boolean,
      sourceChanged?: boolean)
   {
      this.name = name;
      this.transfer = transfer;
      this.dropTarget = dropTarget;
      this.entries = entries;
      this.table = table;
      this.confirmed = confirmed != false;
      this.checkTrap = checkTrap != false;
      this.sourceChanged = sourceChanged == true;
   }

   private transfer: DataTransfer;
   private dropTarget: DropTarget;
   private entries: AssetEntry[];
   private name: string;
   private table: string;
   public confirmed: boolean;
   public checkTrap: boolean;
   public sourceChanged: boolean;
}
