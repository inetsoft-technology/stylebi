/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
import { Tool } from "../../../../../../shared/util/tool";
import { PropertyModel } from "../property-table-view/property-model";

/**
 * Hands the properties written by an organization save to the reload of that organization that
 * follows the save. Organization properties are kept in each cluster node's properties, and the
 * other nodes pick up a change only after a short delay, so the reload can be answered by a node
 * that still returns the properties from before the save (Bug #78447).
 */
@Injectable({ providedIn: "root" })
export class SavedOrgPropertiesService {
   /**
    * How long the saved properties are kept for the reload, well past the delay before the other
    * nodes have the change, so a reload that never happens doesn't leave them behind.
    */
   static readonly EXPIRY = 5000;
   private saved: { orgId: string, properties: PropertyModel[], time: number } = null;

   set(orgId: string, properties: PropertyModel[]): void {
      this.saved = { orgId, properties: Tool.clone(properties), time: Date.now() };
   }

   /**
    * Returns the saved properties if they are for the given organization and haven't expired,
    * or null otherwise. They are returned once: a match clears them, so a later load shows the
    * server's properties. A load of another organization leaves them for the reload.
    */
   take(orgId: string): PropertyModel[] {
      const saved = this.saved;

      if(saved == null || Date.now() - saved.time > SavedOrgPropertiesService.EXPIRY) {
         this.saved = null;
         return null;
      }

      if(orgId == null || saved.orgId?.toLowerCase() !== orgId.toLowerCase()) {
         return null;
      }

      this.saved = null;
      return saved.properties;
   }

   clear(): void {
      this.saved = null;
   }
}
