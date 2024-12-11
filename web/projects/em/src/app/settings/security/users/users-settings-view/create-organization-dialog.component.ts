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

import { HttpClient, HttpParams } from "@angular/common/http";
import { Component, HostListener, Inject, Input, OnDestroy, OnInit } from "@angular/core";
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from "@angular/material/dialog";
import { BehaviorSubject, Subscription } from "rxjs";
import { debounceTime, distinctUntilChanged } from "rxjs/operators";
import { MessageDialog, MessageDialogType } from "../../../../common/util/message-dialog";
import { convertToKey, IdentityId } from "../identity-id";

@Component({
   selector: "em-create-organization-dialog",
   templateUrl: "./create-organization-dialog.component.html"
})
export class CreateOrganizationDialogComponent implements OnInit, OnDestroy {
   existingOrganizations: string[] = [];
   existingOrganizationNames: string[] = [];
   copyFromOrgID: string = "";
   searchOpen = false;
   private subscriptions = new Subscription();
   searchResults: { id: string, name: string }[] = [];
   private searchQuery$ = new BehaviorSubject<string>("");
   providerName: string = "";

   constructor(@Inject(MAT_DIALOG_DATA) data: any,
               private dialogRef: MatDialogRef<CreateOrganizationDialogComponent>,
               private dialog: MatDialog,
               private http: HttpClient)
   {
      this.providerName = data.name;
   }

   ngOnInit(): void {
      let params = new HttpParams();
      params = params.set("name", this.providerName);

      this.http.get<IdentityId[]>("../api/em/security/users/get-all-organizations", {params}).subscribe(
         (orgIDList) => {
         for(let orgID of orgIDList) {
            this.existingOrganizationNames.push(orgID.name);
            this.existingOrganizations.push(orgID.orgID);
         }
         }
      );

      this.subscriptions.add(
         this.searchQuery$
            .pipe(
               distinctUntilChanged(),
               debounceTime(500)
            )
            .subscribe(value => {
               if(value) {
                  this.filterOrgs(value);
               }
               else {
                  this.initSearchResults();
               }
            })
      );
   }

   filterOrgs(name: string): void {
      let orgs = this.getOrgIdsAndNames();
      this.searchResults = orgs.filter(org => org.name.toLowerCase().includes(name.toLowerCase()));
   }

   ngOnDestroy(): void {
      if(!!this.subscriptions) {
         this.subscriptions.unsubscribe();
      }
   }

   submit() {
      if(!!this.copyFromOrgID && this.copyFromOrgID != "") {
         let orgName = this.existingOrganizationNames[this.existingOrganizations.indexOf(this.copyFromOrgID)];
         let orgKey = encodeURIComponent(convertToKey({name: orgName, orgID: this.copyFromOrgID }))

         this.http.get<string>("../api/em/security/users/get-organization-detail-string/" + orgKey).subscribe(
            (stringOrganizationDetails) => {
               var confirm = this.dialog.open(MessageDialog, {
                  data: {
                     title: "_#(js:em.settings.confirmCopyOrganization)",
                     content: stringOrganizationDetails,
                     type: MessageDialogType.CONFIRMATION
                  }
               });

               confirm.afterClosed().subscribe(value => {
                  if(value) {
                     this.dialogRef.close({newOrgString: this.copyFromOrgID, proceed: true});
                  }
               })
            });
      }
      else {
         this.dialogRef.close({newOrgString: this.copyFromOrgID, proceed: true});
      }
   }

   getOrgIdsAndNames(): {id: string, name: string}[] {
      let result = [];
      this.existingOrganizations.forEach((orgID, index) => {
         result.push({id:orgID, name: this.existingOrganizationNames[index]});
      });

      return result;
   }

   initSearchResults(): void {
      this.searchResults = this.getOrgIdsAndNames();
   }

   isSelected(orgId: string): boolean {
      return !!orgId && orgId == this.copyFromOrgID;
   }

   toggleSearch(): void {
      this.searchOpen = !this.searchOpen;
   }

   get searchQuery(): string {
      return this.searchQuery$.value;
   }

   set searchQuery(value: string) {
      this.searchQuery$.next(value ? value.trim() : "");
   }

   onSelectOrg(event: any): void {
      this.copyFromOrgID = event.option.value;
      this.searchOpen = false;
      this.searchQuery = "";
   }

   @HostListener("window:keyup.esc", [])
   onKeyUp() {
      this.cancel();
   }

   cancel(): void {
      this.dialogRef.close({newOrgString: null, proceed: false});
   }
}
