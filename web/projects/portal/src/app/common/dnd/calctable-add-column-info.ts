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
import { ViewsheetEvent } from "../viewsheet-client/index";
import { ChangeColumnInfo } from "./change-column-info";
import { Rectangle } from "../data/rectangle";
import { AssetEntry } from "../../../../../shared/data/asset-entry";

/**
 * Event for dnd.
 */
export class CalcTableAddColumnInfo extends ChangeColumnInfo {
   /**
    * Creates a new instance of <tt>TableRemoveColumnInfo</tt>.
    */
   constructor(entries: AssetEntry[], rect: Rectangle,
      assembly: string, objectType: string = "vscalctable")
   {
      super(assembly, objectType);
      this.rect = rect;
      this.entries = entries;
   }

   public rect: Rectangle;
   public entries: AssetEntry[];
}
