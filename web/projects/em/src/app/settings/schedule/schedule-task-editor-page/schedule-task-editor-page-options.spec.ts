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
import { NgIf } from "@angular/common";
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { ReactiveFormsModule } from "@angular/forms";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { By } from "@angular/platform-browser";
import { ActivatedRoute, Router } from "@angular/router";
import { BehaviorSubject, of } from "rxjs";
import { ScheduleTaskDialogModel } from "../../../../../../shared/schedule/model/schedule-task-dialog-model";
import { TaskOptionsPaneModel } from "../../../../../../shared/schedule/model/task-options-pane-model";
import { ScheduleTaskNamesService } from "../../../../../../shared/schedule/schedule-task-names.service";
import { ScheduleUsersService } from "../../../../../../shared/schedule/schedule-users.service";
import { TimeZoneService } from "../../../../../../shared/schedule/time-zone.service";
import { Tool } from "../../../../../../shared/util/tool";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { KEY_DELIMITER } from "../../security/users/identity-id";
import { ExecuteAsType, TaskOptionsPane } from "../task-options-pane/task-options-pane.component";
import { ScheduleTaskEditorDataService } from "./schedule-task-editor-data.service";
import { ScheduleTaskEditorPageComponent } from "./schedule-task-editor-page.component";

const ORG = "orgc";

function options(over: Partial<TaskOptionsPaneModel> = {}): TaskOptionsPaneModel {
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
      idName: null,
      idType: ExecuteAsType.USER,
      organizationName: null,
      timeZone: "UTC",
      ...over
   };
}

function dialogModel(opts: TaskOptionsPaneModel): ScheduleTaskDialogModel {
   return {
      name: "task1",
      label: "task1",
      internalTask: false,
      timeZone: "UTC",
      timeZoneOptions: [{ timeZoneId: "UTC", label: "UTC" }],
      taskConditionPaneModel: { conditions: [{ label: "Cond 1", conditionType: "TimeCondition" }] },
      taskActionPaneModel: {
         actions: [{ label: "Action 1", actionType: "ViewsheetAction", actionClass: "GeneralActionModel" }]
      },
      taskOptionsPaneModel: opts
   } as any;
}

/**
 * Bug #77512: the editor page with a real TaskOptionsPane child (logic only). The page passes
 * the owner the task was loaded or last saved with to the pane, which accepts it as valid.
 */
