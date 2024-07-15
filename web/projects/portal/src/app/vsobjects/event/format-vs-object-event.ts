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
import { ViewsheetEvent } from "../../common/viewsheet-client/viewsheet-event";
import { TableDataPath } from "../../common/data/table-data-path";
import { VSObjectFormatInfoModel } from "../../common/data/vs-object-format-info-model";

/**
 * Event used to change the format of one or more objects.
 */
export class FormatVSObjectEvent implements ViewsheetEvent {
   /**
    * The new format.
    */
   public format: VSObjectFormatInfoModel;

   /**
    * The original format.
    */
   public origFormat: VSObjectFormatInfoModel;

   /**
    * The list of objects to change (not including charts).
    */
   public objects: string[];

   /**
    * The data paths of the object regions to change.
    */
   public data: TableDataPath[][];

   /**
    * The list of selected charts.
    */
   public charts: string[];

   /**
    * The list of selected chart regions.
    */
   public regions: string[];

   /**
    * The list of selected chart region indexes.
    */
   public indexes: number[][];

   /**
    * The list of column names.
    */
   public columnNames: string[][];

   /**
    * Whether this is for a print layout or normal vs
    */
   public layout: boolean;

   /**
    * Layout region
    */
   public layoutRegion: number;

   /**
    * For show-values and not text field.
    */
   public valueText: boolean;

   /**
    * Whether this is a reset event
    */
   public reset: boolean;

   public copyFormat: boolean;
}
