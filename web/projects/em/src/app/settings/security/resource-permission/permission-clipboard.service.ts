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

@Injectable({
   providedIn: "root"
})
export class PermissionClipboardService {
   private copiedPermissions: { permissions: ResourcePermissionTableModel[], requiresBoth: boolean } | null = null;

   get canPaste(): boolean {
      return this.copiedPermissions != null;
   }

   copy(permissions: ResourcePermissionTableModel[], requiresBoth: boolean): void {
      this.copiedPermissions = {
         permissions: Tool.clone(permissions),
         requiresBoth
      };
   }

   paste(displayActions: ResourceAction[]): { permissions: ResourcePermissionTableModel[], requiresBoth: boolean } | null {
      if(!this.copiedPermissions) {
         return null;
      }

      const pastedPermissions: ResourcePermissionTableModel[] =
         Tool.clone(this.copiedPermissions.permissions).map(row => {
            row.actions = row.actions.filter(a => displayActions.includes(a));

            if(row.actions.length === 0) {
               row.actions = displayActions.slice(0);
            }

            return row;
         });

      return {
         permissions: pastedPermissions,
         requiresBoth: this.copiedPermissions.requiresBoth
      };
   }
}
