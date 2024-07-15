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
import { VSAssemblyScriptPaneModel } from "../../widget/dialog/vsassembly-script-pane/vsassembly-script-pane-model";
import { RangeSliderGeneralPaneModel } from "./range-slider-general-pane-model";
import { RangeSliderDataPaneModel } from "./range-slider-data-pane-model";
import { RangeSliderAdvancedPaneModel } from "./range-slider-advanced-pane-model";

export interface RangeSliderPropertyDialogModel {
   rangeSliderGeneralPaneModel: RangeSliderGeneralPaneModel;
   rangeSliderDataPaneModel: RangeSliderDataPaneModel;
   rangeSliderAdvancedPaneModel: RangeSliderAdvancedPaneModel;
   vsAssemblyScriptPaneModel: VSAssemblyScriptPaneModel;
}
