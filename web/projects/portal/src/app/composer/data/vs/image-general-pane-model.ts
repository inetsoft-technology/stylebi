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
import { OutputGeneralPaneModel } from "./output-general-pane-model";
import { StaticImagePaneModel } from "./static-image-pane-model";
import { SizePositionPaneModel } from "../../../vsobjects/model/size-position-pane-model";
import { TipPaneModel } from "../../../vsobjects/model/tip-pane-model";

export interface ImageGeneralPaneModel {
   outputGeneralPaneModel: OutputGeneralPaneModel;
   staticImagePaneModel: StaticImagePaneModel;
   sizePositionPaneModel: SizePositionPaneModel;
   tipPaneModel: TipPaneModel;
}
