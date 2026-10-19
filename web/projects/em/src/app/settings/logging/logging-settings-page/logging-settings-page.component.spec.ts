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
import { ChangeDetectorRef, Component } from "@angular/core";
import { ComponentFixture, TestBed, waitForAsync } from "@angular/core/testing";
import { Location } from "@angular/common";
import { MatDialog } from "@angular/material/dialog";
import { By } from "@angular/platform-browser";
import { EMPTY } from "rxjs";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatDialogModule } from "@angular/material/dialog";
import { MatDividerModule } from "@angular/material/divider";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { Router, RouterModule, RouterOutlet } from "@angular/router";
import { DownloadTargetComponent } from "../../../../../../shared/download/download-target.component";
import { AppInfoService } from "../../../../../../shared/util/app-info.service";
import { EditorPanelComponent } from "../../../common/util/editor-panel/editor-panel.component";
import { TableView } from "../../../common/util/table/table-view.component";
import { AddLoggingLevelDialogComponent } from "../add-logging-level-dialog/add-logging-level-dialog.component";
import { LoggingLevelTableComponent } from "../logging-level-table/logging-level-table.component";
import { LOGGING_ROUTES } from "../logging.routes";
import { LoggingSettingsViewComponent } from "../logging-settings-view/logging-settings-view.component";
import { ErrorHandlerService } from "../../../common/util/error/error-handler.service";
import { LoggingSettingsPageComponent } from "./logging-settings-page.component";

