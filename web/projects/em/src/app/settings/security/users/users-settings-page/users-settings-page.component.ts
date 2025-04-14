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
import {FlatTreeControl} from "@angular/cdk/tree";
import {HttpClient, HttpErrorResponse} from "@angular/common/http";
import { Component, OnDestroy, OnInit } from "@angular/core";
import {MatDialog} from "@angular/material/dialog";
import {MatSnackBar} from "@angular/material/snack-bar";
import { Observable, of, Subject } from "rxjs";
import {catchError, map, tap} from "rxjs/operators";
import {IdentityType} from "../../../../../../../shared/data/identity-type";
import {Tool} from "../../../../../../../shared/util/tool";
import {ErrorHandlerService} from "../../../../common/util/error/error-handler.service";
import {MessageDialog, MessageDialogType} from "../../../../common/util/message-dialog";
import {ContextHelp} from "../../../../context-help";
import {OrganizationDropdownService} from "../../../../navbar/organization-dropdown.service";
import {PageHeaderService} from "../../../../page-header/page-header.service";
import {Searchable} from "../../../../searchable";
import {Secured} from "../../../../secured";
import {
   SecurityProviderStatus,
   SecurityProviderStatusList
} from "../../security-provider/security-provider-model/security-provider-status-list";
import {IdentityModel} from "../../security-table-view/identity-model";
import {FlatSecurityTreeNode, SecurityTreeNode} from "../../security-tree-view/security-tree-node";
import {
   EditGroupPaneModel,
   EditIdentityPaneModel,
   EditOrganizationPaneModel,
   EditRolePaneModel,
   EditUserPaneModel
} from "../edit-identity-pane/edit-identity-pane.model";
import {SecurityTreeService} from "../security-tree.service";
import {SecurityTreeNodeModel} from "../users-settings-view/security-tree-node-model";
import {SecurityTreeRootModel} from "../users-settings-view/security-tree-root-model";
import {DeleteIdentitiesResponse} from "./delete-identities-response";
import {SecurityEnabledEvent} from "../../security-settings-page/security-enabled-event";
import {ScheduleUsersService} from "../../../../../../../shared/schedule/schedule-users.service";
import { convertKeyToID, convertToKey, IdentityId } from "../identity-id";

@Secured({
   route: "/settings/security/users",
   label: "Users"
})
@Searchable({
   route: "/settings/security/users",
   title: "Security Settings Users",
   keywords: ["em.security.users", "em.security.roles", "em.security.groups"]
})
@ContextHelp({
   route: "/settings/security/users",
   link: "EMSettingsSecurityUsers"
})
@Component({
   selector: "em-users-settings-page",
   templateUrl: "./users-settings-page.component.html",
   styleUrls: ["./users-settings-page.component.scss"]
})
export class UsersSettingsPageComponent implements OnInit, OnDestroy {
   public treeData: Observable<SecurityTreeNode[]>;
   public selectedProvider: string;
   selectedNodes: SecurityTreeNode[] = [];
   nodeToExpand: SecurityTreeNode = null;
   providerEditable: boolean = true;
   isMultiTenant: boolean = false;
   pageChanged: boolean = false;
   model: SecurityTreeRootModel;
   isLoadingTemplate: boolean = false;
   public identityEditable = new Subject<boolean>;
   currOrg: string;
   loading: boolean = false;

   constructor(private http: HttpClient,
               private pageTitle: PageHeaderService,
               private treeService: SecurityTreeService,
               private errorService: ErrorHandlerService,
               private orgDropdownService: OrganizationDropdownService,
               private dialog: MatDialog,
               private snackBar: MatSnackBar,
               private usersService: ScheduleUsersService,
               private orgDropDownService: OrganizationDropdownService)
   {
      orgDropdownService.onRefresh.subscribe(res => {
         this.selectedProvider = res.provider;
         this.refreshProvider(this.selectedProvider, res.providerChanged)
      });

      this.refreshProvider(orgDropdownService.getProvider(), false);
   }

   get authenticationProviders(): string[] {
      return this.orgDropdownService.authenticationProviders;
   }
   get userName(): string {
      return this.orgDropdownService.loginUserOrgName;
   }

   get userOrgID(): string {
      return this.orgDropdownService.loginUserOrgID;
   }

   get userOrg(): string {
      return this.orgDropdownService.loginUserOrgName;
   }

   get isSysAdmin(): boolean {
      return this.orgDropdownService.isSystemAdmin();
   }

   ngOnInit(): void {
      this.pageTitle.title = "_#(js:Security Settings Users)";

      this.http.get<string>("../api/em/navbar/organization")
         .subscribe((org) => this.currOrg = org);
   }

