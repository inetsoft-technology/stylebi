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
import { BindingService } from "../binding.service";
import { TableEditorService } from "./table-editor.service";
import { SortColumnEvent } from "../../../vsobjects/event/table/sort-column-event";
import { ViewsheetClientService } from "../../../common/viewsheet-client/viewsheet-client.service";
import { ApplyVSAssemblyInfoEvent } from "../../event/apply-vs-assembly-info-event";

export class VSTableEditorService extends TableEditorService {
   constructor(protected bindingService: BindingService,
               protected clientService: ViewsheetClientService)
   {
      super();
   }

   setBindingModel(): void {
      this.clientService.sendEvent("/events/vs/binding/setbinding",
         new ApplyVSAssemblyInfoEvent(this.bindingService.assemblyName,
         this.bindingService.getBindingModel()));
   }

   sortColumn(col: number, multi: boolean = false): void {
      let event = SortColumnEvent.builder(this.bindingService.assemblyName)
         .row(0)
         .col(col)
         .colName("")
         .multi(multi)
         .detail(true)
         .build();
      this.clientService.sendEvent("/events/table/sort-column", event);
   }
}