describe("LoggingSettingsPageComponent", () => {
   let component: LoggingSettingsPageComponent;
   let fixture: ComponentFixture<LoggingSettingsPageComponent>;
   let httpTesting: HttpTestingController;

   beforeEach(waitForAsync(() => {
      TestBed.configureTestingModule({
         imports: [
            LoggingSettingsPageComponent, LoggingSettingsViewComponent, LoggingLevelTableComponent,
            HttpClientTestingModule,
            FormsModule,
            ReactiveFormsModule,
            RouterModule.forRoot([]),
            MatButtonModule,
            MatCardModule,
            MatCheckboxModule,
            MatDialogModule,
            MatDividerModule,
            MatFormFieldModule,
            MatInputModule,
            MatSelectModule,
            NoopAnimationsModule,
            DownloadTargetComponent,
            EditorPanelComponent,
            TableView
         ],
         providers: [
            AppInfoService
         ]
      })
      .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(LoggingSettingsPageComponent);
      component = fixture.componentInstance;
      httpTesting = TestBed.inject(HttpTestingController);
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });

   // The server refuses log.provider=fluentd on a build that cannot forward (Redmine #76045).
   // The save subscribe had no error callback, so the rejection was swallowed and the page
   // simply failed to update -- the same silent failure this change exists to end.
   it("should show the server's message when the save is rejected", () => {
      const errorService = TestBed.inject(ErrorHandlerService);
      const showDialog = vi.spyOn(errorService, "showDialog").mockReturnValue(EMPTY);
      const rejected = <any>{ provider: "fluentd" };

      component.model = <any>{ provider: "file" };
      component.newModel = rejected;
      component.valid = true;
      component.setConfiguration();

      httpTesting.expectOne("../api/em/log/setting/set-configuration")
         .flush({ message: "only available in the Enterprise edition" },
                { status: 500, statusText: "Internal Server Error" });

      expect(showDialog).toHaveBeenCalledTimes(1);
      // the rejected model must not be adopted as the saved state
      expect(component.model).not.toBe(rejected);
      expect(component.valid).toBe(true);
   });

   it("should adopt the model when the save succeeds", () => {
      const newModel = <any>{ provider: "file" };
      component.newModel = newModel;
      component.valid = true;
      component.setConfiguration();

      httpTesting.expectOne("../api/em/log/setting/set-configuration").flush({});

      // the saved state must be a copy, not the object the view keeps editing (Bug #78725)
      expect(component.model).not.toBe(newModel);
      expect(component.model).toEqual(newModel);
      expect(component.valid).toBe(false);
   });
});

// Bug #78725: after one successful Apply the page kept the edited model as its saved state by
// reference, so Reset no longer undid edits and leaving the page no longer warned. These tests
// drive the real page, view, level table, level dialog, routes and save guard under a Router.
@Component({ selector: "em-test-logging-host", template: "<router-outlet></router-outlet>", imports: [RouterOutlet] })
class LoggingHostComponent {
}

@Component({ selector: "em-test-other-page", template: "other" })
class OtherPageComponent {
}

describe("LoggingSettingsPageComponent saved state after Apply (Bug #78725)", () => {
   const GET_URI = "../api/em/log/setting/get-configuration";
   const SET_URI = "../api/em/log/setting/set-configuration";
   let fixture: ComponentFixture<LoggingHostComponent>;
   let http: HttpTestingController;

   const loadedSettings = () => ({
      provider: "file", outputToStd: false, detailLevel: "info",
      fileSettings: { file: "sree.log", maxLogSize: 5, count: 1 },
      fluentdSettings: {
         host: "h", port: 24224, connectTimeout: 10000, securityEnabled: false,
         userAuthenticationEnabled: false, orgAdminAccess: false, sharedKey: "", username: "",
         password: "", tlsEnabled: false, caCertificateFile: "", logViewUrl: ""
      },
      logLevels: [{ context: "DASHBOARD", name: "Sales", level: "debug" }]
   });

   const wait = (ms: number) => new Promise(r => setTimeout(r, ms));
   const page = (): LoggingSettingsPageComponent =>
      fixture.debugElement.query(By.directive(LoggingSettingsPageComponent))?.componentInstance;
   const input = (name: string): HTMLInputElement =>
      fixture.nativeElement.querySelector(`input[formcontrolname='${name}']`);
   const dialogs = () => TestBed.inject(MatDialog).openDialogs;

   async function settle(): Promise<void> {
      for(let i = 0; i < 5; i++) {
         // the routed page is not reached by the host's change detection on its own
         fixture.debugElement.query(By.directive(LoggingSettingsPageComponent))
            ?.injector.get(ChangeDetectorRef).markForCheck();
         fixture.detectChanges(false);
         await wait(0);
      }
   }

   function flushOthers(): void {
      http.match(r => r.url !== GET_URI && r.url !== SET_URI).forEach(r => r.flush(null));
   }

   async function type(name: string, value: string): Promise<void> {
      const el = input(name);
      el.value = value;
      el.dispatchEvent(new Event("input"));
      await settle();
   }

   async function click(label: string): Promise<void> {
      const button = Array.from(fixture.nativeElement.querySelectorAll("button") as NodeListOf<HTMLButtonElement>)
         .find(b => b.textContent.trim() === label);
      expect(button).toBeTruthy();
      button.click();
      await settle();
   }

   // Apply is debounced by the editor panel
   async function clickApply(): Promise<void> {
      await click("_#(js:Apply)");
      await wait(300);
      await settle();
   }

   async function apply(): Promise<any> {
      await clickApply();
      const req = http.expectOne(SET_URI);
      const body = JSON.parse(JSON.stringify(req.request.body));
      req.flush({});
      await settle();
      return body;
   }

   async function editLevelInDialog(level: string): Promise<void> {
      const table: LoggingLevelTableComponent =
         fixture.debugElement.query(By.directive(LoggingLevelTableComponent)).componentInstance;
      fixture.ngZone.run(() => table.editLevel(table.loggingLevels[0]));
      await settle();
      const dialog = dialogs()[0].componentInstance as AddLoggingLevelDialogComponent;
      dialog.form.get("level").setValue(level);
      dialog.onFormChanged();
      await settle();
      const ok = Array.from(document.querySelectorAll("mat-dialog-actions button") as NodeListOf<HTMLButtonElement>)
         .find(b => b.textContent.trim() === "_#(OK)");
      expect(ok).toBeTruthy();
      ok.click();
      await settle();
   }

   // navigates away; answers "stay" if the unsaved-changes dialog opens
   async function leave(): Promise<{ prompts: number, navigated: boolean }> {
      const result = fixture.ngZone.run(() => TestBed.inject(Router).navigateByUrl("/other"));
      await settle();
      const prompts = dialogs().length;

      if(prompts) {
         dialogs()[0].close(false);
         await settle();
      }

      return { prompts, navigated: await result };
   }

   beforeEach(async () => {
      await TestBed.configureTestingModule({
         imports: [
            HttpClientTestingModule, NoopAnimationsModule, LoggingHostComponent, OtherPageComponent,
            RouterModule.forRoot([
               { path: "logging", children: LOGGING_ROUTES },
               { path: "other", component: OtherPageComponent }
            ])
         ]
      }).compileComponents();

      http = TestBed.inject(HttpTestingController);
      fixture = TestBed.createComponent(LoggingHostComponent);
      fixture.detectChanges(false);
      expect(await fixture.ngZone.run(() => TestBed.inject(Router).navigateByUrl("/logging"))).toBe(true);
      await settle();
      flushOthers();
      http.expectOne(GET_URI).flush(loadedSettings());
      await settle();
      flushOthers();
   });

   afterEach(() => {
      TestBed.inject(MatDialog).closeAll();
   });

   it("should restore the applied value of a form field on Reset", async () => {
      await type("maxLogSize", "7");
      expect((await apply()).fileSettings.maxLogSize).toBe(7);

      await type("maxLogSize", "9");
      expect(page().model.fileSettings.maxLogSize).toBe(7);

      await click("_#(js:Reset)");
      expect(input("maxLogSize").value).toBe("7");
      expect(page().newModel.fileSettings.maxLogSize).toBe(7);
   });

   it("should restore the applied level of a level edited in the dialog on Reset", async () => {
      await type("maxLogSize", "7");
      await apply();

      await editLevelInDialog("error");
      expect(page().newModel.logLevels[0].level).toBe("error");
      expect(page().model.logLevels[0].level).toBe("debug");

      await click("_#(js:Reset)");
      expect(page().newModel.logLevels[0].level).toBe("debug");
   });

   it("should prompt when leaving with an edit made after an Apply", async () => {
      await type("maxLogSize", "7");
      await apply();
      await type("maxLogSize", "9");

      const { prompts, navigated } = await leave();
      expect(prompts).toBe(1);
      expect(navigated).toBe(false);
      expect(TestBed.inject(Location).path()).toBe("/logging");
      expect(input("maxLogSize").value).toBe("9");
   });

   it("should not prompt when leaving after an applied level edit with no further edits", async () => {
      await editLevelInDialog("error");
      expect((await apply()).logLevels[0].level).toBe("error");

      const { prompts, navigated } = await leave();
      expect(prompts).toBe(0);
      expect(navigated).toBe(true);
   });

   it("should post the current edits on a second Apply", async () => {
      await type("maxLogSize", "7");
      await apply();
      await type("maxLogSize", "9");
      expect((await apply()).fileSettings.maxLogSize).toBe(9);
      expect(page().model.fileSettings.maxLogSize).toBe(9);

      const { prompts, navigated } = await leave();
      expect(prompts).toBe(0);
      expect(navigated).toBe(true);
   });

   it("should keep the saved state when the Apply fails", async () => {
      vi.spyOn(TestBed.inject(ErrorHandlerService), "showDialog").mockReturnValue(EMPTY);
      await type("maxLogSize", "7");
      await clickApply();
      http.expectOne(SET_URI).flush({ message: "rejected" }, { status: 500, statusText: "Internal Server Error" });
      await settle();

      expect(page().model.fileSettings.maxLogSize).toBe(5);
      expect((await leave()).prompts).toBe(1);

      await click("_#(js:Reset)");
      expect(input("maxLogSize").value).toBe("5");
   });
});
