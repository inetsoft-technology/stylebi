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
import { HttpClient } from "@angular/common/http";
import { Component, OnDestroy, OnInit } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { MatSlideToggleChange } from "@angular/material/slide-toggle";
import { finalize, takeUntil } from "rxjs/operators";
import { ScheduleUsersService } from "../../../../../../shared/schedule/schedule-users.service";
import { AuthorizationService } from "../../../authorization/authorization.service";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { Secured } from "../../../secured";
import { SecurityEnabledEvent } from "./security-enabled-event";
import { OrganizationDropdownService } from "../../../navbar/organization-dropdown.service";
import { AppInfoService } from "../../../../../../shared/util/app-info.service";
import { Subject } from "rxjs";

@Secured({
   route: "/settings/security",
   label: "Security"
})
@Component({
   selector: "em-security-settings-page",
   templateUrl: "./security-settings-page.component.html",
   styleUrls: ["./security-settings-page.component.scss"]
})
export class SecuritySettingsPageComponent implements OnInit, OnDestroy {
   securityEnabled = false;
   multiTenancyEnabled = false;
   securityProvidersVisible = false;
   securityToggleDisabled = false;
   multiTenancyToggleDisabled = false;
   ldapProviderUsed = false;
   passOrgIdAs = "domain";
   usersVisible = false;
   actionsVisible = false;
   ssoVisible = false;
   googleSsoVisible = false;
   isRefreshed = true;
   isOrgAdminOnly = true;
   selfSignupEnabled = false;
   enterprise: boolean;
   orgIDPassOptions: string[] = ["domain", "path"];
   cloudPlatform = false;
   private destroy$ = new Subject<void>();

   constructor(private pageTitle: PageHeaderService,
               private authzService: AuthorizationService,
               private orgDropdownService: OrganizationDropdownService,
               private dialog: MatDialog,
               private appInfoService: AppInfoService,
               private httpClient: HttpClient,
               private userService: ScheduleUsersService)
   {
   }

   ngOnInit() {
      this.appInfoService.isEnterprise().subscribe(info => this.enterprise = info);
      this.appInfoService.isLdapProviderUsed()
         .pipe(takeUntil(this.destroy$))
         .subscribe(value => this.ldapProviderUsed = value);
      this.pageTitle.title = "_#(js:Security Settings)";
      this.httpClient.get("../api/em/security/get-enable-security")
         .subscribe((event: SecurityEnabledEvent) => {
            this.securityEnabled = event.enable;
            this.securityToggleDisabled = event.toggleDisabled;
         });
      this.httpClient.get("../api/em/security/get-multi-tenancy")
         .subscribe((event: SecurityEnabledEvent) => {
            this.multiTenancyEnabled = event.enable;
            this.multiTenancyToggleDisabled = event.toggleDisabled;
            this.ldapProviderUsed = event.ldapProviderUsed;
            this.appInfoService.setLdapProviderUsed(this.ldapProviderUsed);
            this.passOrgIdAs = event.passOrgIdAs;
            this.cloudPlatform = event.cloudPlatform;
            this.isOrgAdminOnly = event.warning && event.warning === "isOrgAdmin";
         });

      this.authzService.getPermissions("settings/security").subscribe((p) => {
         this.securityProvidersVisible = p.permissions.provider;
         this.usersVisible = p.permissions.users;
         this.actionsVisible = p.permissions.actions;
         this.ssoVisible = p.permissions.sso;
         this.googleSsoVisible = p.permissions.googleSignIn;
      });

      this.httpClient.get("../api/em/security/get-enable-self-signup")
         .subscribe((event: SecurityEnabledEvent) => this.selfSignupEnabled = event.enable);
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   toggleSecurityEnabled(toggleChange: MatSlideToggleChange) {
      this.securityToggleDisabled = true;
      const request: SecurityEnabledEvent = {
         enable: toggleChange.checked,
         toggleDisabled: this.securityToggleDisabled,
         ldapProviderUsed: this.ldapProviderUsed
      };

      this.httpClient.post("../api/em/security/set-enable-security", request)
         .pipe(finalize(() => this.securityToggleDisabled = false))
         .subscribe((event: SecurityEnabledEvent) => {
            this.securityEnabled = event.enable;
            this.userService.loadScheduleUsers();
         });
   }

   toggleEnterpriseToggle(toggleChange: MatSlideToggleChange) {
       this.multiTenancyToggleDisabled = true;
       const request: SecurityEnabledEvent = {
          enable: toggleChange.checked,
          toggleDisabled: this.securityToggleDisabled,
          ldapProviderUsed: this.ldapProviderUsed
       };
       this.httpClient.post<SecurityEnabledEvent>("../api/em/security/set-multi-tenancy", request).pipe(
          finalize(() => this.multiTenancyToggleDisabled = false)
       )
          .subscribe((event: SecurityEnabledEvent) => {
             if(event.warning != null && event.warning != "") {
                const content = event.warning;
                this.dialog.open(MessageDialog, {
                   width: "350px",
                   data: {
                      title: "_#(js:Error)",
                      content: content,
                      type: MessageDialogType.ERROR
                   }
                });
                this.multiTenancyEnabled = true;
                this.refreshContent();
             }
             else {
                this.multiTenancyEnabled = event.enable;
                this.orgDropdownService.refreshProviders();
                this.refreshContent();
             }
          });
   }

   //resets child components after updating isMultiTenant
   refreshContent(): void {
      this.isRefreshed = false;
      setTimeout(() => {
         this.isRefreshed = true;
      }, 50);
   }

   toggleSelfSignupEnabled(toggleChange: MatSlideToggleChange) {
      const request: SecurityEnabledEvent = {
         enable: toggleChange.checked,
         toggleDisabled: this.securityToggleDisabled,
         ldapProviderUsed: this.ldapProviderUsed
      };
      this.httpClient.post("../api/em/security/set-enable-self-signup", request)
         .subscribe((event: SecurityEnabledEvent) => this.selfSignupEnabled = event.enable);
   }

   updatePassOption(option: string): void {
      this.httpClient.post("../api/em/security/updatePassOrgIdOption", option).subscribe();
   }

   passOptionLabel(option: string): string{
      if(option == "path"){
         return "_#(js:Path)";
      }
      else {
         return "_#(js:Domain)";
      }
   }

   get getIsOrgAdminOnly(): boolean {
      return this.isOrgAdminOnly;
   }
}
