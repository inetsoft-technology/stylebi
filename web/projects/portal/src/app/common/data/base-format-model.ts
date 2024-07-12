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
export interface BaseFormatModel {
   top: number;
   left: number;
   width: number;
   height: number;
   zIndex: number;
   border: Border;
   wrapping: Wrapping;
   position: string;
}

export interface Border {
   bottom: string;
   top: string;
   left: string;
   right: string;
}

export interface Wrapping {
   whiteSpace: string;
   wordWrap: string;
   overflow: string;
}

export interface GradientColor {
   apply: boolean;
   direction: string;
   angle: number;
   colors: ColorStop[];
}

export interface ColorStop {
   color: string;
   offset: number;
}