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
 * ScheduleTaskEditorComponent - Pass 2: Risk
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - saveSuccess notification and originalModel update
 *   Group 2 [Risk 3] - executeAsGroup getter
 *   Group 3 [Risk 3] - onCancelTask confirm, remove success, and remove failure paths
 *
 * Mocking strategy:
 *   - direct HttpClient consumer -> provideHttpClient() in helper, then per-test spies on HttpClient.post
 *   - ComponentTool confirm/error dialogs are spied per-test; no real modal flow is required
 */

import { of, throwError } from "rxjs";

import { IdentityType } from "../../../../../../shared/data/identity-type";
import { ComponentTool } from "../../../common/util/component-tool";
import {
   attachScheduleTaskNotifications,
   createScheduleTaskEditor,
   makeScheduleTaskDialogModel,
   makeTaskOptionsPaneModel,
} from "./schedule-task-editor.component.test-helpers";

afterEach(() => {
   vi.restoreAllMocks();
});

describe("ScheduleTaskEditorComponent - risk", () => {
   describe("Group 1 - saveSuccess", () => {
      it("should emit the save success notification and point originalModel at the current model", () => {
         const { comp } = createScheduleTaskEditor();
         const notifications = attachScheduleTaskNotifications(comp);
         const model = makeScheduleTaskDialogModel({ label: "Saved Task" });
         comp.model = model;

         comp.saveSuccess();

         expect(notifications.success).toHaveBeenCalledWith("_#(js:em.schedule.task.saveSuccess)");
         expect(comp.originalModel).toBe(model);
      });
   });

   describe("Group 2 - executeAsGroup", () => {
      it("should return true when idName is set and idType is GROUP", () => {
         const { comp } = createScheduleTaskEditor();
         comp.model = makeScheduleTaskDialogModel({
            taskOptionsPaneModel: makeTaskOptionsPaneModel({
               idName: "admins",
               idType: IdentityType.GROUP,
            }),
         });

         expect(comp.executeAsGroup).toBe(true);
      });

      it("should return false when idType is USER even though idName is set", () => {
         const { comp } = createScheduleTaskEditor();
         comp.model = makeScheduleTaskDialogModel({
            taskOptionsPaneModel: makeTaskOptionsPaneModel({
               idName: "admins",
               idType: IdentityType.USER,
            }),
         });

         expect(comp.executeAsGroup).toBe(false);
      });

      it("should return false when idName is null even though idType is GROUP", () => {
         const { comp } = createScheduleTaskEditor();
         comp.model = makeScheduleTaskDialogModel({
            taskOptionsPaneModel: makeTaskOptionsPaneModel({
               idName: null,
               idType: IdentityType.GROUP,
            }),
         });

         expect(comp.executeAsGroup).toBe(false);
      });
   });

   describe("Group 3 - onCancelTask", () => {
      it("should do nothing when the user cancels the confirmation dialog", async () => {
         const { comp, http } = createScheduleTaskEditor();
         comp.model = makeScheduleTaskDialogModel();
         const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");
         const postSpy = vi.spyOn(http, "post");

         comp.onCancelTask();
         await Promise.resolve();

         expect(confirmSpy).toHaveBeenCalledWith(
            expect.anything(),
            "_#(js:Confirm)",
            "_#(js:portal.schedule.cancel.confirm)",
         );
         expect(postSpy).not.toHaveBeenCalled();
      });

      it("should post the remove payload, preserve the model, and close the editor when confirmation succeeds", async () => {
         const { comp, http, router } = createScheduleTaskEditor();
         comp.model = makeScheduleTaskDialogModel({
            name: "owner:Nightly",
            label: "Nightly",
         });
         comp.returnPath = "/folder/tasks";
         vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
         const postSpy = vi.spyOn(http, "post").mockReturnValue(of(null));

         comp.onCancelTask();
         await Promise.resolve();

         expect(postSpy).toHaveBeenCalledWith("../api/portal/schedule/remove", [
            expect.objectContaining({
               name: "owner:Nightly",
               label: "Nightly",
               owner: { name: "owner", orgID: null },
               enabled: true,
               editable: true,
               removable: true,
            }),
         ]);
         expect(comp.originalModel).toBe(comp.model);
         expect(router.navigate).toHaveBeenCalledWith(
            ["/portal/tab/schedule/tasks"],
            { queryParams: { path: "/folder/tasks" } },
         );
      });

      it("should surface remove failures through ComponentTool.showHttpError", async () => {
         const { comp, http } = createScheduleTaskEditor();
         const error = { status: 500, error: "failed" };
         comp.model = makeScheduleTaskDialogModel({ name: "owner:Nightly" });
         vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
         const errorSpy = vi.spyOn(ComponentTool, "showHttpError").mockImplementation(() => {});
         vi.spyOn(http, "post").mockReturnValue(throwError(() => error));

         comp.onCancelTask();
         await Promise.resolve();

         expect(errorSpy).toHaveBeenCalledWith(
            "_#(js:em.schedule.task.removeFailed)",
            error,
            expect.anything(),
         );
      });
   });
});
