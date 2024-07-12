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
import { Injectable, NgZone, OnDestroy } from "@angular/core";
import { Tool } from "../../../../../shared/util/tool";
import { ViewerToolbarButtonDefinition } from "./viewer-toolbar-button-definition";
import { ViewerToolbarButtonCommand } from "./viewer-toolbar-button-command";
import { ViewerToolbarButtonStatus } from "./viewer-toolbar-button-status";
import { ViewerToolbarEvent } from "./viewer-toolbar-event";

declare const window: any;
let identifier = 0;

@Injectable()
export class ViewerToolbarMessageService implements OnDestroy {
   private readonly messageIdentifier = (++identifier).toString(10);
   private readonly messageListener = m => this.receiveMessage(m);
   private inited = false;
   private buttonDefs: ViewerToolbarButtonDefinition[] = [];
   private buttonStatuses: ViewerToolbarButtonStatus[] = [];

   constructor(private zone: NgZone) {
   }

   ngOnDestroy(): void {
      if(this.inited) {
         this.zone.runOutsideAngular(() => {
            window.removeEventListener("message", this.messageListener);
         });
      }
   }

   refreshButtonDefinitions(defs: ViewerToolbarButtonDefinition[]): void {
      this.init();
      this.buttonDefs = defs;
      this.refresh();
   }

   private init(): void {
      if(!this.inited) {
         this.inited = true;

         this.zone.runOutsideAngular(() => {
            window.addEventListener("message", this.messageListener);
         });
      }
   }

   private refresh(): void {
      if(this.refreshStatus()) {
         this.sendMessage();
      }
   }

   /**
    * @return true if a status has changed, false otherwise.
    */
   private refreshStatus(): boolean {
      let newButtonStatuses: ViewerToolbarButtonStatus[] = [];

      for(let def of this.buttonDefs) {
         newButtonStatuses.push({name: def.name, visible: def.visible, disabled: def.disabled});
      }

      let changed = this.buttonStatuses.length !== newButtonStatuses.length;

      for(let i = 0; i < newButtonStatuses.length && !changed; i++) {
         if(!Tool.isEquals(this.buttonStatuses[i], newButtonStatuses[i])) {
            changed = true;
         }
      }

      this.buttonStatuses = newButtonStatuses;
      return changed;
   }

   private sendMessage(): void {
      const message: ViewerToolbarEvent = {
         identifier: this.messageIdentifier,
         buttonStatuses: this.buttonStatuses
      };

      window.parent.postMessage(message, "*");
   }

   /**
    * Receive message from parent iframe and process it. If the identifier doesn't match, it is
    * ignored.
    */
   private receiveMessage(event: MessageEvent): void {
      const message: ViewerToolbarButtonCommand | any = event.data;

      if(typeof message === "object" && message.identifier === this.messageIdentifier) {
         let def = this.buttonDefs.find(d => d.name === message.name);

         if(def == null) {
            console.error("Could not match toolbar button from message", message);
         }
         else {
            this.zone.run(() => def.action());
         }
      }
   }
}
