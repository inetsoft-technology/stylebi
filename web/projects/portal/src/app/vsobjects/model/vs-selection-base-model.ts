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
import { VSFormatModel } from "./vs-format-model";
import { VSCompositeModel } from "./vs-composite-model";
import { SelectionValueModel } from "./selection-value-model";

export interface VSSelectionBaseModel extends VSCompositeModel {
   measureFormats: Map<string, VSFormatModel>;
   dropdown: boolean;
   hidden: boolean;
   listHeight: number;
   titleRatio: number;
   singleSelection: boolean;
   cellHeight: number;
   showText: boolean;
   textWidth: number;
   showBar: boolean;
   measure?: string;
   barWidth: number;
   submitOnChange: boolean;
   sortType: number;
   contextMenuCell?: SelectionValueModel;
   searchString?: string;
   searchDisplayed?: boolean;
   objectHeight?: number;
   maxMode?: boolean;
}
