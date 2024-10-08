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
import { ImageGeneralPaneModel } from "./image-general-pane-model";
import { DataOutputPaneModel } from "./data-output-pane-model";
import { ImageAdvancedPaneModel } from "./image-advanced-pane-model";
import { ClickableScriptPaneModel } from "./clickable-script-pane-model";

export interface ImagePropertyDialogModel {
   imageGeneralPaneModel: ImageGeneralPaneModel;
   dataOutputPaneModel: DataOutputPaneModel;
   imageAdvancedPaneModel: ImageAdvancedPaneModel;
   clickableScriptPaneModel: ClickableScriptPaneModel;
}
