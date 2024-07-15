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
import { ViewsheetCommand } from "../../common/viewsheet-client/viewsheet-command";
import { AssetEntry } from "../../../../../shared/data/asset-entry";
import { Dimension } from "../../common/data/dimension";

/**
 * Command that provides information used to initialize the grid. Since the grid is no
 * longer used, any other information can probably be moved to another command.
 */
export interface InitGridCommand extends ViewsheetCommand {
   viewsheetId: string;
   embeddedId: string;
   entry: AssetEntry;
   initing: boolean;
   viewSize: Dimension;
   editable: boolean;
   lockOwner: string;
   lastModified: Date;
   scope: number;
   runtimeFontScale: number;
   toolbarVisible: boolean;
   wallboard?: boolean;
   singleClick?: boolean;
   hasSharedFilters?: boolean;
}
