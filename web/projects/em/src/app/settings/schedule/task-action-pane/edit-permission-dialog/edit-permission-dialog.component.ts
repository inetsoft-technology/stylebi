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
import {Component, Inject, OnInit} from "@angular/core";
import {ResourcePermissionModel} from "../../../security/resource-permission/resource-permission-model";
import {MAT_DIALOG_DATA, MatDialogRef} from "@angular/material/dialog";
import {ResourceAction} from "../../../../../../../shared/util/security/resource-permission/resource-action.enum";
import {Tool} from "../../../../../../../shared/util/tool";

export interface EditPermissionDialogResult {
   permission: ResourcePermissionModel;
}

@Component({
   selector: "em-edit-permission-dialog",
   templateUrl: "./edit-permission-dialog.component.html",
   styleUrls: ["./edit-permission-dialog.component.scss"]
})
export class EditPermissionDialogComponent implements OnInit {
   originalModel: ResourcePermissionModel;
   model: ResourcePermissionModel;

   constructor(private dialog: MatDialogRef<EditPermissionDialogComponent>,
               @Inject(MAT_DIALOG_DATA) private data: any)
   {
      this.model = data.permission;

      if(!this.model) {
         this.model = <ResourcePermissionModel> {
            displayActions: [ResourceAction.READ, ResourceAction.WRITE, ResourceAction.DELETE],
            derivePermissionLabel: "_#(js:Use Parent Permissions)",
            securityEnabled: true,
            requiresBoth: false,
            grantReadToAllVisible: false,
            permissions: []
         };
      }

      this.originalModel = Tool.clone(this.model);
   }

   ngOnInit() {
   }

   ok() {
      let result: EditPermissionDialogResult = {permission: null};

      if(!!this.model.permissions && this.model.permissions.length > 0) {
         result = {permission: this.model};
      }

      this.dialog.close(result);
   }

   reset() {
      this.model = Tool.clone(this.originalModel);
   }

   cancel() {
      this.dialog.close();
   }
}
