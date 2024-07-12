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
import { BaseFormatModel, GradientColor } from "../../common/data/base-format-model";
import { Insets } from "../../common/data/insets";

export interface VSFormatModel extends BaseFormatModel {
   alpha: number;
   foreground: string;
   background: string;
   font: string;
   decoration: string;
   hAlign: string;
   vAlign: string;
   alignItems: string;
   justifyContent: string;
   bringToFrontEnabled: boolean;
   sendToBackEnabled: boolean;
   gradientColor?: GradientColor;
   lineHeight?: number;
   roundCorner?: number;
   padding?: Insets;
}