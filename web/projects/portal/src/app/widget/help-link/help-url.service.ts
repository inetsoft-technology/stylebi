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
import { Injectable } from "@angular/core";
import { Observable, of as observableOf } from "rxjs";
import { tap } from "rxjs/operators";
import { ModelService } from "../services/model.service";

@Injectable({
   providedIn: "root"
})
export class HelpUrlService {
   private _helpUrl: string;
   private _scriptHelpUrl: string;

   constructor(private modelService: ModelService) {
   }

   getHelpUrl(): Observable<string> {
      if(!!this._helpUrl) {
         return observableOf(this._helpUrl);
      }

      return this.modelService.getModel<string>("../api/composer/viewsheet/help-url").pipe(
         tap((data) => this._helpUrl = data)
      );
   }

   getScriptHelpUrl(): Observable<string> {
      if(!!this._scriptHelpUrl) {
         return observableOf(this._scriptHelpUrl);
      }

      return this.modelService.getModel<string>("../api/composer/viewsheet/script-help-url").pipe(
         tap((data) => this._scriptHelpUrl = data)
      );
   }
}