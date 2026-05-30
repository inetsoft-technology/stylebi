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
 * ScheduleUsersService — unit tests
 *
 * Risk-first coverage (2 groups, 17 cases):
 *   Group 1 [Risk 3, 3, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2] — loadScheduleUsers (14 cases)
 *   Group 2 [Risk 2, 2, 3]                                    — ngOnDestroy (3 cases)
 *
 * Confirmed bugs (it.failing — remove wrapper once fixed):
 *   - ssoEnable BehaviorSubject is not completed in ngOnDestroy()
 *     Steps to reproduce: N/A — lifecycle defect verified by unit test only; no user-visible
 *     symptom under normal EM/Portal navigation.
 *
 * KEY contracts:
 *   - loadScheduleUsers() is re-entrant-safe: a second call while in-flight sets reload=true and defers
 *   - PORTAL=true → portal URL + STOMP em=false; PORTAL=false → EM URL + STOMP em=true
 *   - STOMP /user/schedule/users-change message triggers a fresh loadScheduleUsers() call
 *   - All BehaviorSubjects must be completed in ngOnDestroy to prevent subscriber leaks
 *
 * Design gaps:
 *   - HTTP error handler is empty (()=>{}); loading never resets — service is permanently stuck after a network error
 *   - @Optional PORTAL=null (token not provided) is semantically distinct from false but exercises the same code path
 */
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { of } from "rxjs";
import { IdentityId } from "../../em/src/app/settings/security/users/identity-id";
import { IdentityIdWithLabel } from "../../em/src/app/settings/security/users/idenity-id-with-label";
import { StompClientService } from "../stomp/stomp-client.service";
import { UsersModel } from "./model/users-model";
import { PORTAL, ScheduleUsersService } from "./schedule-users.service";

// ─── fixtures ────────────────────────────────────────────────────────────────

const EMPTY_USERS: UsersModel = {
   adminName: "admin",
   owners: [],
   groups: [],
   emailUsers: [],
   emailGroups: [],
   ssoEnable: false,
};

const FULL_USERS: UsersModel = {
   adminName: "siteAdmin",
   owners: [
      { identityID: { name: "alice", orgID: null }, label: "Alice" } as IdentityIdWithLabel,
   ],
   groups: [{ name: "analysts", orgID: null } as IdentityId],
   emailUsers: [{ name: "bob@example.com", orgID: null } as IdentityId],
   emailGroups: [{ name: "reports@example.com", orgID: null } as IdentityId],
   ssoEnable: true,
};

// ─── mock factory ────────────────────────────────────────────────────────────

function createMockStomp() {
   const mockConnection = {
      subscribe: vi.fn().mockReturnValue({ unsubscribe: vi.fn() }),
      disconnect: vi.fn(),
   };
   const mockStompClient = {
      connect: vi.fn().mockReturnValue(of(mockConnection)),
   };
   return { mockStompClient, mockConnection };
}

// ─── tests ───────────────────────────────────────────────────────────────────

