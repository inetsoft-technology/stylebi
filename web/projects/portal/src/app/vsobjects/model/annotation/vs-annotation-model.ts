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
import { VSObjectModel } from "../vs-object-model";
import { VSLineModel } from "../vs-line-model";
import { VSRectangleModel } from "../vs-rectangle-model";
import { HtmlContentModel } from "../html-content-model";
import { Point } from "../../../common/data/point";

export interface VSAnnotationModel extends VSObjectModel {
   annotationRectangleModel: VSRectangleModel;
   annotationLineModel: VSLineModel;
   contentModel: HtmlContentModel;

   // row and col in table used to identify which cell contains the annotation
   row: number;
   col: number;

   // true if annotation status is hidden on the viewsheet
   hidden: boolean;

   // {viewsheet, assembly, data}
   annotationType: number;

   // data annotation may need runtime position information
   cellOffset?: Point;
}
