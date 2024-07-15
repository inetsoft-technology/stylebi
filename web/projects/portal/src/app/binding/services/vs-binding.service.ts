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
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";
import { ViewsheetEvent } from "../../common/viewsheet-client";
import { ApplyVSAssemblyInfoEvent } from "../event/apply-vs-assembly-info-event";
import { BindingService } from "./binding.service";
import { ModelService } from "../../widget/services/model.service";
import { UIContextService } from "../../common/services/ui-context.service";

export class VSBindingService extends BindingService {
   constructor(private modelService: ModelService, public http: HttpClient) {
      super(http, new UIContextService());
   }

   loadBindingModel(callback?: Function): void {
   }

   setBindingModel(model?: any, callback?: Function, params?: any): void {
      let binding: any = model ? model : this.getBindingModel();

      this.sendEvent("vs/binding/setbinding",
         new ApplyVSAssemblyInfoEvent(this.assemblyName, binding));
   }

   sendEvent(url: string, vevent: ViewsheetEvent): void {
      this._clientService.sendEvent("/events/" + url, vevent);
   }

   getFormulaFields(oname?: string, tableName?: string): Observable<any> {
      let params = this.getURLParams().set("assemblyName", null);

      if(tableName) {
         params = params.set("tableName", tableName);
      }

      return this.modelService.getModel("../api/composer/vsformula/fields", params);
   }

   getRequestParams(): string {
      return "?vsId=" + this.runtimeId + "&assemblyName=" + this.assemblyName;
   }
}
