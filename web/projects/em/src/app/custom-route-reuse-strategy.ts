/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import { ActivatedRouteSnapshot, DetachedRouteHandle, RouteReuseStrategy } from "@angular/router";
import { Injectable } from "@angular/core";

/**
 * Custom RouteReuseStrategy to control route reuse behavior.
 *
 * This strategy disables route reuse temporarily when `forceNoReuse` is set to true,
 * forcing Angular to destroy and recreate the component on the next navigation.
 *
 * By default, it follows Angular's standard behavior of reusing routes
 * only if the route configuration (`routeConfig`) is the same.
 */
@Injectable()
export class CustomRouteReuseStrategy implements RouteReuseStrategy {
   public static forceNoReuse = false;

   shouldReuseRoute(future: ActivatedRouteSnapshot, curr: ActivatedRouteSnapshot): boolean {
      if(CustomRouteReuseStrategy.forceNoReuse) {
         CustomRouteReuseStrategy.forceNoReuse = false;
         return false;
      }

      // Default behavior: reuse only when the routeConfig is the same
      return future.routeConfig === curr.routeConfig;
   }

   shouldDetach(): boolean {
      return false;
   }

   store(): void {
   }

   shouldAttach(): boolean {
      return false;
   }

   retrieve(): DetachedRouteHandle | null {
      return null;
   }
}
