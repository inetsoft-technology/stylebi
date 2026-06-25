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
 * DatasourcesDatasourceEditorComponent — Pass 2: Risk tests (HTTP flows).
 *
 * Covers: onViewChanged → refreshView (POST refreshView), refreshMetadata (GET refresh-metadata),
 * authorize (POST oauth-params → oauthService.authorize → POST oauth-tokens).
 *
 * Mocking strategy: HTTP is intercepted via MSW (server.use()). DebounceService is mocked to
 * call the callback immediately. OAuthAuthorizationService is mocked per-test via OAUTH_MOCK.
 * NgbModal is mocked for any ComponentTool dialogs triggered on error paths.
 * See test-helpers.ts for mock definitions.
 */

import { http, HttpResponse } from "msw";
import { waitFor } from "@testing-library/angular";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@test-mocks/server";
import { MessageDialog } from "../../../../../widget/dialog/message-dialog/message-dialog.component";
import {
   OAUTH_MOCK,
   makeDataSource,
   makeTabularButton,
   makeTabularView,
   renderEditor,
   resetMocks,
} from "./datasources-datasource-editor.component.test-helpers";

beforeEach(() => {
   resetMocks();
   MessageDialog.lastMessage = null;
   (MessageDialog as any).lastMessageTS = 0;
});
afterEach(() => vi.restoreAllMocks());

// ── Group 1: onViewChanged → refreshView ──────────────────────────────────

describe("DatasourcesDatasourceEditor — onViewChanged triggers refreshView", () => {

   it("calls POST refreshView and updates datasource when dependsOn matches and no refresh button", async () => {
      const depView = makeTabularView({
         type: "EDITOR",
         value: "serverUrl",
         editor: { dependsOn: ["serverUrl"] } as any,
         views: [],
      });
      const tabView = makeTabularView({ views: [depView] });
      const initial = makeDataSource({ name: "DS1", tabularView: tabView });
      // Return a tabularView so clearButtonLoading()'s default arg does not crash on null.views
      const updatedTabView = makeTabularView({ views: [] });
      const updated = makeDataSource({ name: "DS1Updated", tabularView: updatedTabView });

      server.use(
         http.post("*/api/portal/data/datasources/refreshView", () =>
            HttpResponse.json({ ...updated, sequenceNumber: 1 })
         )
      );

      const { comp, fixture } = await renderEditor({ datasource: initial });
      // DEBOUNCE_MOCK calls fn() immediately
      comp.onViewChanged([{ ...depView, value: "serverUrl" }]);

      await waitFor(() => expect(comp.datasource.name).toBe("DS1Updated"));
      await fixture.whenStable();
   });

   it("does not call refreshView when refreshButtonExists is true", async () => {
      const refreshBtnView = makeTabularView({
         type: "BUTTON",
         button: makeTabularButton({ type: "REFRESH" }),
         views: [],
      });
      const depView = makeTabularView({
         type: "EDITOR",
         value: "serverUrl",
         editor: { dependsOn: ["serverUrl"] } as any,
         views: [],
      });
      const tabView = makeTabularView({ views: [refreshBtnView, depView] });
      const ds = makeDataSource({ tabularView: tabView });

      const postSpy = vi.fn();
      server.use(http.post("*/api/portal/data/datasources/refreshView", () => {
         postSpy();
         return HttpResponse.json(ds);
      }));

      const { comp } = await renderEditor({ datasource: ds });
      comp.onViewChanged([depView]);

      // Allow any microtasks to settle
      await new Promise(r => setTimeout(r, 50));
      expect(postSpy).not.toHaveBeenCalled();
   });

   it("emits onWarning when refreshView POST returns an error", async () => {
      const depView = makeTabularView({
         type: "EDITOR",
         value: "serverUrl",
         editor: { dependsOn: ["serverUrl"] } as any,
         views: [],
      });
      const tabView = makeTabularView({ views: [depView] });
      const ds = makeDataSource({ tabularView: tabView });

      server.use(
         http.post("*/api/portal/data/datasources/refreshView", () =>
            HttpResponse.json({ message: "error" }, { status: 500 })
         )
      );

      const { comp } = await renderEditor({ datasource: ds });
      const warnings: string[] = [];
      comp.onWarning.subscribe(w => warnings.push(w));

      comp.onViewChanged([depView]);

      await waitFor(() => expect(warnings.length).toBeGreaterThan(0));
      expect(warnings[0]).toContain("refreshViewError");
   });
});

// ── Group 2: refreshMetadata ──────────────────────────────────────────────

