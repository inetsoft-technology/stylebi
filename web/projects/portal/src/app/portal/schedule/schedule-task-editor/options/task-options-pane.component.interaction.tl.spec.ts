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
 * TaskOptionsPane - Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - constructor subscriptions, locale, and execute-as display logic
 *   Group 2 [Risk 3] - start/end date mutation and form validator wiring
 *   Group 3 [Risk 2] - execute-as enablement, update, dialog selection, and save flow
 *   Group 4 [Risk 2] - initForm subscriptions and loading state
 *
 * Mocking strategy:
 *   - direct HttpClient consumer -> provideHttpClient() + service stubs
 *   - execute-as dialog -> ComponentTool.showDialog spy
 */

import { provideHttpClient } from "@angular/common/http";
import { FormBuilder } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { IdentityType } from "../../../../../../../shared/data/identity-type";
import { ScheduleUsersService } from "../../../../../../../shared/schedule/schedule-users.service";
import { ComponentTool } from "../../../../common/util/component-tool";
import { TaskOptionsPane } from "./task-options-pane.component";
import {
   createScheduleUsersStub,
   createTaskOptionsPane,
   makeIdentityId,
   makeIdentityIdWithLabel,
   makeTaskOptionsPaneModel,
   makeTimeZoneModel,
} from "./task-options-pane.component.test-helpers";

async function renderTaskOptionsPaneDom() {
   const usersService = createScheduleUsersStub();
   usersService.adminName$.next("admin");
   const formBuilder = new FormBuilder();
   const model = makeTaskOptionsPaneModel({
      locale: null,
      startFrom: new Date(2024, 0, 1).getTime(),
      stopOn: new Date(2024, 0, 2).getTime(),
   });

   const result = await render(TaskOptionsPane, {
      providers: [
         { provide: ScheduleUsersService, useValue: usersService },
         { provide: NgbModal, useValue: { open: vi.fn() } },
         provideHttpClient(),
      ],
      componentInputs: {
         model,
         taskName: "Nightly",
         parentForm: formBuilder.group({}),
         timeZoneOptions: [makeTimeZoneModel({ timeZoneId: "UTC" })],
      },
   });

   return {
      fixture: result.fixture,
      comp: result.fixture.componentInstance,
      nativeElement: result.fixture.nativeElement as HTMLElement,
   };
}

afterEach(() => {
   vi.restoreAllMocks();
});

