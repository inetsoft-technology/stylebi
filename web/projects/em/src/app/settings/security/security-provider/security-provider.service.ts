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
import { HttpClient, HttpErrorResponse } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { MatBottomSheet, MatBottomSheetRef } from "@angular/material/bottom-sheet";
import { MatDialog } from "@angular/material/dialog";
import { Router } from "@angular/router";
import { NEVER, Observable, of, of as observableOf } from "rxjs";
import { catchError, map, switchMap } from "rxjs/operators";
import { MapModel } from "../../../../../../portal/src/app/common/data/map-model";
import { ErrorHandlerService } from "../../../common/util/error/error-handler.service";
import { convertToKey, IdentityId } from "../users/identity-id";
import { BaseQueryResult } from "./base-query-result/base-query-result.component";
import { InputQueryParamsDialogComponent } from "./input-query-params-dialog/input-query-params-dialog.component";
import { AuthenticationProviderModel } from "./security-provider-model/authentication-provider-model";
import { AuthorizationProviderModel } from "./security-provider-model/authorization-provider-model";
import { ConnectionStatus } from "./security-provider-model/connection-status";
import { DatabaseAuthenticationProviderModel } from "./security-provider-model/database-authentication-provider-model";
import { IdentityListModel } from "./security-provider-model/identity-list-model";
import { LdapAuthenticationProviderModel } from "./security-provider-model/ldap-authentication-provider-model";
import { SecurityProviderType } from "./security-provider-model/security-provider-type.enum";
import { SystemAdminRolesDialogComponent } from "./system-admin-roles-dialog/system-admin-roles-dialog.component";

const ADD_AUTHORIZATION_PROVIDER_URL = "../api/em/security/add-authorization-provider";
const EDIT_AUTHORIZATION_PROVIDER_URL = "../api/em/security/edit-authorization-provider/";
const ADD_AUTHENTICATION_PROVIDER_URL = "../api/em/security/add-authentication-provider";
const EDIT_AUTHENTICATION_PROVIDER_URL = "../api/em/security/edit-authentication-provider/";

@Injectable()
export class SecurityProviderService {
   defaultErrMsg = "_#(js:add.authorization.provider.error)";

   constructor(private router: Router,
               private http: HttpClient,
               private dialog: MatDialog,
               private bottomSheet: MatBottomSheet,
               private errorService: ErrorHandlerService)
   {
   }

   getAuthorizationModel(form: UntypedFormGroup): AuthorizationProviderModel {
      const name = form.value["providerName"].trim();
      const type = form.value["providerType"];
      let model: AuthorizationProviderModel = {providerName: name, providerType: type};

      if(type === SecurityProviderType.CUSTOM) {
         const customForm = <UntypedFormGroup> form.controls["customForm"];
         model.customProviderModel = {
            className: customForm.value["className"],
            jsonConfiguration: customForm.value["jsonConfiguration"]
         };
      }

      return model;
   }

   getAuthenticationModel(form: UntypedFormGroup): AuthenticationProviderModel {
      const name = form.value["providerName"].trim();
      const type = form.value["providerType"];
      let model: AuthenticationProviderModel = {providerName: name, providerType: type};
      let sysAdminRoles: string;
      let orgAdminRoles: string;

      switch(type) {
      case SecurityProviderType.LDAP:
         model.ldapProviderModel =
            <LdapAuthenticationProviderModel> Object.assign({},
               form.controls["ldapForm"].value,
               form.get("userSearch").value,
               form.get("groupSearch").value,
               form.get("roleSearch").value
            );

         sysAdminRoles = (<UntypedFormGroup>form.controls["ldapForm"]).controls["sysAdminRoles"].value;
         model.ldapProviderModel.sysAdminRoles = this.parseAdminRoles(sysAdminRoles);
         break;
      case SecurityProviderType.DATABASE:
         model.dbProviderModel =
            <DatabaseAuthenticationProviderModel> form.controls["dbForm"].value;
         sysAdminRoles = (<UntypedFormGroup>form.controls["dbForm"]).controls["sysAdminRoles"].value;
         model.dbProviderModel.sysAdminRoles = sysAdminRoles;
         orgAdminRoles = (<UntypedFormGroup>form.controls["dbForm"]).controls["orgAdminRoles"].value;
         model.dbProviderModel.orgAdminRoles = orgAdminRoles;
         break;
      case SecurityProviderType.CUSTOM:
         const customForm = <UntypedFormGroup> form.controls["customForm"];
         model.customProviderModel = {
            className: customForm.value["className"],
            jsonConfiguration: customForm.value["jsonConfiguration"]
         };
         break;
      }

      return model;
   }

