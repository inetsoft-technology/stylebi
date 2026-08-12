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

import { provideHttpClient } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { EmbedDatasourceRegistrationComponent } from "./embed-datasource-registration.component";
import { EmbedDatasourceRegistrationService } from "./embed-datasource-registration.service";

/**
 * A seed shaped like the real one.
 *
 * Every node carries `views: []` because the server always sends it: TabularView initializes the
 * list in its constructor and getViews() returns an array, so a leaf serializes as `views: []`,
 * never absent. The editor's getDependsOn recurses into `view.views` unconditionally and relies on
 * that. A fixture that omits it does not reproduce a payload StyleBI can emit — it just crashes the
 * editor before it renders.
 */
function seeded(type = "Cassandra"): any {
   return {
      name: "", type, description: "", parentPath: "",
      tabularView: {
         views: [{ text: "host", value: "host", editor: { type: "TEXT" }, views: [] }],
      },
   };
}

describe("EmbedDatasourceRegistrationComponent", () => {
   async function setup() {
      const view = await render(EmbedDatasourceRegistrationComponent, {
         providers: [
            EmbedDatasourceRegistrationService, provideHttpClient(), provideHttpClientTesting(),
         ],
      });

      return { view, http: TestBed.inject(HttpTestingController) };
   }

   it("offers the type picker on first render", async () => {
      const { http } = await setup();
      http.expectOne("../api/portal/data/datasource-selection-view")
         .flush({ listings: [{ name: "Apache Cassandra", category: "Big Data" }] });
      http.expectOne("../api/data/datasources/browser").flush({ dataSourceList: [] });

      expect(await screen.findByText("Apache Cassandra")).toBeTruthy();
   });

   // The whole point of the approach: the form is whatever the SERVER describes, so picking a type
   // must seed from the server rather than render anything hand-built.
   it("seeds the editor from the server when a type is picked", async () => {
      const { http } = await setup();
      http.expectOne("../api/portal/data/datasource-selection-view")
         .flush({ listings: [{ name: "Apache Cassandra", category: "Big Data" }] });
      http.expectOne("../api/data/datasources/browser").flush({ dataSourceList: [] });

      await userEvent.click(await screen.findByText("Apache Cassandra"));

      const req = http.expectOne("../api/portal/data/datasources/listing/Apache%20Cassandra");
      req.flush(seeded());

      await waitFor(() => expect(screen.queryByText("Apache Cassandra")).toBeNull());
   });

   it("emits failed — and does not render a half-built form — when seeding fails", async () => {
      const { view, http } = await setup();
      const emissions: string[] = [];
      view.fixture.componentInstance.failed.subscribe((m: string) => emissions.push(m));

      http.expectOne("../api/portal/data/datasource-selection-view")
         .flush({ listings: [{ name: "Apache Cassandra", category: "Big Data" }] });
      http.expectOne("../api/data/datasources/browser").flush({ dataSourceList: [] });

      await userEvent.click(await screen.findByText("Apache Cassandra"));
      http.expectOne("../api/portal/data/datasources/listing/Apache%20Cassandra")
         .flush("boom", { status: 500, statusText: "Server Error" });

      await waitFor(() => expect(emissions.length).toBe(1));
      // Still on the picker — a failed seed must not leave an empty editor on screen.
      expect(await screen.findByText("Apache Cassandra")).toBeTruthy();
   });
});

