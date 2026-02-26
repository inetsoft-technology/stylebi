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
import { Injectable, OnDestroy } from "@angular/core";
import { Subject } from "rxjs";
import { takeUntil } from "rxjs/operators";
import { ResourcePermissionTableModel } from "./resource-permission-table-model";
import { ResourceAction } from "../../../../../../shared/util/security/resource-permission/resource-action.enum";
import { Tool } from "../../../../../../shared/util/tool";
import { OrganizationDropdownService } from "../../../navbar/organization-dropdown.service";
import { CopyPasteContext } from "./copy-paste-context";

@Injectable({
   providedIn: "root"
})
export class PermissionClipboardService implements OnDestroy {
   private copiedPermissions: { permissions: ResourcePermissionTableModel[], requiresBoth: boolean } | null = null;
   private providerAtCopy: string | null = null;
   private contextAtCopy: CopyPasteContext | null = null;
   private destroy$ = new Subject<void>();

   constructor(orgDropdownService: OrganizationDropdownService) {
      orgDropdownService.onRefresh.pipe(takeUntil(this.destroy$)).subscribe(({ provider }) => {
         if(this.providerAtCopy !== null && this.providerAtCopy !== provider) {
            this.copiedPermissions = null;
            this.providerAtCopy = null;
            this.contextAtCopy = null;
         }
      });
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.complete();
   }

   canPaste(context: CopyPasteContext | null = null): boolean {
      return this.copiedPermissions != null && this.contextsMatch(context, this.contextAtCopy);
   }

   /**
    * Returns the number of copied rows that would survive a paste into the current target.
    *
    * A row survives if at least one of its actions is present in {@code displayActions}.
    * This is consistent with {@link paste}: a row that has ADMIN will always be counted
    * when ADMIN is in displayActions (because ADMIN itself overlaps), and those rows are
    * also the ones that get expanded to the full displayActions set by paste(). Rows whose
    * actions have no overlap with displayActions are dropped by both methods.
    *
    * @param context - The paste target's context key; must match the context at copy-time.
    * @param displayActions - The actions supported by the target resource. Pass the model's
    *   `displayActions` array once it has loaded. Passing `null` (or omitting the argument)
    *   means the model is not yet loaded and the count is always 0 — this is intentional so
    *   that "Paste (N)" badges stay hidden until the target model is ready.
    */
   copiedCount(context: CopyPasteContext | null = null, displayActions: ResourceAction[] | null = null): number {
      if(!this.canPaste(context) || displayActions == null) {
         return 0;
      }

      return this.copiedPermissions.permissions
         .filter(row => row.actions.some(a => displayActions.includes(a)))
         .length;
   }

   /**
    * Returns the total number of copied rows, regardless of the target resource's supported actions.
    * Use alongside {@link copiedCount} to detect when action filtering reduces the paste result,
    * so the UI can show a "N of M" badge instead of just "N".
    */
   copiedTotal(context: CopyPasteContext | null = null): number {
      if(!this.canPaste(context)) {
         return 0;
      }

      return this.copiedPermissions.permissions.length;
   }

   copy(permissions: ResourcePermissionTableModel[], requiresBoth: boolean, provider: string,
        context: CopyPasteContext | null = null): void {
      this.copiedPermissions = {
         permissions: Tool.clone(permissions),
         requiresBoth
      };
      this.providerAtCopy = provider;
      this.contextAtCopy = context;
   }

   paste(context: CopyPasteContext | null = null, displayActions: ResourceAction[] | null = null):
      { permissions: ResourcePermissionTableModel[], requiresBoth: boolean } | null
   {
      if(!this.copiedPermissions || !this.contextsMatch(context, this.contextAtCopy) || displayActions == null) {
         return null;
      }

      const pastedPermissions: ResourcePermissionTableModel[] =
         Tool.clone(this.copiedPermissions.permissions)
            .map(row => {
               // ADMIN grants all permissions. If the copied row has ADMIN and the target
               // also supports ADMIN, expand to the full set of target actions so that
               // actions absent from the source (e.g. ACCESS on a portal dashboard) are
               // still granted, matching the semantics of holding the ADMIN permission.
               if(row.actions.includes(ResourceAction.ADMIN) && displayActions.includes(ResourceAction.ADMIN)) {
                  row.actions = [...displayActions];
               }
               else {
                  row.actions = row.actions.filter(a => displayActions.includes(a));
               }

               return row;
            })
            .filter(row => row.actions.length > 0);

      return {
         permissions: pastedPermissions,
         requiresBoth: this.copiedPermissions.requiresBoth
      };
   }

   private contextsMatch(a: CopyPasteContext | null, b: CopyPasteContext | null): boolean {
      return a !== null && b !== null && a === b;
   }
}
