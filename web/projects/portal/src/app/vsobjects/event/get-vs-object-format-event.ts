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
import { VSObjectEvent } from "./vs-object-event";
import { TableDataPath } from "../../common/data/table-data-path";

/**
 * Event used to retrieve a format for the toolbar.
 */
export class GetVSObjectFormatEvent extends VSObjectEvent {
   /**
    * The data path of the selected object region.
    */
   public dataPath: TableDataPath;

   /**
    * If table cell, then the row of the selected.
    */
   public row: number;

   /**
    * If table cell, the column of the selected.
    */
   public column: number;

   /**
    * Chart area name
    */
   public region: string;

   /**
    * Chart binded column name.
    */
   public columnName: string;

   /**
    * Whether the column is a dimension column (if not it's a measure column)
    */
   public dimensionColumn: boolean;

   /**
    * Region index
    */
   public index: number;

   /**
    * Whether this is for a print layout or normal vs
    */
   public layout: boolean;

   /**
    * Layout region
    */
   public layoutRegion: number;

   // True if this is sent from a binding pane
   public binding: boolean;

   // true if this is a votext strictly for show-value (not text field binding)
   public valueText: boolean;

   /**
    * Creates a new instance of <tt>GetVSObjectFormatEvent</tt>.
    *
    * @param name the name of the object.
    */
   constructor(name: string) {
      super(name);
   }
}
