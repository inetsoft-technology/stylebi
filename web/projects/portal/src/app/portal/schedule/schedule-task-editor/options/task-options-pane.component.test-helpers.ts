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

import { HttpClient, provideHttpClient } from "@angular/common/http";
import { TestBed } from "@angular/core/testing";
import { FormBuilder, UntypedFormGroup } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { BehaviorSubject } from "rxjs";

import { IdentityIdWithLabel } from "../../../../../../../em/src/app/settings/security/users/idenity-id-with-label";
import { IdentityId } from "../../../../../../../em/src/app/settings/security/users/identity-id";
import { IdentityType } from "../../../../../../../shared/data/identity-type";
import { TaskOptionsPaneModel } from "../../../../../../../shared/schedule/model/task-options-pane-model";
import { TimeZoneModel } from "../../../../../../../shared/schedule/model/time-zone-model";
import { ScheduleUsersService } from "../../../../../../../shared/schedule/schedule-users.service";
import { TaskOptionsPane } from "./task-options-pane.component";

interface TaskOptionsScheduleUsersStub {
   owners$: BehaviorSubject<IdentityIdWithLabel[]>;
   groups$: BehaviorSubject<IdentityId[]>;
   adminName$: BehaviorSubject<string>;
   getOwners: ReturnType<typeof vi.fn>;
   getGroups: ReturnType<typeof vi.fn>;
   getAdminName: ReturnType<typeof vi.fn>;
   isLoading: boolean;
}

export function makeIdentityId(name: string): IdentityId {
   return {
      name,
      orgID: null,
   };
}

export function makeIdentityIdWithLabel(name: string, label?: string): IdentityIdWithLabel {
   return {
      identityID: makeIdentityId(name),
      label,
   };
}

export function makeTaskOptionsPaneModel(
   overrides: Partial<TaskOptionsPaneModel> = {},
): TaskOptionsPaneModel {
   return {
      enabled: true,
      deleteIfNotScheduledToRun: false,
      startFrom: 0,
      stopOn: 0,
      locale: null,
      locales: ["en_US", "fr_FR"],
      owner: "anonymous",
      description: "",
      securityEnabled: true,
      idName: null,
      idType: IdentityType.USER,
      idAlias: null,
      ownerAlias: null,
      organizationName: "Org",
      selfOrg: false,
      timeZone: "UTC",
      ...overrides,
   };
}

export function makeTimeZoneModel(
   overrides: Partial<TimeZoneModel> = {},
): TimeZoneModel {
   return {
      timeZoneId: "UTC",
      label: "UTC",
      hourOffset: "",
      minuteOffset: 0,
      ...overrides,
   };
}

export function createScheduleUsersStub(): TaskOptionsScheduleUsersStub {
   const owners$ = new BehaviorSubject<IdentityIdWithLabel[]>([]);
   const groups$ = new BehaviorSubject<IdentityId[]>([]);
   const adminName$ = new BehaviorSubject<string>(null);

   return {
      owners$,
      groups$,
      adminName$,
      getOwners: vi.fn(() => owners$.asObservable()),
      getGroups: vi.fn(() => groups$.asObservable()),
      getAdminName: vi.fn(() => adminName$.asObservable()),
      isLoading: false,
   };
}

export function createTaskOptionsPane(options: {
   usersService?: TaskOptionsScheduleUsersStub;
} = {}) {
   const usersService = options.usersService ?? createScheduleUsersStub();
   const modalService = { open: vi.fn() };

   TestBed.resetTestingModule();
   TestBed.configureTestingModule({
      providers: [
         provideHttpClient(),
         FormBuilder,
         { provide: NgbModal, useValue: modalService },
         { provide: ScheduleUsersService, useValue: usersService },
      ],
   });

   const comp = new TaskOptionsPane(
      TestBed.inject(NgbModal),
      TestBed.inject(ScheduleUsersService),
      TestBed.inject(HttpClient),
   );

   const formBuilder = TestBed.inject(FormBuilder);

   return {
      comp,
      usersService,
      modalService,
      parentForm: formBuilder.group({}),
   };
}
