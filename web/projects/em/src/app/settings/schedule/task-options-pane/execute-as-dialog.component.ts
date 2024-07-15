/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, Inject, OnInit } from "@angular/core";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { HttpClient, HttpParams } from "@angular/common/http";
import {
   AbstractControl,
   UntypedFormBuilder,
   UntypedFormGroup,
   ValidationErrors,
   Validators
} from "@angular/forms";
import { Observable } from "rxjs";
import { map, startWith } from "rxjs/operators";
import {convertToKey, IdentityId} from "../../security/users/identity-id";

export interface ExecuteAsIdentitiesModel {
   users: IdentityId[];
   groups: IdentityId[];
}

@Component({
   selector: "em-execute-as-dialog",
   templateUrl: "./execute-as-dialog.component.html",
})
export class ExecuteAsDialogComponent implements OnInit {
   users: IdentityId[] = [];
   groups: IdentityId[] = [];
   filteredIdentities: Observable<IdentityId[]>;
   form: UntypedFormGroup;

   constructor(private dialog: MatDialogRef<ExecuteAsDialogComponent>,
               @Inject(MAT_DIALOG_DATA) private data: any,
               private http: HttpClient,
               private fb: UntypedFormBuilder)
   {
      this.form = fb.group({
         idName: [null, [Validators.required, this.validUserOrGroup]],
         idType: [0]
      });

      this.filteredIdentities = this.form.controls["idName"].valueChanges
         .pipe(
            startWith(""),
            map((input: string) => {
               const type: number = this.form.get("idType").value;
               const list = type == 0 ? this.users : this.getGroups();

               if(!list) {
                  return [];
               }

               if(input) {
                  const filterValue = input.toLowerCase();
                  return list.filter((id) => id.name.toLowerCase().startsWith(filterValue))
                     .slice(0, 100);
               }
               else {
                  return list.slice(0, 100);
               }
            })
         );
   }

   ngOnInit() {
      this.form.get("idType").setValue(this.data.idType);
      this.form.get("idName").setValue(this.data.idName);

      const params = new HttpParams().set("owner", this.data.owner);
      this.http.get("../api/em/schedule/executeAs/identities", {params})
         .subscribe((model: ExecuteAsIdentitiesModel) => {
            this.users = model.users;
            this.groups = model.groups;
            this.form.get("idName").updateValueAndValidity();
         });
   }

   getGroups(): IdentityId[] {
      return this.groups;
   }

   clearIdName() {
      this.form.get("idName").setValue("");
   }

   closeDialog(submit: boolean): void {
      if(submit) {
         const data = {
            idName: this.form.get("idName").value,
            idType: this.form.get("idType").value
         };

         this.dialog.close(data);
      }
      else {
         this.dialog.close();
      }
   }

   private validUserOrGroup = (control: AbstractControl): ValidationErrors | null => {
      if(!control || !this.form) {
         return null;
      }

      const type: number = this.form.get("idType").value;
      const list = type == 0 ? this.users : this.getGroups();
      return list && list.findIndex(i => i.name == control.value) === -1 ?
         (type === 0 ? {invalidUser: true} : {invalidGroup: true}) : null;
   };
}