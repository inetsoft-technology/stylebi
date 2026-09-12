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
 * TaskOptionsPane - Pass 2: Risk
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - model setter side effects for dates, time zone, and execute-as name
 *   Group 2 [Risk 2] - clearStartDate, clearEndDate, and clearUser destructive resets
 *   Group 3 [Risk 2] - getExecuteAsName edge cases and group model passthrough
 *
 * Mocking strategy:
 *   - direct instantiation with ScheduleUsersService subjects
 */

import {
   createTaskOptionsPane,
   makeTaskOptionsPaneModel,
} from "./task-options-pane.component.test-helpers";

afterEach(() => {
   vi.restoreAllMocks();
});

describe("TaskOptionsPane - risk", () => {
   describe("Group 1 - model setter", () => {
      it("should derive dates, local time zone fallback, execute-as name, and executeAsType from the model", () => {
         const resolvedTimeZone = vi.spyOn(Intl.DateTimeFormat.prototype, "resolvedOptions")
            .mockReturnValue({ timeZone: "Asia/Shanghai" } as Intl.ResolvedDateTimeFormatOptions);
         const { comp, parentForm } = createTaskOptionsPane();
         comp.parentForm = parentForm;
         comp.model = makeTaskOptionsPaneModel({
            startFrom: new Date(2024, 4, 5).getTime(),
            stopOn: new Date(2024, 4, 6).getTime(),
            timeZone: null,
            owner: "alice",
            ownerAlias: "Alice",
            idName: null,
            idType: 0,
         });
         comp.ngOnInit();

         expect(comp.startDate).toEqual({ year: 2024, month: 5, day: 5 });
         expect(comp.endDate).toEqual({ year: 2024, month: 5, day: 6 });
         expect(comp.localTimeZoneId).toBe("Asia/Shanghai");
         expect(comp.executeAsName).toBe("Alice");
         expect(comp.executeAsType).toBe(0);
         expect(resolvedTimeZone).toHaveBeenCalled();
      });
   });

   describe("Group 2 - clear methods", () => {
      it("should clear start and end dates from both the model and the form", () => {
         const { comp, parentForm } = createTaskOptionsPane();
         comp.parentForm = parentForm;
         comp.model = makeTaskOptionsPaneModel({
            startFrom: new Date(2024, 0, 1).getTime(),
            stopOn: new Date(2024, 0, 2).getTime(),
         });
         comp.ngOnInit();

         comp.clearStartDate();
         comp.clearEndDate();

         expect(comp.startDate).toBeNull();
         expect(comp.endDate).toBeNull();
         expect(comp._model.startFrom).toBe(0);
         expect(comp._model.stopOn).toBe(0);
         expect(comp.form.get("start").value).toBeNull();
         expect(comp.form.get("stop").value).toBeNull();
      });

      it("should clear execute-as state from both the display and the model", () => {
         const { comp, parentForm } = createTaskOptionsPane();
         comp.parentForm = parentForm;
         comp.model = makeTaskOptionsPaneModel({
            idName: "admins",
            idType: 1,
         });
         comp.ngOnInit();
         comp.executeAsName = "admins";

         comp.clearUser();

         expect(comp.executeAsName).toBe("");
         expect(comp._model.idName).toBeNull();
         expect(comp._model.idType).toBeNull();
      });
   });

   describe("Group 3 - execute as naming", () => {
      it("should hide execute-as names for admin users when security is disabled", () => {
         const { comp, parentForm } = createTaskOptionsPane();
         comp.parentForm = parentForm;
         comp.model = makeTaskOptionsPaneModel({
            owner: "alice",
            ownerAlias: "Alice",
            securityEnabled: false,
            idName: null,
         });
         comp.ngOnInit();
         comp.adminName = "admin";

         comp["getExecuteAsName"]();

         expect(comp.executeAsName).toBe("");
      });

      it("should prefer idAlias over idName and return the raw group model", () => {
         const { comp, parentForm } = createTaskOptionsPane();
         comp.parentForm = parentForm;
         comp.model = makeTaskOptionsPaneModel({
            idName: "admins",
            idAlias: "Admins",
            idType: 1,
            securityEnabled: true,
         });
         comp.groups = [expect.anything()] as any;
         comp.ngOnInit();
         comp.adminName = null;

         comp["getExecuteAsName"]();

         expect(comp.executeAsName).toBe("Admins");
         expect(comp.getGroupModel()).toBe(comp.groups);
      });
   });
});