describe("ScheduleTaskEditorPageComponent Options tab (Bug #77512)", () => {
   let fixture: ComponentFixture<ScheduleTaskEditorPageComponent>;
   let page: ScheduleTaskEditorPageComponent;
   let pane: TaskOptionsPane;
   let saved: any[];
   let saveResult: (payload: any) => ScheduleTaskDialogModel;

   function create(loaded: ScheduleTaskDialogModel) {
      saved = [];
      saveResult = payload => dialogModel({ ...payload.options });

      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule, ScheduleTaskEditorPageComponent],
         providers: [
            {
               provide: ScheduleTaskEditorDataService,
               useValue: {
                  loadTask: () => of(Tool.clone(loaded)),
                  saveTask: (payload: any) => {
                     saved.push(Tool.clone(payload));
                     return of(saveResult(payload));
                  }
               }
            },
            { provide: ActivatedRoute, useValue: { params: of({ task: "task1" }) } },
            { provide: Router, useValue: { navigate: vi.fn() } },
            { provide: MatDialog, useValue: { open: vi.fn() } },
            { provide: MatSnackBar, useValue: { open: vi.fn() } },
            { provide: PageHeaderService, useValue: { title: "", currentOrgId: ORG } },
            { provide: TimeZoneService, useValue: { updateTimeZoneOptions: (tz: any) => tz } },
            { provide: ScheduleTaskNamesService, useValue: { loadScheduleTaskNames: vi.fn() } },
            {
               // org admin dave is the editor
               provide: ScheduleUsersService,
               useValue: {
                  getOwners: () => new BehaviorSubject([
                     { identityID: { name: "dave", orgID: ORG } },
                     { identityID: { name: "erin", orgID: ORG } }
                  ]),
                  getAdminName: () => new BehaviorSubject("dave" + KEY_DELIMITER + ORG),
                  getSSOEnable: () => new BehaviorSubject(false)
               }
            }
         ]
      });
      // keep the real editor page template and the real pane logic; other children are inert
      TestBed.overrideComponent(ScheduleTaskEditorPageComponent, {
         set: { imports: [NgIf, ReactiveFormsModule, TaskOptionsPane], schemas: [NO_ERRORS_SCHEMA] }
      });
      TestBed.overrideComponent(TaskOptionsPane, { set: { template: "", imports: [] } });

      fixture = TestBed.createComponent(ScheduleTaskEditorPageComponent);
      page = fixture.componentInstance;
      fixture.detectChanges();
      TestBed.inject(HttpTestingController).expectOne("../api/em/navbar/organization").flush(ORG);
      pane = fixture.debugElement.query(By.directive(TaskOptionsPane)).componentInstance;
   }

   function editorPanel() {
      return fixture.debugElement.query(By.css("em-editor-panel"));
   }

   function saveEnabled(): boolean {
      // the page template binds [applyDisabled]="!(valid && taskChanged)" on the Save button
      expect(editorPanel().nativeElement.applyDisabled).toBe(!(page.valid && page.taskChanged));
      return !editorPanel().nativeElement.applyDisabled;
   }

   function save() {
      editorPanel().triggerEventHandler("applyClicked", null);   // (applyClicked)="save()"
      fixture.detectChanges();
   }

   function editDescription(text: string) {
      pane.optionsForm.get("description").setValue(text);
      pane.fireModelChanged();
      fixture.detectChanges();   // the page re-binds [model] to the pane's emitted copy
   }

   function changeOwner(name: string) {
      pane.optionsForm.get("owner").setValue(name);
      pane.fireModelChanged();
      fixture.detectChanges();
   }

   it("enables Save after a description edit of a placeholder-owner task and saves no execute-as", () => {
      create(dialogModel(options()));
      expect(pane.originalOwner).toBe("admin");

      editDescription("new description");
      expect(page.model.taskOptionsPaneModel.description).toBe("new description");
      expect(saveEnabled()).toBe(true);

      // a second edit after the [model] re-bind is still valid
      editDescription("newer description");
      expect(saveEnabled()).toBe(true);

      save();
      expect(saved.length).toBe(1);
      expect(saved[0].options.owner).toBe("admin");
      expect(saved[0].options.description).toBe("newer description");
      expect(saved[0].options.idName).toBeNull();
      // the placeholder owner is shown as the Execute As fallback, after the save re-bind too
      expect(pane.executeAsFallback).toBe("admin");
   });

   it("sends a stored execute-as identity back unchanged", () => {
      create(dialogModel(options({ owner: "dave", idName: "dave" })));
      editDescription("new description");
      save();
      expect(saved[0].options.idName).toBe("dave");
   });

   it("saves no execute-as after Clear", () => {
      create(dialogModel(options({ owner: "dave", idName: "sales" + KEY_DELIMITER + ORG,
                                   idType: ExecuteAsType.GROUP, organizationName: ORG })));
      pane.clearUser();
      fixture.detectChanges();
      expect(saveEnabled()).toBe(true);
      expect(pane.optionsForm.get("executeAs").value || null).toBeNull();
      // the owner is shown as the Execute As fallback only (#78038)
      expect(pane.executeAsFallback).toBe("dave");

      save();
      expect(saved[0].options.idName).toBeNull();
      expect(saved[0].options.idType).toBe(ExecuteAsType.USER);
   });

   it("keeps an owner changed to a non-existent user invalid (#75632)", () => {
      create(dialogModel(options()));
      changeOwner("nobody");
      expect(saveEnabled()).toBe(false);

      changeOwner("admin");
      expect(saveEnabled()).toBe(true);
   });

   it("uses the saved owner as the original owner after a successful save", () => {
      create(dialogModel(options()));
      changeOwner("dave");
      expect(saveEnabled()).toBe(true);

      save();   // the page re-binds [model] and [originalOwner] to the saved task
      expect(pane.originalOwner).toBe("dave");
      expect(page.taskChanged).toBe(false);

      // the stored owner is now dave, so the old placeholder owner is a change and is checked
      changeOwner("admin");
      expect(pane.optionsForm.get("owner").errors).toEqual({ invalid: true });
      expect(saveEnabled()).toBe(false);

      changeOwner("dave");
      expect(saveEnabled()).toBe(true);
   });
});