   updateAuthorizationProvider(name: string, authorizationForm: UntypedFormGroup) {
      const url = !name ? ADD_AUTHORIZATION_PROVIDER_URL :
         EDIT_AUTHORIZATION_PROVIDER_URL + name;
      const model = this.getAuthorizationModel(authorizationForm);

      return this.http.post(url, model).pipe(
         catchError((error: HttpErrorResponse) => this.errorService.showSnackBar(error, this.defaultErrMsg))
      )
         .subscribe(() => this.routeToListView());
   }

   updateAuthenticationProvider(name: string, authenticationForm: UntypedFormGroup) {
      const url = !name ? ADD_AUTHENTICATION_PROVIDER_URL :
         EDIT_AUTHENTICATION_PROVIDER_URL + name;
      const model = this.getAuthenticationModel(authenticationForm);

      return this.http.post(url, model).pipe(
         catchError((error: HttpErrorResponse) => this.errorService.showSnackBar(error, this.defaultErrMsg))
      ).subscribe(() => this.routeToListView());
   }

   testConnection(authenticationForm: UntypedFormGroup) {
      const model = this.getAuthenticationModel(authenticationForm);
      return this.http.post<ConnectionStatus>("../api/em/security/get-connection-status", model);
   }

   testDatabaseConnection(authenticationForm: UntypedFormGroup) {
      const model = this.getAuthenticationModel(authenticationForm);
      return this.http.post<ConnectionStatus>("../api/em/security/get-database-connection-status", model);
   }

   getUsers(authenticationForm: UntypedFormGroup): Observable<IdentityListModel> {
      const model = this.getAuthenticationModel(authenticationForm);
      return this.http.post<IdentityListModel>("../api/em/security/get-users", model);
   }

   getGroups(authenticationForm: UntypedFormGroup): Observable<IdentityListModel> {
      const model = this.getAuthenticationModel(authenticationForm);
      return this.http.post<IdentityListModel>("../api/em/security/get-groups", model);
   }

   getOrganizations(authenticationForm: UntypedFormGroup): Observable<IdentityListModel> {
      const model = this.getAuthenticationModel(authenticationForm);
      return this.http.post<IdentityListModel>("../api/em/security/get-organizations", model);
   }

   getRoles(authenticationForm: UntypedFormGroup): Observable<IdentityListModel> {
      const model = this.getAuthenticationModel(authenticationForm);
      return this.http.post<IdentityListModel>("../api/em/security/get-roles", model);
   }

   getDefaultOrganization(): Observable<string> {
      return this.http.get<string>("../api/em/security/get-default-organization");
   }

   routeToListView(): void {
      this.router.navigate(["/settings/security/provider"]);
   }

   getAdminRoles(currentRolesString: string, authenticationForm: UntypedFormGroup, isSysAdmin: boolean): Observable<any> {
      const model = this.getAuthenticationModel(authenticationForm);
      return this.http.post<IdentityListModel>("../api/em/security/get-roles", model).pipe(
         catchError(() => observableOf(<IdentityListModel> {ids: []})),
         switchMap(rolesModel => this.openSystemAdminDialog(this.parseAdminRoles(currentRolesString), rolesModel.ids, isSysAdmin)),
         map(rolesList => this.formatAdminRolesString(rolesList))
      );
   }

   private openSystemAdminDialog(currentRoles: string[], allRoles: IdentityId[], sysAdmin: boolean): Observable<string[]> {
      return this.dialog.open(SystemAdminRolesDialogComponent, {
         width: "40vw",
         height: "75vh",
         data: {
            allRoles: allRoles,
            currentRoles: currentRoles,
            isSysAdmin: sysAdmin
         }
      }).afterClosed().pipe(
         switchMap((roles: string[]) => roles === null ? NEVER : of(roles))
      );
   }

   parseAdminRoles(rolesString: string): string[] {
      return !rolesString ? [] : rolesString.split(",").map(role => role.trim());
   }

   formatAdminRolesString(rolesList: string[]): string {
      return !rolesList ? "" : rolesList.join(", ");
   }

   openQueryResults(queryResult: string[], label?: string, isPreSorted?: boolean): MatBottomSheetRef {
      return this.bottomSheet.open(BaseQueryResult, {
         data: {
            queryResult,
            label,
            selectable: !!label,
            isPreSorted: isPreSorted
         }
      });
   }

   getUser(authenticationForm: UntypedFormGroup, userName: IdentityId): Observable<MapModel<string, string>> {
      const model = this.getAuthenticationModel(authenticationForm);
      return this.http.post<MapModel<string, string>>(
         "../api/em/security/get-user/" + encodeURIComponent(convertToKey(userName)), model);
   }

