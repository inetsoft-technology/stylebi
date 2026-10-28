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

/**
 * Bug #79437: VSText text-measuring code (isHTMLContent / getEllipsisText and helpers) must never
 * parse user text live into a DOM element (stored XSS via <img onerror>). Uses the real Angular
 * DomSanitizer. jsdom does not fire onerror, so these specs assert on what reaches the innerHTML
 * setter instead; real-Chromium behavior was confirmed separately.
 */
import { TestBed } from "@angular/core/testing";
import { DomSanitizer } from "@angular/platform-browser";
import { TestUtils } from "../../../../common/test/test-utils";
import { VSText } from "./vs-text.component";

const FONT = "12px Arial";

const PAYLOADS: { [name: string]: string } = {
   img: "Acme<img src=x onerror=\"window.__xss=1\">",
   imgWords: "Acme corp has a very long title <img src=x onerror=\"window.__xss=1\"> and more words",
   script: "Acme<script>window.__xss=1</script> tail text that is long enough to wrap",
   svg: "Acme<svg onload=\"window.__xss=1\"></svg> tail",
   href: "<a href=\"javascript:window.__xss=1\">click</a> some long text here to be truncated",
   style: "<span style=\"background:url(javascript:window.__xss=1)\" onclick=\"x()\">styled</span> text",
   noSpace: "abcdefghij<img src=x onerror=\"window.__xss=1\">klmnopqrstuvwxyz",
};

function createText(autoSize: boolean, domSanitizer: DomSanitizer): VSText {
   const comp = new VSText(
      { sendEvent: vi.fn(), runtimeId: "vs1", commands: { subscribe: vi.fn() } } as any,
      {} as any, {} as any, {} as any, {} as any,
      { viewer: true, preview: false, binding: false, composer: false } as any,
      { detectChanges: vi.fn(), markForCheck: vi.fn() } as any,
      {} as any, domSanitizer, {} as any,
      { run: (fn: () => void) => fn(), runOutsideAngular: (fn: () => void) => fn() } as any,
      {} as any, {} as any, document);
   const model = TestUtils.createMockVSTextModel("Text1");
   const format = TestUtils.createMockVSFormatModel();
   comp.model = Object.assign(model, {
      autoSize,
      url: false,
      breakAll: false,
      text: "",
      objectFormat: {
         ...format,
         width: 100,
         height: 40,
         lineHeight: 20,
         font: FONT,
         wrapping: { whiteSpace: "normal", wordWrap: "break-word" },
      },
   } as any);
   return comp;
}

describe("VSText text measuring (Bug #79437)", () => {
   let domSanitizer: DomSanitizer;
   let written: string[];
   let unsafeElement: string[];
   const innerHtmlDesc = Object.getOwnPropertyDescriptor(Element.prototype, "innerHTML");
   const clientHeightDesc = Object.getOwnPropertyDescriptor(Element.prototype, "clientHeight");

   beforeEach(() => {
      TestBed.configureTestingModule({});
      domSanitizer = TestBed.inject(DomSanitizer);
      written = [];
      unsafeElement = [];

      // record every value assigned to any element's innerHTML, and whether the resulting
      // element tree carries an event-handler attribute, a javascript: value or a script element
      // (escaped text such as "onclick=&quot;x()&quot;" is inert and fine)
      Object.defineProperty(Element.prototype, "innerHTML", {
         configurable: true,
         get: innerHtmlDesc.get,
         set(this: Element, value: string) {
            written.push(String(value));
            innerHtmlDesc.set.call(this, value);

            this.querySelectorAll("*").forEach(e => {
               if(e.nodeName == "SCRIPT" || Array.from(e.attributes).some(a => /^on/i.test(a.name)
                  || /^\s*javascript:/i.test(a.value)))
               {
                  unsafeElement.push(e.outerHTML);
               }
            });
         }
      });

      // fake layout: 10 chars per line, 20px per line, <br> starts a new line
      Object.defineProperty(Element.prototype, "clientHeight", {
         configurable: true,
         get(this: HTMLElement) {
            if(this.style?.width === "99999999px") {
               return 20;
            }

            const parts = this.innerHTML.split(/<br\s*\/?>/i);
            let lines = 0;

            for(const p of parts) {
               const probe = document.createElement("span");
               innerHtmlDesc.set.call(probe, p);
               lines += Math.max(1, Math.ceil(probe.textContent.length / 10));
            }

            return lines * 20;
         }
      });
   });

   afterEach(() => {
      Object.defineProperty(Element.prototype, "innerHTML", innerHtmlDesc);
      Object.defineProperty(Element.prototype, "clientHeight", clientHeightDesc);
      document.body.replaceChildren();
   });

   describe("XSS payloads never reach a live parse", () => {
      for(const autoSize of [false, true]) {
         for(const name of Object.keys(PAYLOADS)) {
            it(`textChanged(): ${name}, autoSize=${autoSize}`, () => {
               const comp = createText(autoSize, domSanitizer);
               comp.model.text = PAYLOADS[name];
               comp.textChanged();

               expect(unsafeElement).toEqual([]);
            });
         }
      }

      for(const name of Object.keys(PAYLOADS)) {
         it(`isHTMLContent(): ${name} does not write innerHTML`, () => {
            const comp = createText(false, domSanitizer);
            comp.isHTMLContent(PAYLOADS[name]);

            expect(written).toEqual([]);
            expect(unsafeElement).toEqual([]);
         });

         it(`getEllipsisText() branches: ${name}`, () => {
            const comp = createText(false, domSanitizer);
            // every branch: word path (spaces), no-word path, with the attached measuring div
            comp.getEllipsisText(PAYLOADS[name], FONT);
            comp.getEllipsisText(PAYLOADS[name].replace(/ /g, ""), FONT);
            comp.getEllipsisText(PAYLOADS[name] + "<br>" + PAYLOADS[name], FONT);

            expect(written.length).toBeGreaterThan(0);
            expect(unsafeElement).toEqual([]);
            expect(document.body.children.length).toBe(0);
         });
      }
   });

   describe("existing behavior preserved", () => {
      const cases: [string, string, boolean, string][] = [
         // [name, input, isHTMLContent, getEllipsisText]
         ["plain short", "hello", false, "hello"],
         ["plain words", "alpha beta gamma delta epsilon zeta eta theta", false, "alpha b..."],
         ["plain no space", "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz", false, "abcdefg..."],
         ["br lines", "line one<br>line two<br>line three<br>line four", false, "line on..."],
         ["nbsp trailing", "abcdefghijklmnopqrstuvwxyzabcdefghijkl&nbsp;&nbsp;", false, "abcdefg..."],
         ["nbsp inside", "alpha&nbsp;beta gamma delta epsilon zeta eta theta iota", false, "alpha&n..."],
         ["rich tags", "<b>bold</b> and <i>italic</i> long long long long text", true, "<b>bold</b> an..."],
         ["html wrapper", "<html><b>bold</b> text</html>", true, "<html><b>bold</b> text</html>"],
         ["literal lt", "a < b and c > d and e & f repeated many many times here", false, "a < b a..."],
      ];

      for(const [name, input, html, ellipsis] of cases) {
         it(`${name}`, () => {
            const comp = createText(false, domSanitizer);
            expect(comp.isHTMLContent(input)).toBe(html);
            const result = comp.getEllipsisText(input, FONT);
            expect(result).toBe(ellipsis);
         });
      }
   });
});
