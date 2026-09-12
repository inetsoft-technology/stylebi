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
 * AnalyzeMVDialog - Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - constructor wiring and ngOnInit bootstrap refresh
 *   Group 2 [Risk 3] - showCreateMVPage state hydration
 *   Group 3 [Risk 2] - refresh hide toggles and model reloads
 *   Group 4 [Risk 2] - setCycle posts the request and refreshes the current view
 *
 * Mocking strategy:
 *   - direct HttpClient -> provideHttpClient() + MSW
 *   - modal/message helpers -> ComponentTool spies
 */

import { waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { of } from "rxjs";

import { RepositoryEntryType } from "../../../../../../shared/data/repository-entry-type.enum";
import { DateTypeFormatter } from "../../../../../../shared/util/date-type-formatter";
import { server } from "@test-mocks/server";
import {
   createAnalyzeMVDialog,
   makeAnalyzeMVDialogResponse,
   makeAnalyzeMVSelectedNode,
   makeAnalyzeMVStatus,
} from "./analyze-mv-dialog.component.test-helpers";

afterEach(() => {
   vi.restoreAllMocks();
});

describe("AnalyzeMVDialog - interaction", () => {
   describe("Group 1 - bootstrap", () => {
      it("connects the repository client and loads security state in the constructor", async () => {
         server.use(
            http.get("*/api/em/security/get-enable-security", () =>
               HttpResponse.json({ enable: true }),
            ),
         );

         const { comp, repositoryClient } = createAnalyzeMVDialog({});

         await waitFor(() => expect(comp.securityEnabled).toBe(true));
         expect(repositoryClient.connect).toHaveBeenCalled();
         expect(comp.analyzeMVModel).toEqual({
            fullData: true,
            bypass: false,
            groupExpanded: false,
            applyParentVsParameters: false,
         });
      });

      it("ngOnInit should load and format existing materialized views", async () => {
         const formatterSpy = vi.spyOn(DateTypeFormatter, "getLocalTime").mockReturnValue("formatted-time");
         server.use(
            http.get("*/api/em/security/get-enable-security", () =>
               HttpResponse.json({ enable: false }),
            ),
            http.post("*/api/portal/content/materialized-view/info", async ({ request }) => {
               expect(await request.json()).toEqual({
                  expanded: false,
                  full: true,
                  bypass: false,
                  applyParentVsParameters: false,
                  nodes: [],
               });

               return HttpResponse.json({
                  dateFormat: "yyyy-MM-dd",
                  mvs: [
                     {
                        name: "mv_one",
                        hasData: true,
                        exists: false,
                        lastModifiedTimestamp: 5,
                     },
                     {
                        name: "mv_two",
                        hasData: false,
                        exists: true,
                        lastModifiedTimestamp: 0,
                     },
                  ],
               });
            }),
         );
         const { comp } = createAnalyzeMVDialog({});

         comp.ngOnInit();

         await waitFor(() => expect(comp.existingModels?.length).toBe(2));
         expect(formatterSpy).toHaveBeenCalledWith(5, "yyyy-MM-dd");
         expect(comp.existingModels).toEqual([
            expect.objectContaining({
               name: "mv_one",
               dataString: "_#(js:Yes)",
               existString: "_#(js:False)",
               lastModifiedTime: "formatted-time",
            }),
            expect.objectContaining({
               name: "mv_two",
               dataString: "_#(js:No)",
               existString: "_#(js:True)",
            }),
         ]);
      });
   });

   describe("Group 2 - showCreateMVPage", () => {
      it("hydrates analyzed state, cycles, and formatted timestamps from the analysis response", () => {
         const formatterSpy = vi.spyOn(DateTypeFormatter, "getLocalTime").mockReturnValue("localized");
         const { comp } = createAnalyzeMVDialog({});
         const response = makeAnalyzeMVDialogResponse({
            status: [
               makeAnalyzeMVStatus({ name: "mv_one", lastModifiedTimestamp: 10 }),
               makeAnalyzeMVStatus({ name: "mv_two", lastModifiedTimestamp: 0 }),
            ],
            cycles: [{ name: "daily", label: "Daily" }],
            onDemand: true,
            defaultCycle: "daily",
            runInBackground: true,
            dateFormat: "MM/dd/yyyy",
         });

         comp.showCreateMVPage(response as never);

         expect(formatterSpy).toHaveBeenCalledWith(10, "MM/dd/yyyy");
         expect(comp.models).toEqual([
            expect.objectContaining({ name: "mv_one", lastModifiedTime: "localized" }),
            expect.objectContaining({ name: "mv_two" }),
         ]);
         expect(comp.cycles).toEqual([{ name: "daily", label: "Daily" }]);
         expect(comp.mvCycle).toBe("daily");
         expect(comp.runInBackground).toBe(true);
         expect(comp.analyzed).toBe(true);
      });

      it("clears the cycle when the response is not on-demand", () => {
         const { comp } = createAnalyzeMVDialog({});

         comp.showCreateMVPage(makeAnalyzeMVDialogResponse({
            onDemand: false,
            defaultCycle: "daily",
         }) as never);

         expect(comp.mvCycle).toBe("");
      });
   });

   describe("Group 3 - refresh", () => {
      it("refresh should keep hide toggles mutually exclusive and reload both model sources", async () => {
         let getModelQuery = "";
         server.use(
            http.get("*/api/em/security/get-enable-security", () =>
               HttpResponse.json({ enable: false }),
            ),
            http.get("*/api/em/content/repository/mv/get-model/*", ({ request }) => {
               getModelQuery = new URL(request.url).search;
               return HttpResponse.json({ status: [{ name: "fresh" }] });
            }),
         );
         const { comp } = createAnalyzeMVDialog({});
         comp.analysisId = "analysis-1";
         comp.hideData = true;
         comp.hideExist = true;
         const refreshModelsSpy = vi.spyOn(comp, "refreshModels").mockImplementation(() => {});

         comp.refresh("DATA");

         await waitFor(() => expect(comp.models).toEqual([{ name: "fresh" }]));
         expect(comp.hideExist).toBe(false);
         expect(getModelQuery).toBe("?hideData=true&hideExist=false");
         expect(refreshModelsSpy).toHaveBeenCalledWith();
      });

      it("refresh should clear hideData when the EXIST filter is toggled on", async () => {
         server.use(
            http.get("*/api/em/security/get-enable-security", () =>
               HttpResponse.json({ enable: false }),
            ),
            http.get("*/api/em/content/repository/mv/get-model/*", () =>
               HttpResponse.json({ status: [] }),
            ),
         );
         const { comp } = createAnalyzeMVDialog({});
         comp.analysisId = "analysis-1";
         comp.hideData = true;
         comp.hideExist = true;
         vi.spyOn(comp, "refreshModels").mockImplementation(() => {});

         comp.refresh("EXIST");

         await waitFor(() => expect(comp.models).toEqual([]));
         expect(comp.hideData).toBe(false);
      });
   });

   describe("Group 4 - setCycle", () => {
      it("posts the cycle request and refreshes the current analysis view", async () => {
         let requestBody: unknown;
         server.use(
            http.get("*/api/em/security/get-enable-security", () =>
               HttpResponse.json({ enable: false }),
            ),
            http.post("*/api/em/content/repository/mv/set-cycle/*", async ({ request }) => {
               requestBody = await request.json();
               return HttpResponse.json({});
            }),
         );
         const { comp } = createAnalyzeMVDialog({});
         comp.analysisId = "analysis-1";
         const refreshSpy = vi.spyOn(comp, "refresh").mockImplementation(() => {});

         comp.setCycle({ mvNames: ["mv_one"], cycle: "daily" } as never);

         await waitFor(() => expect(refreshSpy).toHaveBeenCalledWith(""));
         expect(requestBody).toEqual({ mvNames: ["mv_one"], cycle: "daily" });
      });
   });
});
