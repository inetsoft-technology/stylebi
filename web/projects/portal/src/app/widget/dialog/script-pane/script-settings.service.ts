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
import { Injectable } from "@angular/core";
import { Observable, of as observableOf } from "rxjs";
import { tap } from "rxjs/operators";
import { ModelService } from "../../services/model.service";

@Injectable({
   providedIn: "root"
})
export class ScriptSettingsService {
   private _cursorTop: boolean | undefined;

   constructor(private modelService: ModelService) {
   }

   isCursorTop(): Observable<boolean> {
      if(this._cursorTop !== undefined) {
         return observableOf(this._cursorTop);
      }

      return this.modelService.getModel<boolean>(
         "../api/composer/viewsheet/script-cursor-top").pipe(
         tap((val) => this._cursorTop = val)
      );
   }
}
