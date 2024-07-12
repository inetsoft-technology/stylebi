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
export enum AssetType {
   UNKNOWN = "UNKNOWN",
   FOLDER = "FOLDER",
   ACTUAL_FOLDER = "ACTUAL_FOLDER",
   WORKSHEET = "WORKSHEET",
   DATA = "DATA",
   COLUMN = "COLUMN",
   TABLE = "TABLE",
   QUERY = "QUERY",
   LOGIC_MODEL = "LOGIC_MODEL",
   DATA_SOURCE = "DATA_SOURCE",
   VIEWSHEET = "VIEWSHEET",
   PHYSICAL = "PHYSICAL",
   PHYSICAL_FOLDER = "PHYSICAL_FOLDER",
   PHYSICAL_TABLE = "PHYSICAL_TABLE",
   PHYSICAL_COLUMN = "PHYSICAL_COLUMN",
   COMPONENT = "COMPONENT",
   SHAPE = "SHAPE",
   REPOSITORY_FOLDER = "REPOSITORY_FOLDER",
   VIEWSHEET_SNAPSHOT = "VIEWSHEET_SNAPSHOT",
   VIEWSHEET_BOOKMARK = "VIEWSHEET_BOOKMARK",
   VARIABLE = "VARIABLE",
   DATA_SOURCE_FOLDER = "DATA_SOURCE_FOLDER",
   BEAN = "BEAN",
   PARAMETER_SHEET = "PARAMETER_SHEET",
   META_TEMPLATE = "META_TEMPLATE",
   TABLE_STYLE = "TABLE_STYLE",
   SCRIPT = "SCRIPT",
   REPORT_COMPONENT = "REPORT_COMPONENT",
   BEAN_FOLDER = "BEAN_FOLDER",
   PARAMETER_SHEET_FOLDER = "PARAMETER_SHEET_FOLDER",
   META_TEMPLATE_FOLDER = "META_TEMPLATE_FOLDER",
   TABLE_STYLE_FOLDER = "TABLE_STYLE_FOLDER",
   SCRIPT_FOLDER = "SCRIPT_FOLDER",
   DATA_MODEL = "DATA_MODEL",
   PARTITION = "PARTITION",
   EXTENDED_MODEL = "EXTENDED_MODEL",
   EXTENDED_PARTITION = "EXTENDED_PARTITION",
   EXTENDED_LOGIC_MODEL = "EXTENDED_LOGIC_MODEL",
   VPM = "VPM",
   QUERY_FOLDER = "QUERY_FOLDER",
   LOCAL_QUERY_FOLDER = "LOCAL_QUERY_FOLDER",
   REPORT_WORKSHEET_FOLDER = "REPORT_WORKSHEET_FOLDER",
   EMBEDDED_PS_FOLDER = "EMBEDDED_PS_FOLDER",
   REPLET = "REPLET",
   DOMAIN = "DOMAIN",
   ERM = "ERM",
   SCHEDULE_TASK = "SCHEDULE_TASK",
   SCHEDULE_TASK_FOLDER = "SCHEDULE_TASK_FOLDER",
   LIBRARY_FOLDER="LIBRARY_FOLDER"
}

const typeIds = new Map<number, AssetType>([
   [ 0, AssetType.UNKNOWN ],
   [ 1, AssetType.FOLDER ],
   [ 3, AssetType.ACTUAL_FOLDER ],
   [ 2, AssetType.WORKSHEET ],
   [ 4, AssetType.DATA ],
   [ 12, AssetType.COLUMN ],
   [ 21, AssetType.TABLE ],
   [ 37, AssetType.QUERY ],
   [ 101, AssetType.LOGIC_MODEL ],
   [ 69, AssetType.DATA_SOURCE ],
   [ 128, AssetType.VIEWSHEET ],
   [ 256, AssetType.PHYSICAL ],
   [ 769, AssetType.PHYSICAL_FOLDER ],
   [ 1281, AssetType.PHYSICAL_TABLE ],
   [ 2304, AssetType.PHYSICAL_COLUMN ],
   [ 2048, AssetType.COMPONENT ],
   [ 10000, AssetType.SHAPE ],
   [ 4097, AssetType.REPOSITORY_FOLDER ],
   [ 8320, AssetType.VIEWSHEET_SNAPSHOT ],
   [ 16384, AssetType.VIEWSHEET_BOOKMARK ],
   [ 32768, AssetType.VARIABLE ],
   [ 65605, AssetType.DATA_SOURCE_FOLDER ],
   [ 65536, AssetType.BEAN ],
   [ 131072, AssetType.PARAMETER_SHEET ],
   [ 262144, AssetType.META_TEMPLATE ],
   [ 524288, AssetType.TABLE_STYLE ],
   [ 1048576, AssetType.SCRIPT ],
   [ 2031616, AssetType.REPORT_COMPONENT ],
   [ 65537, AssetType.BEAN_FOLDER ],
   [ 131073, AssetType.PARAMETER_SHEET_FOLDER ],
   [ 262145, AssetType.META_TEMPLATE_FOLDER ],
   [ 524289, AssetType.TABLE_STYLE_FOLDER ],
   [ 1048577, AssetType.SCRIPT_FOLDER ],
   [ 2097157, AssetType.DATA_MODEL ],
   [ 4194309, AssetType.PARTITION ],
   [ 8388608, AssetType.EXTENDED_MODEL ],
   [ 12582917, AssetType.EXTENDED_PARTITION ],
   [ 8388709, AssetType.EXTENDED_LOGIC_MODEL ],
   [ 16777220, AssetType.VPM ],
   [ 33554469, AssetType.QUERY_FOLDER ],
   [ 67108865, AssetType.LOCAL_QUERY_FOLDER ],
   [ 134217729, AssetType.REPORT_WORKSHEET_FOLDER ],
   [ 268435457, AssetType.EMBEDDED_PS_FOLDER ],
   [ 536870912, AssetType.REPLET ],
   [ 1073741825, AssetType.DOMAIN ],
   [ 56623205, AssetType.ERM ],
   [ 5, AssetType.SCHEDULE_TASK ],
   [ 6, AssetType.SCHEDULE_TASK_FOLDER ],
   [ 10, AssetType.LIBRARY_FOLDER ]
]);

export function getTypeForId(id: number): AssetType {
   let type: AssetType = null;

   if(typeIds.has(id)) {
      type = typeIds.get(id);
   }

   return type;
}

export function getTypeId(assetType: AssetType): number {
   let id: number;

   typeIds.forEach((v, k) => {
      if(v == assetType) {
         id = k;
      }
   });

   return id;
}