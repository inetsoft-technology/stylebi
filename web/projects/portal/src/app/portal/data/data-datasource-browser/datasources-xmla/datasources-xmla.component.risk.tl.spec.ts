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
 * DatasourcesXmlaComponent — Pass 2: Risk tests (HTTP flows).
 *
 * Covers:
 *   - ngOnInit: editing=true branch (GET edit/:datasourcePath + GET metadataTree)
 *   - ngOnInit: editing=false branch (GET new?parentPath=...)
 *   - loadCatalogs: POST catalogs — success and error paths
 *   - loadMetadata: POST metadata/refresh — success and error paths
 *   - testDatabase: POST test — connected=true (success) and connected=false (error dialog)
 *   - ok(): editing=true (POST update) and editing=false (POST new)
 *
 * Mocking strategy: ActivatedRoute.paramMap is a Subject; emitting triggers ngOnInit HTTP.
 * MSW intercepts all HTTP calls. NgbModal is mocked for dialogs.
 * DataNotificationsComponent is stubbed with all 4 notification methods.
 */

import { By } from "@angular/platform-browser";
import { convertToParamMap } from "@angular/router";
import { waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@test-mocks/server";
import { MessageDialog } from "../../../../widget/dialog/message-dialog/message-dialog.component";
import {
   DataNotificationsStub,
   lastRenderedFixture,
   makeCube,
   makeDomain,
   makeTreeNode,
   makeXmlaModel,
   MODAL_MOCK,
   paramMap$,
   renderXmla,
   resetMocks,
   ROUTER_MOCK,
} from "./datasources-xmla.component.test-helpers";

beforeEach(() => {
   resetMocks();
   MessageDialog.lastMessage = null;
   (MessageDialog as any).lastMessageTS = 0;
});
afterEach(() => {
   vi.restoreAllMocks();
   lastRenderedFixture?.destroy();
});

// ── Group 1: ngOnInit editing branch (GET edit + GET metadataTree) ─────────

describe("DatasourcesXmla — ngOnInit editing branch", () => {

   it("loads model via GET edit when datasourcePath is provided", async () => {
      const loadedModel = makeXmlaModel({ name: "ExistingXmla", url: "http://olap.example.com" });

      server.use(
         http.get("*/api/portal/data/datasource/xmla/edit/ExistingXmla", () =>
            HttpResponse.json(loadedModel)
         ),
         http.get("*/api/portal/data/datasource/xmla/metadataTree/*", () =>
            HttpResponse.json(makeTreeNode())
         )
      );

      const { comp } = await renderXmla();

      paramMap$.next(convertToParamMap({
         datasourcePath: "ExistingXmla",
         parentPath: "",
      }));

      await waitFor(() => expect(comp.form.get("name").value).toBe("ExistingXmla"));
      expect(comp.editing).toBe(true);
   });

   it("loads cubeTree from metadataTree GET response", async () => {
      const treeRoot = makeTreeNode({ label: "Root", children: [] });

      server.use(
         http.get("*/api/portal/data/datasource/xmla/edit/*", () =>
            HttpResponse.json(makeXmlaModel({ name: "DS" }))
         ),
         http.get("*/api/portal/data/datasource/xmla/metadataTree/*", () =>
            HttpResponse.json(treeRoot)
         )
      );

      const { comp } = await renderXmla();

      paramMap$.next(convertToParamMap({ datasourcePath: "DS", parentPath: "" }));

      await waitFor(() => expect(comp.cubeTree?.label).toBe("Root"));
   });
});

// ── Group 2: ngOnInit new branch (GET new?parentPath=...) ─────────────────

describe("DatasourcesXmla — ngOnInit new branch", () => {

   it("loads model via GET new when datasourcePath is empty", async () => {
      const newModel = makeXmlaModel({ name: "", url: "" });

      server.use(
         http.get("*/api/portal/data/datasource/xmla/new", () =>
            HttpResponse.json(newModel)
         )
      );

      const { comp } = await renderXmla();

      paramMap$.next(convertToParamMap({ datasourcePath: null, parentPath: "folder1" }));

      await waitFor(() => expect(comp.editing).toBe(false));
   });
});

// ── Group 3: loadCatalogs ─────────────────────────────────────────────────

describe("DatasourcesXmla — loadCatalogs", () => {

   it("sets catalogs on success", async () => {
      server.use(
         http.post("*/api/portal/data/datasource/xmla/catalogs", () =>
            HttpResponse.json(["AdventureWorks", "Northwind"])
         )
      );

      const { comp } = await renderXmla({ model: makeXmlaModel() });

      comp.loadCatalogs();

      await waitFor(() => expect(comp.catalogs).toEqual(["AdventureWorks", "Northwind"]));
      expect(comp.loadingCatalogs).toBe(false);
      expect(comp.loadingCatalogsFailed).toBe(false);
   });

   it("sets loadingCatalogsFailed=true on POST error", async () => {
      server.use(
         http.post("*/api/portal/data/datasource/xmla/catalogs", () =>
            HttpResponse.json({ message: "Connection refused" }, { status: 500 })
         )
      );

      const { comp } = await renderXmla({ model: makeXmlaModel() });

      comp.loadCatalogs();

      await waitFor(() => expect(comp.loadingCatalogsFailed).toBe(true));
      expect(comp.loadingCatalogs).toBe(false);
   });
});

// ── Group 4: loadMetadata ─────────────────────────────────────────────────

describe("DatasourcesXmla — loadMetadata", () => {

   it("updates model.domain and cubeTree on success", async () => {
      const domain = makeDomain([makeCube({ name: "Sales" })]);
      const tree = makeTreeNode({ label: "MetaRoot" });

      server.use(
         http.post("*/api/portal/data/datasource/xmla/metadata/refresh", () =>
            HttpResponse.json({ domain, cubeTree: tree })
         )
      );

      const { comp } = await renderXmla({ model: makeXmlaModel() });

      comp.loadMetadata();

      await waitFor(() => expect(comp.cubeTree?.label).toBe("MetaRoot"));
      expect(comp.model.domain.cubes[0].name).toBe("Sales");
      expect(comp.loadingMeta).toBe(false);
   });

   it("sets loadingMeta=false on POST error", async () => {
      server.use(
         http.post("*/api/portal/data/datasource/xmla/metadata/refresh", () =>
            HttpResponse.json({ message: "Server error" }, { status: 500 })
         )
      );

      const { comp } = await renderXmla({ model: makeXmlaModel() });

      comp.loadMetadata();

      await waitFor(() => expect(comp.loadingMeta).toBe(false));
   });
});

// ── Group 5: testDatabase ─────────────────────────────────────────────────

describe("DatasourcesXmla — testDatabase", () => {

   it("shows success notification when connection is connected=true", async () => {
      server.use(
         http.post("*/api/portal/data/datasource/xmla/test", () =>
            HttpResponse.json({ connected: true, status: "Connection successful" })
         )
      );

      const { comp, fixture } = await renderXmla({ model: makeXmlaModel() });
      const dataNotif = fixture.debugElement.query(By.directive(DataNotificationsStub))?.componentInstance;
      expect(dataNotif).toBeDefined();

      comp.testDatabase();

      await waitFor(() => expect(dataNotif.notifications.success).toHaveBeenCalled());
   });

   it("opens error dialog when connection.connected is false", async () => {
      server.use(
         http.post("*/api/portal/data/datasource/xmla/test", () =>
            HttpResponse.json({ connected: false, status: "Connection failed" })
         )
      );

      const { comp } = await renderXmla({ model: makeXmlaModel() });

      comp.testDatabase();

      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalled());
   });
});