   refreshProvider(provider: string, providerChanged: boolean = true) {
      this.changeProvider(provider, providerChanged, false);
   }

   changeProvider(provider: string, providerChanged: boolean = true, selectProvider: boolean = true) {
      if(!provider) {
         return;
      }

      this.selectedNodes = [];
      this.selectedProvider = provider;
      this.refreshTree(null, null, providerChanged, selectProvider);
   }

   selectionChanged(event: SecurityTreeNode[]) {
      if(this.pageChanged && event.length == 1 &&
         this.selectedNodes.length >= 1 && this.selectedNodes[0] != event[0])
      {
         const ref = this.dialog.open(MessageDialog, {
            data: {
               title: "_#(js:em.settings.userSettingsChanged)",
               content: "_#(js:em.settings.userSettings.confirm)",
               type: MessageDialogType.CONFIRMATION
            }
         });

         ref.afterClosed().subscribe(val => {
            if(val) {
               this.pageChanged = false;
               this.selectedNodes = event;
            }
            else {
               //Force the security tree view to update its selection
               this.selectedNodes = this.selectedNodes.splice(0);
            }
         });
      }
      else {
         this.selectedNodes = event;
      }
   }

   public newUser(parentGroup: string) {
      const uri = "../api/em/security/users/create-user/" + Tool.byteEncodeURLComponent(this.selectedProvider);
      this.http.post<EditUserPaneModel>(uri, {parentGroup})
         .pipe(catchError((error: HttpErrorResponse) => this.errorService.showSnackBar(error)))
         .subscribe(model => {
            if(model) {
               let id: IdentityId = {name: model.name, orgID: model.organization};
               this.refreshTree(id, IdentityType.USER);
            }
         });
   }

   setUser(model: EditIdentityPaneModel) {
      if(model.organization === this.userOrgID && model.oldName === this.userName &&
         (model.oldName != model.name || (<EditUserPaneModel>model).password)) {
         this.dialog.open(MessageDialog, {
            data: {
               title: "_#(js:em.security.userInfoChangedTitle)",
               content: "_#(js:em.security.userInfoChanged)",
               type: MessageDialogType.CONFIRMATION
            }
         }).afterClosed().subscribe(val => {
            if(val) {
               this.postUserInfo(model, true);
            }
         });
      }
      else {
         this.postUserInfo(model, false);
      }
   }

   public newOrganization(parentGroup: string) {
      this.loading = true;
      const uri = "../api/em/security/users/create-organization/" + Tool.byteEncodeURLComponent(this.selectedProvider);
      this.http.post<EditOrganizationPaneModel>(uri, {parentGroup})
         .pipe(catchError((error: HttpErrorResponse) => this.errorService.showSnackBar(error)))
         .subscribe(model => {
            if(model) {
               let id: IdentityId = {name: model.name, orgID: model.id};
               this.refreshTree(id, IdentityType.ORGANIZATION);
               this.orgDropDownService.refresh();
            }

            this.loading = false;
         });
   }

   setOrganization(model: EditIdentityPaneModel) {
      let isCurrentOrgAndChanged = (model.oldName == this.userOrg && model.oldName != model.name) ||
         ((model.oldName == this.userOrg || model.name == this.userOrg) && (model as EditOrganizationPaneModel).id != this.userOrgID);

      if(isCurrentOrgAndChanged) {
         this.dialog.open(MessageDialog, {
            data: {
               title: "_#(js:em.security.userInfoChangedTitle)",
               content: "_#(js:em.security.userOrgInfoChanged)",
               type: MessageDialogType.CONFIRMATION
            }
         }).afterClosed().subscribe(val => {
            if(val) {
               let provider = Tool.byteEncodeURLComponent(this.selectedProvider);
               this.http.post("../api/em/security/users/edit-organization/" + provider, model).pipe(
                  catchError((error: HttpErrorResponse) => {
                     this.identityEditable.next(true);
                     return this.errorService.showSnackBar(error);
                  })).subscribe((msg) => {
                  if(msg) {
                     this.dialog.open(MessageDialog, {
                        data: {
                           title: "_#(js:Confirm)",
                           content: "_#(js:em.organization.renameIssue)",
                           type: MessageDialogType.CONFIRMATION
                        }
                     });
                  }

                  window.open("../logout?fromEm=true", "_self");
               });
            }
         });
      }
      else {
         this.loading = true;
         let provider = Tool.byteEncodeURLComponent(this.selectedProvider);
         this.http.post("../api/em/security/users/edit-organization/" + provider, model).pipe(
            catchError((error: HttpErrorResponse) => {
               this.loading = false;
               this.identityEditable.next(true);
               return this.errorService.showSnackBar(error);
            }),
            tap(() => this.refreshTree({name: model.name, orgID: (model as EditOrganizationPaneModel).id}, IdentityType.ORGANIZATION))
         ).subscribe((msg) => {
            if(msg) {
               this.dialog.open(MessageDialog, {
                  data: {
                     title: "_#(js:Confirm)",
                     content: "_#(js:em.organization.renameIssue)",
                     type: MessageDialogType.CONFIRMATION
                  }
               });
            }

            this.loading = false;
         });
      }
   }

