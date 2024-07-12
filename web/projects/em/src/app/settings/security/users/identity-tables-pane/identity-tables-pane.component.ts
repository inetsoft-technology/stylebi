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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { Tool } from "../../../../../../../shared/util/tool";
import { IdentityModel } from "../../security-table-view/identity-model";
import { IdentityType } from "../../../../../../../shared/data/identity-type";
import { SecurityTreeDialogData } from "../../security-tree-dialog/security-tree-dialog-data";
import { PropertyModel } from "../../property-table-view/property-model";
import { MessageDialog, MessageDialogType } from "../../../../common/util/message-dialog";
import { MatDialog } from "@angular/material/dialog";
import {convertToKey, IdentityId} from "../identity-id";

@Component({
   selector: "em-identity-tables-pane",
   templateUrl: "./identity-tables-pane.component.html",
   styleUrls: ["./identity-tables-pane.component.scss"]
})
export class IdentityTablesPaneComponent {
   @Input() name: string;
   @Input() globalRole: boolean = false;
   @Input() type: number;
   @Input() members: IdentityModel[];
   @Input() currentUserName: string;
   @Input() roles: IdentityModel[] = [];
   @Input() root: boolean = false;
   @Input() permittedIdentities: IdentityModel[] = [];
   @Input() properties: PropertyModel[] = [];
   @Input() editable: boolean = true;
   @Output() membersChanged: EventEmitter<IdentityModel[]> = new EventEmitter();
   @Output() rolesChanged: EventEmitter<IdentityModel[]> = new EventEmitter();
   @Output() permittedIdentitiesChanged: EventEmitter<IdentityModel[]> = new EventEmitter();
   @Output() propertiesChanged: EventEmitter<PropertyModel[]> = new EventEmitter();
   _provider: string;

   @Input() set provider(provider: string) {
      this._provider = provider;
      this.membersType.provider = provider;
      this.groupsType.provider = provider;
      this.rolesType.provider = provider;
      this.organizationsType.provider = provider;
      this.permissionsType.provider = provider;
   }

   get provider(): string {
      return this._provider;
   }

   constructor(private dialog: MatDialog) {
   }

   get userTableLabel(): string {
      return this.type === IdentityType.ROLE ? "_#(js:Assigned to)" :
         this.type === IdentityType.GROUP || this.type === IdentityType.ORGANIZATION ? "_#(js:Members)" : "_#(js:Member Of)";

   }

   get roleTableLabel(): string {
      return this.type === IdentityType.ROLE ? "_#(js:Inherit from)" : "_#(js:Roles)";
   }

   get permissionTableLabel(): string {
      return this.root || this.type !== IdentityType.ROLE ?
         "_#(js:Administrator Permissions)" : "_#(js:Assign Permissions)";
   }

   get propertyLabel(): string {
      return "_#(js:Properties)";
   }

   membersType = <SecurityTreeDialogData> {
      dialogTitle: "_#(js:Add Group/User)",
      usersEnabled: true,
      groupsEnabled: true
   };

   allMembersType = <SecurityTreeDialogData> {
      dialogTitle: "_#(js:Add Group/User/Organization)",
      usersEnabled: true,
      groupsEnabled: true,
      rolesEnabled: true,
      organizationsEnabled: true
   };

   groupsType = <SecurityTreeDialogData> {
      dialogTitle: "_#(js:Add Group)",
      groupsEnabled: true
   };

   organizationsType = <SecurityTreeDialogData> {
      dialogTitle: "_#(js:Add Organization)",
      organizationsEnabled: true
   };

   rolesType = <SecurityTreeDialogData> {
      dialogTitle: "_#(js:Add Role)",
      rolesEnabled: true
   };

   permissionsType = <SecurityTreeDialogData> {
      dialogTitle: "_#(js:Add Permission)",
      usersEnabled: true,
      groupsEnabled: true,
      rolesEnabled: true,
      organizationsEnabled: true
   };

   addMembers(members: IdentityModel[]) {
      const identityMap = this.getIdentityMap(this.members);

      members.forEach(member => {
         const isSelf = this.type === member.type && this.name === member.identityID.name;
         const canAddUsers = !(this.type === IdentityType.USER && member.type ===
            IdentityType.USER);

         if((this.type == IdentityType.ORGANIZATION || (member.type !== IdentityType.ROLE)) && !isSelf && canAddUsers &&
            !this.containsIdentity(identityMap, member))
         {
            this.members.push(member);
            this.addToIdentityMap(identityMap, member);
         }
      });

      this.members = this.members.slice(0);
      this.membersChanged.emit(this.members);
   }

