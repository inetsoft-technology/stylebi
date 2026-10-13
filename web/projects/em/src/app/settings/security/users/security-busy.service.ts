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
import { BehaviorSubject } from "rxjs";
import { Tool } from "../../../../../../shared/util/tool";
import { PropertyModel } from "../property-table-view/property-model";

@Injectable({ providedIn: "root" })
export class SecurityBusyService {
   private _orgLoading = new BehaviorSubject<boolean>(false);
   orgLoading$ = this._orgLoading.asObservable();
   private savedOrgProperties: { orgId: string, properties: PropertyModel[] } = null;

   get orgLoading() {
      return this._orgLoading.value;
   }

   beginOrgSave() {
      this._orgLoading.next(true);
   }

   endOrgSave() {
      this._orgLoading.next(false);
   }

   /**
    * Remembers the properties that a successful organization save wrote, for the reload of that
    * organization that follows the save. Organization properties are kept in each cluster node's
    * properties, and the other nodes pick up a change only after a short delay, so the reload can
    * be answered by a node that still returns the properties from before the save (Bug #78447).
    */
   setSavedOrgProperties(orgId: string, properties: PropertyModel[]) {
      this.savedOrgProperties = { orgId, properties: Tool.clone(properties) };
   }

   /**
    * Returns the properties remembered by setSavedOrgProperties() if they are for the given
    * organization, or null otherwise. They are used for one load only: any organization load
    * clears them, so a later selection shows the server's properties again.
    */
   takeSavedOrgProperties(orgId: string): PropertyModel[] {
      const saved = this.savedOrgProperties;
      this.savedOrgProperties = null;

      return saved != null && orgId != null && saved.orgId?.toLowerCase() === orgId.toLowerCase() ?
         saved.properties : null;
   }
}