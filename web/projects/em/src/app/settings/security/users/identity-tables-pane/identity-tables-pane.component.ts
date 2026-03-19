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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { Tool } from "../../../../../../../shared/util/tool";
import { IdentityModel } from "../../security-table-view/identity-model";
import { IdentityType } from "../../../../../../../shared/data/identity-type";
import { SecurityTreeDialogData } from "../../security-tree-dialog/security-tree-dialog-data";
import { PropertyModel } from "../../property-table-view/property-model";
import { MessageDialog, MessageDialogType } from "../../../../common/util/message-dialog";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import {convertToKey, IdentityId} from "../identity-id";
import {
   COPY_PASTE_CONTEXT_IDENTITY_MEMBERS,
   COPY_PASTE_CONTEXT_IDENTITY_PERMISSIONS,
   COPY_PASTE_CONTEXT_IDENTITY_ROLES,
   IdentityCopyPasteContext
} from "../../security-table-view/identity-clipboard.service";

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

   readonly copyPasteContextMembers = COPY_PASTE_CONTEXT_IDENTITY_MEMBERS;
   readonly copyPasteContextRoles = COPY_PASTE_CONTEXT_IDENTITY_ROLES;
   readonly copyPasteContextPermissions = COPY_PASTE_CONTEXT_IDENTITY_PERMISSIONS;

   // dataSource is intentionally not passed as pasteExcludeIdentities: paste replaces the full list,
   // so the badge count equals the number of entries the list will contain after pasting.
   private _selfIdentity: IdentityModel[] = [];
   private _selfIdentityName: string;
   private _selfIdentityType: number;

   get selfIdentity(): IdentityModel[] {
      if(this.name !== this._selfIdentityName || this.type !== this._selfIdentityType) {
         this._selfIdentityName = this.name;
         this._selfIdentityType = this.type;
         this._selfIdentity = this.name
            ? [{ identityID: { name: this.name, orgID: null }, type: this.type }]
            : [];
      }

      return this._selfIdentity;
   }

   constructor(private dialog: MatDialog, private snackBar: MatSnackBar) {
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
      this.addMembersCore(members);
      this.membersChanged.emit(this.members);
   }

   private addMembersCore(members: IdentityModel[]) {
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

   private removeMembers0(members: IdentityModel[]) {
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
      this.addRolesCore(roles);
      this.rolesChanged.emit(this.roles);
   }

   private addRolesCore(roles: IdentityModel[]) {
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
      this.addPermittedIdentitiesCore(identities);
      this.permittedIdentitiesChanged.emit(this.permittedIdentities);
   }

   private addPermittedIdentitiesCore(identities: IdentityModel[]) {
      const identityMap = this.getIdentityMap(this.permittedIdentities);

      identities.forEach(identity => {
         if(!this.containsIdentity(identityMap, identity)) {
            this.permittedIdentities.push(identity);
            this.addToIdentityMap(identityMap, identity);
         }
      });

      this.permittedIdentities = this.permittedIdentities.slice(0);
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

   // The roles table accepts both the ROLES and MEMBERS clipboard contexts so that a copied
   // members list can be pasted into the roles table (filtered to ROLE type by rolesPasteTypeFilter).
   // pasteTypeFilter is a first pass (type restriction); addMembers/addRoles do a second pass for
   // runtime guards (self-reference, deduplication) that the stateless clipboard service can't check.
   readonly rolesPasteContexts: IdentityCopyPasteContext[] = [COPY_PASTE_CONTEXT_IDENTITY_ROLES, COPY_PASTE_CONTEXT_IDENTITY_MEMBERS];
   readonly rolesPasteTypeFilter: IdentityType[] = [IdentityType.ROLE];
   private static readonly USER_MEMBER_PASTE_FILTER: IdentityType[] = [IdentityType.GROUP];
   private static readonly GROUP_ROLE_MEMBER_PASTE_FILTER: IdentityType[] = [IdentityType.USER, IdentityType.GROUP];

   get membersPasteTypeFilter(): IdentityType[] | null {
      switch(this.type) {
         case IdentityType.USER: return IdentityTablesPaneComponent.USER_MEMBER_PASTE_FILTER;
         // ROLE members are users/groups assigned to the role; ROLEs are excluded because addMembers() blocks them anyway.
         case IdentityType.GROUP:
         case IdentityType.ROLE: return IdentityTablesPaneComponent.GROUP_ROLE_MEMBER_PASTE_FILTER;
         default: return null;  // ORGANIZATION: accept all member types
      }
   }

   pasteMembers(identities: IdentityModel[]): void {
      this.pasteReplace(identities, this.members,
         list => this.members = list,
         ids => this.addMembersCore(ids),
         () => this.members,
         list => this.membersChanged.emit(list));
   }

   pasteRoles(identities: IdentityModel[]): void {
      this.pasteReplace(identities, this.roles,
         list => this.roles = list,
         ids => this.addRolesCore(ids),
         () => this.roles,
         list => this.rolesChanged.emit(list));
   }

   // No pasteExcludeIdentities for permissions: an entity can grant itself admin permissions.
   pastePermittedIdentities(identities: IdentityModel[]): void {
      this.pasteReplace(identities, this.permittedIdentities,
         list => this.permittedIdentities = list,
         ids => this.addPermittedIdentitiesCore(ids),
         () => this.permittedIdentities,
         list => this.permittedIdentitiesChanged.emit(list));
   }

   private pasteReplace(identities: IdentityModel[],
                         previous: IdentityModel[],
                         setList: (list: IdentityModel[]) => void,
                         addFn: (ids: IdentityModel[]) => void,
                         getList: () => IdentityModel[],
                         emitChanged: (list: IdentityModel[]) => void): void
   {
      // No try/catch: addFn (addMembersCore etc.) only does array push + slice on pre-validated data.
      setList([]);
      addFn(identities);
      const result = getList();

      // Intentionally checks identities (not previous): show "no match" even when previous was empty.
      if(result.length === 0 && identities.length > 0) {
         setList(previous);
         emitChanged(previous);
         this.snackBar.open("_#(js:em.security.clipboard.noMatchingIdentities)", null, { duration: Tool.SNACKBAR_DURATION });
      }
      else {
         emitChanged(result); // filtering count already shown via paste button badge ("N of M")
         this.snackBar.open("_#(js:em.security.identitiesPasted)", null, { duration: Tool.SNACKBAR_DURATION });
      }
   }

   protected readonly IdentityType = IdentityType;
}
