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
import { HttpParams } from "@angular/common/http";
import { Tool } from "../../../../../shared/util/tool";

export class TrapInfo {
   constructor(targetURI: string, objectName: string, runtimeId: string, payload: any,
               params?: HttpParams)
   {
      this.controllerURI = targetURI + objectName + "/" + Tool.encodeURIPath(runtimeId);
      this.payload = payload;

      if(params) {
         this.params = params;
      }
   }

   controllerURI: string;
   payload: any;
   params: HttpParams;
}
