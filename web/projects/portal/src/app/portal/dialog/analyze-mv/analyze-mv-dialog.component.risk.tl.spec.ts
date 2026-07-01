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
 * AnalyzeMVDialog - Pass 2: Risk
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - analyzeMV request gating and invalid-node rejection
 *   Group 2 [Risk 3] - deleteMV removal flow and timeout cleanup
 *   Group 3 [Risk 3] - polling, exception dialog routing, and result completion
 *   Group 4 [Risk 3] - create/show-plan/message flows
 *   Group 5 [Risk 2] - create-pane delegates and selection gating
 *   Group 6 [Risk 2] - close/commit/repository-change outputs and teardown
 *
 * Mocking strategy:
 *   - direct HttpClient -> provideHttpClient() + MSW
 *   - modal/message helpers -> ComponentTool spies
 *   - timer-based flows -> fake timers
 */

import { waitFor } from "@testing-library/angular";
import { EMPTY, of, throwError } from "rxjs";
import { http, HttpResponse } from "msw";

import { RepositoryEntryType } from "../../../../../../shared/data/repository-entry-type.enum";
import { Tool } from "../../../../../../shared/util/tool";
import { ComponentTool } from "../../../common/util/component-tool";
import { server } from "@test-mocks/server";
import {
   asAnalyzeMVDialogPrivateApi,
   attachAnalyzeMVAnalyzePane,
   attachAnalyzeMVCreatePane,
   createAnalyzeMVDialog,
   makeAnalyzeMVDialogResponse,
   makeAnalyzeMVModel,
   makeAnalyzeMVSelectedNode,
} from "./analyze-mv-dialog.component.test-helpers";

afterEach(() => {
   vi.restoreAllMocks();
   vi.useRealTimers();
});