   getUserEmails(authenticationForm: UntypedFormGroup, userName: IdentityId): Observable<IdentityListModel> {
      const model = this.getAuthenticationModel(authenticationForm);
      return this.http.post<IdentityListModel>(
         "../api/em/security/get-user-emails/" + encodeURIComponent(convertToKey(userName)), model);
   }

   getUserRoles(authenticationForm: UntypedFormGroup, userName: IdentityId): Observable<IdentityListModel> {
      const model = this.getAuthenticationModel(authenticationForm);
      return this.http.post<IdentityListModel>(
         "../api/em/security/get-roles/" + encodeURIComponent(convertToKey(userName)), model);
   }

   getGroupUsers(authenticationForm: UntypedFormGroup, group: IdentityId): Observable<IdentityListModel> {
      const model = this.getAuthenticationModel(authenticationForm);
      return this.http.post<IdentityListModel>(
         "../api/em/security/group/users/" + encodeURIComponent(convertToKey(group)), model);
   }

   getOrganizationName(authenticationForm: UntypedFormGroup, organization: string): Observable<string> {
      const model = this.getAuthenticationModel(authenticationForm);
      return this.http.post<string>("../api/em/security/get-organizationName/" + organization, model);
   }

   getOrganizationMembers(authenticationForm: UntypedFormGroup, organization: string): Observable<IdentityListModel> {
      const model = this.getAuthenticationModel(authenticationForm);
      return this.http.post<IdentityListModel>("../api/em/security/organization-members/" + organization, model);
   }

   private showInputQueryParmasDialog(paramName: string,
                                      callback: (paramValue: string) => void): void
   {
      this.dialog.open(InputQueryParamsDialogComponent, {
         role: "dialog",
         width: "500px",
         maxWidth: "100%",
         maxHeight: "100%",
         disableClose: true,
         data: {
            params: [
               {
                  name: paramName
               }
            ]
         }
      }).afterClosed().subscribe((params) => {
         if(!!params && callback) {
            callback(params[0].value);
         }
      });
   }

   triggerUserListQuery(form: UntypedFormGroup, isMulti: boolean): void {
      this.getUsers(form).subscribe(users => {
         this.openQueryResults(isMulti ? this.getDistinctIdentityIDLabels(users.ids) : this.getDistinctIdentityNames(users.ids), null, true);
      });
   }

   triggerUsersQuery(form: UntypedFormGroup, isMulti: boolean): void {
      //get organization, then user name only, send request for get user id=name, org
      if(isMulti && !!form.value.dbForm.userListQuery?.trim() && !!form.value.dbForm.organizationListQuery?.trim()) {
         this.getOrganizations(form).subscribe(orgs => {
            this.openQueryResults(this.getDistinctIdentityNames(orgs.ids), "_#(js:em.security.database.userQueryHint)", true)
               .afterDismissed().subscribe(orgID =>
            {
               if(!!orgID) {
                  this.getUsers(form).subscribe(users => {
                     this.openQueryResults(this.getOrganizationIdentityNames(users.ids, orgID), "_#(js:em.security.database.userQueryHint)", true)
                        .afterDismissed().subscribe(userName =>
                     {
                        if(!!userName) {
                           this.doUsersQuery(form, {name: userName, orgID: orgID});
                        }
                     });
                  });
               }
            });
         });
      }
      else if(!isMulti && !!form.value.dbForm.userListQuery?.trim()){
         this.getDefaultOrganization().subscribe(orgID => {
            this.getUsers(form).subscribe(users => {
               this.openQueryResults(this.getOrganizationIdentityNames(users.ids, orgID), "_#(js:em.security.database.userQueryHint)", true)
                  .afterDismissed().subscribe(userName =>
               {
                  if(!!userName) {
                     this.doUsersQuery(form, {name: userName, orgID: orgID});
                  }
               });
            });
         });
      }
      else {
         this.showInputQueryParmasDialog("_#(js:Organization)", (orgID: string) => {
            this.showInputQueryParmasDialog("_#(js:Username)", (userName: string) => {
               this.doUsersQuery(form, {name: userName, orgID: orgID});
            });
         });
      }
   }

   triggerOrganizationNameQuery(form: UntypedFormGroup): void {
      if(!!form.value.dbForm.organizationListQuery?.trim()) {
         this.getOrganizations(form).subscribe(users => {
            this.openQueryResults(users.ids.map(id => id.name), "_#(js:em.security.database.userQueryHint)")
               .afterDismissed().subscribe(orgID =>
            {
               if(!!orgID) {
                  this.doOrgNameQuery(form, orgID);
               }
            });
         });
      }
      else {
         this.showInputQueryParmasDialog("_#(js:OrganizationId)", (orgID: string) => {
            this.doOrgNameQuery(form, orgID);
         });
      }
   }

