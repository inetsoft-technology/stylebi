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
import { AssetEntry } from "../../data/asset-entry";
import { AddParameterDialogModel } from "./add-parameter-dialog-model";
import { ScheduleActionModel } from "./schedule-action-model";

export interface BatchActionModel extends ScheduleActionModel {
   taskName: string;
   queryEntry: AssetEntry;
   queryParameters: AddParameterDialogModel[];
   embeddedParameters: AddParameterDialogModel[][];
   queryEnabled: boolean;
   embeddedEnabled: boolean;
}