describe("AnalyzeMVDialog - risk", () => {
   describe("Group 1 - analyzeMV", () => {
      it("analyzeMV should post valid worksheet/viewsheet nodes and schedule completion polling", async () => {
         server.use(
            http.get("*/api/em/security/get-enable-security", () =>
               HttpResponse.json({ enable: false }),
            ),
            http.post("*/api/portal/content/repository/mv/analyze", async ({ request }) =>
               HttpResponse.json({
                  analysisId: "analysis-9",
               }),
            ),
         );
         const { comp } = createAnalyzeMVDialog({});
         comp.selectedNodes = [
            makeAnalyzeMVSelectedNode({
               identifier: "vs",
               type: RepositoryEntryType.VIEWSHEET,
            }) as never,
            makeAnalyzeMVSelectedNode({
               identifier: "ws",
               type: RepositoryEntryType.WORKSHEET,
            }) as never,
         ];
         const checkCompletedSpy = vi.spyOn(comp, "checkCompleted").mockImplementation(() => {});

         comp.analyzeMV();

         await waitFor(() => expect(checkCompletedSpy).toHaveBeenCalledWith());
         expect(comp.analysisId).toBe("analysis-9");
      });

      it("analyzeMV should show an error dialog when a folder node is included", () => {
         const showMessageSpy = vi.spyOn(ComponentTool, "showMessageDialog")
            .mockReturnValue(Promise.resolve());
         const { comp } = createAnalyzeMVDialog({});
         comp.selectedNodes = [
            makeAnalyzeMVSelectedNode({
               type: RepositoryEntryType.FOLDER,
            }) as never,
         ];

         comp.analyzeMV();

         expect(showMessageSpy).toHaveBeenCalledWith(
            expect.anything(),
            "_#(js:Error)",
            "_#(js:em.mv.folderFound)",
         );
         expect(comp.loading).toBe(false);
      });

      it("analyzeMV should surface backend errors and clear loading", async () => {
         const showMessageSpy = vi.spyOn(ComponentTool, "showMessageDialog")
            .mockReturnValue(Promise.resolve());
         server.use(
            http.get("*/api/em/security/get-enable-security", () =>
               HttpResponse.json({ enable: false }),
            ),
            http.post("*/api/portal/content/repository/mv/analyze", () =>
               HttpResponse.json({ message: "analyze failed" }, { status: 500 }),
            ),
         );
         const { comp } = createAnalyzeMVDialog({});
         comp.selectedNodes = [
            makeAnalyzeMVSelectedNode({
               type: RepositoryEntryType.VIEWSHEET,
            }) as never,
         ];

         comp.analyzeMV();

         await waitFor(() =>
            expect(showMessageSpy).toHaveBeenCalledWith(
               expect.anything(),
               "_#(js:Error)",
               "analyze failed",
            ),
         );
         expect(comp.loading).toBe(false);
      });
   });

   describe("Group 2 - deleteMV", () => {
      it("deleteMV should remove the selected materialized views and refresh the list", async () => {
         let requestBody: unknown;
         server.use(
            http.get("*/api/em/security/get-enable-security", () =>
               HttpResponse.json({ enable: false }),
            ),
            http.post("*/api/em/content/materialized-view/remove", async ({ request }) => {
               requestBody = await request.json();
               return HttpResponse.json({});
            }),
         );
         const { comp } = createAnalyzeMVDialog({});
         attachAnalyzeMVAnalyzePane(comp, [makeAnalyzeMVModel({ name: "mv_one" })]);
         const refreshSpy = vi.spyOn(comp, "refreshModels").mockImplementation(() => {});

         comp.deleteMV();

         await waitFor(() => expect(refreshSpy).toHaveBeenCalled());
         expect(requestBody).toEqual({
            mvs: [expect.objectContaining({ name: "mv_one" })],
         });
         expect(comp.loading).toBe(false);
      });

      it("deleteMV should clear the spinner when the timeout expires before the request resolves", () => {
         vi.useFakeTimers();
         server.use(
            http.get("*/api/em/security/get-enable-security", () =>
               HttpResponse.json({ enable: false }),
            ),
            http.post("*/api/em/content/materialized-view/remove", async () =>
               new Promise<Response>(() => {}),
            ),
         );
         const { comp } = createAnalyzeMVDialog({});
         attachAnalyzeMVAnalyzePane(comp, [makeAnalyzeMVModel({ name: "mv_one" })]);

         comp.deleteMV();
         vi.advanceTimersByTime(30000);

         expect(comp.loading).toBe(false);
      });
   });

   describe("Group 3 - polling and result handling", () => {
      it("checkCompleted should delegate the response to processAnalyzeResult", async () => {
         server.use(
            http.get("*/api/em/security/get-enable-security", () =>
               HttpResponse.json({ enable: false }),
            ),
            http.get("*/api/em/content/materialized-view/check-analysis/*", () =>
               HttpResponse.json({ completed: true }),
            ),
         );
         const { comp } = createAnalyzeMVDialog({});
         comp.analysisId = "analysis-1";
         const processSpy = vi.spyOn(comp, "processAnalyzeResult").mockImplementation(() => {});

         comp.checkCompleted(600);

         await waitFor(() =>
            expect(processSpy).toHaveBeenCalledWith(expect.objectContaining({ completed: true }), 600),
         );
      });

      it("processAnalyzeResult should retry with a bounded backoff while the analysis is incomplete", () => {
         vi.useFakeTimers();
         const { comp } = createAnalyzeMVDialog({});
         const checkSpy = vi.spyOn(comp, "checkCompleted").mockImplementation(() => {});

         comp.processAnalyzeResult({ completed: false } as never, 1000);
         vi.advanceTimersByTime(1000);

         expect(checkSpy).toHaveBeenCalledWith(1500);
      });

      it("processAnalyzeResult should load exceptions and open the dialog before showing the create page", async () => {
         const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockImplementation((..._args) => {
            return {} as never;
         });
         server.use(
            http.get("*/api/em/security/get-enable-security", () =>
               HttpResponse.json({ enable: false }),
            ),
            http.get("*/api/em/content/repository/mv/exceptions/*", () =>
               HttpResponse.json({ exceptions: ["broken mv"] }),
            ),
         );
         const { comp } = createAnalyzeMVDialog({});
         comp.analysisId = "analysis-1";
         const showCreateSpy = vi.spyOn(comp, "showCreateMVPage").mockImplementation(() => {});

         comp.processAnalyzeResult({
            completed: true,
            exception: true,
            status: [],
         } as never, 500);

         await waitFor(() => expect(showDialogSpy).toHaveBeenCalled());
         expect(showCreateSpy).not.toHaveBeenCalled();
         expect(comp.loading).toBe(false);
      });

      it("processAnalyzeResult should go straight to the create page when no exception is present", () => {
         const { comp } = createAnalyzeMVDialog({});
         const showCreateSpy = vi.spyOn(comp, "showCreateMVPage").mockImplementation(() => {});

         comp.processAnalyzeResult({ completed: true, exception: false, status: [] } as never, 500);

         expect(showCreateSpy).toHaveBeenCalledWith(
            expect.objectContaining({ completed: true, exception: false }),
         );
         expect(comp.loading).toBe(false);
      });
   });

   describe("Group 4 - create and plan dialogs", () => {
      it("create should POST with a generated uuid, refresh on completion, and clear loading", async () => {
         server.use(
            http.get("*/api/em/security/get-enable-security", () =>
               HttpResponse.json({ enable: false }),
            ),
            http.post("*/api/em/content/repository/mv/create", () =>
               HttpResponse.json({ complete: true }),
            ),
         );
         const { comp } = createAnalyzeMVDialog({});
         comp.analysisId = "analysis-1";
         const refreshSpy = vi.spyOn(comp, "refresh").mockImplementation(() => {});
         vi.spyOn(comp, "okClicked").mockImplementation(() => {});
         vi.spyOn(ComponentTool, "showMessageDialog").mockReturnValue(Promise.resolve());
         const uuidSpy = vi.spyOn(Tool, "generateRandomUUID").mockReturnValue("create-1");

         comp.create({ mvNames: ["mv_one"], cycle: "daily" } as never);

         await waitFor(() => expect(refreshSpy).toHaveBeenCalledWith("ALL"));
         expect(comp.loading).toBe(false);
         expect(uuidSpy).toHaveBeenCalledWith();
      });

      it("create should show the completion info message and commit after the request succeeds", async () => {
         const messageSpy = vi.spyOn(ComponentTool, "showMessageDialog")
            .mockReturnValue(Promise.resolve());
         server.use(
            http.get("*/api/em/security/get-enable-security", () =>
               HttpResponse.json({ enable: false }),
            ),
            http.post("*/api/em/content/repository/mv/create", () =>
               HttpResponse.json({ complete: true }),
            ),
         );
         const { comp } = createAnalyzeMVDialog({});
         comp.analysisId = "analysis-1";
         vi.spyOn(comp, "refresh").mockImplementation(() => {});
         const okSpy = vi.spyOn(comp, "okClicked").mockImplementation(() => {});
         vi.spyOn(Tool, "generateRandomUUID").mockReturnValue("create-1");

         comp.create({ mvNames: ["mv_one"], cycle: "daily" } as never);

         await waitFor(() =>
            expect(messageSpy).toHaveBeenCalledWith(
               expect.anything(),
               "_#(js:Info)",
               "_#(js:em.alert.createMV)",
            ),
         );
         await Promise.resolve();
         expect(okSpy).toHaveBeenCalledWith();
      });

      it("create should show the backend error and stop loading on failure", async () => {
         const messageSpy = vi.spyOn(ComponentTool, "showMessageDialog")
            .mockReturnValue(Promise.resolve());
         server.use(
            http.get("*/api/em/security/get-enable-security", () =>
               HttpResponse.json({ enable: false }),
            ),
            http.post("*/api/em/content/repository/mv/create", () =>
               HttpResponse.json({ message: "create failed" }, { status: 500 }),
            ),
         );
         const { comp } = createAnalyzeMVDialog({});
         comp.analysisId = "analysis-1";

         comp.create({ mvNames: ["mv_one"] } as never);

         await waitFor(() =>
            expect(messageSpy).toHaveBeenCalledWith(
               expect.anything(),
               "_#(js:Error)",
               "create failed",
            ),
         );
         expect(comp.loading).toBe(false);
      });

      it("showPlan should render the returned optimize plan", async () => {
         const messageSpy = vi.spyOn(ComponentTool, "showMessageDialog")
            .mockReturnValue(Promise.resolve());
         server.use(
            http.get("*/api/em/security/get-enable-security", () =>
               HttpResponse.json({ enable: false }),
            ),
            http.post("*/api/em/content/repository/mv/show-plan/*", () =>
               HttpResponse.json("PLAN TEXT"),
            ),
         );
         const { comp } = createAnalyzeMVDialog({});
         comp.analysisId = "analysis-1";

         comp.showPlan({ mvNames: ["mv_one"] } as never);

         await waitFor(() =>
            expect(messageSpy).toHaveBeenCalledWith(
               expect.anything(),
               "_#(js:Optimize Plan)",
               "PLAN TEXT",
            ),
         );
      });
   });

   describe("Group 5 - child delegates", () => {
      it("createOrUpdate should delegate to the create pane when it exists", () => {
         const { comp } = createAnalyzeMVDialog({});
         const pane = attachAnalyzeMVCreatePane(comp);

         comp.createOrUpdate();

         expect(pane.createOrUpdate).toHaveBeenCalled();
      });

      it("selectedMVsChanged should require at least one selected model", () => {
         const { comp } = createAnalyzeMVDialog({});

         comp.selectedMVsChanged(["mv_one"]);
         expect(comp.canCreateOrUpdate).toBe(true);

         comp.selectedMVsChanged([]);
         expect(comp.canCreateOrUpdate).toBe(false);
      });

      it("showPlanClicked should delegate to the create pane when it exists", () => {
         const { comp } = createAnalyzeMVDialog({});
         const pane = attachAnalyzeMVCreatePane(comp);

         comp.showPlanClicked();

         expect(pane.showPlanClicked).toHaveBeenCalled();
      });
   });

   describe("Group 6 - outputs and repository changes", () => {
      it("okClicked should emit the ok commit token", () => {
         const { comp } = createAnalyzeMVDialog({});
         const commitSpy = vi.spyOn(comp.onCommit, "emit");

         comp.okClicked();

         expect(commitSpy).toHaveBeenCalledWith("ok");
      });

      it("closeDialog should emit the cancel token", () => {
         const { comp } = createAnalyzeMVDialog({});
         const cancelSpy = vi.spyOn(comp.onCancel, "emit");

         comp.closeDialog();

         expect(cancelSpy).toHaveBeenCalledWith("cancel");
      });

      it("onRepositoryChanged should show the repository-changed dialog and emit cancel when an asset disappears", async () => {
         const messageSpy = vi.spyOn(ComponentTool, "showMessageDialog")
            .mockReturnValue(Promise.resolve());
         server.use(
            http.get("*/api/em/security/get-enable-security", () =>
               HttpResponse.json({ enable: false }),
            ),
            http.get("*/api/em/content/repository/asset-exists", () =>
               HttpResponse.json(false),
            ),
         );
         const { comp } = createAnalyzeMVDialog({});
         const privateApi = asAnalyzeMVDialogPrivateApi(comp);
         const cancelSpy = vi.spyOn(comp.onCancel, "emit");
         comp.selectedNodes = [makeAnalyzeMVSelectedNode() as never];

         privateApi.onRepositoryChanged();

         await waitFor(() => expect(cancelSpy).toHaveBeenCalledWith("cancel"));
         expect(messageSpy).toHaveBeenCalledWith(
            expect.anything(),
            "_#(js:Repository Changed)",
            "_#(js:em.repository.viewsheet.expiredMVDialog)",
         );
      });

      it("ngOnDestroy should stop future repository-change callbacks", async () => {
         vi.useFakeTimers();
         server.use(
            http.get("*/api/em/security/get-enable-security", () =>
               HttpResponse.json({ enable: false }),
            ),
         );
         const { comp, repositoryClient } = createAnalyzeMVDialog({});
         const privateApi = asAnalyzeMVDialogPrivateApi(comp);
         const onRepositoryChangedSpy = vi.spyOn(privateApi, "onRepositoryChanged").mockImplementation(() => {});

         comp.ngOnDestroy();
         repositoryClient.repositoryChanged$.next({});
         vi.advanceTimersByTime(200);

         expect(onRepositoryChangedSpy).not.toHaveBeenCalled();
      });
   });
});
