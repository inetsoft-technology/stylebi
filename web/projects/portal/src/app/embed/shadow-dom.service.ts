/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Injectable, Injector } from "@angular/core";
import { ɵDomSharedStylesHost } from "@angular/platform-browser";

@Injectable({
   providedIn: "root"
})
export class ShadowDomService {
   private hosts: Array<Node> = [];
   private removedDocumentHead: boolean;

   // https://github.com/angular/angular/issues/35039
   // A workaround to get the element working when inside a shadow dom. Prevents <style> elements
   // from being added to document <head> and makes sure that they get added to the shadow root.
   public addShadowRootHost(injector: Injector, element: Element) {
      if(element && this.isInShadowDom(element)) {
         this.removeDocumentHeadHost(injector);

         // to prevent the same shadow root from being added multiple times
         // which would just duplicate all the <style> elements
         if(!this.hosts.includes(element.getRootNode())) {
            injector.get(ɵDomSharedStylesHost).addHost(element.getRootNode());
            this.hosts.push(element.getRootNode());
         }
      }
   }

   private removeDocumentHeadHost(injector: Injector) {
      if(this.removedDocumentHead) {
         return;
      }

      // check if there are any inetsoft-chart elements in the dom (outside of shadow doms)
      let elems = document.querySelectorAll("inetsoft-chart");

      if(!this.removedDocumentHead && (!elems || elems.length == 0)) {
         injector.get(ɵDomSharedStylesHost).removeHost(document.head);
         this.removedDocumentHead = true;
      }
   }

   private isInShadowDom(element: Element): boolean {
      const rootNode = element.getRootNode();
      return rootNode instanceof ShadowRoot;
   }
}