describe("ScheduleUsersService", () => {
   // ---------------------------------------------------------------------------
   // Group 1 [Risk 3, 3, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2] — loadScheduleUsers (14 cases)
   // ---------------------------------------------------------------------------
   describe("loadScheduleUsers()", () => {
      describe("EM context (PORTAL=false)", () => {
         let service: ScheduleUsersService;
         let httpMock: HttpTestingController;
         let mockConnection: ReturnType<typeof createMockStomp>["mockConnection"];

         beforeEach(() => {
            const { mockStompClient, mockConnection: conn } = createMockStomp();
            mockConnection = conn;

            TestBed.configureTestingModule({
               imports: [HttpClientTestingModule],
               providers: [
                  ScheduleUsersService,
                  { provide: PORTAL, useValue: false },
                  { provide: StompClientService, useValue: mockStompClient },
               ],
            });
            service = TestBed.inject(ScheduleUsersService);
            httpMock = TestBed.inject(HttpTestingController);
         });

         afterEach(() => httpMock.verify());

         it("[Risk 2] requests the EM users-model endpoint", () => {
            const req = httpMock.expectOne("../api/em/schedule/users-model");
            expect(req.request.method).toBe("GET");
            req.flush(EMPTY_USERS);
         });

         it("[Risk 2] subscribes to /user/schedule/users-change on the STOMP connection", () => {
            httpMock.expectOne("../api/em/schedule/users-model").flush(EMPTY_USERS);
            expect(mockConnection.subscribe).toHaveBeenCalledWith(
               "/user/schedule/users-change",
               expect.any(Function),
            );
         });

         it("[Risk 2] isLoading is true while in-flight and false after completion", () => {
            expect(service.isLoading).toBe(true);
            httpMock.expectOne("../api/em/schedule/users-model").flush(EMPTY_USERS);
            expect(service.isLoading).toBe(false);
         });

         it("[Risk 3] reload mechanism: a call while in-flight defers and fires after the first completes", () => {
            // 🔁 Regression-sensitive: reload flag must be consumed exactly once per in-flight cycle
            service.loadScheduleUsers();
            httpMock.expectOne("../api/em/schedule/users-model").flush(EMPTY_USERS);
            httpMock.expectOne("../api/em/schedule/users-model").flush(EMPTY_USERS);
         });

         it("[Risk 3] isLoading stays true and no new request fires after an HTTP error", () => {
            // 🔁 Regression-sensitive: empty error handler leaves loading=true; service becomes permanently stuck
            httpMock.expectOne("../api/em/schedule/users-model").error(new ErrorEvent("network"));
            expect(service.isLoading).toBe(true);
            service.loadScheduleUsers();
            httpMock.expectNone("../api/em/schedule/users-model");
         });
      });

      describe("BehaviorSubject population after successful response", () => {
         let service: ScheduleUsersService;
         let httpMock: HttpTestingController;

         beforeEach(() => {
            const { mockStompClient } = createMockStomp();

            TestBed.configureTestingModule({
               imports: [HttpClientTestingModule],
               providers: [
                  ScheduleUsersService,
                  { provide: PORTAL, useValue: false },
                  { provide: StompClientService, useValue: mockStompClient },
               ],
            });
            service = TestBed.inject(ScheduleUsersService);
            httpMock = TestBed.inject(HttpTestingController);

            httpMock.expectOne("../api/em/schedule/users-model").flush(FULL_USERS);
         });

         afterEach(() => httpMock.verify());

         it("[Risk 2] emits the owners list", () => {
            let owners: IdentityIdWithLabel[] | undefined;
            service.getOwners().subscribe(v => (owners = v));
            expect(owners).toEqual(FULL_USERS.owners);
         });

         it("[Risk 2] emits the groups list", () => {
            let groups: IdentityId[] | undefined;
            service.getGroups().subscribe(v => (groups = v));
            expect(groups).toEqual(FULL_USERS.groups);
         });

         it("[Risk 2] emits the emailGroups list", () => {
            let emailGroups: IdentityId[] | undefined;
            service.getEmailGroups().subscribe(v => (emailGroups = v));
            expect(emailGroups).toEqual(FULL_USERS.emailGroups);
         });

         it("[Risk 2] emits the emailUsers list", () => {
            let emailUsers: IdentityId[] | undefined;
            service.getEmailUsers().subscribe(v => (emailUsers = v));
            expect(emailUsers).toEqual(FULL_USERS.emailUsers);
         });

         it("[Risk 2] emits the adminName", () => {
            let adminName: string | null | undefined;
            service.getAdminName().subscribe(v => (adminName = v));
            expect(adminName).toBe("siteAdmin");
         });

         it("[Risk 2] emits ssoEnable=true when SSO is enabled", () => {
            let ssoEnable: boolean | undefined;
            service.getSSOEnable().subscribe(v => (ssoEnable = v));
            expect(ssoEnable).toBe(true);
         });
      });

      describe("Portal context (PORTAL=true)", () => {
         let service: ScheduleUsersService;
         let httpMock: HttpTestingController;
         let mockStompClient: ReturnType<typeof createMockStomp>["mockStompClient"];

         beforeEach(() => {
            const mock = createMockStomp();
            mockStompClient = mock.mockStompClient;

            TestBed.configureTestingModule({
               imports: [HttpClientTestingModule],
               providers: [
                  ScheduleUsersService,
                  { provide: PORTAL, useValue: true },
                  { provide: StompClientService, useValue: mockStompClient },
               ],
            });
            service = TestBed.inject(ScheduleUsersService);
            httpMock = TestBed.inject(HttpTestingController);
         });

         afterEach(() => httpMock.verify());

         it("[Risk 2] requests the portal users-model endpoint", () => {
            const req = httpMock.expectOne("../api/portal/schedule/users-model");
            expect(req.request.method).toBe("GET");
            req.flush(EMPTY_USERS);
         });

         it("[Risk 2] populates owners from the portal endpoint response", () => {
            httpMock.expectOne("../api/portal/schedule/users-model").flush(FULL_USERS);

            let owners: IdentityIdWithLabel[] | undefined;
            service.getOwners().subscribe(v => (owners = v));
            expect(owners).toEqual(FULL_USERS.owners);
         });

         it("[Risk 2] connects to ../vs-events with em=false in portal mode", () => {
            httpMock.expectOne("../api/portal/schedule/users-model").flush(EMPTY_USERS);
            expect(mockStompClient.connect).toHaveBeenCalledWith("../vs-events", false);
         });
      });

      describe("STOMP /user/schedule/users-change callback", () => {
         let service: ScheduleUsersService;
         let httpMock: HttpTestingController;
         let mockConnection: ReturnType<typeof createMockStomp>["mockConnection"];

         beforeEach(() => {
            const { mockStompClient, mockConnection: conn } = createMockStomp();
            mockConnection = conn;

            TestBed.configureTestingModule({
               imports: [HttpClientTestingModule],
               providers: [
                  ScheduleUsersService,
                  { provide: PORTAL, useValue: false },
                  { provide: StompClientService, useValue: mockStompClient },
               ],
            });
            service = TestBed.inject(ScheduleUsersService);
            httpMock = TestBed.inject(HttpTestingController);
         });

         afterEach(() => httpMock.verify());

         it("[Risk 2] triggers a fresh loadScheduleUsers() request when a users-change message arrives", () => {
            httpMock.expectOne("../api/em/schedule/users-model").flush(EMPTY_USERS);
            const stompCallback = mockConnection.subscribe.mock.calls[0][1] as (...args: any[]) => void;
            stompCallback({});
            httpMock.expectOne("../api/em/schedule/users-model").flush(EMPTY_USERS);
         });
      });
   });

   // ---------------------------------------------------------------------------
   // Group 2 [Risk 2, 2, 3] — ngOnDestroy (3 cases)
   // ---------------------------------------------------------------------------
   describe("ngOnDestroy()", () => {
      let service: ScheduleUsersService;
      let httpMock: HttpTestingController;
      let mockConnection: ReturnType<typeof createMockStomp>["mockConnection"];

      beforeEach(() => {
         const { mockStompClient, mockConnection: conn } = createMockStomp();
         mockConnection = conn;

         TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
               ScheduleUsersService,
               { provide: PORTAL, useValue: false },
               { provide: StompClientService, useValue: mockStompClient },
            ],
         });
         service = TestBed.inject(ScheduleUsersService);
         httpMock = TestBed.inject(HttpTestingController);

         httpMock.expectOne("../api/em/schedule/users-model").flush(EMPTY_USERS);
      });

      afterEach(() => httpMock.verify());

      it("[Risk 2] disconnects the STOMP connection", () => {
         service.ngOnDestroy();
         expect(mockConnection.disconnect).toHaveBeenCalled();
      });

      it("[Risk 2] completes all BehaviorSubjects so subscribers are cleaned up", () => {
         const completed: string[] = [];
         service.owners.subscribe({ complete: () => completed.push("owners") });
         service.groups.subscribe({ complete: () => completed.push("groups") });
         service.emailGroups.subscribe({ complete: () => completed.push("emailGroups") });
         service.emailUsers.subscribe({ complete: () => completed.push("emailUsers") });
         service.adminName.subscribe({ complete: () => completed.push("adminName") });

         service.ngOnDestroy();

         expect(completed).toEqual(
            expect.arrayContaining(["owners", "groups", "emailGroups", "emailUsers", "adminName"]),
         );
      });

      // ssoEnable is declared in the service but ngOnDestroy() never calls ssoEnable.complete()
      // Steps to reproduce: N/A — lifecycle defect verified by unit test only; no user-visible
      // symptom under normal EM/Portal navigation.
      it.fails("[Risk 3] ssoEnable BehaviorSubject is not completed in ngOnDestroy", () => {
         let completed = false;
         service.ssoEnable.subscribe({ complete: () => (completed = true) });

         service.ngOnDestroy();

         expect(completed).toBe(true);
      });
   });
});