describe("TaskOptionsPane - interaction", () => {
   describe("Group 1 - init and display state", () => {
      it("should resolve the default locale, owner display name, execute-as type, and constructor streams", () => {
         const usersService = createScheduleUsersStub();
         usersService.owners$.next([makeIdentityIdWithLabel("alice", "Alice")]);
         usersService.groups$.next([makeIdentityId("admins")]);
         usersService.adminName$.next("admin");
         const { comp, parentForm } = createTaskOptionsPane({ usersService });
         comp.parentForm = parentForm;
         comp.model = makeTaskOptionsPaneModel({
            locale: null,
            owner: "alice",
            ownerAlias: "Alice",
            idName: "alice",
            idAlias: "Alice",
            idType: IdentityType.USER,
            securityEnabled: true,
         });

         comp.ngOnInit();

         expect(comp.locale).toBe("Default");
         expect(comp.executeAsName).toBe("Alice");
         expect(comp.getExecuteAsType()).toBe("_#(js:User)");
         expect(comp.executeAsType).toBe(IdentityType.USER);
         expect(comp.owners).toEqual([expect.objectContaining({ label: "Alice" })]);
         expect(comp.groups).toEqual([expect.objectContaining({ name: "admins" })]);
         expect(comp.adminName).toBe("admin");
      });

      it("should map a custom locale back into the model and hide execute-as type when security is disabled", () => {
         const { comp, parentForm } = createTaskOptionsPane();
         comp.parentForm = parentForm;
         comp.model = makeTaskOptionsPaneModel({
            locale: "fr_FR",
            owner: "anonymous",
            securityEnabled: false,
         });

         comp.ngOnInit();
         comp.adminName = "admin";
         comp.locale = "Default";

         expect(comp.locale).toBe("Default");
         expect(comp._model.locale).toBeNull();
         expect(comp.getExecuteAsType()).toBe("");
      });

      it("should render the locale select with Default selected when the model locale is null", async () => {
         const { nativeElement } = await renderTaskOptionsPaneDom();
         const localeSelect = nativeElement.querySelectorAll("select")[2] as HTMLSelectElement;

         expect(localeSelect.value).toBe("Default");
      });
   });

   describe("Group 2 - dates and validator wiring", () => {
      it("should update start and end timestamps from date changes", () => {
         const { comp, parentForm } = createTaskOptionsPane();
         comp.parentForm = parentForm;
         comp.model = makeTaskOptionsPaneModel();
         comp.ngOnInit();

         comp.startDateChange({ year: 2024, month: 2, day: 10 });
         comp.endDateChange({ year: 2024, month: 2, day: 11 });

         expect(comp.startDate).toEqual({ year: 2024, month: 2, day: 10 });
         expect(comp.endDate).toEqual({ year: 2024, month: 2, day: 11 });
         expect(comp._model.startFrom).toBe(new Date(2024, 1, 10, 0, 0, 0).getTime());
         expect(comp._model.stopOn).toBe(new Date(2024, 1, 11, 0, 0, 0).getTime());
      });

      it("should surface the dateGreaterThan validator when the start date is not before the end date", () => {
         const { comp, parentForm } = createTaskOptionsPane();
         comp.parentForm = parentForm;
         comp.model = makeTaskOptionsPaneModel();
         comp.ngOnInit();

         comp.form.get("start").setValue({ year: 2024, month: 11, day: 8 });
         comp.form.get("stop").setValue({ year: 2024, month: 11, day: 7 });

         expect(comp.form.errors).toEqual({ dateGreaterThan: true });

         comp.form.get("stop").setValue({ year: 2024, month: 11, day: 10 });
         expect(comp.form.errors).toBeNull();
      });

      it("should render the stop-after-start validation message in the template", async () => {
         const { comp, fixture, nativeElement } = await renderTaskOptionsPaneDom();

         comp.form.get("start").setValue({ year: 2024, month: 11, day: 8 });
         comp.form.get("stop").setValue({ year: 2024, month: 11, day: 7 });
         fixture.detectChanges();

         const feedback = nativeElement.querySelector(".invalid-feedback") as HTMLElement;

         expect(comp.form.getError("dateGreaterThan")).toBe(true);
         expect(feedback.textContent?.trim()).toBe("_#(stop.after.start.date)");
      });
   });

   describe("Group 3 - execute as and save", () => {
      it("should disable execute-as when the user list is still loading or the task cannot impersonate", () => {
         const usersService = createScheduleUsersStub();
         usersService.isLoading = true;
         const { comp, parentForm } = createTaskOptionsPane({ usersService });
         comp.parentForm = parentForm;
         comp.model = makeTaskOptionsPaneModel({
            securityEnabled: true,
            selfOrg: false,
         });
         comp.ngOnInit();

         expect(comp.loadingUsers).toBe(true);
         expect(comp.disableExecuteAs()).toBe(true);

         usersService.isLoading = false;
         comp._model.selfOrg = true;
         expect(comp.disableExecuteAs()).toBe(true);
      });

      it("should update execute-as names, clear owner passthrough values, and open the picker dialog", () => {
         const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockImplementation((_modal, _comp, onCommit) => {
            onCommit({ name: "admins", type: IdentityType.GROUP, alias: undefined });
            return { users: [], groups: [], type: IdentityType.GROUP } as never;
         });
         const { comp, parentForm } = createTaskOptionsPane();
         comp.parentForm = parentForm;
         comp.model = makeTaskOptionsPaneModel({
            owner: "alice",
            securityEnabled: true,
            selfOrg: false,
         });
         comp.groups = [makeIdentityId("admins")];
         comp.ngOnInit();
         comp.adminName = "admin";
         comp.executeAsName = "alice";

         comp.updateExecuteAs("alice");
         expect(comp._model.idName).toBeNull();

         comp.updateExecuteAs("admins");
         expect(comp._model.idName).toBe("admins");

         comp.openExecuteAsDialog();

         expect(showDialogSpy).toHaveBeenCalled();
         expect(comp._model.idName).toBe("admins");
         expect(comp._model.idType).toBe(IdentityType.GROUP);
         expect(comp.executeAsName).toBe("admins");
      });

      it("should mark the form pristine and emit updateTaskName after save resolves", async () => {
         const { comp, parentForm } = createTaskOptionsPane();
         comp.parentForm = parentForm;
         comp.taskName = "Nightly";
         comp.model = makeTaskOptionsPaneModel();
         comp.saveTask = vi.fn(() => Promise.resolve({}));
         const updateTaskNameSpy = vi.spyOn(comp.updateTaskName, "emit");
         comp.ngOnInit();
         comp.form.markAsDirty();

         comp.save();
         await Promise.resolve();

         expect(comp.saveTask).toHaveBeenCalled();
         expect(comp.form.pristine).toBe(true);
         expect(updateTaskNameSpy).toHaveBeenCalledWith("Nightly");
      });
   });

   describe("Group 4 - form subscriptions", () => {
      it("should initialize form values from the model and sync timeZone changes back to the model", () => {
         const { comp, parentForm } = createTaskOptionsPane();
         comp.parentForm = parentForm;
         comp.timeZoneOptions = [makeTimeZoneModel({ timeZoneId: "UTC" })];
         comp.model = makeTaskOptionsPaneModel({
            startFrom: new Date(2024, 0, 1).getTime(),
            stopOn: new Date(2024, 0, 2).getTime(),
            timeZone: "UTC",
         });

         comp.ngOnInit();
         comp.form.get("timeZone").setValue("America/New_York");

         expect(comp.form.get("start").value).toEqual({ year: 2024, month: 1, day: 1 });
         expect(comp.form.get("stop").value).toEqual({ year: 2024, month: 1, day: 2 });
         expect(comp._model.timeZone).toBe("America/New_York");
      });
   });
});
