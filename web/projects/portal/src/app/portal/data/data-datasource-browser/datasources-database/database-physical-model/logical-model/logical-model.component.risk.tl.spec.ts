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
 * LogicalModelComponent — Pass 2 (risk: async HTTP flows)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — refreshModel(): GET with correct params; sets logicalModel/
 *                        originalModel/loading; handles HTTP error gracefully
 *   Group 2 [Risk 3] — refreshModel() race: rapid route-param changes → both GETs
 *                        in-flight; first response must not overwrite a later response
 *                        (fixed Issue #75584: refreshModel() now cancels the prior
 *                        in-flight subscription before starting a new one)
 *   Group 3 [Risk 2] — getSettings(): GET to /settings with ds param; sets
 *                        logicalModelService.settings
 *   Group 4 [Risk 1] — post-destroy HTTP callbacks should not update component state
 *                        (fixed Issue #75584: refreshModel()'s subscription is now
 *                        stored and unsubscribed in ngOnDestroy())
 *
 * Out of scope this pass:
 *   All interaction/lifecycle paths covered in Pass 1 (interaction.tl.spec.ts).
 *
 * See also: logical-model.component.test-helpers.ts for stubs, route factories, renderComp.
 */

import { provideHttpClient } from "@angular/common/http";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render, waitFor } from "@testing-library/angular";
import { ActivatedRoute, convertToParamMap, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject } from "rxjs";
import { http, HttpResponse as MswHttpResponse } from "msw";

import { server } from "@test-mocks/server";
import { LogicalModelComponent } from "./logical-model.component";
import { LogicalModelPropertyPane } from "./logical-model-property-pane.component";
import { NotificationsComponent } from "../../../../../../widget/notifications/notifications.component";
import { DataModelNameChangeService } from "../../../../services/data-model-name-change.service";
import { FolderChangeService } from "../../../../services/folder-change.service";
import {
   LogicalModelPropertyPaneStub, NotificationsStub,
   makeEditRoute, makeCreateRoute, renderComp,
} from "./logical-model.component.test-helpers";

// ---------------------------------------------------------------------------
// Global lifecycle
// ---------------------------------------------------------------------------

