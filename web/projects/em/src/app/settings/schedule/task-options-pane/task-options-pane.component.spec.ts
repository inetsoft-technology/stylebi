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
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { provideNativeDateAdapter } from "@angular/material/core";
import { MatDialog } from "@angular/material/dialog";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { BehaviorSubject, Observable, of, Subject } from "rxjs";
import { TaskOptionsPaneModel } from "../../../../../../shared/schedule/model/task-options-pane-model";
import { ScheduleUsersService } from "../../../../../../shared/schedule/schedule-users.service";
import { KEY_DELIMITER } from "../../security/users/identity-id";
import { ExecuteAsType, TaskOptionChanges, TaskOptionsPane } from "./task-options-pane.component";

const ORG = "orgc";

function owner(name: string) {
   return { identityID: { name, orgID: ORG }, label: null } as any;
}

function taskModel(over: Partial<TaskOptionsPaneModel> = {}): TaskOptionsPaneModel {
   return {
      enabled: true,
      deleteIfNotScheduledToRun: false,
      startFrom: 0,
      stopOn: 0,
      locale: null,
      locales: [],
      owner: "admin",          // placeholder owner: there is no user "admin" in orgc
      description: "old",
      securityEnabled: true,
      idName: null,            // no Execute As
      idType: ExecuteAsType.USER,
      organizationName: null,
      timeZone: "UTC",
      ...over
   };
}

/**
 * Bug #77512: the EM task Options pane disabled Save for a task whose owner the editor can't
 * administer (e.g. a placeholder owner), showed the owner as Execute As and saved it as an
 * explicit execute-as identity, and Clear on Execute As did nothing.
 */
