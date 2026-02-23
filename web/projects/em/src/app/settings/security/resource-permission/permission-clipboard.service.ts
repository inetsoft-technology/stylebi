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
import { Injectable } from "@angular/core";
import { ResourcePermissionTableModel } from "./resource-permission-table-model";
import { ResourceAction } from "../../../../../../shared/util/security/resource-permission/resource-action.enum";
import { Tool } from "../../../../../../shared/util/tool";
import { OrganizationDropdownService } from "../../../navbar/organization-dropdown.service";

@Injectable({
   providedIn: "root"
})
export class PermissionClipboardService {
   private copiedPermissions: { permissions: ResourcePermissionTableModel[], requiresBoth: boolean } | null = null;
   private providerAtCopy: string | null = null;
   private contextAtCopy: string | null = null;

   constructor(orgDropdownService: OrganizationDropdownService) {
      // This service is a root singleton that lives for the full app lifetime.
      // The subscription intentionally runs forever — no teardown is needed.
      orgDropdownService.onRefresh.subscribe(({ provider }) => {
         if(this.providerAtCopy !== null && this.providerAtCopy !== provider) {
            this.copiedPermissions = null;
            this.providerAtCopy = null;
            this.contextAtCopy = null;
         }
      });
   }

   canPaste(context: string | null = null): boolean {
      return this.copiedPermissions != null && this.contextsMatch(context, this.contextAtCopy);
   }

   copiedCount(context: string | null = null, displayActions: ResourceAction[] | null = null): number {
      if(!this.canPaste(context)) {
         return 0;
      }

      if(displayActions == null) {
         return this.copiedPermissions?.permissions?.length ?? 0;
      }

      return this.copiedPermissions.permissions
         .filter(row => row.actions.some(a => displayActions.includes(a)))
         .length;
   }

   copy(permissions: ResourcePermissionTableModel[], requiresBoth: boolean, provider: string,
        context: string | null = null): void {
      this.copiedPermissions = {
         permissions: Tool.clone(permissions),
         requiresBoth
      };
      this.providerAtCopy = provider;
      this.contextAtCopy = context;
   }

   paste(displayActions: ResourceAction[], context: string | null = null):
      { permissions: ResourcePermissionTableModel[], requiresBoth: boolean } | null
   {
      if(!this.copiedPermissions || !this.contextsMatch(context, this.contextAtCopy)) {
         return null;
      }

      const pastedPermissions: ResourcePermissionTableModel[] =
         Tool.clone(this.copiedPermissions.permissions)
            .map(row => {
               row.actions = row.actions.filter(a => displayActions.includes(a));
               return row;
            })
            .filter(row => row.actions.length > 0);

      return {
         permissions: pastedPermissions,
         requiresBoth: this.copiedPermissions.requiresBoth
      };
   }

   private contextsMatch(a: string | null, b: string | null): boolean {
      return a === b;
   }
}
