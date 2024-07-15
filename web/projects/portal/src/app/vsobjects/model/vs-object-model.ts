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
import { TableDataPath } from "../../common/data/table-data-path";
import { VSObjectType } from "../../common/data/vs-object-type";
import { VSAnnotationModel } from "./annotation/vs-annotation-model";
import { VSFormatModel } from "./vs-format-model";
import { ConditionList } from "../../common/util/condition-list";

import { PopLocation } from "../objects/data-tip/pop-component.service";

export interface VSObjectModel {
   objectFormat: VSFormatModel;
   objectType: VSObjectType;
   enabled: boolean;
   description: string;
   script: string;
   scriptEnabled: boolean;
   hasCondition: boolean
   visible: boolean;
   absoluteName: string;
   active?: boolean;
   container?: string;
   containerType?: VSObjectType;
   grouped?: boolean; // true if in group container
   dataTip: string;
   dataTipAlpha?: number;
   popComponent: string;
   popLocation: PopLocation;
   popAlpha?: number;
   inEmbeddedViewsheet: boolean;
   assemblyAnnotationModels: VSAnnotationModel[];
   dataAnnotationModels: VSAnnotationModel[];
   selectedAnnotations?: string[];
   selectedRegions?: TableDataPath[];
   actionNames: string[];
   advancedStatus?: string;
   genTime: number;
   adhocFilterEnabled: boolean;
   covered?: boolean; // true if this obj is covered by another component
   cubeType?: string;
   worksheetCube?: boolean;
   interactionDisabled?: boolean;
   dragObj?: VSObjectModel; // current obj or cloned obj (ctrl-drag)
   dragZIndex?: number;
   dropZone?: boolean; // true if another component is dragging over this component
   realWidth?: number; // obj width if shrinkToFit
   sourceType?: number;
   sheetMaxMode: boolean; // whether table/crosstab/freehand/chart is displayed in max mode.
   hasDynamic: boolean; // if this obj has Dynamic field, then can't open object-wizard-pane
   originalObjectFormat?: Partial<VSFormatModel>; // original format when editing the format model.
   drillTip?: string;
}