describe("DatasourcesDatasourceEditor — refreshMetadata", () => {

   it("emits onSuccess with refreshSuccess message when GET returns true", async () => {
      server.use(
         http.get("*/api/portal/data/datasource/refresh-metadata", () =>
            HttpResponse.json(true)
         )
      );

      const { comp } = await renderEditor({ datasource: makeDataSource({ name: "GA4DS", parentPath: "" }) });
      const successes: string[] = [];
      comp.onSuccess.subscribe(s => successes.push(s));

      comp.refreshMetadata();

      await waitFor(() => expect(successes.length).toBeGreaterThan(0));
      expect(successes[0]).toContain("refreshSuccess");
   });

   it("emits onSuccess with refreshFailed message when GET returns false", async () => {
      server.use(
         http.get("*/api/portal/data/datasource/refresh-metadata", () =>
            HttpResponse.json(false)
         )
      );

      const { comp } = await renderEditor({ datasource: makeDataSource({ name: "GA4DS", parentPath: "" }) });
      const successes: string[] = [];
      comp.onSuccess.subscribe(s => successes.push(s));

      comp.refreshMetadata();

      await waitFor(() => expect(successes.length).toBeGreaterThan(0));
      expect(successes[0]).toContain("refreshFailed");
   });

   it("builds path as 'parentPath/name' when parentPath is set", async () => {
      let capturedUrl: URL | null = null;
      server.use(
         http.get("*/api/portal/data/datasource/refresh-metadata", ({ request }) => {
            capturedUrl = new URL(request.url);
            return HttpResponse.json(true);
         })
      );

      const ds = makeDataSource({ name: "MyDB", parentPath: "folder1" });
      const { comp } = await renderEditor({ datasource: ds });

      comp.refreshMetadata();

      await waitFor(() => expect(capturedUrl).not.toBeNull());
      expect(capturedUrl.searchParams.get("dataSource")).toBe("folder1/MyDB");
   });

   it("builds path as just 'name' when parentPath is empty", async () => {
      let capturedUrl: URL | null = null;
      server.use(
         http.get("*/api/portal/data/datasource/refresh-metadata", ({ request }) => {
            capturedUrl = new URL(request.url);
            return HttpResponse.json(true);
         })
      );

      const ds = makeDataSource({ name: "MyDB", parentPath: "" });
      const { comp } = await renderEditor({ datasource: ds });

      comp.refreshMetadata();

      await waitFor(() => expect(capturedUrl).not.toBeNull());
      expect(capturedUrl.searchParams.get("dataSource")).toBe("MyDB");
   });
});

// ── Group 3: authorize — OAuth flow ───────────────────────────────────────

describe("DatasourcesDatasourceEditor — authorize", () => {

   it("emits onWarning with error message when POST oauth-params returns an error field", async () => {
      server.use(
         http.post("*/api/portal/data/datasources/oauth-params", () =>
            HttpResponse.json({ error: "OAuth params failed" })
         )
      );

      const { comp } = await renderEditor();
      const warnings: string[] = [];
      comp.onWarning.subscribe(w => warnings.push(w));

      const btn = makeTabularButton({ type: "OAUTH", oauthServiceName: "google" });
      comp.authorize(btn);

      await waitFor(() => expect(warnings.length).toBeGreaterThan(0));
      expect(warnings[0]).toBe("OAuth params failed");
   });

   it("emits onWarning with generic message when POST oauth-params returns HTTP error", async () => {
      server.use(
         http.post("*/api/portal/data/datasources/oauth-params", () =>
            HttpResponse.json({ message: "server error" }, { status: 500 })
         )
      );

      const { comp } = await renderEditor();
      const warnings: string[] = [];
      comp.onWarning.subscribe(w => warnings.push(w));

      comp.authorize(makeTabularButton({ type: "OAUTH" }));

      await waitFor(() => expect(warnings.length).toBeGreaterThan(0));
      expect(warnings[0]).toContain("authorizationError");
   });

   it("updates datasource and emits datasourceChanged on successful OAuth flow", async () => {
      const returnedDs = makeDataSource({ name: "OAuthDS", type: "TABULAR" });

      server.use(
         http.post("*/api/portal/data/datasources/oauth-params", () =>
            HttpResponse.json({ authUri: "https://auth.example.com" })
         ),
         http.post("*/api/portal/data/datasources/oauth-tokens", () =>
            HttpResponse.json(returnedDs)
         )
      );

      OAUTH_MOCK.authorize.mockReturnValue({
         // Observable that immediately emits tokens
         pipe: () => ({ subscribe: (next: any) => next({ accessToken: "tok" }) }),
         // Provide a minimal observable-like object
      });

      // Use a proper observable from the OAUTH_MOCK
      const { of } = await import("rxjs");
      OAUTH_MOCK.authorize.mockReturnValue(of({ accessToken: "tok" }));

      const { comp } = await renderEditor();
      const changed: any[] = [];
      comp.datasourceChanged.subscribe(v => changed.push(v));

      comp.authorize(makeTabularButton({ type: "OAUTH" }));

      await waitFor(() => expect(changed.length).toBeGreaterThan(0));
      expect(changed[0].name).toBe("OAuthDS");
   });
});