// ── Group 6: ok() — editing (POST update) ────────────────────────────────

describe("DatasourcesXmla — ok() editing branch (POST update)", () => {

   it("calls POST update and navigates away on success", async () => {
      server.use(
         http.post("*/api/portal/data/datasource/xmla/update", () =>
            HttpResponse.json({})
         )
      );

      const { comp } = await renderXmla({ model: makeXmlaModel({ name: "ExistingXmla" }) });
      (comp as any).originalModel = makeXmlaModel({ name: "ExistingXmla" });
      comp.editing = true;

      comp.ok();

      await waitFor(() => expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources"],
         expect.anything(),
      ));
   });

   it("opens error dialog when POST update fails", async () => {
      server.use(
         http.post("*/api/portal/data/datasource/xmla/update", () =>
            HttpResponse.json({ message: "Save error" }, { status: 500 })
         )
      );

      const { comp } = await renderXmla({ model: makeXmlaModel({ name: "ExistingXmla" }) });
      (comp as any).originalModel = makeXmlaModel({ name: "ExistingXmla" });
      comp.editing = true;

      comp.ok();

      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalled());
   });
});

// ── Group 7: ok() — new (POST new) ────────────────────────────────────────

describe("DatasourcesXmla — ok() new branch (POST new)", () => {

   it("calls POST new and navigates away on success", async () => {
      server.use(
         http.post("*/api/portal/data/datasource/xmla/new", () =>
            HttpResponse.json({})
         )
      );

      const { comp } = await renderXmla({ model: makeXmlaModel({ name: "NewXmla" }) });
      (comp as any).originalModel = makeXmlaModel({ name: "" });
      comp.editing = false;

      comp.ok();

      await waitFor(() => expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources"],
         expect.anything(),
      ));
   });
});
