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
import { ActivatedRouteSnapshot, Resolve, RouterStateSnapshot } from "@angular/router";
import { RepositoryEntry } from "../../../../../shared/data/repository-entry";
import { RepositoryTreeService } from "../../widget/repository-tree/repository-tree.service";

@Injectable()
export class RouteEntryResolver implements Resolve<RepositoryEntry> {
   constructor(private repositoryTreeService: RepositoryTreeService) {
   }

   resolve(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): RepositoryEntry {
      return this.repositoryTreeService.getRouteParamsEntry(
         route.url.map((s) => s.path), route.queryParamMap);
   }
}