afterEach(() => {
   vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — refreshModel(): GET request mechanics [Risk 3]
// ---------------------------------------------------------------------------

describe("LogicalModelComponent — refreshModel()", () => {
   // 🔁 Regression-sensitive: if params don't include physicalModel/name, the server
   // returns 404 and the model is never initialized.

   it("should set loading=true before the GET completes", async () => {
      server.use(
         http.get("*/api/data/logicalmodel/models", async () => {
            // Capture loading state mid-flight (before response resolves)
            return MswHttpResponse.json({
               name: "LM1", partition: "physModel", entities: [],
               description: "", connection: null, parent: null, folder: "",
            });
         })
      );

      // loading is set synchronously before the GET returns
      const routeParamMap = new Subject<any>();
      const { fixture } = await render(LogicalModelComponent, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            provideHttpClient(),
            { provide: NgbModal, useValue: {} },
            { provide: DataModelNameChangeService, useValue: { nameChangeObservable: new Subject().asObservable() } },
            { provide: FolderChangeService, useValue: { emitFolderChange: vi.fn() } },
            { provide: ActivatedRoute, useValue: { paramMap: routeParamMap.asObservable() } },
            { provide: Router, useValue: { navigate: vi.fn() } },
         ],
         importOverrides: [
            { replace: LogicalModelPropertyPane, with: LogicalModelPropertyPaneStub },
            { replace: NotificationsComponent, with: NotificationsStub },
         ],
      });
      const comp = fixture.componentInstance as LogicalModelComponent;

      // Emit edit-mode params (no "create") to trigger refreshModel()
      routeParamMap.next(convertToParamMap({
         databasePath: "testDB", physicalModelName: "physModel", logicalModelName: "LM1",
      }));
      // loading is set synchronously before the subscription callback
      expect(comp.loading).toBe(true);
   });

   it("should set logicalModel/originalModel and clear loading on success", async () => {
      const responseModel = {
         name: "LM1", partition: "physModel",
         entities: [{ name: "E1", attributes: [], baseElement: false, errorMessage: null }],
         description: "loaded", connection: null, parent: null, folder: "",
      };
      server.use(
         http.get("*/api/data/logicalmodel/models", () => MswHttpResponse.json(responseModel))
      );

      const { comp } = await renderComp({ route: makeEditRoute() });

      await waitFor(() => expect(comp.loading).toBe(false));
      expect(comp.logicalModel.name).toBe("LM1");
      expect(comp.logicalModel.entities).toHaveLength(1);
      expect(comp.originalModel.name).toBe("LM1");
      expect(comp.isModified).toBe(false);
      expect(comp.initialized).toBe(true);
   });

   it("should send database, physicalModel and name as query params", async () => {
      let capturedUrl: string | undefined;
      server.use(
         http.get("*/api/data/logicalmodel/models", ({ request }) => {
            capturedUrl = request.url;
            return MswHttpResponse.json({
               name: "TargetLM", partition: "myPhys", entities: [],
               description: "", connection: null, parent: null, folder: "",
            });
         })
      );

      await renderComp({
         route: makeEditRoute({ databasePath: "testDB", physicalModelName: "myPhys", logicalModelName: "TargetLM" }),
      });

      await waitFor(() => expect(capturedUrl).toBeDefined());
      const url = new URL(capturedUrl!);
      expect(url.searchParams.get("database")).toBe("testDB");
      expect(url.searchParams.get("physicalModel")).toBe("myPhys");
      expect(url.searchParams.get("name")).toBe("TargetLM");
   });

   it("should include parent query param when editing an extended model", async () => {
      let capturedUrl: string | undefined;
      server.use(
         http.get("*/api/data/logicalmodel/models", ({ request }) => {
            capturedUrl = request.url;
            return MswHttpResponse.json({
               name: "ExtLM", partition: "myPhys", entities: [],
               description: "", connection: "c", parent: "baseLM", folder: "",
            });
         })
      );

      await renderComp({
         route: makeEditRoute({ physicalModelName: "myPhys", logicalModelName: "ExtLM", parent: "baseLM" }),
      });

      await waitFor(() => expect(capturedUrl).toBeDefined());
      const url = new URL(capturedUrl!);
      expect(url.searchParams.get("parent")).toBe("baseLM");
   });

   // Fixed Issue #75584: the error handler in refreshModel() now resets
   // `this.loading = false` instead of being a no-op.
   it("loading flag must be cleared after an HTTP error in refreshModel", async () => {
      server.use(
         http.get("*/api/data/logicalmodel/models", () =>
            MswHttpResponse.json({ error: "Not found" }, { status: 404 })
         )
      );

      const { comp } = await renderComp({ route: makeEditRoute() });
      await waitFor(() => expect(comp.loading).toBe(false), { timeout: 2000 });
   });
});

// ---------------------------------------------------------------------------
// Group 2 — refreshModel() race condition [Risk 3]
// ---------------------------------------------------------------------------

