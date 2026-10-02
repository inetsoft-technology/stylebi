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

import { ApplicationRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { MatButton } from "@angular/material/button";
import {
   MatDialog,
   MatDialogActions,
   MatDialogClose,
   MatDialogContent,
   MatDialogModule
} from "@angular/material/dialog";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { Router } from "@angular/router";
import { BreakpointObserver } from "@angular/cdk/layout";
import { of as observableOf, Subject } from "rxjs";
import { SsoHeartbeatDispatcherService } from "../../../shared/sso/sso-heartbeat-dispatcher.service";
import { StompClientService } from "../../../shared/stomp/stomp-client.service";
import { CurrentUserService } from "../../../shared/util/current-user.service";
import { LogoutService } from "../../../shared/util/logout.service";
import { SessionExpirationModel } from "../../../shared/util/model/session-expiration-model";
import { AppComponent } from "./app.component";
import { AuthorizationService } from "./authorization/authorization.service";
import { OrganizationDropdownService } from "./navbar/organization-dropdown.service";
import { TopScrollService } from "./top-scroll/top-scroll.service";
import { SessionExpirationDialog } from "./widget/dialog/session-expiration-dialog/session-expiration-dialog.component";

describe("AppComponent notifications", () => {
   let fixture: ComponentFixture<AppComponent>;
   let component: AppComponent;
   let dialog: MatDialog;

   // AppComponent is the bootstrapped root and the dialog view lives in the overlay, so refresh
   // the whole application like a zone-triggered tick does in production
   const render = (): void => TestBed.inject(ApplicationRef).tick();

   const dialogTexts = (): string[] =>
      Array.from(document.querySelectorAll("mat-dialog-container"))
         .map(el => el.textContent);

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [NoopAnimationsModule, MatDialogModule, AppComponent],
         providers: [
            { provide: AuthorizationService, useValue: { getPermissions: vi.fn(() => observableOf({})) } },
            { provide: StompClientService, useValue: { connect: vi.fn(() => new Subject()) } },
            { provide: BreakpointObserver, useValue: { observe: vi.fn(() => new Subject()) } },
            { provide: TopScrollService, useValue: { onScroll: new Subject() } },
            { provide: SsoHeartbeatDispatcherService, useValue: { dispatch: vi.fn() } },
            { provide: LogoutService, useValue: { logout: vi.fn(), setInGracePeriod: vi.fn() } },
            { provide: OrganizationDropdownService, useValue: { getProvider: vi.fn(), refresh: vi.fn() } },
            { provide: CurrentUserService, useValue: { getEmCurrentUser: vi.fn(() => observableOf(null)) } },
            { provide: Router, useValue: { events: new Subject() } }
         ]
      });

      // keep only what the notification dialog template needs
      TestBed.overrideComponent(AppComponent, {
         set: {
            imports: [MatDialogContent, MatDialogActions, MatButton, MatDialogClose],
            schemas: [NO_ERRORS_SCHEMA]
         }
      });

      fixture = TestBed.createComponent(AppComponent);
      component = fixture.componentInstance;
      TestBed.inject(ApplicationRef).attachView(fixture.componentRef.hostView);
      dialog = TestBed.inject(MatDialog);
   });

   afterEach(() => {
      dialog.closeAll();
      vi.useRealTimers();
   });

   // Bug #77316: rapid broadcasts showed only the last message
   it("should show every message when several notifications arrive while the dialog is open", () => {
      component.notify({ message: "S23 test notification #1" });
      component.notify({ message: "S23 test notification #2" });
      component.notify({ message: "S23 test notification #3" });
      render();

      expect(dialog.openDialogs.length).toBe(1);
      const texts = dialogTexts();
      expect(texts.length).toBe(1);
      expect(texts[0]).toContain("S23 test notification #1");
      expect(texts[0]).toContain("S23 test notification #2");
      expect(texts[0]).toContain("S23 test notification #3");
   });

   it("should update the rendered dialog when a message arrives after it was shown", () => {
      component.notify({ message: "S23 test notification #1" });
      render();
      expect(dialogTexts()[0]).toContain("S23 test notification #1");

      component.notify({ message: "S23 test notification #2" });
      render();

      expect(dialog.openDialogs.length).toBe(1);
      expect(dialogTexts()[0]).toContain("S23 test notification #1");
      expect(dialogTexts()[0]).toContain("S23 test notification #2");
   });

   it("should open a new dialog when a message arrives while the dialog is closing", async () => {
      component.notify({ message: "first" });
      render();
      dialog.openDialogs[0].close();

      // arrives before afterClosed() has emitted
      component.notify({ message: "second" });
      render();
      await fixture.whenStable();
      render();

      expect(dialog.openDialogs.length).toBe(1);
      const text = dialogTexts().pop();
      expect(text).toContain("second");
      expect(text).not.toContain("first");
   });

   it("should start a fresh message after the dialog is closed", async () => {
      component.notify({ message: "first" });
      render();
      dialog.openDialogs[0].close();
      await fixture.whenStable();

      component.notify({ message: "second" });
      render();

      expect(dialog.openDialogs.length).toBe(1);
      const text = dialogTexts().pop();
      expect(text).toContain("second");
      expect(text).not.toContain("first");
   });

   it("should auto-close a dialog whose messages all have a duration", () => {
      vi.useFakeTimers();
      component.notify({ message: "timed" }, "600px", 5000);
      render();
      const ref = dialog.openDialogs[0];
      const closeSpy = vi.spyOn(ref, "close");

      vi.advanceTimersByTime(5000);

      expect(closeSpy).toHaveBeenCalled();
   });

   it("should not auto-close when a message without a duration was appended", () => {
      vi.useFakeTimers();
      component.notify({ message: "timed" }, "600px", 5000);
      component.notify({ message: "broadcast" });
      render();
      const ref = dialog.openDialogs[0];
      const closeSpy = vi.spyOn(ref, "close");

      vi.advanceTimersByTime(10000);

      expect(closeSpy).not.toHaveBeenCalled();
      expect(dialogTexts()[0]).toContain("timed");
      expect(dialogTexts()[0]).toContain("broadcast");
   });
});