describe("TaskOptionsPane owner validity and Execute As (Bug #77512)", () => {
   let dialogResult: any;
   let dialogData: any;

   function create(sso: boolean, model: TaskOptionsPaneModel,
                   adminName: Observable<string> = new BehaviorSubject("dave" + KEY_DELIMITER + ORG))
   {
      // org admin dave is the editor; adminName is set because the owner list isn't empty
      const usersService = {
         getOwners: () => new BehaviorSubject([owner("dave"), owner("erin")]),
         getAdminName: () => adminName,
         getSSOEnable: () => new BehaviorSubject(sso)
      };
      const dialog = {
         open: (_: any, config: any) => {
            dialogData = config.data;
            return { afterClosed: () => of(dialogResult) };
         }
      };

      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule, TaskOptionsPane],
         providers: [
            { provide: ScheduleUsersService, useValue: usersService },
            { provide: MatDialog, useValue: dialog }
         ]
      });
      TestBed.overrideComponent(TaskOptionsPane, { set: { template: "", imports: [] } });

      const fixture = TestBed.createComponent(TaskOptionsPane);
      TestBed.inject(HttpTestingController).expectOne("../api/em/navbar/organization").flush(ORG);
      const comp = fixture.componentInstance;
      comp.timeZoneOptions = [];
      comp.model = model;
      // what the editor page binds from its originalModel
      comp.originalOwner = model.owner;
      const emitted: TaskOptionChanges[] = [];
      comp.modelChanged.subscribe(c => emitted.push({ valid: c.valid, model: { ...c.model } }));
      return { comp, emitted };
   }

   function editDescription(comp: TaskOptionsPane) {
      comp.optionsForm.get("description").setValue("new description");
      comp.fireModelChanged();   // (change) on the textarea
   }

   function changeOwner(comp: TaskOptionsPane, name: string) {
      comp.optionsForm.get("owner").setValue(name);
      comp.fireModelChanged();
   }

   beforeEach(() => {
      dialogResult = undefined;
      dialogData = undefined;
   });

   describe("owner validity", () => {
      it("accepts an unchanged placeholder owner the editor can't administer", () => {
         const { comp } = create(false, taskModel());
         expect(comp.optionsForm.get("owner").errors).toBeNull();
      });

      it("emits valid for a description-only edit of a placeholder-owner task (non-SSO)", () => {
         const { comp, emitted } = create(false, taskModel());
         editDescription(comp);
         expect(emitted[0].valid).toBe(true);
      });

      it("emits valid when Execute As is set by the dialog on a placeholder-owner task (non-SSO)", () => {
         dialogResult = { idName: "dave", idType: ExecuteAsType.USER };
         const { comp, emitted } = create(false, taskModel());
         comp.openExecuteAsDialog();
         expect(emitted[0].model.idName).toBe("dave");
         expect(emitted[0].valid).toBe(true);
      });

      it("emits valid for an edit with SSO enabled (#75632 owner skip)", () => {
         const { comp, emitted } = create(true, taskModel());
         editDescription(comp);
         expect(emitted[0].valid).toBe(true);
      });

      it("emits valid for an edit with a real administered owner", () => {
         const { comp, emitted } = create(false, taskModel({ owner: "dave" }));
         editDescription(comp);
         expect(emitted[0].valid).toBe(true);
      });

      it("accepts an unchanged real owner the editor doesn't administer", () => {
         const { comp, emitted } = create(false, taskModel({ owner: "frank" }));
         editDescription(comp);
         expect(emitted[0].valid).toBe(true);
      });

      it("keeps an owner changed to a non-existent user invalid (#75632)", () => {
         const { comp, emitted } = create(false, taskModel({ owner: "dave" }));
         changeOwner(comp, "nobody");
         expect(emitted[0].valid).toBe(false);
      });

      it("keeps a placeholder owner changed to a non-existent user invalid", () => {
         const { comp, emitted } = create(false, taskModel());
         changeOwner(comp, "nobody");
         expect(emitted[0].valid).toBe(false);
      });

      it("accepts the owner again when it is changed back to the original", () => {
         const { comp, emitted } = create(false, taskModel());
         changeOwner(comp, "nobody");
         changeOwner(comp, "admin");
         expect(emitted.map(e => e.valid)).toEqual([false, true]);
      });

      it("keeps a changed owner invalid after the parent re-binds the edited model", () => {
         const { comp, emitted } = create(false, taskModel());
         changeOwner(comp, "nobody");
         comp.model = emitted[0].model;   // the editor page stores change.model, bound to [model]
         expect(comp.optionsForm.get("owner").errors).toEqual({ invalid: true });
      });

      it("re-validates the owner when the original owner input changes (after a save)", () => {
         const { comp } = create(false, taskModel());
         comp.optionsForm.get("owner").setValue("nobody");
         expect(comp.optionsForm.get("owner").errors).toEqual({ invalid: true });
         comp.originalOwner = "nobody";
         expect(comp.optionsForm.get("owner").errors).toBeNull();
         comp.originalOwner = "admin";
         expect(comp.optionsForm.get("owner").errors).toEqual({ invalid: true });
      });
   });

   describe("Execute As", () => {
      it("shows the owner only as a fallback when the task has no execute-as identity, and saves none (#78038)", () => {
         const { comp, emitted } = create(false, taskModel({ owner: "dave" }));
         // the owner is shown as the placeholder, never written into the control (#77512)
         expect(comp.executeAsFallback).toBe("dave");
         expect(comp.optionsForm.get("executeAs").value || null).toBeNull();
         editDescription(comp);
         expect(emitted[0].model.idName).toBeNull();
         expect(comp.executeAsFallback).toBe("dave");
      });

      it("shows the owner fallback for a new task (#78038)", () => {
         // a new task: the server sends the owner and no execute-as identity
         const { comp, emitted } = create(false, taskModel({ owner: "dave", description: null }));
         expect(comp.executeAsFallback).toBe("dave");
         comp.optionsForm.get("taskEnabled").setValue(false);
         comp.fireModelChanged();
         comp.model = emitted[0].model;   // the editor page re-binds the emitted copy
         editDescription(comp);
         expect(emitted.map(e => e.model.idName)).toEqual([null, null]);
         expect(comp.executeAsFallback).toBe("dave");
      });

      it("follows an owner change in the fallback and still saves no execute-as (#78038)", () => {
         const { comp, emitted } = create(false, taskModel({ owner: "dave" }));
         changeOwner(comp, "erin");
         expect(comp.executeAsFallback).toBe("erin");
         expect(emitted[0].model.idName).toBeNull();

         comp.model = emitted[0].model;   // the editor page re-binds the emitted copy
         expect(comp.executeAsFallback).toBe("erin");
         expect(comp.optionsForm.get("executeAs").value || null).toBeNull();
         editDescription(comp);
         expect(emitted[1].model.idName).toBeNull();
      });

      it("shows an explicit execute-as identity itself, without the owner fallback (#78038)", () => {
         const { comp } = create(false, taskModel({ owner: "dave", idName: "erin" }));
         expect(comp.optionsForm.get("executeAs").value).toBe("erin");
         expect(comp.executeAsFallback).toBeNull();
      });

      it("shows no owner fallback once the Execute As dialog picks an identity (#78038)", () => {
         dialogResult = { idName: "erin", idType: ExecuteAsType.USER };
         const { comp, emitted } = create(false, taskModel({ owner: "dave" }));
         comp.openExecuteAsDialog();
         expect(emitted[0].model.idName).toBe("erin");
         expect(comp.optionsForm.get("executeAs").value).toBe("erin");
         expect(comp.executeAsFallback).toBeNull();
      });

      it("keeps the owner fallback and saves nothing when the Execute As dialog is cancelled (#78038)", () => {
         dialogResult = undefined;
         const { comp, emitted } = create(false, taskModel({ owner: "dave" }));
         comp.openExecuteAsDialog();
         expect(dialogData.idName).toBeNull();
         expect(emitted.length).toBe(0);
         expect(comp.executeAsFallback).toBe("dave");
         editDescription(comp);
         expect(emitted[0].model.idName).toBeNull();
      });

      it("shows no owner fallback when security is disabled, even if the admin name arrives late (#78038)", () => {
         const admin = new Subject<string>();
         const { comp, emitted } = create(false, taskModel({ owner: "dave", securityEnabled: false }),
                                          admin);
         admin.next("dave" + KEY_DELIMITER + ORG);
         expect(comp.executeAsFallback).toBeNull();
         editDescription(comp);
         expect(emitted[0].model.idName).toBeNull();
      });

      it("keeps the anonymous execute-as blank, without the owner fallback (#78038)", () => {
         const { comp } = create(false, taskModel({ owner: "dave", idName: "anonymous" }));
         expect(comp.optionsForm.get("executeAs").value).toBe("");
         expect(comp.executeAsFallback).toBeNull();
      });

      it("sends a stored execute-as identity that names the owner back unchanged", () => {
         const { comp, emitted } = create(false, taskModel({ owner: "dave", idName: "dave" }));
         editDescription(comp);
         expect(emitted[0].model.idName).toBe("dave");
      });

      it("sends the real group name, not the display label without the organization", () => {
         const group = "sales" + KEY_DELIMITER + ORG;
         const { comp, emitted } = create(false, taskModel({
            owner: "dave", idName: group, idType: ExecuteAsType.GROUP, organizationName: ORG
         }));
         expect(comp.optionsForm.get("executeAs").value).toBe("sales");
         editDescription(comp);
         expect(emitted[0].model.idName).toBe(group);
      });

      it("passes the real identity to the Execute As dialog", () => {
         const group = "sales" + KEY_DELIMITER + ORG;
         const { comp } = create(false, taskModel({
            owner: "dave", idName: group, idType: ExecuteAsType.GROUP, organizationName: ORG
         }));
         comp.openExecuteAsDialog();
         expect(dialogData.idName).toBe(group);
      });

      it("sends a typed execute-as name", () => {
         const { comp, emitted } = create(false, taskModel({ owner: "dave", idName: "erin" }));
         comp.optionsForm.get("executeAs").setValue("frank");
         comp.fireModelChanged();   // (change) on the input
         expect(emitted[0].model.idName).toBe("frank");
      });

      it("notifies the editor with no execute-as on Clear", () => {
         const { comp, emitted } = create(false, taskModel({
            owner: "dave", idName: "sales", idType: ExecuteAsType.GROUP
         }));
         comp.clearUser();
         expect(emitted.length).toBe(1);
         expect(emitted[0].model.idName).toBeNull();
         expect(emitted[0].model.idType).toBe(ExecuteAsType.USER);
         expect(emitted[0].valid).toBe(true);
         expect(comp.optionsForm.get("executeAs").value || null).toBeNull();
         // the task runs as its owner again, shown as the fallback (#78038)
         expect(comp.executeAsFallback).toBe("dave");
      });

      it("shows only the owner fallback after Clear when the parent re-binds the edited model", () => {
         const { comp, emitted } = create(false, taskModel({ owner: "dave", idName: "erin" }));
         comp.clearUser();
         comp.model = emitted[0].model;
         expect(comp.optionsForm.get("executeAs").value || null).toBeNull();
         expect(comp.executeAsFallback).toBe("dave");
         editDescription(comp);
         expect(emitted[1].model.idName).toBeNull();
      });
   });
});

