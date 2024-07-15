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
import { Format } from "../util/format";

export interface FormatInfoModel {
   type: string; // For JsonTypeInfo, sub-interface should override
   color: string;
   backgroundColor: string;
   font: FontInfo;
   align: AlignmentInfo;
   format: Format | string;
   formatSpec?: string;
   dateSpec?: string;
   borderColor?: string;
   borderTopStyle?: string;
   borderTopColor?: string;
   borderTopWidth?: string;
   borderLeftStyle?: string;
   borderLeftColor?: string;
   borderLeftWidth?: string;
   borderBottomStyle?: string;
   borderBottomColor?: string;
   borderBottomWidth?: string;
   borderRightStyle?: string;
   borderRightColor?: string;
   borderRightWidth?: string;
   borderTopCSS?: string;
   borderLeftCSS?: string;
   borderBottomCSS?: string;
   borderRightCSS?: string;
   halignmentEnabled?: boolean;
   valignmentEnabled?: boolean;
   formatEnabled?: boolean;
   decimalFmts?: string[];
   durationPadZeros?: boolean;
}

export class FontInfo {
   fontFamily: string;
   fontSize: string;
   fontStyle: string;
   fontUnderline: string;
   fontStrikethrough: string;
   fontWeight: string;
   smallCaps?: string;
   allCaps?: string;
   subScript?: string;
   supScript?: string;
   shadow?: string;
}

export class AlignmentInfo {
   valign: string;
   halign: string;
   static LEFT = "Left";
   static CENTER = "Center";
   static RIGHT = "Right";
   static TOP = "Top";
   static MIDDLE = "Middle";
   static BOTTOM = "Bottom";
}