   private postUserInfo(model: EditIdentityPaneModel, logout: boolean) {
      this.loading = true;
      const SET_USER_URI = "../api/em/security/users/edit-user/" +
         Tool.byteEncodeURLComponent(this.selectedProvider);
      this.http.post(SET_USER_URI, model).pipe(
         catchError((error: HttpErrorResponse) => {
            this.errorService.showSnackBar(error)
            return of(null);
         }),
         tap(() => {
            if(this.model.namedUsers && model.oldName != model.name) {
               this.snackBar.open("_#(js:em.security.userNameChangeWarning)", "_#(js:Close)", {duration: Tool.SNACKBAR_DURATION});
            }

            let id: IdentityId = {name: model.name, orgID: model.organization};
            this.refreshTree(id, IdentityType.USER);
         })
      ).subscribe(() => {
         if(logout) {
            window.open("../logout?fromEm=true", "_self");
         }

         this.loading = false;
      });
   }

   public newGroup(parentGroup: string) {
      let provider = Tool.byteEncodeURLComponent(this.selectedProvider);
      const uri = `../api/em/security/providers/${provider}/create-group`;
      this.http.post<EditGroupPaneModel>(uri, {parentGroup})
         .subscribe(model => this.refreshTree(
            {name: model.name, orgID: model.organization}, IdentityType.GROUP));
   }

   public newRole() {
      let provider = Tool.byteEncodeURLComponent(this.selectedProvider);
      this.http.get<EditRolePaneModel>("../api/em/security/user/create-role/" + provider)
         .subscribe(model => this.refreshTree(
            {name: model.name, orgID: model.organization}, IdentityType.ROLE));
   }

   setRole(model: EditRolePaneModel) {
      let provider = Tool.byteEncodeURLComponent(this.selectedProvider);
      this.http.post("../api/em/security/user/edit-role/" + provider, model).pipe(
         catchError((error: HttpErrorResponse) => this.errorService.showSnackBar(error)),
         tap(() => this.refreshTree(
            {name: model.name, orgID: model.organization}, IdentityType.ROLE))
      ).subscribe();
   }

   setGroup(model: EditGroupPaneModel) {
      let provider = Tool.byteEncodeURLComponent(this.selectedProvider);
      let group = Tool.byteEncodeURLComponent(convertToKey({name: model.oldName, orgID: model.organization}));

      const url = `../api/em/security/providers/${provider}/groups/${group}/`;
      this.http.post(url, model).pipe(
         catchError((error: HttpErrorResponse) => this.errorService.showSnackBar(error)),
         tap(() => this.refreshTree(
            {name: model.name, orgID: model.organization}, IdentityType.GROUP))
      ).subscribe();
   }

   deleteIdentities(): void {
      const identities: IdentityModel[] =
         this.selectedNodes.map(node => <IdentityModel>{
            identityID: node.identityID,
            type: node.type
         });

      let provider = Tool.byteEncodeURLComponent(this.selectedProvider);
      const uri = `../api/em/security/user/delete-identities/${provider}`;
      this.http.post<DeleteIdentitiesResponse>(uri, identities)
         .subscribe(response => {
            this.selectedNodes = [];

            if(response.warnings && response.warnings.length) {
               const content = response.warnings.join("\n");
               this.dialog.open(MessageDialog, {
                  width: "350px",
                  data: {
                     title: "_#(js:Error)",
                     content: content,
                     type: MessageDialogType.ERROR
                  }
               });
            }

            let sameTypeNode: SecurityTreeNodeModel = this.getSameTypeNode();

            if(this.model.namedUsers && this.selectedNodes.some(node => node.type == IdentityType.USER)) {
               this.snackBar.open("_#(js:em.security.userNameChangeWarning)", "_#(js:Close)", {duration: Tool.SNACKBAR_DURATION});
            }

            if(sameTypeNode != null && this.isSysAdmin) {
               this.refreshTree(sameTypeNode.identityID, sameTypeNode.type);
            }
            else {
               this.refreshTree();
            }
         });
   }

   private getSameTypeNode(): SecurityTreeNodeModel {
      for(let selectNode of this.selectedNodes) {
         let node = this.getSameTypeNode0(selectNode.type);

         if(node != null && this.selectedNodes.find(n => n.identityID === node.identityID) == null) {
            return node;
         }
      }

      return null;
   }

