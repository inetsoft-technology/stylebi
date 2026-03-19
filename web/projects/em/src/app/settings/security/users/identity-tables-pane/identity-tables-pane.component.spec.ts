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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { SimpleChange } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { IdentityType } from "../../../../../../../shared/data/identity-type";
import { IdentityModel } from "../../security-table-view/identity-model";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatCardModule } from "@angular/material/card";
import { MatDividerModule } from "@angular/material/divider";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { MatListModule } from "@angular/material/list";
import { MatSelectModule } from "@angular/material/select";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { IdentityClipboardService } from "../../security-table-view/identity-clipboard.service";
import { SecurityTableViewModule } from "../../security-table-view/security-table-view.module";
import { SecurityTreeDialogModule } from "../../security-tree-dialog/security-tree-dialog.module";
import { IdentityTablesPaneComponent } from "./identity-tables-pane.component";
import { PropertyTableViewModule } from "../../property-table-view/property-table-view.module";

describe("IdentityTablesPaneComponent", () => {
   let component: IdentityTablesPaneComponent;
   let fixture: ComponentFixture<IdentityTablesPaneComponent>;

   beforeEach(() => {
      const mockClipboardService = {
         canPaste: () => false,
         hasContent: () => false,
         copiedCount: () => 0,
         copiedTotal: () => 0,
         copy: () => {},
         paste: () => null
      } as any;

      TestBed.configureTestingModule({
         imports: [
            NoopAnimationsModule,
            HttpClientTestingModule,
            ReactiveFormsModule,
            MatCardModule,
            MatDividerModule,
            MatListModule,
            MatFormFieldModule,
            MatInputModule,
            MatSelectModule,
            FormsModule,
            SecurityTableViewModule,
            PropertyTableViewModule,
            SecurityTreeDialogModule,
         ],
         declarations: [IdentityTablesPaneComponent],
         providers: [
            { provide: IdentityClipboardService, useValue: mockClipboardService }
         ]
      })
         .compileComponents();
   });

   beforeEach(() => {
      fixture = TestBed.createComponent(IdentityTablesPaneComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });

   describe("paste handlers", () => {
      const alice: IdentityModel = { identityID: { name: "alice", orgID: null }, type: IdentityType.USER };
      const editorRole: IdentityModel = { identityID: { name: "editor", orgID: null }, type: IdentityType.ROLE };

      it("pasteMembers should replace members and emit membersChanged", () => {
         const emitted: IdentityModel[][] = [];
         component.membersChanged.subscribe(v => emitted.push(v));
         component.pasteMembers([alice]);
         expect(component.members).toEqual([alice]);
         expect(emitted).toEqual([[alice]]);
      });

      it("pasteMembers should emit empty array when all pasted identities are filtered by addMembers guards", () => {
         component.type = IdentityType.USER;
         component.name = "alice";
         component.members = [];
         const emitted: IdentityModel[][] = [];
         component.membersChanged.subscribe(v => emitted.push(v));
         // alice is the current identity (self-reference) and editorRole is a ROLE — both blocked by addMembers
         component.pasteMembers([alice, editorRole]);
         expect(component.members).toEqual([]);
         expect(emitted).toEqual([[]]);
      });

      it("pasteRoles should replace roles and emit rolesChanged", () => {
         const emitted: IdentityModel[][] = [];
         component.rolesChanged.subscribe(v => emitted.push(v));
         component.pasteRoles([editorRole]);
         expect(component.roles).toEqual([editorRole]);
         expect(emitted).toEqual([[editorRole]]);
      });

      it("pastePermittedIdentities should replace permittedIdentities and emit permittedIdentitiesChanged", () => {
         const emitted: IdentityModel[][] = [];
         component.permittedIdentitiesChanged.subscribe(v => emitted.push(v));
         component.pastePermittedIdentities([alice]);
         expect(component.permittedIdentities).toEqual([alice]);
         expect(emitted).toEqual([[alice]]);
      });
   });

   describe("membersPasteTypeFilter", () => {
      function setType(type: IdentityType): void {
         component.type = type;
         component.ngOnChanges({ type: new SimpleChange(null, type, false) });
      }

      it("should be [GROUP] for USER type", () => {
         setType(IdentityType.USER);
         expect(component.membersPasteTypeFilter).toEqual([IdentityType.GROUP]);
      });

      it("should be [USER, GROUP] for GROUP type", () => {
         setType(IdentityType.GROUP);
         expect(component.membersPasteTypeFilter).toEqual([IdentityType.USER, IdentityType.GROUP]);
      });

      it("should be [USER, GROUP] for ROLE type", () => {
         setType(IdentityType.ROLE);
         expect(component.membersPasteTypeFilter).toEqual([IdentityType.USER, IdentityType.GROUP]);
      });

      it("should be null for ORGANIZATION type", () => {
         setType(IdentityType.ORGANIZATION);
         expect(component.membersPasteTypeFilter).toBeNull();
      });

      it("should update when type changes", () => {
         setType(IdentityType.USER);
         expect(component.membersPasteTypeFilter).toEqual([IdentityType.GROUP]);

         setType(IdentityType.GROUP);
         expect(component.membersPasteTypeFilter).toEqual([IdentityType.USER, IdentityType.GROUP]);
      });
   });
});
