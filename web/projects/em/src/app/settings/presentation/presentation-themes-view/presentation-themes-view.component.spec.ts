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

import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { MatDialog } from "@angular/material/dialog";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { of } from "rxjs";
import { DownloadService } from "../../../../../../shared/download/download.service";
import { CustomThemeModel } from "./custom-theme-model";
import { PresentationThemesViewComponent } from "./presentation-themes-view.component";

describe("PresentationThemesViewComponent", () => {
   let component: PresentationThemesViewComponent;
   let fixture: ComponentFixture<PresentationThemesViewComponent>;
   let http: HttpTestingController;
   let dialog: any;

   beforeEach(async () => {
      dialog = {
         open: vi.fn(() => ({ afterClosed: () => of(true) }))
      };

      await TestBed.configureTestingModule({
         imports: [
            NoopAnimationsModule,
            HttpClientTestingModule,
            PresentationThemesViewComponent],
         providers: [
            { provide: MatDialog, useValue: dialog },
            { provide: DownloadService, useValue: { download: vi.fn() } }
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      })
      .compileComponents();
   });

   beforeEach(() => {
      fixture = TestBed.createComponent(PresentationThemesViewComponent);
      component = fixture.componentInstance;
      http = TestBed.inject(HttpTestingController);
      fixture.detectChanges();

      http.expectOne("../api/em/settings/presentation/themes").flush({ themes: [] });
      http.expectOne("../api/em/navbar/userInfo").flush({ key: "host-org", value: true });
      http.expectOne("../api/em/navbar/isMultiTenant").flush(false);
   });

   afterEach(() => {
      http.verify();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });

   // Bug #75343: renaming a theme changes its id on the server (the id follows the name
   // when they were initialized equal). The save response carries the effective id and the
   // component must adopt it, or subsequent delete/download requests use the stale id and 404.
   it("should adopt the effective theme id from the save response (bug #75343)", () => {
      const original: CustomThemeModel = { id: "theme1", name: "theme1" } as CustomThemeModel;
      component.themes = [original];
      const current: CustomThemeModel = { id: "theme1", name: "theme2" } as CustomThemeModel;

      component.saveTheme(current);

      const putReq = http.expectOne("../api/em/settings/presentation/themes/theme1");
      expect(putReq.request.method).toBe("PUT");
      putReq.flush({ id: "theme2", name: "theme2" });

      // the local list and selection must carry the renamed id
      expect(component.themes.length).toBe(1);
      expect(component.themes[0].id).toBe("theme2");
      expect(component.selectedTheme.id).toBe("theme2");

      // delete must target the new id, not the stale one
      component.deleteTheme(component.selectedTheme.id);
      const deleteReq = http.expectOne("../api/em/settings/presentation/themes/theme2");
      expect(deleteReq.request.method).toBe("DELETE");
      deleteReq.flush(null);
   });

   it("should keep the current id when the save response has no id (bug #75343)", () => {
      const original: CustomThemeModel = { id: "theme1", name: "theme1" } as CustomThemeModel;
      component.themes = [original];
      const current: CustomThemeModel = { id: "theme1", name: "theme2" } as CustomThemeModel;

      component.saveTheme(current);

      // older servers respond with an empty body
      http.expectOne("../api/em/settings/presentation/themes/theme1").flush(null);

      expect(component.themes[0].id).toBe("theme1");
      expect(component.selectedTheme.id).toBe("theme1");
   });
});