describe("LogicalModelComponent — refreshModel() race condition", () => {
   // Fixed Issue #75584: refreshModel() now unsubscribes the previous in-flight
   // request before starting a new one, so a stale first-GET response can no
   // longer overwrite the model set by a newer second-GET response.

   it("stale first-GET response must not overwrite newer second-GET model", async () => {
      let resolveFirst!: (r: MswHttpResponse<any>) => void;
      let resolveSecond!: (r: MswHttpResponse<any>) => void;

      const firstModel = {
         name: "FirstLM", partition: "physModel", entities: [], description: "",
         connection: null, parent: null, folder: "",
      };
      const secondModel = {
         name: "SecondLM", partition: "physModel", entities: [], description: "",
         connection: null, parent: null, folder: "",
      };

      let callCount = 0;
      server.use(
         http.get("*/api/data/logicalmodel/models", () => {
            callCount++;
            if(callCount === 1) {
               return new Promise<MswHttpResponse<any>>((res) => { resolveFirst = res as any; });
            }
            else {
               return new Promise<MswHttpResponse<any>>((res) => { resolveSecond = res as any; });
            }
         })
      );

      // Use a Subject so we can emit multiple param sets
      const routeParamMap = new Subject<any>();
      const { fixture } = await render(LogicalModelComponent, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            provideHttpClient(),
            { provide: NgbModal, useValue: {} },
            { provide: DataModelNameChangeService, useValue: { nameChangeObservable: new Subject().asObservable() } },
            { provide: FolderChangeService, useValue: { emitFolderChange: vi.fn() } },
            { provide: ActivatedRoute, useValue: { paramMap: routeParamMap.asObservable() } },
            { provide: Router, useValue: { navigate: vi.fn() } },
         ],
         importOverrides: [
            { replace: LogicalModelPropertyPane, with: LogicalModelPropertyPaneStub },
            { replace: NotificationsComponent, with: NotificationsStub },
         ],
      });
      const comp = fixture.componentInstance as LogicalModelComponent;

      // First params emission → first GET starts
      routeParamMap.next(convertToParamMap({
         databasePath: "testDB", physicalModelName: "physModel", logicalModelName: "FirstLM",
      }));

      await waitFor(() => expect(callCount).toBe(1));

      // Second params emission before first GET resolves → second GET starts
      routeParamMap.next(convertToParamMap({
         databasePath: "testDB", physicalModelName: "physModel", logicalModelName: "SecondLM",
      }));

      await waitFor(() => expect(callCount).toBe(2));

      // Second GET resolves first — correct (most recent params)
      resolveSecond!(MswHttpResponse.json(secondModel) as any);
      await waitFor(() => expect(comp.logicalModel.name).toBe("SecondLM"));

      // First (stale) GET resolves after the second — must be a no-op now that
      // refreshModel() cancels the prior subscription before starting a new one.
      resolveFirst!(MswHttpResponse.json(firstModel) as any);
      await new Promise<void>(r => setTimeout(r, 0));

      expect(comp.logicalModel.name).toBe("SecondLM");
   });
});

// ---------------------------------------------------------------------------
// Group 3 — getSettings() [Risk 2]
// ---------------------------------------------------------------------------

