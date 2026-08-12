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
import { render, screen, waitFor } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { EmbedDatasourceRegistrationComponent } from "./embed-datasource-registration.component";
import { EmbedDatasourceRegistrationService } from "./embed-datasource-registration.service";

function seeded(type = "Cassandra"): any {
   return {
      name: "", type, description: "", parentPath: "",
      tabularView: { views: [{ text: "host", value: "host", editor: { type: "TEXT" } }] },
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
         .flush({ dataSourceListings: [{ name: "Apache Cassandra" }] });
      http.expectOne("../api/data/datasources/browser").flush({ files: [] });

      expect(await screen.findByText("Apache Cassandra")).toBeTruthy();
   });

   // The whole point of the approach: the form is whatever the SERVER describes, so picking a type
   // must seed from the server rather than render anything hand-built.
   it("seeds the editor from the server when a type is picked", async () => {
      const { http } = await setup();
      http.expectOne("../api/portal/data/datasource-selection-view")
         .flush({ dataSourceListings: [{ name: "Apache Cassandra" }] });
      http.expectOne("../api/data/datasources/browser").flush({ files: [] });

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
         .flush({ dataSourceListings: [{ name: "Apache Cassandra" }] });
      http.expectOne("../api/data/datasources/browser").flush({ files: [] });

      await userEvent.click(await screen.findByText("Apache Cassandra"));
      http.expectOne("../api/portal/data/datasources/listing/Apache%20Cassandra")
         .flush("boom", { status: 500, statusText: "Server Error" });

      await waitFor(() => expect(emissions.length).toBe(1));
      // Still on the picker — a failed seed must not leave an empty editor on screen.
      expect(await screen.findByText("Apache Cassandra")).toBeTruthy();
   });
});
