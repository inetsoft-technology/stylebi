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
import { ErrorHandlerService } from "../../../common/util/error/error-handler.service";
import { ExportAssetsService } from "./import-export/export-assets.service";
import { ContentRepositoryPageComponent } from "./content-repository-page/content-repository-page.component";
import { contentRepositorySaveGuard } from "./content-repository-page/content-repository-save.guard";

export const REPOSITORY_ROUTES: Routes = [
   {
      path: "",
      component: ContentRepositoryPageComponent,
      children: [],
      canDeactivate: [contentRepositorySaveGuard],
      providers: [ErrorHandlerService, ExportAssetsService]
   }
];
