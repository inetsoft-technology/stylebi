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
import { TestBed } from "@angular/core/testing";
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { RepositoryEntryType } from "../../../../../../../shared/data/repository-entry-type.enum";
import { ErrorHandlerService } from "../../../../common/util/error/error-handler.service";
import {
   RepositoryScriptEditorModel,
   RepositoryScriptSettingsPageComponent
} from "./repository-script-settings-page.component";

/**
 * Bug #80303 (round 1 follow-up): apply() must surface a server-side rejection of the script
 * name (ScriptLibraryValidator.requireValidName) instead of silently swallowing it — the
 * subscribe() call had no error callback, so a rejected rename produced no user-visible error.
 */
describe("RepositoryScriptSettingsPageComponent apply() error surfacing", () => {
   let httpMock: HttpTestingController;
   let comp: RepositoryScriptSettingsPageComponent;

   beforeEach(async () => {
      await TestBed.configureTestingModule({
         imports: [RepositoryScriptSettingsPageComponent, HttpClientTestingModule],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      httpMock = TestBed.inject(HttpTestingController);
      const fixture = TestBed.createComponent(RepositoryScriptSettingsPageComponent);
      comp = fixture.componentInstance;
      comp.model = <RepositoryScriptEditorModel>{
         path: "/Script",
         type: RepositoryEntryType.SCRIPT,
         scriptSettings: { name: "a-b", oname: "oldName", description: "" }
      };
      fixture.detectChanges();
   });

   afterEach(() => {
      httpMock.verify();
   });

   it("shows the server's error message instead of failing silently", () => {
      const errorService = TestBed.inject(ErrorHandlerService);
      const showSnackBarSpy = vi.spyOn(errorService, "showSnackBar");

      comp.apply();

      const req = httpMock.expectOne(r =>
         r.url.endsWith("/api/em/settings/content/repository/script"));
      req.flush({ message: "Invalid script name" },
         { status: 500, statusText: "Internal Server Error" });

      expect(showSnackBarSpy).toHaveBeenCalled();
   });
});
