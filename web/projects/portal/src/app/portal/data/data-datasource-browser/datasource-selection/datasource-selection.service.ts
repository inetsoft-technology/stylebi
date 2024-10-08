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
import { Observable } from "rxjs";
import { ModelService } from "../../../../widget/services/model.service";
import { DatasourceSelectionViewModel } from "./datasource-selection-view-model";
import { Tool } from "../../../../../../../shared/util/tool";
import { DatasourceType } from "../datasource-type";

@Injectable()
export class DatasourceSelectionService {
   constructor(private modelService: ModelService) {
   }

   getDatasourceSelectionViewModel(): Observable<DatasourceSelectionViewModel> {
      return this.modelService.getModel<DatasourceSelectionViewModel>(
         "../api/portal/data/datasource-selection-view");
   }

   isTabularDataSource(listingName: string): Observable<boolean> {
      return this.modelService.getModel<boolean>(
         "../api/portal/data/datasource-listing/is-tabular/"
         + Tool.encodeURIComponentExceptSlash(listingName));
   }

   getDataSourceType(listingName: string): Observable<DatasourceType> {
      return this.modelService.getModel<DatasourceType>(
         "../api/portal/data/datasource-listing/sourceType/"
         + Tool.encodeURIComponentExceptSlash(listingName));
   }
}
