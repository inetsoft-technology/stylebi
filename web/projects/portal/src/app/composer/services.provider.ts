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
import { HttpClient } from "@angular/common/http";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { BindingService } from "../binding/services/binding.service";
import { ChartEditorService } from "../binding/services/chart/chart-editor.service";
import { VSChartEditorService } from "../binding/services/chart/vs-chart-editor.service";
import { TableEditorService } from "../binding/services/table/table-editor.service";
import { VSCalcTableEditorService } from "../binding/services/table/vs-calc-table-editor.service";
import { VSTableEditorService } from "../binding/services/table/vs-table-editor.service";
import { VSBindingService } from "../binding/services/vs-binding.service";
import { BindingTreeService } from "../binding/widget/binding-tree/binding-tree.service";
import { VSBindingTreeService } from "../binding/widget/binding-tree/vs-binding-tree.service";
import { DndService } from "../common/dnd/dnd.service";
import { VSDndService } from "../common/dnd/vs-dnd.service";
import { ModelService } from "../widget/services/model.service";
import { UIContextService } from "../common/services/ui-context.service";
import { VirtualScrollService } from "../widget/tree/virtual-scroll.service";
import { ViewsheetClientService } from "../common/viewsheet-client";

export const SERVICE_PROVIDERS: any[] = [
   {
      provide: BindingService,
      useClass: VSBindingService,
      deps: [ModelService, HttpClient, UIContextService]
   },
   {
      provide: ChartEditorService,
      useClass: VSChartEditorService,
      deps: [BindingService, ModelService, UIContextService]
   },
   {
      provide: TableEditorService,
      useClass: VSTableEditorService,
      deps: [BindingService, ViewsheetClientService]
   },
   {
      provide: BindingTreeService,
      useClass: VSBindingTreeService,
      deps: [BindingService]
   },
   {
      provide: VSCalcTableEditorService,
      useClass: VSCalcTableEditorService,
      deps: [BindingService, NgbModal, ModelService]
   },
   {
      provide: DndService,
      useClass: VSDndService,
      deps: [HttpClient, NgbModal]
   }
];
