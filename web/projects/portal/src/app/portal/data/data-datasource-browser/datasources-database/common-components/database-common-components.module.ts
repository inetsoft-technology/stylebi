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
import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { FormatModule } from "../../../../../format/format.module";
import {
   AttributeFormattingPane
} from "../database-physical-model/logical-model/attribute-editor/format-dialog/attribute-formatting-pane.component";
import {
   JoinNodeGraphComponent
} from "./join-node-graph/join-node-graph.component";
import {
   LoadingIndicatorPaneComponent
} from "./loading-indicator-pane/loading-indicator-pane.component";
import {
   EditJoinTableColumnComponent
} from "./edit-join-table-column.component";
import {
   EditJoinTableComponent
} from "./edit-join-table.component";

@NgModule({
   imports: [
      CommonModule,
      FormatModule,
      FormsModule,
      ReactiveFormsModule
   ],
   declarations: [
      JoinNodeGraphComponent,
      EditJoinTableComponent,
      EditJoinTableColumnComponent,
      LoadingIndicatorPaneComponent,
      AttributeFormattingPane
   ],
   exports: [
      JoinNodeGraphComponent,
      EditJoinTableComponent,
      EditJoinTableColumnComponent,
      LoadingIndicatorPaneComponent,
      AttributeFormattingPane
   ]
})
export class DatabaseCommonComponentsModule {
}
