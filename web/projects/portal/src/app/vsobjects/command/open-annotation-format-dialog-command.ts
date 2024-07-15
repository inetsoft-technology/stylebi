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
import { Annotation } from "../../common/data/annotation";
import { ViewsheetCommand } from "../../common/viewsheet-client/viewsheet-command";
import { AnnotationFormatDialogModel } from "../dialog/annotation/annotation-format-dialog-model";

export interface OpenAnnotationFormatDialogCommand extends ViewsheetCommand {
   formatDialogModel: AnnotationFormatDialogModel;

   // The name of the annotation that is being formatted
   assemblyName: string;

   // true to show the line formatting options (non-assembly annotations)
   annotationType: Annotation.Type;

   // if this base assembly of the annotation is chart.
   forChart: boolean;
}