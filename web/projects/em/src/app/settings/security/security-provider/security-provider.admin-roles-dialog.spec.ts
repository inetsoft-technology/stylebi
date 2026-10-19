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

/**
 * System / Organization Administrator Roles dialog — regression tests for Bug #78720.
 *
 * Uses the real AuthenticationProviderDetailViewComponent with its real LDAP and Database
 * provider views, the real SecurityProviderService, the real MatDialog and the real
 * SystemAdminRolesDialogComponent. Only the editor panel is stubbed, with a real Apply button
 * bound to applyDisabled, and HTTP is answered by HttpTestingController.
 *
 * Contract:
 *   - Escape, a backdrop click and the dialog's Cancel button leave the roles field unchanged
 *     and keep Apply disabled (Escape/backdrop close with undefined, Cancel with null).
 *   - OK after removing every role still clears the field, enables Apply and saves no roles.
 *   - A role added in the dialog is discarded when the dialog is then closed by Escape.
 *   - After Escape, editing another field and clicking Apply saves the original roles.
 */

import { provideHttpClient } from "@angular/common/http";
import { HttpTestingController, provideHttpClientTesting } from "@angular/common/http/testing";
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { provideNoopAnimations } from "@angular/platform-browser/animations";
import { provideRouter } from "@angular/router";
import { of } from "rxjs";
import { AppInfoService } from "../../../../../../shared/util/app-info.service";
import { CodemirrorService } from "../../../../../../shared/util/codemirror/codemirror.service";
import { EditorPanelComponent } from "../../../common/util/editor-panel/editor-panel.component";
import { OrganizationDropdownService } from "../../../navbar/organization-dropdown.service";
import { AuthenticationProviderDetailViewComponent } from "./authentication-provider-detail-view/authentication-provider-detail-view.component";
import { SecurityProviderType } from "./security-provider-model/security-provider-type.enum";
import { SecurityProviderService } from "./security-provider.service";

@Component({
   selector: "em-editor-panel",
   standalone: true,
   template: `<ng-content></ng-content>
   <button class="test-apply" [disabled]="applyDisabled" (click)="applyClicked.emit()">Apply</button>`
})
class StubEditorPanelComponent {
   @Input() applyDisabled = false;
   @Output() applyClicked = new EventEmitter<any>();
   @Output() resetClicked = new EventEmitter<any>();
}

const API = "../api/em/security/";
const SYS_ROLES_LABEL = "_#(System Administrator Roles)";
const ORG_ROLES_LABEL = "_#(Organization Administrator Roles)";

function ldapProvider(): any {
   return {
      providerName: "Corp LDAP", oldName: "Corp LDAP", providerType: SecurityProviderType.LDAP,
      ldapProviderEnabled: true, dbProviderEnabled: true, customProviderEnabled: true,
      ldapProviderModel: {
         ldapServer: SecurityProviderType.GENERIC, protocol: "ldap", startTls: true,
         hostName: "ldap.example.com", hostPort: 389, rootDN: "dc=example,dc=com",
         adminID: "cn=admin", password: "secret", confirmPassword: "secret", useCredential: false,
         searchTree: true, sysAdminRoles: ["Administrator", "Auditor"],
         userFilter: "(objectClass=person)", userBase: "ou=people", userAttr: "uid",
         mailAttr: "mail", groupFilter: "(objectClass=group)", groupBase: "ou=groups",
         groupAttr: "cn", roleFilter: "(objectClass=role)", roleBase: "ou=roles", roleAttr: "cn"
      }
   };
}

function databaseProvider(): any {
   return {
      providerName: "Accounts", oldName: "Accounts", providerType: SecurityProviderType.DATABASE,
      ldapProviderEnabled: true, dbProviderEnabled: true, customProviderEnabled: true,
      dbProviderModel: {
         driver: "org.h2.Driver", url: "jdbc:h2:mem:users", requiresLogin: true,
         useCredential: false, user: "sa", password: "pw", hashAlgorithm: "BCRYPT",
         appendSalt: true,
         userQuery: "select password from users where name = ?",
         userListQuery: "select name from users",
         groupListQuery: "select name from groups",
         roleListQuery: "select name from roles",
         userRolesQuery: "select role from user_roles where name = ?",
         groupUsersQuery: "select name from users where grp = ?",
         organizationListQuery: "",
         sysAdminRoles: "Administrator, Auditor",
         orgAdminRoles: "OrgBoss"
      }
   };
}

interface RolesFieldCase {
   label: string;
   view: string;
   model: () => any;
   placeholder: string;
   // index of the roles field's suffix button among the view's read-only role fields
   buttonIndex: number;
   multiTenant: boolean;
   before: string;
   posted: (body: any) => any;
   postedWhenCleared: any;
   postedOriginal: any;
   otherField: string;
}

