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
import { DataRef } from "../../common/data/data-ref";
import { InputParameterDialogModel } from "./input-parameter-dialog-model";

export interface HyperlinkDialogModel {
   linkType: number;
   webLink: string;
   assetLinkPath: string;
   assetLinkId: string;
   bookmark: string;
   targetFrame: string;
   self: boolean;
   tooltip: string;
   disableParameterPrompt: boolean;
   sendViewsheetParameters: boolean;
   sendSelectionsAsParameters: boolean;
   paramList: InputParameterDialogModel[];
   row: number;
   col: number;
   fields: DataRef[];
   colName?: string;
   table: boolean;
   isAxis?: boolean;
   applyToRow?: boolean;
   showRow?: boolean;
   grayedOutFields: DataRef[];
   // The write-coordination revision this model was read at. Sent back only on a final commit
   // (OK), never on Apply -- the dialog's own held copy is never refreshed after a commit, so
   // echoing it back on Apply would make the dialog conflict with its own prior Apply. See
   // 2026-08-17-write-coordination-design.md.
   revision?: number;
}
