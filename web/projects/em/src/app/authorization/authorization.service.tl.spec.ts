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
 * AuthorizationService — unit tests
 *
 * Risk-first coverage (5 test blocks):
 *   1 — getPermissions: path normalization (it.each table)
 *   2 — getPermissions: cache miss → hit, normalized-path key sharing
 *   3 — getPermissions: useCache=false bypass + cache update
 *   4 — getPermissions: HTTP error propagation + cache not polluted
 *   5 — getPermissions: concurrent calls expose no in-flight deduplication
 *
 * Design gaps documented:
 *   - Only the first leading slash is stripped: "//foo" → "/foo", not "foo".
 *   - No public cache-invalidation API; useCache=false is the only refresh path.
 *   - Concurrent requests for the same uncached path each hit the server
 *     independently (no shareReplay / in-flight deduplication).
 */
import { TestBed } from "@angular/core/testing";
import { HttpClientModule } from "@angular/common/http";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { firstValueFrom, forkJoin } from "rxjs";

import { server } from "../../../../../mocks/server";
import { AuthorizationService } from "./authorization.service";
import { ComponentPermissions } from "./component-permissions";

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

const MOCK_PERMISSIONS: ComponentPermissions = {
   permissions: { reports: true, admin: false },
   labels: { reports: "Reports", admin: "Admin" },
   multiTenancyHiddenComponents: {},
};

const FRESH_PERMISSIONS: ComponentPermissions = {
   permissions: { reports: false, admin: true },
   labels: { reports: "Reports", admin: "Admin" },
   multiTenancyHiddenComponents: {},
};

// ---------------------------------------------------------------------------
// TestBed lifecycle — fresh service (empty cache) per test
// ---------------------------------------------------------------------------

let service: AuthorizationService;

beforeEach(() => {
   TestBed.configureTestingModule({ imports: [HttpClientModule] });
   service = TestBed.inject(AuthorizationService);
});

afterEach(() => TestBed.resetTestingModule());

// ---------------------------------------------------------------------------
// 1. Path normalization
// ---------------------------------------------------------------------------

// Verifies the `path` query parameter actually sent to the server after
// normalization. Each row: [input, expected query param value].
//
// Boundary row "//foo" → "/foo": only the first slash is stripped
// (path.substring(1) runs once). Callers must not rely on double-slash inputs
// being fully normalized.
it.each([
   [null,              ""],               // falsy guard: !path → ""
   [undefined,        ""],               // falsy guard
   ["",               ""],               // falsy guard
   ["/settings",      "settings"],       // leading slash stripped
   ["/",              ""],               // stripping "/"  leaves ""
   ["settings/a",     "settings/a"],     // no leading slash — unchanged
   ["//foo",          "/foo"],           // boundary: only ONE slash stripped
])(
   "should normalize path %p and send %p as the query param",
   async (input: any, expectedParam: string) => {
      let capturedPath = "(not called)";
      server.use(
         http.get("*/api/em/authz", ({ request }) => {
            capturedPath = new URL(request.url).searchParams.get("path") ?? "";
            return MswHttpResponse.json(MOCK_PERMISSIONS);
         })
      );

      await firstValueFrom(service.getPermissions(input));

      expect(capturedPath).toBe(expectedParam);
   }
);

// ---------------------------------------------------------------------------
// 2. Cache: miss → hit, and normalized path as cache key
// ---------------------------------------------------------------------------

// Three checkpoints in one sequential flow:
//   a) cache miss → HTTP call made, correct value returned
//   b) "/settings" and "settings" share the same normalized cache key ("settings")
//   c) cache hit → no additional HTTP request
it("should fetch on cache miss, then serve the same entry for both '/settings' and 'settings' without a second HTTP call", async () => {
   let requestCount = 0;
   server.use(
      http.get("*/api/em/authz", () => {
         requestCount++;
         return MswHttpResponse.json(MOCK_PERMISSIONS);
      })
   );

   // (a) cache miss: "/settings" → normalized to "settings", HTTP call #1
   const first = await firstValueFrom(service.getPermissions("/settings"));
   expect(first).toEqual(MOCK_PERMISSIONS);
   expect(requestCount).toBe(1);

   // (b+c) "settings" shares the same cache key — no HTTP call
   const second = await firstValueFrom(service.getPermissions("settings"));
   expect(second).toEqual(MOCK_PERMISSIONS);
   expect(requestCount).toBe(1);
});

// ---------------------------------------------------------------------------
// 3. useCache=false: bypasses cache and updates it with the fresh response
// ---------------------------------------------------------------------------

// Three checkpoints:
//   a) cache populated with MOCK_PERMISSIONS (call 1)
//   b) useCache=false ignores cache, hits server again (call 2), returns FRESH
//   c) cache now holds FRESH — next useCache=true read returns FRESH (no call 3)
it("should bypass the cache when useCache=false and update it so the next read returns the fresh value", async () => {
   let requestCount = 0;
   server.use(
      http.get("*/api/em/authz", () => {
         requestCount++;
         return MswHttpResponse.json(
            requestCount === 1 ? MOCK_PERMISSIONS : FRESH_PERMISSIONS
         );
      })
   );

   // (a) populate cache
   await firstValueFrom(service.getPermissions("settings"));
   expect(requestCount).toBe(1);

   // (b) force refresh — must bypass cache and write FRESH into it
   const refreshed = await firstValueFrom(service.getPermissions("settings", false));
   expect(refreshed).toEqual(FRESH_PERMISSIONS);
   expect(requestCount).toBe(2);

   // (c) next cache read returns FRESH, no third request
   const cached = await firstValueFrom(service.getPermissions("settings"));
   expect(cached).toEqual(FRESH_PERMISSIONS);
   expect(requestCount).toBe(2);
});

// ---------------------------------------------------------------------------
// 4. Error handling: propagated to caller, cache not polluted
// ---------------------------------------------------------------------------

// Two checkpoints:
//   a) HTTP error is propagated (tap is skipped on error — cache stays empty)
//   b) subsequent call retries the server rather than returning undefined
it("should propagate HTTP errors and leave the cache empty so the next call retries", async () => {
   let callCount = 0;
   server.use(
      http.get("*/api/em/authz", () => {
         callCount++;
         return callCount === 1
            ? new MswHttpResponse(null, { status: 500 })
            : MswHttpResponse.json(MOCK_PERMISSIONS);
      })
   );

   // (a) first call fails and the error reaches the caller
   await expect(firstValueFrom(service.getPermissions("settings"))).rejects.toThrow();

   // (b) cache must be empty — second call hits the server and succeeds
   const result = await firstValueFrom(service.getPermissions("settings"));
   expect(callCount).toBe(2);
   expect(result).toEqual(MOCK_PERMISSIONS);
});

// ---------------------------------------------------------------------------
// 5. Concurrent calls — no in-flight deduplication (design gap)
// ---------------------------------------------------------------------------

// forkJoin subscribes to both observables simultaneously. Because neither has
// completed (and thus populated the cache) when the other is subscribed, both
// see an empty cache and each issues its own HTTP request.
// A future fix would cache the in-flight Observable (shareReplay or a
// Map<path, Observable>) to deduplicate concurrent requests.
it("should make two HTTP requests when two concurrent calls target the same uncached path", async () => {
   let requestCount = 0;
   server.use(
      http.get("*/api/em/authz", () => {
         requestCount++;
         return MswHttpResponse.json(MOCK_PERMISSIONS);
      })
   );

   await firstValueFrom(forkJoin([
      service.getPermissions("concurrent-path"),
      service.getPermissions("concurrent-path"),
   ]));

   expect(requestCount).toBe(2);
});
