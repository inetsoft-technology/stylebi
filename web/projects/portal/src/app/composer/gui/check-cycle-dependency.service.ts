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
import { Observable } from "rxjs";
import { ModelService } from "../../widget/services/model.service";

const CHECK_DATATIP_DEPENDENCY_URI = "../api/composer/vs/check-datatip-dependency";

export class CheckCycleDependencyService {

   constructor(private modelService: ModelService,
               private vsId: string,
               private assemblyName: string) {
   }

   public checkCycleDependency(tipView: string): Observable<any> {
      const params = new HttpParams()
         .set("runtimeId", this.vsId)
         .set("assemblyName", this.assemblyName)
         .set("tipView", tipView);

      return this.modelService.getModel(CHECK_DATATIP_DEPENDENCY_URI, params);
   }
}