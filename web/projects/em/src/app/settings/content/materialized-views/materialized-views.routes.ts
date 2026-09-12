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
import { Routes } from "@angular/router";
import { MAT_FORM_FIELD_DEFAULT_OPTIONS } from "@angular/material/form-field";
import { ContentMaterializedViewsViewComponent } from "../content-materialized-views-view/content-materialized-views-view.component";

export const MATERIALIZED_VIEWS_ROUTES: Routes = [
   {
      path: "",
      component: ContentMaterializedViewsViewComponent,
      providers: [
         { provide: MAT_FORM_FIELD_DEFAULT_OPTIONS, useValue: { float: "always" } }
      ]
   }
];
