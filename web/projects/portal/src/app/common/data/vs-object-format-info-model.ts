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
import { FormatInfoModel } from "./format-info-model";

export interface VSObjectFormatInfoModel extends FormatInfoModel {
   type: "inetsoft.web.composer.model.vs.VSObjectFormatInfoModel";
   shape?: boolean;
   image?: boolean;
   chart?: boolean;
   colorType?: string;
   backgroundColorType?: string;
   backgroundAlpha?: number;
   roundCorner?: number;
   roundTopCornersOnly?: boolean;
   wrapText?: boolean;
   cssID?: string;
   cssClass?: string;
   cssIDs?: string[];
   cssClasses?: string[];
   cssType?: string;
   presenter?: string;
   presenterLabel?: string;
   borderDisabled?: boolean;
   dynamicColorDisabled?: boolean;
   alignEnabled?: boolean;
   presenterHasDescriptors?: boolean;
   valueFillColor?: string;
}
