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
import {HttpClient, HttpErrorResponse} from "@angular/common/http";
import {Component, EventEmitter, Input, OnChanges, Output, SimpleChanges} from "@angular/core";
import { MatDialog, MatDialogConfig } from "@angular/material/dialog";
import { Observable, of as observableOf, Subject, throwError } from "rxjs";
import { MessageDialog, MessageDialogType } from "../../../../common/util/message-dialog";
import {SecurityTreeNode} from "../../security-tree-view/security-tree-node";
import {IdentityType} from "../../../../../../../shared/data/identity-type";
import {convertToKey, IdentityId} from "../identity-id";
import {
   EditGroupPaneModel,
   EditIdentityPaneModel,
   EditOrganizationPaneModel,
   EditRolePaneModel,
   EditUserPaneModel
} from "./edit-identity-pane.model";
import {catchError} from "rxjs/operators";
import {Tool} from "../../../../../../../shared/util/tool";

@Component({
   selector: "em-edit-identity-pane",
   templateUrl: "./edit-identity-pane.component.html",
   styleUrls: ["./edit-identity-pane.component.scss"]
})
export class EditIdentityPaneComponent implements OnChanges {
   @Input() selectedIdentity: SecurityTreeNode;
   @Input() provider: string;
   @Input() smallDevice = false;
   @Input() treeData: SecurityTreeNode[] = [];
   @Input() isSysAdmin: boolean = false;
   @Input() isLoadingTemplate: boolean = false;
   @Input() identityEditable: Subject<boolean>;
   @Output() cancel = new EventEmitter<void>();
   @Output() roleSettingsChanged = new EventEmitter<EditRolePaneModel>();
   @Output() userSettingsChanged = new EventEmitter<EditUserPaneModel>();
   @Output() groupSettingsChanged = new EventEmitter<EditGroupPaneModel>();
   @Output() organizationSettingsChanged = new EventEmitter<EditOrganizationPaneModel>();
   @Output() pageEdited = new EventEmitter<boolean>();
   @Output() loadIdentityError = new EventEmitter<void>();
   public editModel$: Observable<EditIdentityPaneModel>;

   constructor(private http: HttpClient, private dialog: MatDialog) {
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.selectedIdentity && this.selectedIdentity != null) {
         let model$: Observable<any>;
         let provider = Tool.byteEncodeURLComponent(this.provider);
         let id = convertToKey(this.selectedIdentity.identityID);
         let identity = Tool.byteEncodeURLComponent(id);
         let orgIdentity = Tool.byteEncodeURLComponent(convertToKey(this.selectedIdentity.identityID));

         switch(this.selectedIdentity.type) {
         case IdentityType.USER:
            const userUri = "../api/em/security/providers/" + provider + "/users/" + identity + "/";
            model$ = this.http.get<EditUserPaneModel>(userUri).pipe(
               catchError(error => {
                  const orgInvalid = error.error.type == "InvalidOrgException";
                  const errContent: string = orgInvalid ? error.error.message :
                                             "_#(js:em.security.orgAdmin.identityPermissionDenied)";

                  this.dialog.open(MessageDialog, <MatDialogConfig>{
                     width: "350px",
                     data: {
                        title: "_#(js:Error)",
                        content: errContent,
                        type: MessageDialogType.ERROR
                     }
                  });
                  return observableOf(null);
               }));
            break;
         case IdentityType.GROUP:

            const groupUri = "../api/em/security/providers/" + provider + "/groups/" + identity + "/";
            model$ = this.http.get<EditGroupPaneModel>(groupUri).pipe(
               catchError(error => {
                  const orgInvalid = error.error.type == "InvalidOrgException";
                  const errContent: string = orgInvalid ? error.error.message :
                                             "_#(js:em.security.orgAdmin.identityPermissionDenied)";

                  this.dialog.open(MessageDialog, <MatDialogConfig>{
                     width: "350px",
                     data: {
                        title: "_#(js:Error)",
                        content: errContent,
                        type: MessageDialogType.ERROR
                     }
                  });
                  return observableOf(null);
               }));
            break;
         case IdentityType.ROLE:
            const roleUri = "../api/em/security/providers/" + provider + "/roles/" + identity + "/";
            model$ = this.http.get<EditRolePaneModel>(roleUri).pipe(
               catchError(error => {
                  const orgInvalid = error.error.type == "InvalidOrgException";
                  const errContent: string = orgInvalid ? error.error.message :
                     "_#(js:em.security.orgAdmin.identityPermissionDenied)";

                  this.dialog.open(MessageDialog, <MatDialogConfig>{
                     width: "350px",
                     data: {
                        title: "_#(js:Error)",
                        content: errContent,
                        type: MessageDialogType.ERROR
                     }
                  });
                  return observableOf(null);
               }));
            break;
         case IdentityType.ORGANIZATION:
            const organizationUri = "../api/em/security/providers/" + provider + "/organization/" + orgIdentity + "/";
            model$ = this.http.get<EditOrganizationPaneModel>(organizationUri).pipe(
               catchError(error => {
                  const orgInvalid = error.error.type == "InvalidOrgException";
                  const errContent: string = orgInvalid ? error.error.message :
                                             "_#(js:em.security.orgAdmin.identityPermissionDenied)";

                  this.dialog.open(MessageDialog, <MatDialogConfig>{
                     width: "350px",
                     data: {
                        title: "_#(js:Error)",
                        content: errContent,
                        type: MessageDialogType.ERROR
                     }
                  });
                  return observableOf(null);
               }));
            break;
         default:
            // apply name, members, and roles shared by all objects
            model$ = observableOf(null);
            break;
         }

         this.editModel$ = model$.pipe(
            catchError((error: HttpErrorResponse) => this.handleLoadIdentityError(error))
         );
      }
   }

   private handleLoadIdentityError(error: HttpErrorResponse): Observable<never> {
      this.loadIdentityError.emit();
      return throwError(error);
   }
   onPageChanged(changed: boolean = true): void {
      this.pageEdited.emit(changed);
   }
}
