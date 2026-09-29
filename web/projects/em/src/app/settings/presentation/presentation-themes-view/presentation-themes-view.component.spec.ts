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
import { ChangeDetectorRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { MatDialog } from "@angular/material/dialog";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { of } from "rxjs";
import { DownloadService } from "../../../../../../shared/download/download.service";
import { MessageDialogType } from "../../../common/util/message-dialog";
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

      // download must target the new id (checked before delete, which clears the selection)
      const downloadService = TestBed.inject(DownloadService) as any;
      component.downloadTheme(component.selectedTheme.id);
      expect(downloadService.download).toHaveBeenCalledWith(expect.stringContaining("theme2"));

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

   // Bug #77285: a rejected save must be reported instead of failing silently
   describe("save errors (bug #77285)", () => {
      const uri = "../api/em/settings/presentation/themes/theme1";
      let original: CustomThemeModel;
      let current: CustomThemeModel;

      beforeEach(() => {
         original = { id: "theme1", name: "theme1" } as CustomThemeModel;
         current = { id: "theme1", name: "renamed" } as CustomThemeModel;
         component.themes = [original];
         component.selectedTheme = current;
         dialog.open.mockClear();
      });

      function shownMessage(): string {
         expect(dialog.open).toHaveBeenCalledTimes(1);
         const config = dialog.open.mock.calls[0][1];
         expect(config.data.type).toBe(MessageDialogType.ERROR);
         return config.data.content;
      }

      it("should show the ProblemDetail reason of a rejected save and keep the edits", () => {
         component.saveTheme(current);
         http.expectOne(uri).flush(
            { type: "about:blank", title: "Bad Request", status: 400, detail: "not allowed: theme1" },
            { status: 400, statusText: "Bad Request" });

         expect(shownMessage()).toBe("not allowed: theme1");
         // the list is unchanged and the form still holds the unsaved edits
         expect(component.themes[0].name).toBe("theme1");
         expect(component.selectedTheme.name).toBe("renamed");
         expect(component.themeModified).toBe(true);
      });

      it("should show the message of a GenericError", () => {
         component.saveTheme(current);
         http.expectOne(uri).flush({ message: "server failure" },
            { status: 500, statusText: "Internal Server Error" });

         expect(shownMessage()).toBe("server failure");
      });

      it("should show a default message when the error has no body", () => {
         component.saveTheme(current);
         http.expectOne(uri).flush(null, { status: 500, statusText: "Internal Server Error" });

         expect(shownMessage()).toBe("_#(js:em.presentation.theme.saveFailed)");
      });

      it("should report a failed theme creation", () => {
         dialog.open.mockImplementation(() => ({ afterClosed: () => of({ id: "t2", name: "t2" }) }));

         component.createTheme();
         http.expectOne("../api/em/settings/presentation/themes/ids").flush([]);
         const post = http.expectOne("../api/em/settings/presentation/themes");
         expect(post.request.method).toBe("POST");
         post.flush({ detail: "cannot create" }, { status: 400, statusText: "Bad Request" });

         const errorCall = dialog.open.mock.calls.find(c => c[1]?.data?.type === MessageDialogType.ERROR);
         expect(errorCall[1].data.content).toBe("cannot create");
      });
   });

   // Bug #77285: "Default for All Organizations" is only shown in single-tenant mode and to a
   // site admin in the host organization; elsewhere its (server-reported) value must not be sent
   describe("default for all organizations payload (bug #77285)", () => {
      const uri = "../api/em/settings/presentation/themes/theme1";

      function savedPayload(): CustomThemeModel {
         const current = { id: "theme1", name: "theme1", defaultThemeGlobal: true } as CustomThemeModel;
         component.themes = [{ ...current }];
         component.saveTheme(current);
         const req = http.expectOne(uri);
         const body = req.request.body;
         req.flush({ id: "theme1" });
         return body;
      }

      it("should not assert the flag for a site admin in another organization", () => {
         component.isMultiTenant = true;
         component.isSiteAdmin = true;
         component.orgId = "org1";

         expect(savedPayload().defaultThemeGlobal).toBeNull();
         // the local model keeps the server's value
         expect(component.themes[0].defaultThemeGlobal).toBe(true);
      });

      it("should not assert the flag for an organization admin", () => {
         component.isMultiTenant = true;
         component.isSiteAdmin = false;
         component.orgId = "org1";

         expect(savedPayload().defaultThemeGlobal).toBeNull();
      });

      it("should send the flag for a site admin in the host organization", () => {
         component.isMultiTenant = true;
         component.isSiteAdmin = true;
         component.orgId = "host-org";

         expect(savedPayload().defaultThemeGlobal).toBe(true);
      });

      it("should send the flag in single-tenant mode", () => {
         component.isMultiTenant = false;
         component.isSiteAdmin = false;

         expect(savedPayload().defaultThemeGlobal).toBe(true);
      });
   });

   // Bug #77315: a rejected save must also re-enable apply, which the editor panel disables
   // when it is clicked, or the page looks saved.
   describe("when the save is rejected (bug #77315)", () => {
      const reasons = [
         "A theme that is not visible to all organizations can't be the default for all " +
            "organizations: theme1",
         "A theme of another organization can't be the default for this organization: theme1"
      ];

      let original: CustomThemeModel;

      beforeEach(() => {
         original = {
            id: "theme1", name: "theme1", global: false, defaultThemeGlobal: false,
            defaultThemeOrg: false
         } as CustomThemeModel;
         component.themes = [original];
         component.selectedTheme = { ...original, defaultThemeGlobal: true };
         fixture.debugElement.injector.get(ChangeDetectorRef).markForCheck();
         fixture.detectChanges();
      });

      function getApplyPanel() {
         const panel = component.themeEditor.editorPanel;
         expect(panel).toBeTruthy();
         // the panel disables apply when it is clicked
         panel.changeApplyDisabledState(true);
         return panel;
      }

      function rejectSave(body: any) {
         dialog.open.mockClear();
         component.saveTheme(component.selectedTheme);
         http.expectOne("../api/em/settings/presentation/themes/theme1")
            .flush(body, { status: 400, statusText: "Bad Request" });
      }

      it.each(reasons)("should re-enable apply after the rejection: %s", (reason) => {
         const panel = getApplyPanel();

         rejectSave({ type: "about:blank", title: "Bad Request", status: 400, detail: reason });

         expect(dialog.open).toHaveBeenCalledTimes(1);
         expect(dialog.open.mock.calls[0][1].data.content).toBe(reason);
         expect(dialog.open.mock.calls[0][1].data.type).toBe(MessageDialogType.ERROR);

         // the saved list is unchanged and the edit is kept
         expect(component.themes).toEqual([original]);
         expect(component.selectedTheme.defaultThemeGlobal).toBe(true);
         expect(component.themeModified).toBe(true);

         // apply is enabled again so the user can retry
         expect(panel.applyDisabled).toBe(false);
      });

      it("should re-enable the apply button after a rejected click on it", async () => {
         const apply: HTMLButtonElement = fixture.nativeElement
            .querySelector("em-editor-panel button[color='primary']");
         expect(apply).toBeTruthy();
         expect(apply.disabled).toBe(false);

         apply.click();
         fixture.detectChanges();
         expect(apply.disabled).toBe(true);

         // the panel debounces the click before it emits
         await new Promise(resolve => setTimeout(resolve, 250));
         http.expectOne("../api/em/settings/presentation/themes/theme1")
            .flush({ status: 400, detail: reasons[0] }, { status: 400, statusText: "Bad Request" });
         // TestBed runs zoneless here, so stand in for the zone tick the app gets after the response
         fixture.componentRef.changeDetectorRef.markForCheck();
         fixture.detectChanges();

         expect(dialog.open).toHaveBeenCalledTimes(1);
         expect(dialog.open.mock.calls[0][1].data.content).toBe(reasons[0]);
         expect(apply.disabled).toBe(false);
      });
   });
});
