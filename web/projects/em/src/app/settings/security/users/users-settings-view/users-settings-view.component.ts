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
import { BreakpointObserver } from "@angular/cdk/layout";
import { FlatTreeControl } from "@angular/cdk/tree";
import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { Subject, Subscription } from "rxjs";
import { IdentityType } from "../../../../../../../shared/data/identity-type";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";
import { Tool } from "../../../../../../../shared/util/tool";
import { MessageDialog, MessageDialogType } from "../../../../common/util/message-dialog";
import { TopScrollService } from "../../../../top-scroll/top-scroll.service";
import {
   FlatSecurityTreeNode,
   SecurityTreeNode
} from "../../security-tree-view/security-tree-node";
import {
   EditGroupPaneModel,
   EditRolePaneModel,
   EditOrganizationPaneModel,
   EditUserPaneModel
} from "../edit-identity-pane/edit-identity-pane.model";
import { SecurityTreeViewComponent } from "../../security-tree-view/security-tree-view.component";
import { MatSlideToggleChange } from "@angular/material/slide-toggle";

const SMALL_WIDTH_BREAKPOINT = 720;

@Component({
   selector: "em-users-settings-view",
   templateUrl: "./users-settings-view.component.html",
   styleUrls: ["./users-settings-view.component.scss"]
})
export class UsersSettingsViewComponent implements OnInit {
   @Input() treeData: SecurityTreeNode[];
   @Input() authenticationProviders: string[];
   @Input() selectedProvider: string;
   @Input() providerEditable: boolean = true;
   @Input() isSysAdmin = false;
   @Input() isLoadingTemplate: boolean = false;
   @Input() identityEditable: Subject<boolean>;

   @Output() providerChanged: EventEmitter<string> = new EventEmitter();
   @Output() newUser = new EventEmitter<String>();
   @Output() newRole = new EventEmitter<void>();
   @Output() newGroup = new EventEmitter<String>();
   @Output() newOrganization = new EventEmitter<String>();
   @Output() userSettingsChanged = new EventEmitter<EditUserPaneModel>();
   @Output() roleSettingsChanged = new EventEmitter<EditRolePaneModel>();
   @Output() groupSettingsChanged = new EventEmitter<EditGroupPaneModel>();
   @Output() organizationSettingsChanged = new EventEmitter<EditOrganizationPaneModel>();
   @Output() setTemplateOrganizationClicked = new EventEmitter<EditOrganizationPaneModel>();
   @Output() deleteIdentities = new EventEmitter<void>();
   @Output() selectionChanged = new EventEmitter<SecurityTreeNode[]>();
   @Output() treeUpdated = new EventEmitter<FlatTreeControl<FlatSecurityTreeNode>>();
   @Output() pageChangedEmitter = new EventEmitter<boolean>();
   @ViewChild(SecurityTreeViewComponent) securityTree: SecurityTreeViewComponent;
   private _selectedNodes: SecurityTreeNode[] = [];
   private rootNodes: string[] = ["_#(js:Users)", "_#(js:Groups)", "_#(js:Roles)", "_#(js:Organizations)", "_#(js:Organization Roles)"];
   pageChanged: boolean = false;
   isEnterprise: boolean;
   selectedIdentity: SecurityTreeNode;
   private subscriptions = new Subscription()

   //For small device use only
   private _editing = false;

   @Input()
   set selectedNodes(selectedNodes: SecurityTreeNode[]) {
      this._selectedNodes = selectedNodes;

      if(selectedNodes.length == 1) {
         this.pageChanged = false;
         this.selectedIdentity = this.selectedNodes[0];
      }
      else if(selectedNodes.length == 0) {
         this.selectedIdentity = null;
      }
   }

   get selectedNodes(): SecurityTreeNode[] {
      return this._selectedNodes;
   }

   get editing(): boolean {
      return this._editing;
   }

   set editing(value: boolean) {
      if(value !== this._editing) {
         this._editing = value;
         this.scrollService.scroll("up");
      }
   }