/**
 * Bug #78038: with the real template, the Execute As field of a task without an execute-as
 * identity shows the owner as a placeholder that is visible without focus.
 */
describe("TaskOptionsPane Execute As owner fallback rendering (Bug #78038)", () => {
   function render(model: TaskOptionsPaneModel) {
      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule, NoopAnimationsModule, TaskOptionsPane],
         providers: [
            {
               provide: ScheduleUsersService,
               useValue: {
                  getOwners: () => new BehaviorSubject([owner("dave"), owner("erin")]),
                  getAdminName: () => new BehaviorSubject("dave" + KEY_DELIMITER + ORG),
                  getSSOEnable: () => new BehaviorSubject(false)
               }
            },
            { provide: MatDialog, useValue: { open: vi.fn() } },
            provideNativeDateAdapter()
         ]
      });

      const fixture = TestBed.createComponent(TaskOptionsPane);
      TestBed.inject(HttpTestingController).expectOne("../api/em/navbar/organization").flush(ORG);
      fixture.componentInstance.timeZoneOptions = [];
      fixture.componentInstance.model = model;
      fixture.componentInstance.originalOwner = model.owner;
      fixture.detectChanges();
      return fixture;
   }

   function executeAsField(fixture: any) {
      const input: HTMLInputElement =
         fixture.nativeElement.querySelector("input[formcontrolname='executeAs']");
      return { input, field: input.closest("mat-form-field") as HTMLElement };
   }

   it("shows the owner as an always-visible placeholder when there is no execute-as identity", () => {
      const fixture = render(taskModel({ owner: "dave" }));
      const { input, field } = executeAsField(fixture);
      expect(input.value).toBe("");
      expect(input.placeholder).toBe("dave");
      // the label floats without focus, so the placeholder is visible
      expect(field.classList).toContain("mat-mdc-form-field-label-always-float");
   });

   it("shows an explicit execute-as identity as the value", () => {
      const fixture = render(taskModel({ owner: "dave", idName: "erin" }));
      const { input } = executeAsField(fixture);
      expect(input.value).toBe("erin");
      expect(input.placeholder).not.toBe("dave");
   });
});