const FIELDS: RolesFieldCase[] = [
   {
      label: "LDAP system admin roles", view: "em-ldap-provider-view", model: ldapProvider,
      placeholder: SYS_ROLES_LABEL, buttonIndex: 0, multiTenant: false,
      before: "Administrator, Auditor", posted: b => b.ldapProviderModel.sysAdminRoles,
      postedWhenCleared: [], postedOriginal: ["Administrator", "Auditor"], otherField: "_#(Host Name)"
   },
   {
      label: "Database system admin roles", view: "em-database-provider-view", model: databaseProvider,
      placeholder: SYS_ROLES_LABEL, buttonIndex: 0, multiTenant: false,
      before: "Administrator, Auditor", posted: b => b.dbProviderModel.sysAdminRoles,
      postedWhenCleared: "", postedOriginal: "Administrator, Auditor", otherField: "_#(URL)"
   },
   {
      label: "Database org admin roles (multi-tenant)", view: "em-database-provider-view",
      model: databaseProvider, placeholder: ORG_ROLES_LABEL, buttonIndex: 1, multiTenant: true,
      before: "OrgBoss", posted: b => b.dbProviderModel.orgAdminRoles,
      postedWhenCleared: "", postedOriginal: "OrgBoss", otherField: "_#(URL)"
   }
];

const tick = () => new Promise(resolve => setTimeout(resolve, 20));

