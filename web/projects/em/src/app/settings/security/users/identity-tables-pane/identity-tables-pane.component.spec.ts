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
import { SecurityTableViewComponent } from "../../security-table-view/security-table-view.component";
import { SecurityTreeDialogComponent } from "../../security-tree-dialog/security-tree-dialog.component";
import { IdentityTablesPaneComponent } from "./identity-tables-pane.component";
import { PropertyTableViewComponent } from "../../property-table-view/property-table-view.component";

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
            SecurityTableViewComponent,
            PropertyTableViewComponent,
            SecurityTreeDialogComponent,
            IdentityTablesPaneComponent
         ],
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

      it("pasteMembers should replace a non-empty members list", () => {
         const bob: IdentityModel = { identityID: { name: "bob", orgID: null }, type: IdentityType.USER };
         component.members = [alice];
         const emitted: IdentityModel[][] = [];
         component.membersChanged.subscribe(v => emitted.push(v));
         component.pasteMembers([bob]);
         expect(component.members).toEqual([bob]);
         expect(emitted).toEqual([[bob]]);
      });

      it("pasteMembers should restore previous members when all pasted identities are filtered by addMembers guards", () => {
         const bob: IdentityModel = { identityID: { name: "bob", orgID: null }, type: IdentityType.USER };
         component.type = IdentityType.USER;
         component.name = "alice";
         component.members = [bob];
         const emitted: IdentityModel[][] = [];
         component.membersChanged.subscribe(v => emitted.push(v));
         // alice is the current identity (self-reference) and editorRole is a ROLE — both blocked by addMembers
         component.pasteMembers([alice, editorRole]);
         expect(component.members).toEqual([bob]);
         expect(emitted.length).toBe(1);
         expect(emitted[0]).toEqual([bob]);
      });

      it("pasteMembers should restore empty previous list when all pasted identities are rejected", () => {
         component.type = IdentityType.USER;
         component.name = "alice";
         component.members = [];
         const emitted: IdentityModel[][] = [];
         component.membersChanged.subscribe(v => emitted.push(v));
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

   // Bug #77314: a site admin's members list for a global role is authoritative on the server, so a
   // paste that dropped other organizations' rows removed the role from every other org's holders.
   describe("pasteMembers into a global role", () => {
      const user = (name: string, orgID: string): IdentityModel =>
         ({ identityID: { name, orgID }, type: IdentityType.USER });
      const hostAdmin = user("admin", "host-org");
      const bob = user("bob", "host-org");
      const g7coa = user("g7coa", "g7d");
      const oa = user("oa", "g5a");
      const salesAdmin = user("orgadmin", "sales1");
      const g5aOrg: IdentityModel = { identityID: { name: "g5a", orgID: "g5a" }, type: IdentityType.ORGANIZATION };
      const editorRole: IdentityModel = { identityID: { name: "editor", orgID: "host-org" }, type: IdentityType.ROLE };
      let emitted: IdentityModel[][];

      beforeEach(() => {
         component.type = IdentityType.ROLE;
         component.name = "Organization Administrator";
         component.members = [hostAdmin, g7coa, oa, salesAdmin, g5aOrg];
         emitted = [];
         component.membersChanged.subscribe(v => emitted.push(v));
      });

      it("should keep other organizations' members and replace only the pasted organization's members", () => {
         component.globalRole = true;
         component.pasteMembers([bob]);
         expect(component.members).toEqual([g7coa, oa, salesAdmin, g5aOrg, bob]);
         expect(emitted).toEqual([[g7coa, oa, salesAdmin, g5aOrg, bob]]);
      });

      it("should replace every pasted organization's members when the clipboard spans several organizations", () => {
         const x = user("x", "g5a");
         component.globalRole = true;
         component.pasteMembers([oa, x, bob]);
         expect(component.members).toEqual([g7coa, salesAdmin, g5aOrg, oa, x, bob]);
      });

      it("should keep the current organization's members when every pasted identity is rejected", () => {
         component.globalRole = true;
         component.pasteMembers([editorRole]);
         expect(component.members).toEqual([hostAdmin, g7coa, oa, salesAdmin, g5aOrg]);
         expect(emitted).toEqual([[hostAdmin, g7coa, oa, salesAdmin, g5aOrg]]);
      });

      it("should still replace the whole list when the role is not a global role", () => {
         component.globalRole = false;
         component.pasteMembers([bob]);
         expect(component.members).toEqual([bob]);
         expect(emitted).toEqual([[bob]]);
      });
   });

   describe("membersPasteTypeFilter", () => {
      it("should be [GROUP] for USER type", () => {
         component.type = IdentityType.USER;
         expect(component.membersPasteTypeFilter).toEqual([IdentityType.GROUP]);
      });

      it("should be [USER, GROUP] for GROUP type", () => {
         component.type = IdentityType.GROUP;
         expect(component.membersPasteTypeFilter).toEqual([IdentityType.USER, IdentityType.GROUP]);
      });

      it("should be [USER, GROUP] for ROLE type", () => {
         component.type = IdentityType.ROLE;
         expect(component.membersPasteTypeFilter).toEqual([IdentityType.USER, IdentityType.GROUP]);
      });

      it("should be null for ORGANIZATION type", () => {
         component.type = IdentityType.ORGANIZATION;
         expect(component.membersPasteTypeFilter).toBeNull();
      });

      it("should update when type changes", () => {
         component.type = IdentityType.USER;
         expect(component.membersPasteTypeFilter).toEqual([IdentityType.GROUP]);

         component.type = IdentityType.GROUP;
         expect(component.membersPasteTypeFilter).toEqual([IdentityType.USER, IdentityType.GROUP]);
      });
   });
});
