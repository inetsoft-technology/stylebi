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
