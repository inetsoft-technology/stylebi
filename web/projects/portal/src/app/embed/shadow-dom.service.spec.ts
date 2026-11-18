/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
import { Injector } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { ɵSharedStylesHost } from "@angular/platform-browser";
import { ShadowDomService } from "./shadow-dom.service";

describe("ShadowDomService", () => {
   let service: ShadowDomService;
   let stylesHost: { addHost: ReturnType<typeof vi.fn>, removeHost: ReturnType<typeof vi.fn> };
   let injector: Injector;
   let created: Element[];

   // an element placed inside the shadow root of a new host element
   function elementInShadowRoot(): { element: Element, root: ShadowRoot } {
      const host = document.createElement("div");
      document.body.appendChild(host);
      created.push(host);
      const root = host.attachShadow({ mode: "open" });
      const element = document.createElement("span");
      root.appendChild(element);
      return { element, root };
   }

   function elementInDocument(tag: string = "div"): Element {
      const element = document.createElement(tag);
      document.body.appendChild(element);
      created.push(element);
      return element;
   }

   beforeEach(() => {
      created = [];
      stylesHost = { addHost: vi.fn(), removeHost: vi.fn() };
      injector = { get: (token: any) => token === ɵSharedStylesHost ? stylesHost : null } as Injector;
      service = TestBed.inject(ShadowDomService);
   });

   afterEach(() => {
      created.forEach(e => e.remove());
   });

   it("does nothing for a missing element", () => {
      service.addShadowRootHost(injector, null);

      expect(stylesHost.addHost).not.toHaveBeenCalled();
      expect(stylesHost.removeHost).not.toHaveBeenCalled();
   });

   it("does nothing for an element that is not inside a shadow dom", () => {
      service.addShadowRootHost(injector, elementInDocument());

      expect(stylesHost.addHost).not.toHaveBeenCalled();
      expect(stylesHost.removeHost).not.toHaveBeenCalled();
   });

   it("registers the shadow root so that styles are added to it", () => {
      const { element, root } = elementInShadowRoot();

      service.addShadowRootHost(injector, element);

      expect(stylesHost.addHost).toHaveBeenCalledTimes(1);
      expect(stylesHost.addHost).toHaveBeenCalledWith(root);
   });

   it("does not register the same shadow root twice", () => {
      const { element, root } = elementInShadowRoot();
      const sibling = document.createElement("span");
      root.appendChild(sibling);

      service.addShadowRootHost(injector, element);
      service.addShadowRootHost(injector, sibling);

      expect(stylesHost.addHost).toHaveBeenCalledTimes(1);
   });

   it("registers each distinct shadow root", () => {
      const first = elementInShadowRoot();
      const second = elementInShadowRoot();

      service.addShadowRootHost(injector, first.element);
      service.addShadowRootHost(injector, second.element);

      expect(stylesHost.addHost).toHaveBeenCalledTimes(2);
      expect(stylesHost.addHost).toHaveBeenNthCalledWith(1, first.root);
      expect(stylesHost.addHost).toHaveBeenNthCalledWith(2, second.root);
   });

   it("stops adding styles to the document head when no element lives outside a shadow dom", () => {
      const { element } = elementInShadowRoot();

      service.addShadowRootHost(injector, element);

      expect(stylesHost.removeHost).toHaveBeenCalledTimes(1);
      expect(stylesHost.removeHost).toHaveBeenCalledWith(document.head);
   });

   it("removes the document head only once", () => {
      service.addShadowRootHost(injector, elementInShadowRoot().element);
      service.addShadowRootHost(injector, elementInShadowRoot().element);

      expect(stylesHost.removeHost).toHaveBeenCalledTimes(1);
   });

   it.each(["inetsoft-chart", "inetsoft-viewer"])(
      "keeps the document head when a <%s> element is outside a shadow dom", (tag) => {
         elementInDocument(tag);

         service.addShadowRootHost(injector, elementInShadowRoot().element);

         expect(stylesHost.removeHost).not.toHaveBeenCalled();
         expect(stylesHost.addHost).toHaveBeenCalledTimes(1);
      });
});
