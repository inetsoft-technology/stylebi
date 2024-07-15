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
import { ViewsheetCommand } from "../../common/viewsheet-client/index";
import { SortInfo } from "../objects/table/sort-info";
import { BaseTableCellModel } from "../model/base-table-cell-model";
import { TableStylePaneModel } from "../../widget/table-style/table-style-pane-model";

/**
 * Command used to instruct the client to add or update an assembly object.
 */
export interface LoadPreviewTableCommand extends ViewsheetCommand {
   tableData: BaseTableCellModel[][];
   worksheetId: string;
   sortInfo: SortInfo;
   colWidths?: number[];
   styleModel: TableStylePaneModel;
   prototypeCache: BaseTableCellModel[];
}
