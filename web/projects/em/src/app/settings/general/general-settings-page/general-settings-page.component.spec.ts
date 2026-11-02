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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { ActivatedRoute, RouterModule } from "@angular/router";
import { of } from "rxjs";
import { AppInfoService } from "../../../../../../shared/util/app-info.service";
import { GeneralSettingsPageComponent } from "./general-settings-page.component";

const pageModel = (): any => ({
   cacheSettingsModel: { directory: "/var/cache", cleanUpStartup: false },
   performanceSettingsModel: {
      queryTimeout: 10, maxQueryRowCount: 100, queryPreviewTimeout: 10,
      maxQueryPreviewRowCount: 100, maxTableRowCount: 100, dataSetCaching: false,
      dataCacheSize: 100, dataCacheTimeout: 10
   }
});

// Bug #78724: the real page with its real cache and performance section views
describe("GeneralSettingsPageComponent reset", () => {
   let fixture: ComponentFixture<GeneralSettingsPageComponent>;
   let http: HttpTestingController;
   let originalScrollIntoView: any;

   const el = (): HTMLElement => fixture.nativeElement;
   const button = (label: string) => Array.from(el().querySelectorAll(".editor-panel-actions button"))
      .find(b => b.textContent.trim() === label) as HTMLButtonElement;
   const apply = () => button("_#(js:Apply)");
   const detect = () => fixture.componentRef.changeDetectorRef.detectChanges();
   const stable = async () => {
      detect();
      await fixture.whenStable();
      detect();
   };
   const input = (placeholder: string) =>
      el().querySelector(`input[placeholder="${placeholder}"]`) as HTMLInputElement;
   const type = async (placeholder: string, value: string) => {
      const i = input(placeholder);
      i.value = value;
      i.dispatchEvent(new Event("input"));
      await stable();
   };

   beforeEach(async () => {
      originalScrollIntoView = Element.prototype.scrollIntoView;
      Element.prototype.scrollIntoView = () => {};
      TestBed.configureTestingModule({
         imports: [GeneralSettingsPageComponent, HttpClientTestingModule, NoopAnimationsModule,
            RouterModule.forRoot([])],
         providers: [
            { provide: AppInfoService, useValue: { isEnterprise: () => of(false) } },
            { provide: ActivatedRoute, useValue: { fragment: of(null) } }
         ]
      });
      fixture = TestBed.createComponent(GeneralSettingsPageComponent);
      http = TestBed.inject(HttpTestingController);
      detect();
      http.expectOne("../api/em/general/settings/model").flush(pageModel());
      await stable();
   });

   afterEach(() => {
      Element.prototype.scrollIntoView = originalScrollIntoView;
   });

   it("should enable Apply for a valid edit in another section after an invalid edit was reset", async () => {
      await type("_#(Data Cache Size)", "-1");
      expect(apply().disabled).toBe(true);

      button("_#(js:Reset)").click();
      await stable();
      expect(input("_#(Data Cache Size)").value).toBe("100");

      await type("_#(Directory)", "/other");
      expect(fixture.componentInstance.changed).toBe(true);
      expect(apply().disabled).toBe(false);

      // a new invalid value still disables Apply
      await type("_#(Data Cache Size)", "-5");
      expect(apply().disabled).toBe(true);
   });
});
