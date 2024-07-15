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
import { AssetEntryHelper } from "./asset-entry-helper";

export class AssetConstants {
   /**
    * Name event.
    */
   public static NAME_EVENT: string = "__NAME";
   /**
    * Expression event.
    */
   public static EXPRESSION_EVENT: string = "__EXPRESSION";

   /**
    * Query scope asset.
    */
   public static QUERY_SCOPE: number = AssetEntryHelper.QUERY_SCOPE;
   /**
    * Global scope asset.
    */
   public static GLOBAL_SCOPE: number = AssetEntryHelper.GLOBAL_SCOPE;
   /**
    * Report scope asset.
    */
   public static REPORT_SCOPE: number = AssetEntryHelper.REPORT_SCOPE;
   /**
    * User scope asset.
    */
   public static USER_SCOPE: number = AssetEntryHelper.USER_SCOPE;
   /**
    * Temporary scope asset.
    */
   public static COMPONENT_SCOPE: number = AssetEntryHelper.COMPONENT_SCOPE;

   /**
    * Temporary scope asset.
    */
   public static TEMPORARY_SCOPE: number = AssetEntryHelper.TEMPORARY_SCOPE;

   /**
    * Preview worksheet.
    */
   public static PREVIEW_WORKSHEET: string = "__PREVIEW_WORKSHEET__";

   /**
    * Preview viewsheet.
    */
   public static PREVIEW_VIEWSHEET: string = "__PREVIEW_VIEWSHEET__";

   /**
    * Level message.
    */
   // public static LEVEL_MESSAGE: string = StyleConstants.LEVEL_MESSAGE;
   /**
    * Data message.
    */
   // public static DATA_MESSAGE: string = StyleConstants.DATA_MESSAGE;

   /**
    * Design Mode.
    */
   public static DESIGN_MODE: number = 1;
   /**
    * Live Mode.
    */
   public static LIVE_MODE: number = 2;
   /**
    * Runtime Mode.
    */
   public static RUNTIME_MODE: number = 4;
   /**
    * Embedded Mode.
    */
   public static EMBEDDED_MODE: number = 8;

   /**
    * Condition asset.
    */
   public static CONDITION_ASSET: number = 1;
   /**
    * Named group asset.
    */
   public static NAMED_GROUP_ASSET: number = 2;
   /**
    * Variable asset.
    */
   public static VARIABLE_ASSET: number = 3;
   /**
    * Table asset.
    */
   public static TABLE_ASSET: number = 4;
   /**
    * Date Condition asset.
    */
   public static DATE_RANGE_ASSET: number = 5;
}