// Bug #77340: when the server shutdown warning timer ends, a guest must not be logged out, because
// logging out sends a guest to the login page
describe("AppComponent session expiration timer", () => {
   let component: AppComponent;
   let logoutService: { logout: ReturnType<typeof vi.fn>, setInGracePeriod: ReturnType<typeof vi.fn> };
   let connection: { send: ReturnType<typeof vi.fn> };
   let ref: { componentInstance: SessionExpirationDialog, close: ReturnType<typeof vi.fn>,
              afterClosed: () => Subject<any> };

   beforeEach(() => {
      vi.useFakeTimers();
      logoutService = { logout: vi.fn(), setInGracePeriod: vi.fn() };
      connection = { send: vi.fn() };

      const dialogService = {
         open: vi.fn((type: any, config: any) => {
            const closed = new Subject<any>();
            ref = <any> {
               close: vi.fn((v: any) => {
                  closed.next(v);
                  closed.complete();
               }),
               afterClosed: () => closed
            };
            ref.componentInstance = new SessionExpirationDialog(<any> ref, config.data);
            return ref;
         })
      };

      component = new AppComponent(
         <any> {}, <any> {}, <any> { run: (fn: () => any) => fn() }, <any> dialogService,
         <any> {}, <any> {}, <any> {}, <any> {}, <LogoutService> <any> logoutService,
         <any> {}, <any> {}, <any> {});
      (component as any).connection = connection;
   });

   afterEach(() => {
      ref?.componentInstance.ngOnDestroy();
      vi.useRealTimers();
   });

   const showDialog = (model: SessionExpirationModel): void =>
      (component as any).showExpirationDialog(model);

   it("should close the dialog instead of logging out a guest", () => {
      showDialog({ remainingTime: 2000, expiringSoon: true, nodeProtection: true, guest: true });
      vi.advanceTimersByTime(3000);

      expect(logoutService.logout).not.toHaveBeenCalled();
      expect(ref.close).toHaveBeenCalledWith(false);
      expect(connection.send).not.toHaveBeenCalled();
      expect((component as any).protectionExpirationDialog).toBeNull();
   });

   it("should log out a named user", () => {
      showDialog({ remainingTime: 2000, expiringSoon: true, nodeProtection: true, guest: false });
      vi.advanceTimersByTime(3000);

      expect(logoutService.logout).toHaveBeenCalledWith(false, true);
   });

   it("should log out when the guest flag is missing", () => {
      showDialog({ remainingTime: 2000, expiringSoon: true, nodeProtection: false });
      vi.advanceTimersByTime(3000);

      expect(logoutService.logout).toHaveBeenCalledWith(false, true);
   });
});
