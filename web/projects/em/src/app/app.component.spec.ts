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

import { NO_ERRORS_SCHEMA } from "@angular/core";
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
import { AppComponent } from "./app.component";
import { AuthorizationService } from "./authorization/authorization.service";
import { OrganizationDropdownService } from "./navbar/organization-dropdown.service";
import { TopScrollService } from "./top-scroll/top-scroll.service";

describe("AppComponent notifications", () => {
   let fixture: ComponentFixture<AppComponent>;
   let component: AppComponent;
   let dialog: MatDialog;

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
      fixture.detectChanges();

      expect(dialog.openDialogs.length).toBe(1);
      const texts = dialogTexts();
      expect(texts.length).toBe(1);
      expect(texts[0]).toContain("S23 test notification #1");
      expect(texts[0]).toContain("S23 test notification #2");
      expect(texts[0]).toContain("S23 test notification #3");
   });

   it("should start a fresh message after the dialog is closed", async () => {
      component.notify({ message: "first" });
      fixture.detectChanges();
      dialog.openDialogs[0].close();
      await fixture.whenStable();

      component.notify({ message: "second" });
      fixture.detectChanges();

      expect(dialog.openDialogs.length).toBe(1);
      const text = dialogTexts().pop();
      expect(text).toContain("second");
      expect(text).not.toContain("first");
   });

   it("should auto-close a dialog whose messages all have a duration", () => {
      vi.useFakeTimers();
      component.notify({ message: "timed" }, "600px", 5000);
      fixture.detectChanges();
      const ref = dialog.openDialogs[0];
      const closeSpy = vi.spyOn(ref, "close");

      vi.advanceTimersByTime(5000);

      expect(closeSpy).toHaveBeenCalled();
   });

   it("should not auto-close when a message without a duration was appended", () => {
      vi.useFakeTimers();
      component.notify({ message: "timed" }, "600px", 5000);
      component.notify({ message: "broadcast" });
      fixture.detectChanges();
      const ref = dialog.openDialogs[0];
      const closeSpy = vi.spyOn(ref, "close");

      vi.advanceTimersByTime(10000);

      expect(closeSpy).not.toHaveBeenCalled();
      expect(dialogTexts()[0]).toContain("timed");
      expect(dialogTexts()[0]).toContain("broadcast");
   });
});