   private getSameTypeNode0(type: number): SecurityTreeNodeModel {
      if(type == IdentityType.ROLE && this.model.roles.children.length != 0) {
         return this.model.roles.children[0];
      }
      else if(type == IdentityType.GROUP && this.model.groups.children.length != 0) {
         return this.model.groups.children[0];
      }
      else if(type == IdentityType.USER && this.model.users.children.length != 0) {
         return this.model.users.children[0];
      }

      return null;
   }

   private refreshTree(id?: IdentityId, type?: number, providerChanged?: boolean, selectProvider: boolean = false) {
      if(!!selectProvider) {
         this.orgDropdownService.refresh(this.selectedProvider, providerChanged);
      }

      let provider = Tool.byteEncodeURLComponent(this.selectedProvider);
      const url = "../api/em/security/user/get-security-tree-root/" +
         provider + "/" + !!providerChanged;
      this.treeData = this.http.get<SecurityTreeRootModel>(url).pipe(
         map(model => {
            this.isMultiTenant = model.isMultiTenant;
            this.model = model;
            this.providerEditable = model.editable;

            if(!model.isMultiTenant) {
               const userRoot = this.treeService.createSecurityTreeNode(model.users);
               const groupRoot = this.treeService.createSecurityTreeNode(model.groups);
               const roleRoot = this.treeService.createSecurityTreeNode(model.roles);

               if(id && type == IdentityType.ROLE) {
                  this.selectedNodes = [new SecurityTreeNode(id, type)];
                  this.nodeToExpand = roleRoot;
               }
               else if(id && (type == IdentityType.USER || type == IdentityType.GROUP)) {
                  this.selectedNodes = [new SecurityTreeNode(id, type)];
                  this.nodeToExpand = type == IdentityType.USER ? userRoot : groupRoot;
               }

               return [userRoot, groupRoot, roleRoot];
            }
            else {
               const orgRoot = this.treeService.createSecurityTreeNode(model.organizations);
               const roleRoot = this.treeService.createSecurityTreeNode(model.roles);


               if(id && type == IdentityType.ROLE) {
                  this.selectedNodes = [new SecurityTreeNode(id, type)];

                  if(id.name == this.getOrgRoleRoot().identityID.name) {
                     this.selectedNodes[0].root = true;
                  }
                  else {
                     this.nodeToExpand = this.getOrgRoleRoot();
                  }
               }
               else if(id && (type == IdentityType.USER || type == IdentityType.GROUP)) {
                  this.selectedNodes = [new SecurityTreeNode(id, type)];

                  if(id.name == this.getUserRoot().identityID.name ||
                     id.name == this.getGroupRoot().identityID.name)
                  {
                     this.selectedNodes[0].root = true;
                  }
                  else {
                     this.nodeToExpand = type == IdentityType.USER ? this.getUserRoot() : this.getGroupRoot();
                  }
               }
               else if(id && (type == IdentityType.ORGANIZATION)) {
                  this.selectedNodes = [new SecurityTreeNode(id, type)];
                  this.nodeToExpand = orgRoot;
               }

               if(roleRoot && roleRoot.children && roleRoot.children.length > 0) {
                  return [orgRoot, roleRoot];
               }
               else {
                  return [orgRoot];
               }
            }
         }));

      // refresh the users after making changes
      if(id != null || providerChanged) {
         this.usersService.loadScheduleUsers();
      }
   }

   private getOrgRoleRoot() {
      return new SecurityTreeNode({name: "Organization Roles", orgID: this.currOrg}, IdentityType.ROLE);
   }

   private getGroupRoot() {
      return new SecurityTreeNode({name: "Groups", orgID: this.currOrg}, IdentityType.GROUP);
   }

   private getUserRoot() {
      return new SecurityTreeNode({name: "Users", orgID: this.currOrg}, IdentityType.USER);
   }

   expandNode(treeControl: FlatTreeControl<FlatSecurityTreeNode>) {
      if(this.nodeToExpand) {
         let node: FlatSecurityTreeNode = treeControl.dataNodes
            .find(dataNode => dataNode.identityID == this.nodeToExpand.identityID && this.nodeToExpand.type == dataNode.type);

         if(node) {
            if(!this.isMultiTenant) {
               treeControl.expand(node);
               this.nodeToExpand = null;
            }
            else {
               treeControl.expand(node);

               //also expand root organization node
               node = treeControl.dataNodes
                  .find(dataNode => IdentityType.ORGANIZATION == dataNode.type);
               treeControl.expand(node);

               this.nodeToExpand = null;
            }
         }
      }
   }

   ngOnDestroy(): void {
      this.identityEditable.complete();
   }
}