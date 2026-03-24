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
import { ElementRef, NgZone, Renderer2 } from "@angular/core";
import { EnterSubmitDirective } from "./enter-submit.directive";

const makeEvent = (keyCode: number, target?: EventTarget): KeyboardEvent => {
   const event = new KeyboardEvent("keydown");
   Object.defineProperty(event, "keyCode", { value: keyCode, configurable: true });
   Object.defineProperty(event, "which", { value: keyCode, configurable: true });
   if(target) {
      Object.defineProperty(event, "target", { value: target, configurable: true });
   }
   return event;
};

const makeDirective = (): EnterSubmitDirective => {
   const el = document.createElement("div");
   const ref = new ElementRef(el);
   const renderer: Renderer2 = {
      listen: (elem: any, event: string, cb: Function) => {
         elem.addEventListener(event, cb);
         return () => elem.removeEventListener(event, cb);
      }
   } as any;
   const zone = new NgZone({ enableLongStackTrace: false });
   return new EnterSubmitDirective(ref, renderer, zone);
};

describe("EnterSubmitDirective", () => {
   it("should emit onEnter when Enter is pressed and enterSubmitEnable is true", () => {
      const dir = makeDirective();
      dir.enterSubmitEnable = true;
      const entered: any[] = [];
      dir.onEnter.subscribe(() => entered.push(true));
      dir.ngAfterContentInit();
      dir.onKeyDown(makeEvent(13));
      expect(entered.length).toBe(1);
   });

   it("should emit onEsc when Escape (keyCode 27) is pressed", () => {
      const dir = makeDirective();
      const escaped: any[] = [];
      dir.onEsc.subscribe(() => escaped.push(true));
      dir.ngAfterContentInit();
      dir.onKeyDown(makeEvent(27));
      expect(escaped.length).toBe(1);
   });

   it("should not emit onEnter when Enter is pressed on a HTMLButtonElement", () => {
      const button = document.createElement("button");
      const dir = makeDirective();
      dir.enterSubmitEnable = true;
      const entered: any[] = [];
      dir.onEnter.subscribe(() => entered.push(true));
      dir.ngAfterContentInit();
      dir.onKeyDown(makeEvent(13, button));
      expect(entered.length).toBe(0);
   });

   it("should not emit onEnter when enterSubmitEnable is false and enterSubmit is not set", () => {
      const dir = makeDirective();
      dir.enterSubmitEnable = false;
      const entered: any[] = [];
      dir.onEnter.subscribe(() => entered.push(true));
      dir.ngAfterContentInit();
      dir.onKeyDown(makeEvent(13));
      expect(entered.length).toBe(0);
   });

   it("should emit onEnter when enterSubmit is boolean true", () => {
      const dir = makeDirective();
      dir.enterSubmit = true;
      const entered: any[] = [];
      dir.onEnter.subscribe(() => entered.push(true));
      dir.ngAfterContentInit();
      dir.onKeyDown(makeEvent(13));
      expect(entered.length).toBe(1);
   });

   it("should emit onEnter when enterSubmit is a function returning true", () => {
      const dir = makeDirective();
      dir.enterSubmit = () => true;
      const entered: any[] = [];
      dir.onEnter.subscribe(() => entered.push(true));
      dir.ngAfterContentInit();
      dir.onKeyDown(makeEvent(13));
      expect(entered.length).toBe(1);
   });

   it("should not emit onEnter when enterSubmit is a function returning false", () => {
      const dir = makeDirective();
      dir.enterSubmit = () => false;
      const entered: any[] = [];
      dir.onEnter.subscribe(() => entered.push(true));
      dir.ngAfterContentInit();
      dir.onKeyDown(makeEvent(13));
      expect(entered.length).toBe(0);
   });

   it("should not emit onEnter when Enter is pressed on an HTMLAnchorElement", () => {
      const anchor = document.createElement("a");
      const dir = makeDirective();
      dir.enterSubmitEnable = true;
      const entered: any[] = [];
      dir.onEnter.subscribe(() => entered.push(true));
      dir.ngAfterContentInit();
      dir.onKeyDown(makeEvent(13, anchor));
      expect(entered.length).toBe(0);
   });

   it("should not emit onEnter when Enter is pressed in a textarea without Ctrl/Meta", () => {
      const textarea = document.createElement("textarea");
      const dir = makeDirective();
      dir.enterSubmitEnable = true;
      const entered: any[] = [];
      dir.onEnter.subscribe(() => entered.push(true));
      dir.ngAfterContentInit();
      const event = makeEvent(13, textarea);
      dir.onKeyDown(event);
      expect(entered.length).toBe(0);
   });

   it("should emit onEnter when Enter+Ctrl is pressed in a textarea", () => {
      const textarea = document.createElement("textarea");
      const dir = makeDirective();
      dir.enterSubmitEnable = true;
      const entered: any[] = [];
      dir.onEnter.subscribe(() => entered.push(true));
      dir.ngAfterContentInit();
      const event = new KeyboardEvent("keydown", { ctrlKey: true });
      Object.defineProperty(event, "keyCode", { value: 13, configurable: true });
      Object.defineProperty(event, "target", { value: textarea, configurable: true });
      dir.onKeyDown(event);
      expect(entered.length).toBe(1);
   });

   it("should emit onEnter when Enter+Meta is pressed in a textarea", () => {
      const textarea = document.createElement("textarea");
      const dir = makeDirective();
      dir.enterSubmitEnable = true;
      const entered: any[] = [];
      dir.onEnter.subscribe(() => entered.push(true));
      dir.ngAfterContentInit();
      const event = new KeyboardEvent("keydown", { metaKey: true });
      Object.defineProperty(event, "keyCode", { value: 13, configurable: true });
      Object.defineProperty(event, "target", { value: textarea, configurable: true });
      dir.onKeyDown(event);
      expect(entered.length).toBe(1);
   });
});
