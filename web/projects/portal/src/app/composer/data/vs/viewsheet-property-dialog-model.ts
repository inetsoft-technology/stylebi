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
import { ViewsheetOptionsPaneModel } from "./viewsheet-options-pane-model";
import { FiltersPaneModel } from "./filters-pane-model";
import { ScreensPaneModel } from "./screens-pane-model";
import { LocalizationPaneModel } from "./localization-pane-model";
import { ViewsheetScriptPaneModel } from "./viewsheet-script-pane-model";

export interface ViewsheetPropertyDialogModel {
   vsOptionsPane: ViewsheetOptionsPaneModel;
   filtersPane: FiltersPaneModel;
   screensPane: ScreensPaneModel;
   localizationPane?: LocalizationPaneModel;
   vsScriptPane: ViewsheetScriptPaneModel;
   id?: string;
   onDemandMVEnabled: boolean;
   width: number;
   height: number;
   preview: boolean;
}
