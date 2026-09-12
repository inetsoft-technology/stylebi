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
 * DriverWizardComponent - Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - ngOnInit and uploadType subscription state wiring
 *   Group 2 [Risk 3] - mavenSearch/searchMaven request and debounce flow
 *   Group 3 [Risk 2] - selectDriverFile multi-key selection behavior
 *   Group 4 [Risk 2] - validators for plugin id, upload files, and maven coordinate
 *   Group 5 [Risk 1] - scanDrivers, trackByIdx, and selectDriver user flow
 *
 * Mocking strategy:
 *   - direct HttpClient -> provideHttpClient() + MSW
 */

import { HttpClient } from "@angular/common/http";
import { TestBed } from "@angular/core/testing";
import { waitFor } from "@testing-library/angular";
import { firstValueFrom, of, Subject } from "rxjs";
import { http, HttpResponse } from "msw";

import { server } from "@test-mocks/server";
import {
   asDriverWizardPrivateApi,
   createDriverWizardComponent,
   makeDriverWizardMouseEvent,
} from "./driver-wizard.component.test-helpers";

afterEach(() => {
   vi.restoreAllMocks();
   vi.useRealTimers();
});

describe("DriverWizardComponent - interaction", () => {
   describe("Group 1 - init and uploadType subscription", () => {
      it("loads plugin ids on init when plugins input is empty", async () => {
         server.use(
            http.get("*/api/data/plugins", () =>
               HttpResponse.json({
                  supportUploadDriver: true,
                  plugins: [
                     { id: "driver-a", name: "A", version: "1.0.0", vendor: "x", readOnly: false },
                     { id: "driver-b", name: "B", version: "1.0.0", vendor: "x", readOnly: false },
                  ],
               }),
            ),
         );
         const comp = createDriverWizardComponent();

         comp.ngOnInit();

         await waitFor(() => expect(comp.plugins).toEqual(["driver-a", "driver-b"]));
      });

      it("skips plugin loading when plugins input is already populated", () => {
         const comp = createDriverWizardComponent();
         const getSpy = vi.spyOn(TestBed.inject(HttpClient), "get");
         comp.plugins = ["existing"];

         comp.ngOnInit();

         expect(getSpy).not.toHaveBeenCalled();
      });

      it("enables and disables mavenCoord when uploadType changes", () => {
         const comp = createDriverWizardComponent();

         expect(comp.uploadForm.get("mavenCoord").disabled).toBe(true);

         comp.uploadForm.get("uploadType").setValue("maven");
         expect(comp.uploadForm.get("mavenCoord").enabled).toBe(true);

         comp.uploadForm.get("uploadType").setValue("upload");
         expect(comp.uploadForm.get("mavenCoord").disabled).toBe(true);
      });
   });

   describe("Group 2 - maven search", () => {
      it("searchMaven should request the backend and return result strings", async () => {
         let capturedQuery = "";
         server.use(
            http.get("*/api/em/upload/maven-search", ({ request }) => {
               capturedQuery = new URL(request.url).searchParams.get("q") || "";
               return HttpResponse.json({ results: ["mysql:mysql-connector-java:8.0.0"] });
            }),
         );
         const comp = createDriverWizardComponent();
         const privateApi = asDriverWizardPrivateApi(comp);

         const results = await firstValueFrom(privateApi.searchMaven("mysql"));

         expect(capturedQuery).toBe("mysql");
         expect(results).toEqual(["mysql:mysql-connector-java:8.0.0"]);
      });

      it("filters out terms shorter than 4 characters without calling searchMaven", async () => {
         vi.useFakeTimers();
         const comp = createDriverWizardComponent();
         const privateApi = asDriverWizardPrivateApi(comp);
         const term$ = new Subject<string>();
         const searchSpy = vi.spyOn(privateApi, "searchMaven").mockReturnValue(of([]));

         comp.mavenSearch(term$.asObservable()).subscribe();

         term$.next("abc");
         vi.advanceTimersByTime(1000);
         await Promise.resolve();

         expect(searchSpy).not.toHaveBeenCalled();
      });

      it("debounces the search call by 1 second and fires on the last term in the window", async () => {
         vi.useFakeTimers();
         const comp = createDriverWizardComponent();
         const privateApi = asDriverWizardPrivateApi(comp);
         const term$ = new Subject<string>();
         const searchSpy = vi.spyOn(privateApi, "searchMaven").mockReturnValue(of(["g:a:v"]));
         const received: string[][] = [];

         comp.mavenSearch(term$.asObservable()).subscribe(results => received.push(results));

         term$.next("abcd");
         vi.advanceTimersByTime(999);
         term$.next("abcde");
         vi.advanceTimersByTime(1000);
         await Promise.resolve();

         expect(searchSpy).toHaveBeenCalledTimes(1);
         expect(searchSpy).toHaveBeenCalledWith("abcde");
         expect(received).toEqual([["g:a:v"]]);
      });

      it("does not call searchMaven when the same term is submitted twice consecutively", async () => {
         vi.useFakeTimers();
         const comp = createDriverWizardComponent();
         const privateApi = asDriverWizardPrivateApi(comp);
         const term$ = new Subject<string>();
         const searchSpy = vi.spyOn(privateApi, "searchMaven").mockReturnValue(of(["g:a:v"]));

         comp.mavenSearch(term$.asObservable()).subscribe();

         term$.next("abcde");
         vi.advanceTimersByTime(1000);
         await Promise.resolve();

         term$.next("abcde");
         vi.advanceTimersByTime(1000);
         await Promise.resolve();

         expect(searchSpy).toHaveBeenCalledTimes(1);
      });
   });

   describe("Group 3 - selectDriverFile", () => {
      it("clears previous selection on plain click and selects the clicked index", () => {
         const comp = createDriverWizardComponent();
         comp.selectedDriverFiles = [1, 3];

         comp.selectDriverFile(makeDriverWizardMouseEvent(), 2);

         expect(comp.selectedDriverFiles).toEqual([2]);
      });

      it("toggles the clicked index on ctrl-click", () => {
         const comp = createDriverWizardComponent();
         comp.selectedDriverFiles = [1, 3];

         comp.selectDriverFile(makeDriverWizardMouseEvent({ ctrlKey: true }), 3);
         expect(comp.selectedDriverFiles).toEqual([1]);

         comp.selectDriverFile(makeDriverWizardMouseEvent({ ctrlKey: true }), 2);
         expect(comp.selectedDriverFiles).toEqual([1, 2]);
      });

      it("creates a range when shift-clicking after an existing selection", () => {
         const comp = createDriverWizardComponent();
         comp.selectedDriverFiles = [2];

         comp.selectDriverFile(makeDriverWizardMouseEvent({ shiftKey: true }), 5);

         expect(comp.selectedDriverFiles).toEqual([5, 3, 4, 2]);
      });
   });

   describe("Group 4 - validators", () => {
      it("pluginExists should flag duplicate plugin ids", () => {
         const comp = createDriverWizardComponent();
         comp.plugins = ["existing-plugin"];

         comp.pluginForm.get("pluginId").setValue("existing-plugin");
         comp.pluginForm.get("pluginId").updateValueAndValidity();

         expect(comp.pluginForm.get("pluginId").errors).toEqual({ pluginExists: true });
      });

      it("uploadFilesRequired should require files in upload mode", () => {
         const comp = createDriverWizardComponent();
         comp.uploadForm.get("uploadType").setValue("upload");
         comp.uploadForm.get("uploadFiles").setValue([]);
         comp.uploadForm.updateValueAndValidity();

         expect(comp.uploadForm.errors).toEqual({ uploadFilesRequired: true });
       });

      it("mavenCoordRequired should require a coordinate in maven mode", () => {
         const comp = createDriverWizardComponent();
         comp.uploadForm.get("uploadType").setValue("maven");
         comp.uploadForm.get("mavenCoord").setValue("");
         comp.uploadForm.get("mavenCoord").updateValueAndValidity();

         expect(comp.uploadForm.get("mavenCoord").errors).toEqual({ required: true });
      });
   });

   describe("Group 5 - driver selection helpers", () => {
      it("scanDrivers should return the scanned driver list", async () => {
         server.use(
            http.get("*/api/em/settings/content/plugins/drivers/scan/*", () =>
               HttpResponse.json({ drivers: ["driver.one", "driver.two"] }),
            ),
         );
         const comp = createDriverWizardComponent();
         const privateApi = asDriverWizardPrivateApi(comp);

         const drivers = await firstValueFrom(privateApi.scanDrivers("scan-id"));

         expect(drivers).toEqual(["driver.one", "driver.two"]);
      });

      it("trackByIdx should return the item index", () => {
         const comp = createDriverWizardComponent();

         expect(comp.trackByIdx(7)).toBe(7);
      });

      it("selectDriver should write the selected driver names to the form control", () => {
         const comp = createDriverWizardComponent();
         comp.drivers = ["driver.a", "driver.b", "driver.c"];
         comp.selectedDrivers = [true, false, true];

         comp.selectDriver(2);

         expect(comp.driverForm.get("drivers").value).toEqual(["driver.a", "driver.c"]);
         expect(comp.driverForm.get("drivers").dirty).toBe(true);
      });
   });
});
