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
import { GeneralPropPaneModel } from "../../../vsobjects/model/general-prop-pane-model";
import { TitlePropPaneModel } from "../../../vsobjects/model/title-prop-pane-model";
import { SizePositionPaneModel } from "../../../vsobjects/model/size-position-pane-model";

export interface SelectionGeneralPaneModel {
   showType: number;
   listHeight: number;
   sortType: number;
   submitOnChange: boolean;
   singleSelection: boolean;
   selectFirstItem?: boolean;
   singleSelectionLevels?: string[];
   suppressBlank: boolean;
   showNonContainerProps: boolean;
   inSelectionContainer: boolean;
   generalPropPaneModel: GeneralPropPaneModel;
   titlePropPaneModel: TitlePropPaneModel;
   sizePositionPaneModel: SizePositionPaneModel;
}
