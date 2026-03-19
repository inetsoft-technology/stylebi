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
import { Subscription } from "rxjs";
import { IdentityType } from "../../../../../../shared/data/identity-type";
import { Tool } from "../../../../../../shared/util/tool";
import { OrganizationDropdownService } from "../../../navbar/organization-dropdown.service";
import { IdentityModel } from "./identity-model";

export const COPY_PASTE_CONTEXT_IDENTITY_MEMBERS = "identity-members";
export const COPY_PASTE_CONTEXT_IDENTITY_ROLES = "identity-roles";
export const COPY_PASTE_CONTEXT_IDENTITY_PERMISSIONS = "identity-permissions";

export type IdentityCopyPasteContext =
   typeof COPY_PASTE_CONTEXT_IDENTITY_MEMBERS |
   typeof COPY_PASTE_CONTEXT_IDENTITY_ROLES |
   typeof COPY_PASTE_CONTEXT_IDENTITY_PERMISSIONS;

@Injectable({
   providedIn: "root"
})
// implements OnDestroy so tests can call ngOnDestroy() directly; Angular only calls it at application teardown for root-scoped services.
export class IdentityClipboardService implements OnDestroy {
   private copiedIdentities: IdentityModel[] | null = null;
   private contextAtCopy: IdentityCopyPasteContext | null = null;
   private readonly subscription = new Subscription();

   constructor(orgDropdownService: OrganizationDropdownService) {
      this.subscription.add(
         orgDropdownService.onRefresh.subscribe(({ providerChanged }) => {
            if(providerChanged && this.copiedIdentities !== null) {
               this.copiedIdentities = null;
               this.contextAtCopy = null;
            }
         })
      );

      this.subscription.add(
         orgDropdownService.onOrgChange.subscribe(() => {
            this.copiedIdentities = null;
            this.contextAtCopy = null;
         })
      );
   }

   /**
    * Returns true if there is clipboard content that matches the given paste context.
    *
    * Note: passing no argument (or {@code null}) always returns {@code false} because a null context
    * never matches any stored context. Always pass the target context to get a meaningful result.
    */
   canPaste(context: IdentityCopyPasteContext | IdentityCopyPasteContext[] | null = null): boolean {
      return this.copiedIdentities != null && this.anyContextMatches(context, this.contextAtCopy);
   }

   /**
    * Returns true if the clipboard has any content, regardless of context.
    * Use to distinguish an empty clipboard from a context mismatch in UI messages.
    */
   hasContent(): boolean {
      return this.copiedIdentities != null && this.copiedIdentities.length > 0;
   }

   /**
    * Returns the number of copied identities that would survive a paste into the current target.
    *
    * A row survives if its type is present in {@code typeFilter} and it does not match any entry
    * in {@code excludeIdentities} (matched by type + name). Pass {@code null} to skip either filter.
    */
   copiedCount(
      context: IdentityCopyPasteContext | IdentityCopyPasteContext[] | null = null,
      typeFilter: IdentityType[] | null = null,
      excludeIdentities: IdentityModel[] | null = null
   ): number {
      if(!this.canPaste(context)) {
         return 0;
      }

      return this.applyFilters(this.copiedIdentities!, typeFilter, excludeIdentities).length;
   }

   /**
    * Returns the total number of copied identities, regardless of type filtering.
    * Use alongside {@link copiedCount} to detect when filtering reduces the paste result,
    * so the UI can show a "N of M" badge instead of just "N".
    */
   copiedTotal(
      context: IdentityCopyPasteContext | IdentityCopyPasteContext[] | null = null
   ): number {
      if(!this.canPaste(context)) {
         return 0;
      }

      return this.copiedIdentities!.length;
   }

   ngOnDestroy(): void {
      this.subscription.unsubscribe();
   }

   copy(identities: IdentityModel[], context: IdentityCopyPasteContext | null = null): void {
      if(!identities.length) {
         return;
      }

      this.copiedIdentities = Tool.clone(identities);
      this.contextAtCopy = context;
   }

   paste(
      context: IdentityCopyPasteContext | IdentityCopyPasteContext[] | null = null,
      typeFilter: IdentityType[] | null = null,
      excludeIdentities: IdentityModel[] | null = null
   ): IdentityModel[] | null {
      if(!this.canPaste(context)) {
         return null;
      }

      const cloned = Tool.clone(this.copiedIdentities!);
      return this.applyFilters(cloned, typeFilter, excludeIdentities);
   }

   private applyFilters(
      identities: IdentityModel[],
      typeFilter: IdentityType[] | null,
      excludeIdentities: IdentityModel[] | null
   ): IdentityModel[] {
      let result = typeFilter == null
         ? identities
         : identities.filter(i => typeFilter.includes(i.type));

      if(excludeIdentities != null && excludeIdentities.length > 0) {
         result = result.filter(i => !excludeIdentities.some(
            e => e.type === i.type && e.identityID.name === i.identityID.name));
      }

      return result;
   }

   private anyContextMatches(
      context: IdentityCopyPasteContext | IdentityCopyPasteContext[] | null,
      stored: IdentityCopyPasteContext | null
   ): boolean {
      if(context === null || stored === null) {
         return false;
      }

      if(Array.isArray(context)) {
         return context.some(c => c === stored);
      }

      return context === stored;
   }
}
