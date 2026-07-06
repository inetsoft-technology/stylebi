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
 * DatasourcesDatasourceComponent — Pass 2: Risk tests (HTTP flows and dialogs).
 *
 * Covers:
 *   - ngOnInit: editing branch (GET datasource) — success and 403 error paths
 *   - ngOnInit: datasourceType branch (POST refreshView to load new datasource)
 *   - ngOnInit: listingName branch (GET listing datasource)
 *   - ok() → saveDataSource: PUT (editing) and POST (new) branches, error path
 *   - deleteAdditional: confirm dialog + deleteAdditionals
 *
 * Mocking strategy: ActivatedRoute.paramMap is a Subject; emitting a value triggers ngOnInit
 * HTTP flows. MSW intercepts the actual HTTP calls. NgbModal is mocked for dialogs.
 * DataNotificationsComponent is stubbed (all 4 methods).
 *
 * Note on paramMap emission: emit via `convertToParamMap()` from `@angular/router` to create
 * a proper ParamMap that satisfies `routeParams.get()`.
 */

import { By } from "@angular/platform-browser";
import { convertToParamMap } from "@angular/router";
import { waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { Subject } from "rxjs";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@test-mocks/server";
import { MessageDialog } from "../../../../widget/dialog/message-dialog/message-dialog.component";
import {
   DataNotificationsStub,
   lastRenderedFixture,
   makeDataSource,
   MODAL_MOCK,
   paramMap$,
   renderDatasource,
   resetMocks,
   ROUTER_MOCK,
} from "./datasources-datasource.component.test-helpers";

beforeEach(() => {
   resetMocks();
   MessageDialog.lastMessage = null;
   (MessageDialog as any).lastMessageTS = 0;
});
afterEach(() => {
   vi.restoreAllMocks();
   lastRenderedFixture?.destroy();
});

// ── Group 1: ngOnInit editing branch — GET datasource ─────────────────────

describe("DatasourcesDatasource — ngOnInit editing branch", () => {

   it("loads datasource via GET when no listingName and no datasourceType", async () => {
      const loadedDs = makeDataSource({ name: "ExistingDB", type: "JDBC" });

      server.use(
         http.get("*/api/portal/data/datasources/ExistingDB", () =>
            HttpResponse.json(loadedDs)
         )
      );

      const { comp } = await renderDatasource();

      paramMap$.next(convertToParamMap({
         datasourcePath: "ExistingDB",
         parentPath: "",
         datasourceType: null,
         listingName: null,
      }));

      await waitFor(() => expect(comp.datasource.name).toBe("ExistingDB"));
      expect(comp.editing).toBe(true);
   });

   it("navigates away on GET 403 error", async () => {
      server.use(
         http.get("*/api/portal/data/datasources/SecuredDS", () =>
            HttpResponse.json({ message: "Forbidden" }, { status: 403 })
         )
      );

      const { comp } = await renderDatasource();

      paramMap$.next(convertToParamMap({
         datasourcePath: "SecuredDS",
         parentPath: "",
         datasourceType: null,
         listingName: null,
      }));

      await waitFor(() => expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources"],
         expect.anything(),
      ));
   });
});

// ── Group 2: ngOnInit datasourceType branch — POST refreshView ────────────

describe("DatasourcesDatasource — ngOnInit datasourceType branch", () => {

   it("loads datasource via POST refreshView when datasourceType is provided", async () => {
      const newDs = makeDataSource({ name: "", type: "TABULAR" });

      server.use(
         http.post("*/api/portal/data/datasources/refreshView", () =>
            HttpResponse.json(newDs)
         )
      );

      const { comp } = await renderDatasource();

      paramMap$.next(convertToParamMap({
         datasourcePath: null,
         parentPath: "",
         datasourceType: "TABULAR",
         listingName: null,
      }));

      await waitFor(() => expect(comp.datasource.type).toBe("TABULAR"));
      expect(comp.editing).toBe(false);
   });
});

// ── Group 3: ngOnInit listingName branch — GET listing ────────────────────

describe("DatasourcesDatasource — ngOnInit listingName branch", () => {

   it("loads datasource via GET listing when listingName is provided", async () => {
      const listingDs = makeDataSource({ name: "MySQL", type: "LISTING" });

      server.use(
         http.get("*/api/portal/data/datasources/listing/mysql-listing", () =>
            HttpResponse.json(listingDs)
         )
      );

      const { comp } = await renderDatasource();

      paramMap$.next(convertToParamMap({
         datasourcePath: null,
         parentPath: "",
         datasourceType: null,
         listingName: "mysql-listing",
      }));

      await waitFor(() => expect(comp.datasource.name).toBe("MySQL"));
      expect(comp.editing).toBe(false);
   });
});

// ── Group 4: ok() → saveDataSource (editing — PUT) ────────────────────────

