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
import { Type } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { MatDialog } from "@angular/material/dialog";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { ActivatedRoute } from "@angular/router";
import { config, of } from "rxjs";
import { PresentationOrgSettingsViewComponent } from "./presentation-org-settings-view.component";
import { PresentationSettingsViewComponent } from "./presentation-settings-view.component";

const MODEL_URI = "../api/em/settings/presentation/model";
const RESET_URI = "../api/em/settings/presentation/model/reset";
const DATE_FORMAT = `input[placeholder="_#(Date Format)"]`;
const LOGIN_BANNER = `textarea[placeholder="_#(Login Banner)"]`;

const model = (org: boolean): any => ({
   orgSettings: org,
   formatsSettingsModel: { dateFormat: "yyyy-MM-dd", timeFormat: "HH:mm:ss", dateTimeFormat: "medium" },
   loginBannerSettingsModel: { bannerType: "0", loginBanner: "Hello" }
});

// Bug #78724: the real page with its real section views. The org page only wraps the same view.
for(const org of [false, true]) {
   describe(`PresentationSettingsViewComponent reset (${org ? "org" : "global"} page)`, () => {
      let fixture: ComponentFixture<any>;
      let http: HttpTestingController;
      let confirm: boolean;
      let originalScrollIntoView: any;
      let originalOnUnhandledError: any;

      const el = (): HTMLElement => fixture.nativeElement;
      const view = (): PresentationSettingsViewComponent => org ?
         fixture.componentInstance.settingsView : fixture.componentInstance;
      const button = (label: string) =>
         Array.from(el().querySelectorAll(".presentation-settings-actions button"))
            .find(b => b.textContent.trim() === label) as HTMLButtonElement;
      const apply = () => button("_#(Apply)");
      const detect = () => fixture.componentRef.changeDetectorRef.detectChanges();
      const stable = async () => {
         detect();
         await fixture.whenStable();
         detect();
      };
      // the section views subscribe to their form changes after a 200 ms delay
      const settle = async () => {
         await new Promise(r => setTimeout(r, 250));
         await stable();
      };
      const field = (selector: string) => el().querySelector(selector) as HTMLInputElement;
      const type = async (selector: string, value: string) => {
         const i = field(selector);
         i.value = value;
         i.dispatchEvent(new Event("input"));
         await stable();
      };
      const clickReset = async () => {
         button("_#(Reset to Default)").click();
         await stable();
      };

      beforeEach(async () => {
         confirm = true;
         originalScrollIntoView = Element.prototype.scrollIntoView;
         originalOnUnhandledError = config.onUnhandledError;
         Element.prototype.scrollIntoView = () => {};
         TestBed.configureTestingModule({
            imports: [org ? PresentationOrgSettingsViewComponent : PresentationSettingsViewComponent,
               HttpClientTestingModule, NoopAnimationsModule],
            providers: [
               { provide: ActivatedRoute, useValue: { fragment: of(null) } },
               { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(confirm) }) } }
            ]
         });
         fixture = TestBed.createComponent(
            (org ? PresentationOrgSettingsViewComponent : PresentationSettingsViewComponent) as Type<any>);
         http = TestBed.inject(HttpTestingController);
         detect();
         http.expectOne("../api/em/navbar/userInfo").flush({ key: "org0", value: !org });
         const get = http.expectOne(r => r.url === MODEL_URI);
         expect(get.request.params.get("orgSettings")).toBe(`${org}`);
         get.flush(model(org));
         await settle();
         await type(DATE_FORMAT, "qq");
         expect(apply().disabled).toBe(true);
      });

      afterEach(() => {
         Element.prototype.scrollIntoView = originalScrollIntoView;
         config.onUnhandledError = originalOnUnhandledError;
      });

      it("should enable Apply for a valid edit after a confirmed reset", async () => {
         await clickReset();
         const req = http.expectOne(RESET_URI);
         expect(req.request.body.orgSettings).toBe(org);
         req.flush(model(org));
         await settle();
         expect(field(DATE_FORMAT).value).toBe("yyyy-MM-dd");

         await type(LOGIN_BANNER, "Welcome");
         expect(view().changed).toBe(true);
         expect(apply().disabled).toBe(false);

         // a new invalid value still disables Apply
         await type(DATE_FORMAT, "qq");
         expect(apply().disabled).toBe(true);
      });

      it("should keep Apply disabled when the reset is cancelled", async () => {
         confirm = false;
         await clickReset();
         http.expectNone(RESET_URI);
         await settle();
         expect(field(DATE_FORMAT).value).toBe("qq");

         await type(LOGIN_BANNER, "Welcome");
         expect(view().changed).toBe(true);
         expect(view().saveModel.formatsSettingsModel.dateFormat).toBe("qq");
         expect(apply().disabled).toBe(true);
      });

      it("should keep Apply disabled when the reset request fails", async () => {
         // the page rethrows the request error after showing it in a snack bar
         config.onUnhandledError = () => {};
         await clickReset();
         http.expectOne(RESET_URI).flush({ message: "failed" }, { status: 500, statusText: "Error" });
         await settle();

         await type(LOGIN_BANNER, "Welcome");
         expect(view().changed).toBe(true);
         expect(view().saveModel.formatsSettingsModel.dateFormat).toBe("qq");
         expect(apply().disabled).toBe(true);
      });
   });
}
