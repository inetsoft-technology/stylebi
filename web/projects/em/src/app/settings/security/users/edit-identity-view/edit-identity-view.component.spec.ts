/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatDialogModule } from "@angular/material/dialog";
import { EditIdentityViewComponent } from "./edit-identity-view.component";
import { Subject } from "rxjs";

describe("EditIdentityViewComponent", () => {
   let component: EditIdentityViewComponent;
   let fixture: ComponentFixture<EditIdentityViewComponent>;
   let identityEditableSubject: Subject<boolean>;

   beforeEach(async(() => {
      identityEditableSubject = new Subject<boolean>();
      TestBed.configureTestingModule({
         imports: [
            FormsModule,
            MatDialogModule,
            ReactiveFormsModule,
            HttpClientTestingModule
         ],
         declarations: [EditIdentityViewComponent],
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
});