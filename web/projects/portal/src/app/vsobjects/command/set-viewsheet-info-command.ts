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
import { ViewsheetCommand } from "../../common/viewsheet-client/index";
import { AssetEntry } from "../../../../../shared/data/asset-entry";

/**
 * Command used to set the viewsheet info on the client.
 */
export interface SetViewsheetInfoCommand extends ViewsheetCommand {
   /**
    * The viewsheet info.
    */
   info: {[name: string]: any};

   /**
    * The viewsheet assembly info.
    */
   assemblyInfo: any;

   /**
    * The worksheet asset entry.
    */
   baseEntry: AssetEntry;

   /**
    * The list of layout names.
    */
   layouts: string[];

   /**
    * The link uri.
    */
   linkUri: string;

   annotation: boolean;
   annotated: boolean;
   formTable: boolean;
   assetId?: string;
   hasScript?: boolean;
}