   private doUsersQuery(form: UntypedFormGroup, userName: IdentityId): void {
      this.getUser(form, userName).pipe(map(mm => MapModel.fromClientMap(mm)))
         .subscribe(mapModel => {
            this.openQueryResults(
               mapModel.mapArray.map(entry => `${entry.key} : ${entry.value}`));
         });
   }

   triggerUserRolesQuery(form: UntypedFormGroup, isMulti: boolean): void {
      if(isMulti && !!form.value.dbForm.userListQuery?.trim() && !!form.value.dbForm.organizationListQuery?.trim()) {
         this.getOrganizations(form).subscribe(orgs => {
            this.openQueryResults(this.getDistinctIdentityNames(orgs.ids), "_#(js:em.security.database.userQueryHint)")
               .afterDismissed().subscribe(orgID =>
            {
               if(!!orgID) {
                  this.getUsers(form).subscribe(users => {
                     this.openQueryResults(this.getOrganizationIdentityNames(users.ids, orgID), "_#(js:em.security.database.userQueryHint)", true)
                        .afterDismissed().subscribe(userName =>
                     {
                        if(!!userName) {
                           this.doUserRolesQuery(form, {name: userName, orgID: orgID});
                        }
                     });
                  });
               }
            });
         });
      }
      else if(!isMulti && !!form.value.dbForm.userListQuery?.trim()) {
         this.getDefaultOrganization().subscribe(orgID => {
            this.getUsers(form).subscribe(users => {
               this.openQueryResults(this.getOrganizationIdentityNames(users.ids, orgID), "_#(js:em.security.database.userQueryHint)", true)
                  .afterDismissed().subscribe(userName =>
               {
                  if(!!userName) {
                     this.doUserRolesQuery(form, {name: userName, orgID: orgID});
                  }
               });
            });
         });
      }
      else {
         this.showInputQueryParmasDialog("_#(js:Organization)", (orgValue: string) => {
            this.showInputQueryParmasDialog("_#(js:Username)", (paramValue: string) => {
               this.doUserRolesQuery(form, {name: paramValue, orgID: orgValue});
            });

         });
      }
   }

   private doUserRolesQuery(form: UntypedFormGroup, userName: IdentityId): void {
      this.getUserRoles(form, userName).subscribe(roles => {
         this.openQueryResults(roles.ids.map(id => id.name), null, true);
      });
   }

   private doOrgNameQuery(form: UntypedFormGroup, id: string): void {
      this.getOrganizationName(form, id).subscribe(name => {
            this.openQueryResults([name]);
         });
   }

   triggerUserEmailsQuery(form: UntypedFormGroup, isMulti: boolean): void {
      if(isMulti && !!form.value.dbForm.userListQuery?.trim() && !!form.value.dbForm.organizationListQuery?.trim()) {
         this.getOrganizations(form).subscribe(orgs => {
            this.openQueryResults(this.getDistinctIdentityNames(orgs.ids), "_#(js:em.security.database.userQueryHint)")
               .afterDismissed().subscribe(orgID =>
            {
               if(!!orgID) {
                  this.getUsers(form).subscribe(users => {
                     this.openQueryResults(this.getOrganizationIdentityNames(users.ids, orgID), "_#(js:em.security.database.userQueryHint)", true)
                        .afterDismissed().subscribe(userName =>
                     {
                        if(!!userName) {
                           this.doUserEmailsQuery(form, {name: userName, orgID: orgID});
                        }
                     });
                  });
               }
            });
         });
      }
      else if(!isMulti && !!form.value.dbForm.userListQuery?.trim()) {
         this.getDefaultOrganization().subscribe(orgID => {
            this.getUsers(form).subscribe(users => {
               this.openQueryResults(this.getOrganizationIdentityNames(users.ids, orgID), "_#(js:em.security.database.userQueryHint)", true)
                  .afterDismissed().subscribe(userName =>
               {
                  if(!!userName) {
                     this.doUserEmailsQuery(form, {name: userName, orgID: orgID});
                  }
               });
            });
         });
      }
      else {
         this.showInputQueryParmasDialog("_#(js:Organization)", (orgID: string) => {
            this.showInputQueryParmasDialog("_#(js:Username)", (userName: string) => {
               this.doUserEmailsQuery(form, {name: userName, orgID: orgID});
            });
         });
      }
   }

