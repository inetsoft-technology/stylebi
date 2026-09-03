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

/**
 * CurrentUserService — unit tests
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — getPortalCurrentUser(): request URL, caching, failure -> null, retry
 *   Group 2 [Risk 2] — getEmCurrentUser(): request URL, failure -> null
 *
 * KEY contracts:
 *   - A failed call emits null rather than erroring, so a bare subscribe() in a consumer cannot
 *     silently skip its next callback (Bug #74027: a blank Composer asset tree with no recovery).
 *   - Successful responses are cached and shared (Bug #74395: no duplicate API calls).
 *   - A failure must NOT be cached: shareReplay resets on error, so the next subscriber re-issues
 *     the request. The catchError lives in the getter for exactly this reason -- inside the shared
 *     pipe it would complete the stream, and shareReplay never resets a completed buffer.
 */
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { Subscription } from "rxjs";
import { CurrentUser } from "../../portal/src/app/portal/current-user";
import { CurrentUserService } from "./current-user.service";

const PORTAL_URL = "../api/portal/get-current-user";
const EM_URL = "../api/em/security/get-current-user";

const USER = {
   name: { name: "alice", orgID: "acme" },
   email: ["alice@example.com"],
} as CurrentUser;

describe("CurrentUserService", () => {
   let service: CurrentUserService;
   let httpMock: HttpTestingController;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule],
         providers: [CurrentUserService],
      });
      service = TestBed.inject(CurrentUserService);
      httpMock = TestBed.inject(HttpTestingController);
   });

   afterEach(() => httpMock.verify());

   // ---------------------------------------------------------------------------
   // Group 1 [Risk 3] — getPortalCurrentUser()
   // ---------------------------------------------------------------------------
   describe("getPortalCurrentUser()", () => {
      it("[Risk 2] does not issue a request until subscribed", () => {
         httpMock.expectNone(PORTAL_URL);
      });

      it("[Risk 2] requests the portal endpoint and emits the user", () => {
         let emitted: CurrentUser;
         service.getPortalCurrentUser().subscribe(user => (emitted = user));

         const req = httpMock.expectOne(PORTAL_URL);
         expect(req.request.method).toBe("GET");
         req.flush(USER);

         expect(emitted).toEqual(USER);
      });

      // 🔁 Regression-sensitive: Bug #74027/#74480. A consumer with a success-only subscribe()
      //    (e.g. AssetTreeComponent.ngOnInit) never runs its callback if this errors, leaving the
      //    asset tree permanently blank with no retry and no message.
      it("[Risk 3] emits null instead of erroring when the call fails", () => {
         const emitted: CurrentUser[] = [];
         let errored = false;
         let completed = false;

         service.getPortalCurrentUser().subscribe({
            next: user => emitted.push(user),
            error: () => (errored = true),
            complete: () => (completed = true),
         });

         httpMock.expectOne(PORTAL_URL).flush(null, { status: 500, statusText: "Server Error" });

         expect(errored).toBe(false);
         expect(emitted).toEqual([null]);
         expect(completed).toBe(true);
      });

      // 🔁 Regression-sensitive: Bug #74395. Caching must survive so concurrent subscribers share
      //    one request.
      it("[Risk 3] shares a single request between concurrent subscribers", () => {
         const first: CurrentUser[] = [];
         const second: CurrentUser[] = [];
         const sub = new Subscription();
         sub.add(service.getPortalCurrentUser().subscribe(u => first.push(u)));
         sub.add(service.getPortalCurrentUser().subscribe(u => second.push(u)));

         httpMock.expectOne(PORTAL_URL).flush(USER);

         expect(first).toEqual([USER]);
         expect(second).toEqual([USER]);
         sub.unsubscribe();
      });

      it("[Risk 3] replays the cached user to a later subscriber without a second request", () => {
         const sub = service.getPortalCurrentUser().subscribe();
         httpMock.expectOne(PORTAL_URL).flush(USER);

         let emitted: CurrentUser;
         service.getPortalCurrentUser().subscribe(user => (emitted = user));

         expect(emitted).toEqual(USER);
         httpMock.expectNone(PORTAL_URL);
         sub.unsubscribe();
      });

      // 🔁 Regression-sensitive: a failure must not be cached. If the catchError were moved inside
      //    the shareReplay pipe the stream would complete, the null would stick for the lifetime of
      //    the page, and no consumer would ever see the current user again after one transient 500.
      it("[Risk 3] re-issues the request for the next subscriber after a failure", () => {
         service.getPortalCurrentUser().subscribe();
         httpMock.expectOne(PORTAL_URL).flush(null, { status: 500, statusText: "Server Error" });

         let emitted: CurrentUser;
         service.getPortalCurrentUser().subscribe(user => (emitted = user));

         httpMock.expectOne(PORTAL_URL).flush(USER);
         expect(emitted).toEqual(USER);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 2 [Risk 2] — getEmCurrentUser()
   // ---------------------------------------------------------------------------
   describe("getEmCurrentUser()", () => {
      it("[Risk 2] requests the EM endpoint and emits the user", () => {
         let emitted: CurrentUser;
         service.getEmCurrentUser().subscribe(user => (emitted = user));

         const req = httpMock.expectOne(EM_URL);
         expect(req.request.method).toBe("GET");
         req.flush(USER);

         expect(emitted).toEqual(USER);
      });

      it("[Risk 3] emits null instead of erroring when the call fails", () => {
         const emitted: CurrentUser[] = [];
         let errored = false;

         service.getEmCurrentUser().subscribe({
            next: user => emitted.push(user),
            error: () => (errored = true),
         });

         httpMock.expectOne(EM_URL).flush(null, { status: 500, statusText: "Server Error" });

         expect(errored).toBe(false);
         expect(emitted).toEqual([null]);
      });
   });
});
