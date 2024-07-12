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
import { createAssetEntry } from "../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../shared/data/asset-type";

export interface AssetOption {
   value: string;
   label: string;
}

export const NONE_USER: AssetOption = { value: "", label: "_#(js:None)" };

export const ASSET_TYPES: AssetOption[] = [
   {value: "DATA_SOURCE", label: "_#(js:Datasource)"},
   {value: "DEVICE", label: "_#(js:Device)"},
   {value: "LOGIC_MODEL", label: "_#(js:Logical Model)"},
   {value: "PARTITION", label: "_#(js:Physical View)"},
   {value: "SCHEDULE_TASK", label: "_#(js:Scheduled Task)"},
   {value: "SCRIPT", label: "_#(js:Script)"},
   {value: "VIEWSHEET", label: "_#(js:Viewsheet)"},
   {value: "VPM", label: "_#(js:VPM)"},
   {value: "WORKSHEET",label:  "_#(js:Worksheet)"},
   {value: "TABLE_STYLE",label:  "_#(js:Table Style)"}
];

export const ASSET_REQUIRED_TYPES: AssetOption[] = [
   {value: "DATA_SOURCE", label: "_#(js:Datasource)"},
   {value: "LOGIC_MODEL", label: "_#(js:Logical Model)"},
   {value: "PARTITION", label: "_#(js:Physical View)"},
   {value: "SCHEDULE_TASK", label: "_#(js:Scheduled Task)"},
   {value: "VIEWSHEET", label: "_#(js:Viewsheet)"},
   {value: "VPM", label: "_#(js:VPM)"},
   {value: "WORKSHEET",label:  "_#(js:Worksheet)"},
];

export const USER_ASSET_TYPES = new Set<string>([
   "VIEWSHEET", "WORKSHEET"
]);

export const IMPOSSIBLE_DEPENDENCIES = new Map<string, Set<string>>([
   ["SCHEDULE_TASK", new Set<string>([
      "DEVICE"
   ])],
   ["SCRIPT", new Set<string>([
      "SCHEDULE_TASK", "TABLE_STYLE", "VIEWSHEET", "DATA_SOURCE",
      "LOGIC_MODEL", "PARTITION", "VPM", "DATACYCLE", "DEVICE"
   ])],
   ["VIEWSHEET", new Set<string>([
      "SCHEDULE_TASK", "DATACYCLE", "PARTITION", "VPM"
   ])],
   ["WORKSHEET", new Set<string>([
      "SCHEDULE_TASK", "TABLE_STYLE", "DATACYCLE", "PARTITION",
      "VPM", "DEVICE"
   ])],
   ["DATA_SOURCE", new Set<string>([
      "SCHEDULE_TASK", "TABLE_STYLE", "SCRIPT",
      "DATACYCLE", "XQUERY", "PARTITION", "LOGIC_MODEL", "VPM", "DEVICE", "DATA_SOURCE"
   ])],
   ["LOGIC_MODEL",new Set<string>([
      "SCHEDULE_TASK", "TABLE_STYLE", "SCRIPT",
      "DATACYCLE", "VPM", "DATA_SOURCE", "DEVICE", "LOGIC_MODEL"
   ])],
   ["PARTITION", new Set<string>([
      "SCHEDULE_TASK", "TABLE_STYLE", "SCRIPT", "VIEWSHEET", "WORKSHEET",
      "DATACYCLE", "LOGIC_MODEL", "VPM", "XQUERY", "DEVICE", "PARTITION"
   ])],
   ["VPM", new Set<string>([
      "SCHEDULE_TASK", "TABLE_STYLE", "SCRIPT", "VIEWSHEET", "WORKSHEET",
      "DATACYCLE", "VPM", "XQUERY", "LOGIC_MODEL", "DEVICE"
   ])],
   ["DEVICE", new Set<string>([
      "SCHEDULE_TASK", "TABLE_STYLE", "SCRIPT", "WORKSHEET", "VIEWSHEET", "DATA_SOURCE",
      "LOGIC_MODEL", "PARTITION", "VPM", "XQUERY", "DATACYCLE", "DEVICE"
   ])],
   ["TABLE_STYLE", new Set<string>([
      "TABLE_STYLE", "SCRIPT", "WORKSHEET", "VIEWSHEET", "DATA_SOURCE",
      "LOGIC_MODEL", "PARTITION", "VPM", "XQUERY", "DATACYCLE", "DEVICE"
   ])]
]);

export function getAssetLabel(id: string): string {
   if(!id) {
      return null;
   }

   const entry = createAssetEntry(id);
   return entry.type == AssetType.SCHEDULE_TASK ? entry.path.replace(/^\//, "") : entry.path;
}