describe("Administrator Roles dialog dismissal (Bug #78720)", () => {
   let fixture: ComponentFixture<AuthenticationProviderDetailViewComponent>;
   let http: HttpTestingController;
   let submitted: any[];

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [AuthenticationProviderDetailViewComponent],
         providers: [
            provideHttpClient(), provideHttpClientTesting(), provideNoopAnimations(), provideRouter([]),
            SecurityProviderService,
            {
               provide: CodemirrorService,
               useValue: {
                  createCodeMirrorInstance: () => ({
                     on: vi.fn(), getValue: () => "", setValue: vi.fn(), refresh: vi.fn(),
                     focus: vi.fn(), getCursor: vi.fn(), setCursor: vi.fn(), toTextArea: vi.fn()
                  })
               }
            },
            { provide: AppInfoService, useValue: { isEnterprise: () => of(true) } },
            { provide: OrganizationDropdownService, useValue: { refreshProviders: vi.fn() } }
         ]
      });
      TestBed.overrideComponent(AuthenticationProviderDetailViewComponent, {
         remove: { imports: [EditorPanelComponent] },
         add: { imports: [StubEditorPanelComponent] }
      });
      http = TestBed.inject(HttpTestingController);
   });

   afterEach(() => {
      document.querySelectorAll(".cdk-overlay-container").forEach(e => e.innerHTML = "");
   });

   async function settle(n = 10): Promise<void> {
      for(let i = 0; i < n; i++) {
         fixture.detectChanges(false);
         await tick();
      }
   }

   async function load(model: any, multiTenant: boolean): Promise<void> {
      fixture = TestBed.createComponent(AuthenticationProviderDetailViewComponent);
      fixture.componentInstance.isMultiTenant = multiTenant;
      submitted = [];
      const service = TestBed.inject(SecurityProviderService);

      // as the detail page does: onSubmit -> updateAuthenticationProvider
      fixture.componentInstance.onSubmit.subscribe((form: any) => {
         service.updateAuthenticationProvider(model.providerName, form);
         submitted.push(
            http.expectOne(API + "edit-authentication-provider/" + model.providerName).request.body);
      });

      fixture.detectChanges(false);
      await tick();
      // the page binds the provider model after the first change-detection pass
      fixture.componentInstance.model = model;
      await settle(4);
   }

   const rolesInput = (field: RolesFieldCase): HTMLInputElement =>
      fixture.nativeElement.querySelector(`${field.view} input[placeholder="${field.placeholder}"]`);
   const applyButton = (): HTMLButtonElement => fixture.nativeElement.querySelector("button.test-apply");
   const dialogElement = (): HTMLElement => document.querySelector("em-system-admin-roles-dialog");

   function dialogButton(text: string): HTMLElement {
      return Array.from(dialogElement().querySelectorAll("button"))
         .find(b => b.textContent.trim() === text) as HTMLElement;
   }

   async function openRolesDialog(field: RolesFieldCase): Promise<void> {
      const roleButtons = Array.from(fixture.nativeElement.querySelectorAll(`${field.view} button[matSuffix]`))
         .filter((b: HTMLElement) => b.closest("mat-form-field").querySelector("input[readonly]"));
      const button = roleButtons[field.buttonIndex] as HTMLElement;
      button.click();
      fixture.detectChanges(false);
      http.expectOne(API + "get-roles").flush({
         ids: [
            { name: "Administrator", orgID: null },
            { name: "Auditor", orgID: null },
            { name: "OrgBoss", orgID: null }
         ]
      });
      await settle(3);
      expect(dialogElement()).not.toBeNull();
   }

   async function pressEscape(): Promise<void> {
      const event = new KeyboardEvent("keydown", { key: "Escape", code: "Escape", bubbles: true });

      // CDK's overlay keyboard dispatcher still reads keyCode
      Object.defineProperty(event, "keyCode", { get: () => 27 });
      dialogElement().dispatchEvent(event);
      await settle();
   }

   async function clickBackdrop(): Promise<void> {
      (document.querySelector(".cdk-overlay-backdrop") as HTMLElement).click();
      await settle();
   }

   async function clickCancel(): Promise<void> {
      dialogButton("_#(Cancel)").click();
      await settle();
   }

   async function removeAllRolesAndClickOk(): Promise<void> {
      let remove: HTMLElement;

      while((remove = dialogElement().querySelector(
         "button[aria-label=\"_#(Remove System Administrator Role)\"]")))
      {
         remove.click();
         fixture.detectChanges(false);
         await tick();
      }

      dialogButton("_#(OK)").click();
      await settle();
   }

   async function addRoleInDialog(role: string): Promise<void> {
      const input = dialogElement().querySelector("input") as HTMLInputElement;
      input.value = role;
      input.dispatchEvent(new Event("input"));
      fixture.detectChanges(false);
      await tick();
      (dialogElement().querySelector(
         "button[aria-label=\"_#(Add System Administrator Role)\"]") as HTMLElement).click();
      fixture.detectChanges(false);
      await tick();
   }

   async function typeInto(placeholder: string, value: string): Promise<void> {
      const input: HTMLInputElement = fixture.nativeElement.querySelector(`input[placeholder="${placeholder}"]`);
      input.value = value;
      input.dispatchEvent(new Event("input"));
      await settle(3);
   }

   const DISMISSALS: [string, () => Promise<void>][] = [
      ["Escape", pressEscape],
      ["a backdrop click", clickBackdrop],
      ["Cancel", clickCancel]
   ];

   for(const field of FIELDS) {
      describe(field.label, () => {
         for(const [how, dismiss] of DISMISSALS) {
            it(`keeps the roles and leaves Apply disabled when the dialog is closed by ${how}`, async () => {
               await load(field.model(), field.multiTenant);
               expect(rolesInput(field).value).toBe(field.before);
               expect(applyButton().disabled).toBe(true);

               await openRolesDialog(field);
               await dismiss();

               expect(dialogElement()).toBeNull();
               expect(rolesInput(field).value).toBe(field.before);
               expect(applyButton().disabled).toBe(true);
               // nothing was saved and no further security request was made
               expect(http.match(req => req.url.startsWith(API)).length).toBe(0);
            });
         }

         it("still clears the roles when every role is removed and OK is clicked", async () => {
            await load(field.model(), field.multiTenant);
            await openRolesDialog(field);
            await removeAllRolesAndClickOk();

            expect(dialogElement()).toBeNull();
            expect(rolesInput(field).value).toBe("");
            expect(applyButton().disabled).toBe(false);

            applyButton().click();
            await settle(2);

            expect(submitted.length).toBe(1);
            expect(field.posted(submitted[0])).toEqual(field.postedWhenCleared);
         });

         it("keeps the roles after Escape, then clears them when the dialog is reopened and cleared with OK",
            async () => {
               await load(field.model(), field.multiTenant);
               await openRolesDialog(field);
               await pressEscape();
               expect(rolesInput(field).value).toBe(field.before);

               await openRolesDialog(field);
               await removeAllRolesAndClickOk();
               expect(rolesInput(field).value).toBe("");
               expect(applyButton().disabled).toBe(false);
            });

         it("discards a role added in the dialog when the dialog is then closed by Escape", async () => {
            await load(field.model(), field.multiTenant);
            await openRolesDialog(field);
            await addRoleInDialog("NewRole");
            expect(dialogElement().textContent).toContain("NewRole");
            await pressEscape();
            expect(dialogElement()).toBeNull();
            expect(rolesInput(field).value).toBe(field.before);
            expect(applyButton().disabled).toBe(true);
            await openRolesDialog(field);
            expect(dialogElement().textContent).not.toContain("NewRole");
         });

         it("saves the original roles when another field is edited and applied after Escape", async () => {
            await load(field.model(), field.multiTenant);
            await openRolesDialog(field);
            await pressEscape();
            // the bug saved the blanked roles along with any other edit
            await typeInto(field.otherField, "changed.example.com");
            expect(rolesInput(field).value).toBe(field.before);
            expect(applyButton().disabled).toBe(false);
            applyButton().click();
            await settle(2);
            expect(submitted.length).toBe(1);
            expect(field.posted(submitted[0])).toEqual(field.postedOriginal);
         });
      });
   }
});