describe("LogicalModelComponent — getSettings()", () => {
   // 🔁 Regression-sensitive: if getSettings() doesn't pass the `ds` query param, the
   // server returns settings for the wrong database (or default), causing wrong date-support flags.

   // renderComp() fixes the ActivatedRoute params at construction time; these tests need to
   // emit multiple param sets after render, so a Subject-based route is required.
   async function renderWithSubjectRoute() {
      const routeParamMap = new Subject<any>();
      const { fixture } = await render(LogicalModelComponent, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [
            provideHttpClient(),
            { provide: NgbModal, useValue: {} },
            { provide: DataModelNameChangeService, useValue: { nameChangeObservable: new Subject().asObservable() } },
            { provide: FolderChangeService, useValue: { emitFolderChange: vi.fn() } },
            { provide: ActivatedRoute, useValue: { paramMap: routeParamMap.asObservable() } },
            { provide: Router, useValue: { navigate: vi.fn() } },
         ],
         importOverrides: [
            { replace: LogicalModelPropertyPane, with: LogicalModelPropertyPaneStub },
            { replace: NotificationsComponent, with: NotificationsStub },
         ],
      });
      return { fixture, routeParamMap, comp: fixture.componentInstance as LogicalModelComponent };
   }

   it("should GET /settings with ds=databaseName and store result in lmService.settings", async () => {
      const settingsResponse = { fullDateSupport: false };
      let capturedUrl: string | undefined;
      server.use(
         http.get("*/api/data/logicalmodel/settings", ({ request }) => {
            capturedUrl = request.url;
            return MswHttpResponse.json(settingsResponse);
         })
      );

      const { comp } = await renderComp({ route: makeCreateRoute({ databasePath: "myDB" }) });

      await waitFor(() => expect(capturedUrl).toBeDefined());
      const url = new URL(capturedUrl!);
      expect(url.searchParams.get("ds")).toBe("myDB");

      await waitFor(() => expect(comp.lmService.settings).toBeDefined());
      expect(comp.lmService.settings!.fullDateSupport).toBe(false);
   });

   it("should update settings when databasePath changes", async () => {
      let getCount = 0;
      server.use(
         http.get("*/api/data/logicalmodel/settings", () => {
            getCount++;
            return MswHttpResponse.json({ fullDateSupport: getCount > 1 });
         })
      );

      const { routeParamMap, comp } = await renderWithSubjectRoute();

      // First database
      routeParamMap.next(convertToParamMap({
         databasePath: "db1", physicalModelName: "p", logicalModelName: "l", create: "true",
      }));
      await waitFor(() => expect(getCount).toBeGreaterThanOrEqual(1));

      // Change to a different database path → should re-trigger getSettings()
      routeParamMap.next(convertToParamMap({
         databasePath: "db2", physicalModelName: "p", logicalModelName: "l", create: "true",
      }));
      await waitFor(() => expect(getCount).toBeGreaterThanOrEqual(2));
      await waitFor(() => expect(comp.lmService.settings?.fullDateSupport).toBe(true));
   });

   it("should NOT re-fetch settings when only physicalModelName changes", async () => {
      let getCount = 0;
      server.use(
         http.get("*/api/data/logicalmodel/settings", () => {
            getCount++;
            return MswHttpResponse.json({ fullDateSupport: true });
         })
      );

      const { routeParamMap } = await renderWithSubjectRoute();

      // First emission
      routeParamMap.next(convertToParamMap({
         databasePath: "sameDB", physicalModelName: "phys1", logicalModelName: "lm", create: "true",
      }));
      await waitFor(() => expect(getCount).toBe(1));

      // Same databasePath, different physicalModelName → no second settings fetch
      routeParamMap.next(convertToParamMap({
         databasePath: "sameDB", physicalModelName: "phys2", logicalModelName: "lm", create: "true",
      }));

      // Brief wait to see if a second call fires
      await new Promise<void>(r => setTimeout(r, 50));
      expect(getCount).toBe(1);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — post-destroy HTTP callback leak [Risk 1]
// ---------------------------------------------------------------------------

describe("LogicalModelComponent — post-destroy HTTP callback (memory leak)", () => {
   // Fixed Issue #75584: refreshModel()'s subscription is now stored in
   // this.modelSubscription and unsubscribed in ngOnDestroy(), so a response
   // arriving after destroy is dropped instead of mutating component state.
   it("post-destroy refreshModel response must not update logicalModel", async () => {
      let resolveGet!: (r: MswHttpResponse<any>) => void;
      server.use(
         http.get("*/api/data/logicalmodel/models", () =>
            new Promise<MswHttpResponse<any>>((res) => { resolveGet = res as any; })
         )
      );

      const { comp, fixture } = await renderComp({ route: makeEditRoute() });
      // loading=true, GET in-flight
      expect(comp.loading).toBe(true);

      fixture.destroy();

      // Resolve the GET after destroy — should be a no-op (but currently updates state)
      resolveGet!(MswHttpResponse.json({
         name: "PostDestroyLM", partition: "physModel", entities: [],
         description: "", connection: null, parent: null, folder: "",
      }) as any);
      await new Promise<void>(r => setTimeout(r, 0));

      // logicalModel should NOT have been updated after destroy
      expect(comp.logicalModel.name).not.toBe("PostDestroyLM");
   });
});
