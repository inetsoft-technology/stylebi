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
import { ChangeDetectorRef, NgZone } from "@angular/core";
import { Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject } from "rxjs";
import { SsoHeartbeatDispatcherService } from "../../../shared/sso/sso-heartbeat-dispatcher.service";
import { StompClientService } from "../../../shared/stomp/stomp-client.service";
import { LogoutService } from "../../../shared/util/logout.service";
import { SessionExpirationModel } from "../../../shared/util/model/session-expiration-model";
import { AppComponent } from "./app.component";
import { SessionExpirationDialog } from "./widget/dialog/session-expiration-dialog/session-expiration-dialog.component";
import { NotificationsComponent } from "./widget/notifications/notifications.component";

// Bug #77340: when the node protection (server shutdown) warning timer ends on a dialog that was
// left open, a guest must not be logged out, because logging out sends a guest to the login page
describe("AppComponent session expiration timer", () => {
   let component: AppComponent;
   let logoutService: { logout: ReturnType<typeof vi.fn>, setInGracePeriod: ReturnType<typeof vi.fn> };
   let modal: { componentInstance: SessionExpirationDialog, result: Promise<any>,
                close: ReturnType<typeof vi.fn>, dismiss: ReturnType<typeof vi.fn> };

   beforeEach(() => {
      vi.useFakeTimers();
      logoutService = { logout: vi.fn(), setInGracePeriod: vi.fn() };

      const modalService = {
         open: vi.fn(() => {
            let resolve: (v: any) => void;
            let reject: (v: any) => void;
            const result = new Promise<any>((res, rej) => {
               resolve = res;
               reject = rej;
            });
            modal = {
               componentInstance: new SessionExpirationDialog(),
               result,
               close: vi.fn((v: any) => resolve(v)),
               dismiss: vi.fn((v: any) => reject(v))
            };
            return modal;
         })
      };

      component = new AppComponent(
         <Router> <any> { events: new Subject() },
         <StompClientService> <any> { connect: vi.fn(() => new Subject()) },
         <NgbModal> <any> modalService,
         <NgZone> <any> { run: (fn: () => any) => fn() },
         document,
         <SsoHeartbeatDispatcherService> <any> { dispatch: vi.fn() },
         <LogoutService> <any> logoutService);
   });

   afterEach(() => {
      modal?.componentInstance.ngOnDestroy();
      vi.useRealTimers();
   });

   const showDialog = (model: SessionExpirationModel): void =>
      (component as any).showExpirationDialog(model);

   const finishTimer = async (): Promise<void> => {
      // count the timer down to 0, then let the dialog dismissal settle
      await vi.advanceTimersByTimeAsync(3000);
   };

   it("should close the dialog instead of logging out a guest", async () => {
      showDialog({ remainingTime: 2000, expiringSoon: true, nodeProtection: true, guest: true });
      await finishTimer();

      expect(logoutService.logout).not.toHaveBeenCalled();
      expect(modal.dismiss).toHaveBeenCalled();
      expect((component as any).protectionExpirationDialog).toBeNull();
   });

   it("should log out a named user", async () => {
      showDialog({ remainingTime: 2000, expiringSoon: true, nodeProtection: true, guest: false });
      await finishTimer();

      expect(logoutService.logout).toHaveBeenCalledTimes(1);
      expect((component as any).protectionExpirationDialog).toBeNull();
   });

   it("should log out when the guest flag is missing", async () => {
      showDialog({ remainingTime: 2000, expiringSoon: true, nodeProtection: false });
      await finishTimer();

      expect(logoutService.logout).toHaveBeenCalledTimes(1);
      expect((component as any).sessionExpirationDialog).toBeNull();
   });

   it("should show a new warning after a guest dialog was closed by the timer", async () => {
      showDialog({ remainingTime: 2000, expiringSoon: true, nodeProtection: true, guest: true });
      await finishTimer();
      const first = modal;

      showDialog({ remainingTime: 2000, expiringSoon: true, nodeProtection: true, guest: true });

      expect(modal).not.toBe(first);
      expect(logoutService.logout).not.toHaveBeenCalled();
   });
});

// Bug #77624: each notification toast must show only its own message, not every earlier message
describe("AppComponent notifications", () => {
   let component: AppComponent;
   let info: ReturnType<typeof vi.fn>;

   beforeEach(() => {
      component = new AppComponent(
         <Router> <any> { events: new Subject() },
         <StompClientService> <any> { connect: vi.fn(() => new Subject()) },
         <NgbModal> <any> {},
         <NgZone> <any> { run: (fn: () => any) => fn() },
         document,
         <SsoHeartbeatDispatcherService> <any> { dispatch: vi.fn() },
         <LogoutService> <any> { logout: vi.fn(), setInGracePeriod: vi.fn() });
      info = vi.fn();
      component.notifications = <any> { info };
   });

   const notify = (message: string): void => (component as any).notify({ message });

   it("should show only the newest message in each toast", () => {
      notify("first");
      notify("second");
      notify("third");

      expect(info.mock.calls.map(c => c[0])).toEqual(["first", "second", "third"]);
   });

   it("should show a message again after an earlier toast with it", () => {
      notify("authorized");
      notify("other");
      notify("authorized");

      expect(info.mock.calls.map(c => c[0])).toEqual(["authorized", "other", "authorized"]);
   });

   it("should drop a message identical to a toast still showing", () => {
      vi.useFakeTimers();
      const toasts = new NotificationsComponent(<ChangeDetectorRef> <any> { detectChanges: vi.fn() });
      toasts.timeout = 5000;
      component.notifications = toasts;

      try {
         notify("authorized");
         notify("authorized");
         expect(toasts.alerts.map(a => a.message)).toEqual(["authorized"]);

         vi.advanceTimersByTime(5000);
         notify("authorized");
         expect(toasts.alerts.map(a => a.message)).toEqual(["authorized"]);
      }
      finally {
         vi.useRealTimers();
      }
   });
});
