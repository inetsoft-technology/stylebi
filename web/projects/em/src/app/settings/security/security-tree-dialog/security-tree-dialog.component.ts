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
import { Component, Inject, OnInit } from "@angular/core";
import { MAT_DIALOG_DATA, MatDialogRef } from "@angular/material/dialog";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { IdentityType } from "../../../../../../shared/data/identity-type";
import { SecurityTreeNode } from "../security-tree-view/security-tree-node";
import { SecurityTreeViewComponent } from "../security-tree-view/security-tree-view.component";
import { SecurityTreeService } from "../users/security-tree.service";
import { SecurityTreeRootModel } from "../users/users-settings-view/security-tree-root-model";
import { SecurityTreeDialogData } from "./security-tree-dialog-data";

@Component({
  selector: "em-add-permission-dialog",
  templateUrl: "./security-tree-dialog.component.html",
  styleUrls: ["./security-tree-dialog.component.scss"]
})
export class SecurityTreeDialogComponent implements OnInit {
   treeData: Observable<SecurityTreeNode[]>;
   validTreeSelection: boolean = false;
   dialogData: SecurityTreeDialogData;

  constructor( private http: HttpClient,
               private treeService: SecurityTreeService,
               private dialogRef: MatDialogRef<SecurityTreeDialogComponent>,
               @Inject(MAT_DIALOG_DATA) data: SecurityTreeDialogData)
  {
     this.dialogData = data;
  }

  ngOnInit() {
     this.treeData = this.getTreeData();
  }

   private getTreeData(): Observable<SecurityTreeNode[]> {
      let params = new HttpParams();

      if(this.dialogData && this.dialogData.provider) {
         params = params.set("provider", this.dialogData.provider);
      }

      if(this.dialogData && this.dialogData.hideOrgAdminRole) {
         params = params.set("hideOrgAdminRole", this.dialogData.hideOrgAdminRole);
      }

      return this.http.get<SecurityTreeRootModel>("../api/em/settings/content/resource-tree",
         { params: params })
         .pipe(
            map((model: SecurityTreeRootModel) => {
               const isMultiTenant = model.isMultiTenant;
               const treeData = [];

               if(!isMultiTenant) {
                  if(this.dialogData.usersEnabled) {
                     treeData.push(this.treeService.createSecurityTreeNode(model.users));
                  }

                  if(this.dialogData.groupsEnabled) {
                     treeData.push(this.treeService.createSecurityTreeNode(model.groups));
                  }

                  if(this.dialogData.rolesEnabled) {
                     treeData.push(this.treeService.createSecurityTreeNode(model.roles));
                  }
               }
               else {
                  if(this.dialogData.organizationsEnabled) {
                     //structure to match security tree

                     //filter out children that are not enabled
                     if(this.dialogData.usersEnabled == null || !this.dialogData.usersEnabled) {
                        model.organizations.children = model.organizations.children.filter(child => child.identityID.name !== "Users");
                     }

                     if(!this.dialogData.groupsEnabled) {
                        model.organizations.children =  model.organizations.children.filter(child => child.identityID.name !== "Groups");
                     }

                     if(!this.dialogData.rolesEnabled) {
                        model.organizations.children =  model.organizations.children.filter(child => child.identityID.name !== "Organization Roles");
                     }

                     treeData.push(this.treeService.createSecurityTreeNode(model.organizations));

                     if(this.dialogData.rolesEnabled) {
                        if(model.roles && model.roles.children.length > 0) {
                           treeData.push(this.treeService.createSecurityTreeNode(model.roles));
                        }
                     }
                  }
                  else {
                     let users = model.organizations.children.find(child => child.identityID.name === "Users");
                     let groups = model.organizations.children.find(child => child.identityID.name === "Groups");
                     let orgRoles = model.organizations.children.find(child => child.identityID.name === "Organization Roles");
                     let globalRoles = model.roles;

                     if(this.dialogData.usersEnabled) {
                        treeData.push(this.treeService.createSecurityTreeNode(users));
                     }

                     if(this.dialogData.groupsEnabled) {
                        treeData.push(this.treeService.createSecurityTreeNode(groups));
                     }

                     if(this.dialogData.rolesEnabled) {
                        treeData.push(this.treeService.createSecurityTreeNode(orgRoles));

                        if(globalRoles && globalRoles.children.length > 0) {
                           treeData.push(this.treeService.createSecurityTreeNode(globalRoles));
                        }
                     }
                  }
               }

               for(let node of treeData) {
                  this.filterReadOnlyChildren(node);
               }

               return treeData;
            }));
   }

   private filterReadOnlyChildren(node: SecurityTreeNode) {
     if(node.children != null && node.children.length > 0) {
        let newChildren: SecurityTreeNode[] = [];
        let oldChildren: SecurityTreeNode[] = node.children;

        for(let child of oldChildren) {
           if(!child.readOnly) {
              newChildren.push(child);
              this.filterReadOnlyChildren(child);
           }
           else if(!!child.children) {
              child.children
                 .filter(c => c.type == child.type &&
                    !oldChildren.map(o => o.identityID).includes(c.identityID))
                 .map(c => oldChildren.push(c));
           }
        }

        node.children = newChildren;
     }
   }

   onTreeSelectionChange(selection: SecurityTreeNode[]): void {
      const validTypes = [IdentityType.USER, IdentityType.GROUP, IdentityType.ROLE, IdentityType.ORGANIZATION];
      this.validTreeSelection = selection.length > 0 &&
         selection.every(node => validTypes.includes(node.type) &&
            (!node.root || (node.root && node.type === IdentityType.USER)));
   }

   addPermission(tree: SecurityTreeViewComponent) {
      const newSelection = [];
      const selection = tree.sendSelection();
      const userRootNodeIndex = selection.findIndex(
         (node) => node.root && node.type === IdentityType.USER);

      // add all the users if user root node is selected
      if(userRootNodeIndex != -1) {
         for(let childNode of selection[userRootNodeIndex].children) {
            newSelection.push(childNode);
         }
      }

      // avoid duplicates
      selection.forEach((node) => {
         if(userRootNodeIndex === -1 || node.type !== IdentityType.USER) {
            newSelection.push(node);
         }
      });

      this.dialogRef.close(newSelection);
   }

   cancel() {
      this.dialogRef.close(null);
   }
}