   constructor(private dialog: MatDialog,
               private breakpointObserver: BreakpointObserver,
               private appInfoService: AppInfoService,
               private scrollService: TopScrollService)
   {
   }

   ngOnInit() {
      this.subscriptions.add(this.appInfoService.isEnterprise().subscribe((isEnterprise) => {
         this.isEnterprise = isEnterprise;
      }));
   }

   get deleteDisabled(): boolean {
      return !this.providerEditable || !this.selectedNodes || this.selectedNodes.length == 0 ||
         this.selectedNodes.every(node => this.rootNodes.indexOf(node.identityID.name) != -1) ||
         !!this.selectedNodes.find(node => node.readOnly);
   }

   get newUserDisabled(): boolean {
      if(!this.treeData || !this.providerEditable) {
         return true;
      }

      if(this.selectedIdentity && this.selectedIdentity.type === IdentityType.GROUP &&
         !this.selectedIdentity.root)
      {
         return this.selectedIdentity.readOnly;
      }

      // check root permission
      const root = this.searchRootNode(IdentityType.USER);
      return !!root && root.readOnly;
   }

   get newOrganizationDisabled(): boolean {
      if(!this.treeData || !this.providerEditable) {
         return true;
      }

      return false;
   }

   get newGroupDisabled(): boolean {
      if(!this.treeData || !this.providerEditable) {
         return true;
      }

      if(this.selectedIdentity && this.selectedIdentity.type === IdentityType.GROUP) {
         return this.selectedIdentity.readOnly;
      }

      // check root permission
      const root = this.searchRootNode(IdentityType.GROUP);
      return !!root && root.readOnly;
   }

   get newRoleDisabled(): boolean {
      if(!this.treeData || !this.providerEditable) {
         return true;
      }

      const root = this.searchRootNode(IdentityType.ROLE);

      return (root != null && root.readOnly);
   }

   private searchRootNode(type: number): SecurityTreeNode {
      if(this.treeData?.length <= 0) {
         return null;
      }

      let orgNode: SecurityTreeNode;

      for(let node of this.treeData) {
         if(node.root && node?.type == type) {
            return node;
         }

         if(node.type == IdentityType.ORGANIZATION) {
            orgNode = node;
         }
      }

      if(orgNode?.children) {
         return orgNode.children.find(n => n?.root && n?.type == type);
      }

      return null;
   }

   selectedIdentityChanged(identity: SecurityTreeNode) {
      if(!this.pageChanged) {
         this.selectedIdentity = identity;
      }
   }

   createUser(): void {
      this.emitNewIdentity(this.newUser);
   }

   createGroup(): void {
      this.emitNewIdentity(this.newGroup);
   }

   createOrganization(): void {
      this.emitNewIdentity(this.newOrganization);
   }

   delete() {
      let dialogRef = this.dialog.open(MessageDialog, {
         width: "350px",
         data: {
            title: "_#(js:Delete)",
            content: "_#(js:em.security.delete.confirm)",
            type: MessageDialogType.DELETE
         }
      });

      dialogRef.afterClosed().subscribe(del => {
         if(del) {
            this.deleteIdentities.emit();
         }
      });
   }

   onLoadIdentityError(): void {
      this.selectedNodes = [];
   }

   private emitNewIdentity(emitter: EventEmitter<String>): void {
      let parent: string = null;

      if(this.selectedIdentity && !this.selectedIdentity.root && this.selectedIdentity.type == IdentityType.GROUP)
      {
         parent = this.selectedIdentity.identityID.name;
      }

      emitter.emit(parent);
   }
   onPageChanged(changed: boolean = true): void {
      this.pageChanged = changed;
      this.pageChangedEmitter.emit(changed);
   }

   isScreenSmall(): boolean {
      return this.breakpointObserver.isMatched(`(max-width: ${SMALL_WIDTH_BREAKPOINT}px)`);
   }

   newOrgEnabled(): boolean {
      const userRoot = this.treeData ? this.treeData.find(node => node.root && node.type === IdentityType.USER) : null;
      return (userRoot == null && this.isSysAdmin && this.isEnterprise);
   }
}