   private doUserEmailsQuery(form: UntypedFormGroup, userName: IdentityId): void {
      this.getUserEmails(form, userName).subscribe(emails => {
         this.openQueryResults(emails.ids.map(id => id.name));
      });
   }

   triggerRoleListQuery(form: UntypedFormGroup, isMulti: boolean): void {
      this.getRoles(form).subscribe(roles => {
         this.openQueryResults(isMulti ? this.getDistinctIdentityIDLabels(roles.ids) : this.getDistinctIdentityNames(roles.ids), null, true);
      });
   }

   triggerGroupListQuery(form: UntypedFormGroup, isMulti: boolean): void {
      this.getGroups(form).subscribe(groups => {
         this.openQueryResults(isMulti ? this.getDistinctIdentityIDLabels(groups.ids) : this.getDistinctIdentityNames(groups.ids), null, true);
      });
   }

   getDistinctIdentityIDLabels(ids: IdentityId[]): string[] {
      let groupNames = new Set(ids.map(id => id.name + " : " + (!!id.orgID ? id.orgID : "__GLOBAL__")));
      return Array.from(groupNames);
   }

   getDistinctIdentityNames(ids: IdentityId[]): string[] {
      let groupNames = new Set(ids.map(id => id.name));
      return Array.from(groupNames);
   }

   getOrganizationIdentityNames(ids: IdentityId[], org: string): string[] {
      let groupNames = new Set(ids.filter(id => id.orgID == org).map(id => id.name));
      return Array.from(groupNames);
   }

   triggerOrganizationListQuery(form: UntypedFormGroup): void {
      this.getOrganizations(form).subscribe(organizations => {
         this.openQueryResults(organizations.ids.map(id => id.name));
      });
   }

   triggerGroupUsersQuery(form: UntypedFormGroup, isMulti: boolean): void {
      if(isMulti && !!form.value.dbForm.groupListQuery?.trim() && !!form.value.dbForm.organizationListQuery?.trim()) {
         this.getOrganizations(form).subscribe(orgs => {
            this.openQueryResults(this.getDistinctIdentityNames(orgs.ids), "_#(js:em.security.database.userQueryHint)")
               .afterDismissed().subscribe(orgID =>
            {
               if(!!orgID) {
                  this.getGroups(form).subscribe(groups => {
                     this.openQueryResults(this.getOrganizationIdentityNames(groups.ids, orgID), "_#(js:em.security.database.userQueryHint)", true)
                        .afterDismissed().subscribe(group =>
                     {
                        if(!!group) {
                           this.doGroupUsersQuery(form, {name: group, orgID: orgID});
                        }
                     });
                  });
               }
            });
         });
      }
      else if(!isMulti && !!form.value.dbForm.userListQuery?.trim()) {
         this.getDefaultOrganization().subscribe(orgID => {
            this.getGroups(form).subscribe(groups => {
               this.openQueryResults(this.getOrganizationIdentityNames(groups.ids, orgID), "_#(js:em.security.database.userQueryHint)", true)
                  .afterDismissed().subscribe(group =>
               {
                  if(!!group) {
                     this.doGroupUsersQuery(form, {name: group, orgID: orgID});
                  }
               });
            });
         });
      }
      else {
         this.showInputQueryParmasDialog("_#(js:Organization)", (orgID: string) => {
            this.showInputQueryParmasDialog("_#(js:Group Name)", (groupName: string) => {
               this.doGroupUsersQuery(form, {name: groupName, orgID: orgID});
            });
         });
      }
   }

   triggerOrganizationMembersQuery(form: UntypedFormGroup): void {
      if(!!form.value.dbForm.organizationListQuery?.trim()) {
         this.getOrganizations(form).subscribe(orgs => {
            this.openQueryResults(orgs.ids.map(id => id.name), "_#(js:em.security.database.userQueryHint)")
               .afterDismissed().subscribe(org =>
            {
               if(!!org) {
                  this.doOrganizationMembersQuery(form, org);
               }
            });
         });
      }
      else {
         this.showInputQueryParmasDialog("_#(js:Organization Name)", (paramValue: string) => {
            this.doOrganizationMembersQuery(form, paramValue);
         });
      }
   }

   private doGroupUsersQuery(form: UntypedFormGroup, group: IdentityId): void {
      this.getGroupUsers(form, group).subscribe(users => {
         this.openQueryResults(users.ids.map(id => id.name));
      });
   }

   private doOrganizationMembersQuery(form: UntypedFormGroup, org: string): void {
      this.getOrganizationMembers(form, org).subscribe(members => {
         this.openQueryResults(members.ids.map(id => id.name));
      });
   }
}
