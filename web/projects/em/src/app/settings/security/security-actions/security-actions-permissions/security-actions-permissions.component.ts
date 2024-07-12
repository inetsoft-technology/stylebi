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
import { HttpErrorResponse } from "@angular/common/http";
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { MatSnackBar } from "@angular/material/snack-bar";
import { Observable, throwError } from "rxjs";
import { catchError } from "rxjs/operators";
import { ResourcePermissionModel } from "../../resource-permission/resource-permission-model";
import { Tool } from "../../../../../../../shared/util/tool";
import { ActionTreeNode } from "../action-tree-node";
import { SecurityActionService } from "../security-action.service";

@Component({
   selector: "em-security-actions-permissions",
   templateUrl: "./security-actions-permissions.component.html",
   styleUrls: ["./security-actions-permissions.component.scss"]
})
export class SecurityActionsPermissionsComponent {
   @Output() unsavedChanges = new EventEmitter<boolean>();
   @Output() closed = new EventEmitter<void>();
   @Input()
   get action(): ActionTreeNode {
      return this._action;
   }

   set action(value: ActionTreeNode) {
      this._action = value;

      if(value) {
         this.originalModel = null;
         this.loadPermissions(value.type, value.resource, value.grant);
      }
      else {
         this.tableModel = null;
         this.originalModel = null;
      }
   }

   tableModel: ResourcePermissionModel;
   readonly actionsStyle = { "margin-bottom": "12px" };
   private originalModel: ResourcePermissionModel;
   private _action: ActionTreeNode;

   constructor(private actionService: SecurityActionService, private snackBar: MatSnackBar) {
   }

   isModelChanged(): boolean {
      return !Tool.isEquals(this.tableModel, this.originalModel);
   }

   save(): void {
      this.actionService.setPermissions(this.action.type, this.action.resource, this.action.grant, this.tableModel)
         .pipe(catchError(error => this.handleSetPermissionsError(error)))
         .subscribe((model) => {
            this.unsavedChanges.emit(false);
            this.closed.emit();
            this.tableModel = model;
            this.originalModel = Tool.clone(model);

            //if no longer have access to permission, reload page
            this.actionService.getPermissions(this.action.type, this.action.resource, this.action.grant)
                .subscribe(() => {}, (error) => window.location.reload())
         });
   }

   restore(): void {
      this.unsavedChanges.emit(false);
      this.closed.emit();
      this.tableModel = this.originalModel;
      this.originalModel = Tool.clone(this.tableModel);
   }

   private loadPermissions(type: string, path: string, isGrant: boolean): void {
      this.actionService.getPermissions(type, path, isGrant)
         .pipe(catchError(error => this.handleGetPermissionsError(error)))
         .subscribe((model) => {
            this.tableModel = model;
            this.originalModel = Tool.clone(this.tableModel);
         });
   }

   private handleGetPermissionsError(error: HttpErrorResponse): Observable<ResourcePermissionModel> {
      this.snackBar.open(error.message, null, {
         duration: Tool.SNACKBAR_DURATION
      });
      console.error("Failed to get permissions: ", error);
      return throwError(error);
   }

   private handleSetPermissionsError(error: HttpErrorResponse): Observable<ResourcePermissionModel> {
      this.snackBar.open(error.message, null, {
         duration: Tool.SNACKBAR_DURATION
      });
      console.error("Failed to set permissions: ", error);
      return throwError(error);
   }
}
