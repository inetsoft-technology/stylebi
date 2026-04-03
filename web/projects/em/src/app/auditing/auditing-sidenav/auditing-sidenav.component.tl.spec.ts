/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * AuditingSidenavComponent — Testing Library style
 *
 * This component has no fetchParameters / fetchData pattern and does not use MSW.
 * It is tested via Angular service mocks.
 *
 * Risk-first coverage (from scenario analysis):
 *   Group 1 — permission binding: all 12 boolean visibility flags set from AuthorizationService
 *   Group 2 — isScreenSmall: breakpoint at 720px determines sidenav behavior
 */
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { of } from "rxjs";
import { RouterTestingModule } from "@angular/router/testing";
import { BreakpointObserver } from "@angular/cdk/layout";
import { HttpClientModule } from "@angular/common/http";

import { AuditingSidenavComponent } from "./auditing-sidenav.component";
import { PageHeaderService } from "../../page-header/page-header.service";
import { AuthorizationService } from "../../authorization/authorization.service";

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

/** Full permissions map with all 12 auditing permissions granted. */
const ALL_PERMISSIONS_GRANTED = {
   permissions: {
      "inactive-resource": true,
      "inactive-user": true,
      "identity-info": true,
      "logon-error": true,
      "logon-history": true,
      "modification-history": true,
      "user-session": true,
      "dependent-assets": true,
      "required-assets": true,
      "export-history": true,
      "schedule-history": true,
      "bookmark-history": true,
   },
};

/** All permissions denied. */
const ALL_PERMISSIONS_DENIED = {
   permissions: {
      "inactive-resource": false,
      "inactive-user": false,
      "identity-info": false,
      "logon-error": false,
      "logon-history": false,
      "modification-history": false,
      "user-session": false,
      "dependent-assets": false,
      "required-assets": false,
      "export-history": false,
      "schedule-history": false,
      "bookmark-history": false,
   },
};

/** Creates a minimal MatSidenav stub that satisfies @ViewChild(MatSidenav). */
function makeSidenavStub() {
   return { close: jest.fn().mockReturnValue(Promise.resolve()) };
}

/** Creates an AuthorizationService mock returning the given permissions. */
function makeAuthzServiceMock(permissions = ALL_PERMISSIONS_GRANTED) {
   return {
      getPermissions: jest.fn().mockReturnValue(of(permissions)),
   };
}

/** Creates a BreakpointObserver stub. */
function makeBreakpointObserverStub(matched: boolean) {
   return { isMatched: jest.fn().mockReturnValue(matched) };
}

/** Renders the component, injecting mocks for Router, AuthorizationService, BreakpointObserver. */
async function renderComponent(
   permissions = ALL_PERMISSIONS_GRANTED,
   breakpointMatched = false
) {
   const authzMock = makeAuthzServiceMock(permissions);
   const breakpointStub = makeBreakpointObserverStub(breakpointMatched);

   const renderResult = await render(AuditingSidenavComponent, {
      imports: [HttpClientModule, RouterTestingModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: PageHeaderService, useValue: { title: "" } },
         { provide: AuthorizationService, useValue: authzMock },
         { provide: BreakpointObserver, useValue: breakpointStub },
      ],
   });

   // Expose the sidenav stub via @ViewChild after render
   renderResult.fixture.componentInstance.sidenav = makeSidenavStub() as any;

   return { ...renderResult, authzMock, breakpointStub };
}

// ---------------------------------------------------------------------------
// Group 1: permission binding — 12 boolean visibility flags
// ---------------------------------------------------------------------------

describe("AuditingSidenavComponent — permission binding", () => {

   // P0 / Happy — all 12 flags set true
   // ngOnInit subscribes to getPermissions("auditing") and maps each key to a component field.
   // If any key is misspelled or omitted the corresponding menu item is permanently hidden.
   it("should set all 12 visibility flags to true when all permissions are granted", async () => {
      const { fixture } = await renderComponent(ALL_PERMISSIONS_GRANTED);
      const comp = fixture.componentInstance;

      expect(comp.inactiveResourceVisible).toBe(true);
      expect(comp.inactiveUserVisible).toBe(true);
      expect(comp.identityInfoVisible).toBe(true);
      expect(comp.logonErrorVisible).toBe(true);
      expect(comp.logonHistoryVisible).toBe(true);
      expect(comp.modificationHistoryVisible).toBe(true);
      expect(comp.userSessionVisible).toBe(true);
      expect(comp.dependentAssetsVisible).toBe(true);
      expect(comp.requiredAssetsVisible).toBe(true);
      expect(comp.exportHistoryVisible).toBe(true);
      expect(comp.scheduleHistoryVisible).toBe(true);
      expect(comp.bookmarkHistoryVisible).toBe(true);
   });

   // P0 / Happy — all 12 flags set false when denied
   // Verifies the negative path so that a mistakenly hardcoded `true` would be caught.
   it("should set all 12 visibility flags to false when all permissions are denied", async () => {
      const { fixture } = await renderComponent(ALL_PERMISSIONS_DENIED);
      const comp = fixture.componentInstance;

      expect(comp.inactiveResourceVisible).toBe(false);
      expect(comp.inactiveUserVisible).toBe(false);
      expect(comp.identityInfoVisible).toBe(false);
      expect(comp.logonErrorVisible).toBe(false);
      expect(comp.logonHistoryVisible).toBe(false);
      expect(comp.modificationHistoryVisible).toBe(false);
      expect(comp.userSessionVisible).toBe(false);
      expect(comp.dependentAssetsVisible).toBe(false);
      expect(comp.requiredAssetsVisible).toBe(false);
      expect(comp.exportHistoryVisible).toBe(false);
      expect(comp.scheduleHistoryVisible).toBe(false);
      expect(comp.bookmarkHistoryVisible).toBe(false);
   });

   // P1 / Happy — getPermissions called with correct route key
   // The route key "auditing" must be passed exactly; a typo would return empty permissions.
   it("should call getPermissions with the 'auditing' route key", async () => {
      const { authzMock } = await renderComponent();

      expect(authzMock.getPermissions).toHaveBeenCalledWith("auditing");
   });
});

// ---------------------------------------------------------------------------
// Group 2: isScreenSmall — breakpoint at 720px
// ---------------------------------------------------------------------------

describe("AuditingSidenavComponent — isScreenSmall", () => {

   // P1 / Happy — returns true when breakpoint matches (viewport <= 720px)
   it("should return true when the viewport matches the 720px breakpoint", async () => {
      const { fixture } = await renderComponent(ALL_PERMISSIONS_GRANTED, true);
      expect(fixture.componentInstance.isScreenSmall()).toBe(true);
   });

   // P1 / Happy — returns false when breakpoint does not match
   it("should return false when the viewport does not match the 720px breakpoint", async () => {
      const { fixture } = await renderComponent(ALL_PERMISSIONS_GRANTED, false);
      expect(fixture.componentInstance.isScreenSmall()).toBe(false);
   });

   // P1 / Happy — isMatched called with the correct media query string
   it("should query BreakpointObserver with max-width: 720px", async () => {
      const { fixture, breakpointStub } = await renderComponent(ALL_PERMISSIONS_GRANTED, false);

      fixture.componentInstance.isScreenSmall();

      expect(breakpointStub.isMatched).toHaveBeenCalledWith("(max-width: 720px)");
   });
});