   removeMembers(members: IdentityModel[]) {
      if(!!this.currentUserName && members != null &&
         members.find(identify => identify.identityID.name == this.currentUserName) != null)
      {
         this.dialog.open(MessageDialog, {
            width: "350px",
            data: {
               title: "_#(js:Delete)",
               content: "_#(js:em.security.delCurrentUser)",
               type: MessageDialogType.INFO
            }
         });

         return;
      }

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
            this.removeMembers0(members);
         }
      });
   }

   removeMembers0(members: IdentityModel[]) {
      const identityMap = this.getIdentityMap(this.members);

      members.forEach(member => {
         identityMap.delete(this.getIdentityKey(member));
      });

      const newMembers = [];

      this.members.forEach((member) => {
         if(this.containsIdentity(identityMap, member)) {
            newMembers.push(member);
         }
      });

      this.members = newMembers;
      this.membersChanged.emit(this.members);
   }

   addRoles(roles: IdentityModel[]) {
      const identityMap = this.getIdentityMap(this.roles);

      roles.forEach(role => {
         if(role.type === IdentityType.ROLE && role.identityID.name !== this.name &&
            !this.containsIdentity(identityMap, role))
         {
            this.roles.push(role);
            this.addToIdentityMap(identityMap, role);
         }
      });

      this.roles = this.roles.slice(0);
      this.rolesChanged.emit(this.roles);
   }

   removeRoles(roles: IdentityModel[]) {
      const identityMap = this.getIdentityMap(this.roles);

      roles.forEach(role => {
         identityMap.delete(this.getIdentityKey(role));
      });

      const newRoles = [];

      this.roles.forEach((role) => {
         if(this.containsIdentity(identityMap, role)) {
            newRoles.push(role);
         }
      });

      this.roles = newRoles;
      this.rolesChanged.emit(this.roles);
   }

   addPermittedIdentities(identities: IdentityModel[]) {
      const identityMap = this.getIdentityMap(this.permittedIdentities);

      identities.forEach(identity => {
         if(!this.containsIdentity(identityMap, identity)) {
            this.permittedIdentities.push(identity);
            this.addToIdentityMap(identityMap, identity);
         }
      });

      this.permittedIdentities = this.permittedIdentities.slice(0);
      this.permittedIdentitiesChanged.emit(this.permittedIdentities);
   }

   addProperties(models: PropertyModel[]) {
      let newProperties = [];

      for(let i = 0; i < this.properties.length; i++) {
         let find = false;
         let property = this.properties[i];

         for(let j = 0; j < models.length; j++) {
            if(property.name == models[j].name) {
               find = true;
               break;
            }
         }

         if(!find) {
            newProperties.push(property);
         }
      }

      for(let j = 0; j < models.length; j++) {
         newProperties.push(models[j]);
      }

      this.properties = newProperties;
      this.propertiesChanged.emit(this.properties);
   }

   removeProperties(models: PropertyModel[]) {
      let newProperties = [];

      for(let i = 0; i < this.properties.length; i++) {
         let find = false;
         let property = this.properties[i];

         for(let j = 0; j < models.length; j++) {
            if(property.name == models[j].name) {
               find = true;
               break;
            }
         }

         if(!find) {
            newProperties.push(property);
         }
      }

      this.properties = newProperties;
      this.propertiesChanged.emit(this.properties);
   }

   removePermittedIdentities(identities: IdentityModel[]) {
      const identityMap = this.getIdentityMap(this.permittedIdentities);

      identities.forEach(identity => {
         identityMap.delete(this.getIdentityKey(identity));
      });

      const newPermittedIdentities = [];

      this.permittedIdentities.forEach((identity) => {
         if(this.containsIdentity(identityMap, identity)) {
            newPermittedIdentities.push(identity);
         }
      });

      this.permittedIdentities = newPermittedIdentities;
      this.permittedIdentitiesChanged.emit(this.permittedIdentities);
   }

   containsIdentity(identityMap: Map<String, boolean>, identity: IdentityModel): boolean {
      return identityMap.has(this.getIdentityKey(identity));
   }

   private getIdentityKey(identity: IdentityModel): string {
      return convertToKey(identity.identityID) + "," + identity.type;
   }

   private getIdentityMap(identities: IdentityModel[]): Map<string, boolean> {
      const identityMap = new Map<string, boolean>();

      identities.forEach((identity) => {
         identityMap.set(this.getIdentityKey(identity), true);
      });

      return identityMap;
   }

   private addToIdentityMap(identityMap: Map<string, boolean>, identity: IdentityModel) {
      identityMap.set(this.getIdentityKey(identity), true);
   }

   protected readonly IdentityType = IdentityType;
}
