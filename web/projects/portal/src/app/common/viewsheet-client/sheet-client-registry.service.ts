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
import { ViewsheetClientService } from "./viewsheet-client.service";

export type PairingSheetType = "VIEWSHEET" | "WORKSHEET";

export interface RegisteredSheetClient {
   client: ViewsheetClientService;
   sheetType: PairingSheetType;
}

/**
 * Root-level lookup from a runtime id to the `ViewsheetClientService` of the Composer pane that
 * owns that runtime. A formula-editor dialog opened through `NgbModal` only sees the root
 * injector, so it cannot inject the pane's own (component-level) client; panes register it here
 * instead.
 *
 * Entries are the client OBJECTS, matched on the client's current `runtimeId` at lookup time:
 * a pane's runtime id is assigned after the client exists and changes while the pane lives, so
 * an id snapshot taken at registration would go stale.
 */
@Injectable({ providedIn: "root" })
export class SheetClientRegistry {
   private entries: RegisteredSheetClient[] = [];

   /** Registers a pane's own client; returns the function that unregisters it. */
   register(client: ViewsheetClientService, sheetType: PairingSheetType): () => void {
      let entry = this.entries.find(e => e.client === client);

      if(!entry) {
         entry = { client, sheetType };
         this.entries.push(entry);
      }

      const registered = entry;
      return () => {
         const index = this.entries.indexOf(registered);

         if(index >= 0) {
            this.entries.splice(index, 1);
         }
      };
   }

   /**
    * The ONE registered client whose current runtimeId equals `runtimeId`, else null. Two or
    * more matches also give null: hiding the pairing button is safe, minting against the wrong
    * client is not.
    */
   lookup(runtimeId: string | null | undefined): RegisteredSheetClient | null {
      if(!runtimeId) {
         return null;
      }

      const matches = this.entries.filter(e => e.client.runtimeId === runtimeId);

      if(matches.length > 1) {
         console.warn(`SheetClientRegistry: runtime ${runtimeId} is owned by ${matches.length} ` +
                      "registered clients, pairing disabled");
         return null;
      }

      return matches.length == 1 ? matches[0] : null;
   }
}
