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
import { TestBed } from "@angular/core/testing";
import {
   ActivatedRouteSnapshot,
   provideRouter,
   Router,
   RouterStateSnapshot,
   UrlTree
} from "@angular/router";
import { firstValueFrom, Observable, of } from "rxjs";
import { AuthorizationService } from "../../authorization/authorization.service";
import { ComponentPermissions } from "../../authorization/component-permissions";
import { canActivateDebugPage } from "./summary.routes";

function permissions(debug: boolean): ComponentPermissions {
   return {
      // the sibling widget-level children of monitoring/summary are always permitted here so
      // that only the debug flag varies between cases
      permissions: {debug, cpuUsage: true, heapMemory: true},
      labels: {debug: "Debug"},
      multiTenancyHiddenComponents: {}
   };
}

function runGuard(debug: boolean): Promise<boolean | UrlTree> {
   const authzMock = {
      getPermissions: (path: string) => {
         expect(path).toBe("monitoring/summary");
         return of(permissions(debug));
      }
   };

   TestBed.configureTestingModule({
      providers: [
         provideRouter([]),
         {provide: AuthorizationService, useValue: authzMock}
      ]
   });

   const result = TestBed.runInInjectionContext(() => canActivateDebugPage(
      {} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));

   return firstValueFrom(result as Observable<boolean | UrlTree>);
}

describe("canActivateDebugPage", () => {
   afterEach(() => TestBed.resetTestingModule());

   it("should allow the debug page when monitoring/summary/debug is permitted", async () => {
      expect(await runGuard(true)).toBe(true);
   });

   // Bug: the debug page was reachable by any user with monitoring/summary access, ignoring the
   // monitoring/summary/debug permission shown on the EM Actions tab.
   it("should redirect to the summary page when monitoring/summary/debug is denied", async () => {
      const result = await runGuard(false);

      expect(result).not.toBe(true);
      expect(TestBed.inject(Router).serializeUrl(result as UrlTree))
         .toBe("/monitoring/summary");
   });
});
