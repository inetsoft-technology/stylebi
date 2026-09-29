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
import { NO_ERRORS_SCHEMA, SimpleChange } from "@angular/core";
import { waitForAsync, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatDialogModule } from "@angular/material/dialog";
import { IdentityType } from "../../../../../../../shared/data/identity-type";
import { EditIdentityPaneModel, EditOrganizationPaneModel } from "../edit-identity-pane/edit-identity-pane.model";
import { EditIdentityViewComponent } from "./edit-identity-view.component";
import { Subject } from "rxjs";

describe("EditIdentityViewComponent", () => {
   let component: EditIdentityViewComponent;
   let fixture: ComponentFixture<EditIdentityViewComponent>;
   let identityEditableSubject: Subject<boolean>;

   beforeEach(waitForAsync(() => {
      identityEditableSubject = new Subject<boolean>();
      TestBed.configureTestingModule({
         imports: [
            FormsModule,
            MatDialogModule,
            ReactiveFormsModule,
            HttpClientTestingModule,
            EditIdentityViewComponent
         ],
         schemas: [NO_ERRORS_SCHEMA]
      })
             .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(EditIdentityViewComponent);
      component = fixture.componentInstance;
      component.identityEditableChanges = identityEditableSubject;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });

   // Issue #77116: the organization pane lists the themes of the edited organization
   it("should request the edited organization's themes", () => {
      const httpTestingController = TestBed.inject(HttpTestingController);
      const model = <EditOrganizationPaneModel> {
         ...createModel(),
         id: "orgy",
         properties: [],
         localesList: [],
         currentUserName: "admin"
      };

      setModel(IdentityType.ORGANIZATION, model);

      const req = httpTestingController.expectOne(r => r.url === "../api/em/security/themes");
      expect(req.request.params.get("orgId")).toBe("orgy");
      req.flush({themes: [{id: "ty", name: "ty"}]});
      expect(component.themes).toEqual([{id: "ty", name: "ty"}]);
   });

   it("should not pass an organization when requesting a user's themes", () => {
      const httpTestingController = TestBed.inject(HttpTestingController);

      setModel(IdentityType.USER, createModel());

      const req = httpTestingController.expectOne(r => r.url === "../api/em/security/themes");
      expect(req.request.params.has("orgId")).toBe(false);
   });

   function setModel(type: IdentityType, model: EditIdentityPaneModel): void {
      component.type = type;
      component.model = model;
      component.ngOnChanges({model: new SimpleChange(null, model, true)});
   }

   function createModel(): EditIdentityPaneModel {
      return <EditIdentityPaneModel> {
         name: "orgy",
         organization: "orgy",
         members: [],
         roles: [],
         permittedIdentities: [],
         identityNames: [],
         editable: true,
         theme: "ty"
      };
   }
});