describe("DatasourcesDatasource — ok() saves datasource via PUT when editing", () => {

   it("calls GET checkDuplicate then PUT datasource on ok() in edit mode", async () => {
      const ds = makeDataSource({ name: "DB1", type: "JDBC" });

      server.use(
         http.get("*/api/portal/data/datasources/checkDuplicate/DB1", () =>
            HttpResponse.json(false)
         ),
         http.put("*/api/portal/data/datasources/DB1", () =>
            HttpResponse.json({})
         )
      );

      const { comp } = await renderDatasource();
      comp.datasource = ds;
      comp.originalDatasource = makeDataSource({ name: "DB1" });
      comp.datasourcePath = "DB1";
      comp.editing = true;
      comp.datasourceValid = true;

      comp.ok();

      await waitFor(() => expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources"],
         expect.anything(),
      ));
   });

   it("shows error notification when PUT fails", async () => {
      const ds = makeDataSource({ name: "DB1", type: "JDBC" });

      server.use(
         http.get("*/api/portal/data/datasources/checkDuplicate/DB1", () =>
            HttpResponse.json(false)
         ),
         http.put("*/api/portal/data/datasources/DB1", () =>
            HttpResponse.json({ message: "save error" }, { status: 500 })
         )
      );

      const { comp, fixture } = await renderDatasource();
      comp.datasource = ds;
      comp.originalDatasource = makeDataSource({ name: "DB1" });
      comp.datasourcePath = "DB1";
      comp.editing = true;

      const dataNotif = fixture.debugElement.query(By.directive(DataNotificationsStub))?.componentInstance;
      expect(dataNotif).toBeDefined();

      comp.ok();

      await waitFor(() => expect(dataNotif.notifications.danger).toHaveBeenCalled());
   });

   it("skips GET checkDuplicate when name equals originalName and editing=true", async () => {
      const ds = makeDataSource({ name: "DB1", type: "JDBC" });

      let duplicateCheckCalled = false;
      server.use(
         http.get("*/api/portal/data/datasources/checkDuplicate/*", () => {
            duplicateCheckCalled = true;
            return HttpResponse.json(false);
         }),
         http.put("*/api/portal/data/datasources/DB1", () =>
            HttpResponse.json({})
         )
      );

      const { comp } = await renderDatasource();
      comp.datasource = ds;
      comp.originalDatasource = makeDataSource({ name: "DB1" });
      comp.datasourcePath = "DB1"; // originalName = "DB1"
      comp.editing = true;

      comp.ok();

      await waitFor(() => expect(ROUTER_MOCK.navigate).toHaveBeenCalled());
      expect(duplicateCheckCalled).toBe(false);
   });
});

// ── Group 5: ok() → saveDataSource (new — POST) ───────────────────────────

describe("DatasourcesDatasource — ok() saves datasource via POST when new", () => {

   it("calls GET checkDuplicate then POST datasource on ok() for new datasource", async () => {
      const ds = makeDataSource({ name: "NewDB", type: "TABULAR" });

      server.use(
         http.get("*/api/portal/data/datasources/checkDuplicate/NewDB", () =>
            HttpResponse.json(false)
         ),
         http.post("*/api/portal/data/datasources", () =>
            HttpResponse.json({})
         )
      );

      const { comp } = await renderDatasource();
      comp.datasource = ds;
      comp.originalDatasource = makeDataSource({ name: "" });
      comp.datasourcePath = null;
      comp.editing = false;

      comp.ok();

      await waitFor(() => expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources"],
         expect.anything(),
      ));
   });
});

// ── Group 6: deleteAdditional — confirm dialog + HTTP ─────────────────────
// ComponentTool.showConfirmDialog subscribes to modal.componentInstance["onCommit"].
// Emit the desired symbol on onCommit to drive dialog resolution.

describe("DatasourcesDatasource — deleteAdditional", () => {

   it("removes additional connection after user confirms delete dialog", async () => {
      const extra = makeDataSource({ name: "Extra1" });

      const onCommit = new Subject<string>();
      const onCancel = new Subject<void>();
      let resolveFn: (value: string) => void;
      const result = new Promise<string>(resolve => { resolveFn = resolve; });
      const closeSpy = vi.fn((value: string) => resolveFn(value));

      MODAL_MOCK.open.mockImplementationOnce(() => ({
         result,
         componentInstance: { onCommit, onCancel },
         close: closeSpy,
         dismiss: vi.fn(),
      }));

      const { comp } = await renderDatasource();
      comp.datasource = makeDataSource({
         name: "MainDS",
         tabularView: { views: [] } as any,
         additionalConnections: [extra],
      });
      comp.selectedAdditionalIndex = [0];

      comp.deleteAdditional();
      // showConfirmDialog subscribed to onCommit — emit "ok" to confirm deletion
      onCommit.next("ok");

      await waitFor(() =>
         expect(comp.datasource.additionalConnections).toHaveLength(0)
      );
   });

   it("does not remove additional connection when user cancels delete dialog", async () => {
      const extra = makeDataSource({ name: "Extra1" });

      const onCommit = new Subject<string>();
      const onCancel = new Subject<void>();
      let resolveFn: (value: string) => void;
      const result = new Promise<string>(resolve => { resolveFn = resolve; });
      const closeSpy = vi.fn((value: string) => resolveFn(value));

      MODAL_MOCK.open.mockImplementationOnce(() => ({
         result,
         componentInstance: { onCommit, onCancel },
         close: closeSpy,
         dismiss: vi.fn(),
      }));

      const { comp } = await renderDatasource();
      comp.datasource = makeDataSource({
         name: "MainDS",
         tabularView: { views: [] } as any,
         additionalConnections: [extra],
      });
      comp.selectedAdditionalIndex = [0];

      comp.deleteAdditional();
      // Emit "cancel" — deleteAdditionals should NOT be called
      onCommit.next("cancel");

      // Give any async work a chance to settle
      await waitFor(() => expect(closeSpy).toHaveBeenCalled());
      expect(comp.datasource.additionalConnections).toHaveLength(1);
   });
});
