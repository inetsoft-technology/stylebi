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
import { Component, Inject, OnDestroy, OnInit } from "@angular/core";
import { MAT_DIALOG_DATA, MatDialogRef, MatDialogContent, MatDialogActions } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { UntypedFormControl, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { BehaviorSubject, Observable, Subscription } from "rxjs";
import { map } from "rxjs/operators";
import { Tool } from "../../../../../../../shared/util/tool";
import { convertToKey, IdentityId } from "../../users/identity-id";
import { MatDivider } from "@angular/material/divider";
import { MatList, MatListItem } from "@angular/material/list";
import { MatOption } from "@angular/material/core";
import { AsyncPipe, NgIf } from "@angular/common";
import { MatIcon } from "@angular/material/icon";
import { MatIconButton, MatButton } from "@angular/material/button";
import { MatAutocompleteTrigger, MatAutocomplete } from "@angular/material/autocomplete";
import { MatInput } from "@angular/material/input";
import { MatFormField, MatLabel, MatSuffix, MatError } from "@angular/material/form-field";
import { ModalHeaderComponent } from "../../../../common/util/modal-header/modal-header.component";

export interface SystemAdminRolesData {
   currentRoles: string[];
   allRoles: IdentityId[];
   isSysAdmin: boolean;
}

@Component({
    selector: "em-system-admin-roles-dialog",
    templateUrl: "./system-admin-roles-dialog.component.html",
    styleUrls: ["./system-admin-roles-dialog.component.scss"],
    imports: [NgIf, ModalHeaderComponent, MatDialogContent, MatFormField, MatLabel, MatInput, FormsModule, MatAutocompleteTrigger, ReactiveFormsModule, MatIconButton, MatSuffix, MatIcon, MatError, MatAutocomplete, MatOption, MatList, MatListItem, MatDivider, MatDialogActions, MatButton, AsyncPipe]
})
export class SystemAdminRolesDialogComponent implements OnInit, OnDestroy {
   adminRoles: string[];
   allRoles: IdentityId[];
   isSysAdmin: boolean;
   enterprise: boolean;
   adminRoleControl = new UntypedFormControl("", [FormValidators.validGroupName]);
   private _autocompleteRoles = new BehaviorSubject<string[]>(null);
   private _subscription = Subscription.EMPTY;

   constructor(private dialog: MatDialogRef<SystemAdminRolesDialogComponent>,
               private snackBar: MatSnackBar,
               private appInfoService: AppInfoService,
               @Inject(MAT_DIALOG_DATA) data: SystemAdminRolesData) {
      this.adminRoles = data.currentRoles;
      this.allRoles = data.allRoles;
      this.isSysAdmin = data.isSysAdmin;

      this.appInfoService.isEnterprise().subscribe(value => this.enterprise = value);
   }

   get autocompleteRoles(): Observable<string[]> {
      return this._autocompleteRoles.asObservable();
   }

   ngOnInit() {
      this._subscription = this.adminRoleControl.valueChanges
         .pipe(map((input: string) => this.filterRoles(input)))
         .subscribe(roles => this._autocompleteRoles.next(roles));
   }

   ngOnDestroy() {
      this._subscription.unsubscribe();
   }

   openAutocomplete(): void {
      this._autocompleteRoles.next(this.filterRoles(this.adminRoleControl.value));
   }

   private filterRoles(input: string): string[] {
      input = input.toLowerCase();
      let roleSet = new Set(this.allRoles
         .map(r => r.name)
         .filter(role => {
            return !this.adminRoles.includes(role) && role.toLowerCase().startsWith(input);
         }));
      return Array.from(roleSet).sort();
   }

   removeRole(role: string): void {
      this.adminRoles = this.adminRoles.filter(e => e !== role);
   }

   addRole(): void {
      const newRole: string = this.adminRoleControl.value;

      if(!this.adminRoles.includes(newRole)) {
         this.adminRoles.push(this.adminRoleControl.value);
      }
      else {
         this.snackBar.open("_#(js:em.security.systemAdmin.duplicate)", null, {duration: Tool.SNACKBAR_DURATION});
      }

      this.adminRoleControl.setValue("");
      this._autocompleteRoles.next([]);
   }

   get addRoleDisabled(): boolean {
      return this.adminRoleControl.invalid || !this.adminRoleControl.value;
   }

   submit(dialogResult: string[]): void {
      this.dialog.close(dialogResult);
   }

   getAdminTitle() {
    return this.isSysAdmin || !this.enterprise ? "_#(js:System Administrator Roles)" : "_#(js:Organization Administrator Roles)";
   }
}
