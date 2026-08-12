/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
// The model lives in projects/shared, not under portal — five levels up from this directory.
// (The plan guessed a portal-local path that does not exist.)
import {
   DataSourceDefinitionModel
} from "../../../../../shared/util/model/data-source-definition-model";

/** One entry in the "pick a data source type" list. */
export interface DatasourceListing {
   name: string;
   iconUrl?: string;
}

// Relative to the portal's base href, matching how the portal's own data pages address these.
const DATASOURCES_URI = "../api/portal/data/datasources";
const SELECTION_VIEW_URI = "../api/portal/data/datasource-selection-view";
const BROWSER_URI = "../api/data/datasources/browser";

/**
 * Every HTTP call the datasource-registration element makes, in one place.
 *
 * Deliberately thin: it exists so the wire contract can be tested without rendering a component,
 * and so the component holds lifecycle only.
 */
@Injectable()
export class EmbedDatasourceRegistrationService {
   constructor(private http: HttpClient) {
   }

   listings(): Observable<DatasourceListing[]> {
      return this.http.get<{ dataSourceListings?: DatasourceListing[] }>(SELECTION_VIEW_URI)
         .pipe(map((r) => r?.dataSourceListings ?? []));
   }

   /**
    * Seed a NEW datasource of the picked type.
    *
    * Must be listing/{name}: posting a bare {name, type} to refreshView returns tabularView:null,
    * because refreshView refreshes an existing view rather than creating one. Seeding from it
    * renders an empty form.
    */
   seedFromListing(listingName: string): Observable<DataSourceDefinitionModel> {
      return this.http.get<DataSourceDefinitionModel>(
         `${DATASOURCES_URI}/listing/${encodeURIComponent(listingName)}`);
   }

   /** Dependent-field refresh: the server re-derives the view from the current values. */
   refreshView(ds: DataSourceDefinitionModel): Observable<DataSourceDefinitionModel> {
      return this.http.post<DataSourceDefinitionModel>(`${DATASOURCES_URI}/refreshView`, ds);
   }

   /** Create. Editing an existing source is out of scope — this element registers new ones. */
   save(ds: DataSourceDefinitionModel): Observable<unknown> {
      return this.http.post(DATASOURCES_URI, ds);
   }

   existingNames(): Observable<string[]> {
      return this.http.get<{ files?: Array<{ name: string }> }>(BROWSER_URI)
         .pipe(map((r) => (r?.files ?? []).map((f) => f.name)));
   }
}
