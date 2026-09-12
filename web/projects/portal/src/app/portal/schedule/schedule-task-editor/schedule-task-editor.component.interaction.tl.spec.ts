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
 * ScheduleTaskEditorComponent - Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - resetConditionListView and name updates
 *   Group 2 [Risk 2] - updateOldTaskName variants and notification side effect
 *   Group 3 [Risk 2] - updateConditionModel, updateActionModel, updateOptionsModel cloning
 *   Group 4 [Risk 2] - onCloseEditor navigation with and without returnPath
 *
 * Mocking strategy:
 *   - direct HttpClient consumer -> provideHttpClient() in helper, but this pass does not hit HTTP
 *   - notifications are injected through the helper because @ViewChild is not created in direct tests
 */

import {
   attachScheduleTaskNotifications,
   createScheduleTaskEditor,
   makeScheduleConditionModel,
   makeScheduleTaskDialogModel,
   makeTaskActionPaneModel,
   makeTaskConditionPaneModel,
   makeTaskOptionsPaneModel,
} from "./schedule-task-editor.component.test-helpers";

afterEach(() => {
   vi.restoreAllMocks();
});

describe("ScheduleTaskEditorComponent - interaction", () => {
   describe("Group 1 - condition list and task name updates", () => {
      it("should set conditionListView to true only when there are multiple conditions", () => {
         const { comp } = createScheduleTaskEditor();
         comp.model = makeScheduleTaskDialogModel({
            taskConditionPaneModel: makeTaskConditionPaneModel({
               conditions: [makeScheduleConditionModel(), makeScheduleConditionModel({ label: "Hourly" })],
            }),
         });

         comp.resetConditionListView();
         expect(comp.conditionListView).toBe(true);

         comp.model.taskConditionPaneModel.conditions = [makeScheduleConditionModel()];
         comp.resetConditionListView();

         expect(comp.conditionListView).toBe(false);
      });

      it("should update the model label and patch the form when updateTaskName is called externally", () => {
         const { comp } = createScheduleTaskEditor();
         comp.model = makeScheduleTaskDialogModel();
         const patchSpy = vi.spyOn(comp.form, "patchValue");

         comp.updateTaskName("Weekly", true);

         expect(comp.model.label).toBe("Weekly");
         expect(patchSpy).toHaveBeenCalledWith({ name: "Weekly" });
      });

      it("should update the model label from the name control without patching the form again", () => {
         const { comp } = createScheduleTaskEditor();
         comp.model = makeScheduleTaskDialogModel({ label: "Nightly" });
         const patchSpy = vi.spyOn(comp.form, "patchValue");

         comp.form.get("name").setValue("Edited by Form");

         expect(comp.model.label).toBe("Edited by Form");
         expect(patchSpy).not.toHaveBeenCalled();
      });
   });

   describe("Group 2 - old task name handling", () => {
      it("should replace the stored task name directly when the old name has no owner prefix", () => {
         const { comp } = createScheduleTaskEditor();
         const notifications = attachScheduleTaskNotifications(comp);
         comp.model = makeScheduleTaskDialogModel({ name: "Nightly" });
         const patchSpy = vi.spyOn(comp.form, "patchValue");

         comp.updateOldTaskName("Weekly");

         expect(notifications.success).toHaveBeenCalledWith("_#(js:em.schedule.task.saveSuccess)");
         expect(comp.model.name).toBe("Weekly");
         expect(patchSpy).not.toHaveBeenCalled();
      });

      it("should keep the prefixed task name and patch the form when the old name already contains an owner prefix", () => {
         const { comp } = createScheduleTaskEditor();
         const notifications = attachScheduleTaskNotifications(comp);
         comp.model = makeScheduleTaskDialogModel({ name: "owner:Nightly" });
         const patchSpy = vi.spyOn(comp.form, "patchValue");

         comp.updateOldTaskName("Weekly");

         expect(notifications.success).toHaveBeenCalledWith("_#(js:em.schedule.task.saveSuccess)");
         expect(comp.model.name).toBe("owner:Nightly");
         expect(patchSpy).toHaveBeenCalledWith({ name: "Weekly" });
      });
   });

   describe("Group 3 - loaded pane models", () => {
      it("should clone the condition model into originalModel when updateConditionModel is called", () => {
         const { comp } = createScheduleTaskEditor();
         comp.originalModel = makeScheduleTaskDialogModel();
         const newConditionModel = makeTaskConditionPaneModel({
            conditions: [makeScheduleConditionModel({ label: "Monthly" })],
         });

         comp.updateConditionModel(newConditionModel);
         newConditionModel.conditions[0].label = "Mutated";

         expect(comp.originalModel.taskConditionPaneModel.conditions[0].label).toBe("Monthly");
      });

      it("should clone the action model into originalModel when updateActionModel is called", () => {
         const { comp } = createScheduleTaskEditor();
         comp.originalModel = makeScheduleTaskDialogModel();
         const newActionModel = makeTaskActionPaneModel({
            actions: [{ label: "Send Mail" } as never],
         });

         comp.updateActionModel(newActionModel);
         newActionModel.actions.push({ label: "Mutated" } as never);

         expect(comp.originalModel.taskActionPaneModel.actions).toEqual([{ label: "Send Mail" }]);
      });

      it("should clone the options model into originalModel when updateOptionsModel is called", () => {
         const { comp } = createScheduleTaskEditor();
         comp.originalModel = makeScheduleTaskDialogModel();
         const newOptionsModel = makeTaskOptionsPaneModel({
            description: "Updated",
         });

         comp.updateOptionsModel(newOptionsModel);
         newOptionsModel.description = "Mutated";

         expect(comp.originalModel.taskOptionsPaneModel.description).toBe("Updated");
      });
   });

   describe("Group 4 - close navigation", () => {
      it("should navigate back to the task list with a path query parameter when returnPath is set", () => {
         const { comp, router } = createScheduleTaskEditor();
         comp.returnPath = "/folder/tasks";

         comp.onCloseEditor();

         expect(router.navigate).toHaveBeenCalledWith(
            ["/portal/tab/schedule/tasks"],
            { queryParams: { path: "/folder/tasks" } },
         );
      });

      it("should navigate back to the task list without query parameters when returnPath is null or root", () => {
         const { comp, router } = createScheduleTaskEditor();
         comp.returnPath = "/";

         comp.onCloseEditor();

         expect(router.navigate).toHaveBeenCalledWith(["/portal/tab/schedule/tasks"]);
      });
   });
});