describe("EmbedDatasourceRegistrationComponent — save lifecycle", () => {
   async function seededSetup() {
      const view = await render(EmbedDatasourceRegistrationComponent, {
         providers: [
            EmbedDatasourceRegistrationService, provideHttpClient(), provideHttpClientTesting(),
         ],
         componentProperties: { listingName: "Apache Cassandra" },
      });
      const http = TestBed.inject(HttpTestingController);
      http.expectOne("../api/portal/data/datasource-selection-view").flush({ listings: [] });
      http.expectOne("../api/data/datasources/browser")
         .flush({ dataSourceList: ["olist"].map((n) => ({ name: n })) });
      http.expectOne("../api/portal/data/datasources/listing/Apache%20Cassandra").flush(seeded());

      return { view, http, cmp: view.fixture.componentInstance };
   }

   it("emits registered with the saved name and type", async () => {
      const { view, http, cmp } = await seededSetup();
      const got: any[] = [];
      cmp.registered.subscribe((r: any) => got.push(r));

      cmp.datasource.name = "my-cassandra";
      cmp.valid = true;
      view.fixture.detectChanges();

      await userEvent.click(await screen.findByRole("button", { name: /save/i }));
      http.expectOne("../api/portal/data/datasources").flush({});

      await waitFor(() => expect(got).toEqual([{ name: "my-cassandra", type: "Cassandra" }]));
   });

   // A rejected save must not discard what the operator typed. Clearing the form on error is the
   // single most annoying failure mode a registration form can have.
   it("keeps the form and its values when the save is rejected", async () => {
      const { view, http, cmp } = await seededSetup();
      const errors: string[] = [];
      cmp.failed.subscribe((m: string) => errors.push(m));

      cmp.datasource.name = "my-cassandra";
      cmp.valid = true;
      view.fixture.detectChanges();

      await userEvent.click(await screen.findByRole("button", { name: /save/i }));
      http.expectOne("../api/portal/data/datasources")
         .flush({ message: "name already in use" }, { status: 400, statusText: "Bad Request" });

      await waitFor(() => expect(errors).toEqual(["name already in use"]));
      expect(cmp.datasource).not.toBeNull();
      expect(cmp.datasource.name).toBe("my-cassandra");
      expect(cmp.saving).toBe(false);
   });

   // Creating a data source is permission-checked in StyleBI. A refusal must READ as a refusal —
   // appearing to succeed is the worst outcome, because the operator walks away believing the
   // source exists.
   it("surfaces a permission refusal instead of appearing to succeed", async () => {
      const { view, http, cmp } = await seededSetup();
      const errors: string[] = [];
      const successes: any[] = [];
      cmp.failed.subscribe((m: string) => errors.push(m));
      cmp.registered.subscribe((r: any) => successes.push(r));

      cmp.datasource.name = "my-cassandra";
      cmp.valid = true;
      view.fixture.detectChanges();

      await userEvent.click(await screen.findByRole("button", { name: /save/i }));
      http.expectOne("../api/portal/data/datasources")
         .flush({ message: "Access denied" }, { status: 403, statusText: "Forbidden" });

      await waitFor(() => expect(errors).toEqual(["Access denied"]));
      expect(successes).toEqual([]);
      expect(cmp.datasource).not.toBeNull();
   });

   it("does not fire a second save while one is in flight", async () => {
      const { view, http, cmp } = await seededSetup();
      cmp.datasource.name = "my-cassandra";
      cmp.valid = true;
      view.fixture.detectChanges();

      const btn = await screen.findByRole("button", { name: /save/i });
      await userEvent.click(btn);
      cmp.save();

      // One in-flight request only; expectOne throws if the guard let a second through.
      http.expectOne("../api/portal/data/datasources").flush({});
   });

   it("emits cancelled and returns to the picker", async () => {
      const { cmp } = await seededSetup();
      const cancels: number[] = [];
      cmp.cancelled.subscribe(() => cancels.push(1));

      cmp.cancel();

      expect(cancels.length).toBe(1);
      expect(cmp.datasource).toBeNull();
   });

   it("passes existing names to the editor so a duplicate is caught before the server", async () => {
      const { cmp } = await seededSetup();
      expect(cmp.usedNames).toEqual(["olist"]);
   });
});

/*
 * Custom elements from this bundle do not get a change-detection pass for free.
 *
 * Measured in a real browser against a real server: the selection-view call returned all 130
 * listings, the component stored them, and the <ul> stayed empty -- until a keystroke in the filter
 * box, at which point 69 matches appeared at once. Assigning state inside an async callback marks
 * nothing and schedules no tick, so the picker reads as "this deployment has no data source types".
 * The same remedy the embedded chart needed: drive change detection explicitly.
 *
 * TestBed runs change detection itself, so it cannot reproduce the blank render. These tests pin
 * the call instead -- remove the detectChanges and they fail, which is the point.
 */
describe("EmbedDatasourceRegistrationComponent — explicit change detection", () => {
   it("runs change detection when the listings arrive", async () => {
      const view = await render(EmbedDatasourceRegistrationComponent, {
         providers: [
            EmbedDatasourceRegistrationService, provideHttpClient(), provideHttpClientTesting(),
         ],
      });
      const cmp: any = view.fixture.componentInstance;
      const spy = vi.spyOn(cmp.cdRef, "detectChanges");
      const http = TestBed.inject(HttpTestingController);

      http.expectOne("../api/portal/data/datasource-selection-view")
         .flush({ listings: [{ name: "Apache Cassandra" }] });

      expect(spy).toHaveBeenCalled();
   });

   it("runs change detection when the seeded form arrives", async () => {
      const view = await render(EmbedDatasourceRegistrationComponent, {
         providers: [
            EmbedDatasourceRegistrationService, provideHttpClient(), provideHttpClientTesting(),
         ],
         componentProperties: { listingName: "Apache Cassandra" },
      });
      const cmp: any = view.fixture.componentInstance;
      const http = TestBed.inject(HttpTestingController);
      http.expectOne("../api/portal/data/datasource-selection-view").flush({ listings: [] });
      http.expectOne("../api/data/datasources/browser").flush({ dataSourceList: [] });

      const spy = vi.spyOn(cmp.cdRef, "detectChanges");
      http.expectOne("../api/portal/data/datasources/listing/Apache%20Cassandra").flush(seeded());

      expect(spy).toHaveBeenCalled();
   });
});
