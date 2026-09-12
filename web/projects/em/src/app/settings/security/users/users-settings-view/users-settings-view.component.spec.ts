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
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatCardModule } from "@angular/material/card";
import { MatDialog, MatDialogModule } from "@angular/material/dialog";
import { MatDividerModule } from "@angular/material/divider";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatGridListModule } from "@angular/material/grid-list";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { RouterModule } from "@angular/router";
import { of } from "rxjs";
import { IdentityType } from "../../../../../../../shared/data/identity-type";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";
import { SecurityTableViewComponent } from "../../security-table-view/security-table-view.component";
import { SecurityTreeViewComponent } from "../../security-tree-view/security-tree-view.component";
import { UsersSettingsViewComponent } from "./users-settings-view.component";

describe("UsersSettingsViewComponent", () => {
   let component: UsersSettingsViewComponent;
   let fixture: ComponentFixture<UsersSettingsViewComponent>;
   let httpMock: HttpTestingController;
   let dialogData: any;

   beforeEach(() => {
      dialogData = undefined;
      const dialogStub = {
         open: (_cmp: any, config: any) => {
            dialogData = config?.data;
            return { afterClosed: () => of(true) };
         }
      };

      TestBed.configureTestingModule({
         imports: [
            NoopAnimationsModule,
            RouterModule.forRoot([]),
            SecurityTreeViewComponent,
            MatSelectModule,
            MatDialogModule,
            HttpClientTestingModule,
            MatInputModule,
            MatGridListModule,
            MatDividerModule,
            MatCardModule,
            MatFormFieldModule,
            FormsModule,
            ReactiveFormsModule,
            SecurityTableViewComponent,
            UsersSettingsViewComponent],
         providers: [
            AppInfoService,
            { provide: MatDialog, useValue: dialogStub }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      })
         .compileComponents();

      fixture = TestBed.createComponent(UsersSettingsViewComponent);
      component = fixture.componentInstance;
      httpMock = TestBed.inject(HttpTestingController);
   });

   it("should create", () => {
      fixture.detectChanges();
      expect(component).toBeTruthy();
   });

   it("lists affected tasks in the confirm dialog, then emits delete on confirm", () => {
      component.selectedProvider = "provider1";
      component.selectedNodes = [
         { identityID: { name: "u1", orgID: "o1" }, type: IdentityType.USER } as any
      ];
      const emitSpy = vi.spyOn(component.deleteIdentities, "emit");

      component.delete();

      const req = httpMock.expectOne(r => r.url.endsWith("/affected-tasks"));
      expect(req.request.method).toBe("POST");
      req.flush({ ownedTasks: ["Daily Report"], executeAsTasks: ["Weekly Export"] });

      expect(dialogData.content).toContain("Daily Report");
      expect(dialogData.content).toContain("Weekly Export");
      expect(emitSpy).toHaveBeenCalled();
   });

   it("falls back to the plain confirm and still deletes when the impact check fails", () => {
      component.selectedProvider = "provider1";
      component.selectedNodes = [
         { identityID: { name: "u1", orgID: "o1" }, type: IdentityType.USER } as any
      ];
      const emitSpy = vi.spyOn(component.deleteIdentities, "emit");

      component.delete();

      const req = httpMock.expectOne(r => r.url.endsWith("/affected-tasks"));
      req.flush("err", { status: 500, statusText: "Server Error" });

      expect(dialogData).toBeDefined();
      expect(emitSpy).toHaveBeenCalled();
   });
});
