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
import { TestUtils } from "../../common/test/test-utils";
import { EmbedAssemblyContextProviderFactory } from "../../vsobjects/context-provider.service";
import { AddVsObjectMode } from "../../vsobjects/command/add-vs-object-command";
import { VSTextModel } from "../../vsobjects/model/output/vs-text-model";
import { EmbedTextComponent } from "./embed-text.component";

/**
 * Regression coverage for the stylebi-side half of bug-75975: <inetsoft-text> (like every other
 * embed element) never dispatched any DOM event a consumer could use to learn when it actually
 * finished rendering - a plain custom-element consumer had nothing but a fixed client-side
 * timeout to fall back on. A text assembly has no async loading of its own (its content is
 * plain DOM - innerHTML - rendered synchronously from the model by Angular change detection), so
 * "rendered" here is just "the model this command carried has been applied and change detection
 * has run". Built off the prototype rather than through TestBed: EmbedTextComponent is an
 * Angular Elements custom element wired to a websocket client, and standing all of that up would
 * test the DI setup instead of the dispatch - see embed-table.component.spec.ts for the same
 * pattern.
 */
describe("EmbedTextComponent.dispatchLoaded", () => {
   let component: any;
   let host: HTMLElement;
   let text: VSTextModel;

   beforeEach(() => {
      text = TestUtils.createMockVSTextModel("Text1");
      host = document.createElement("div");
      component = Object.create(EmbedTextComponent.prototype);
      component.assemblyName = "Text1";
      component.contextProvider = EmbedAssemblyContextProviderFactory();
      component.miniToolbarService = { hiddenFreeze: vi.fn(), hiddenUnfreeze: vi.fn() };
      component.cdRef = { detectChanges: vi.fn() };
      component.elementRef = { nativeElement: host };
      component.updateVSInfo = () => {};
   });

   it("dispatches a bubbling, composed 'loaded' CustomEvent on the host element", () => {
      const listener = vi.fn();
      host.addEventListener("loaded", listener);

      component.dispatchLoaded();

      expect(listener).toHaveBeenCalledTimes(1);
      const event: CustomEvent = listener.mock.calls[0][0];
      expect(event.bubbles).toBe(true);
      expect(event.composed).toBe(true);
   });

   it("dispatches 'loaded' after applying a fresh AddVSObjectCommand", () => {
      const listener = vi.fn();
      host.addEventListener("loaded", listener);

      component.processAddVSObjectCommand({
         name: "Text1", mode: AddVsObjectMode.RUNTIME_MODE, model: text, parent: null
      });

      expect(component.cdRef.detectChanges).toHaveBeenCalled();
      expect(listener).toHaveBeenCalledTimes(1);
   });

   it("dispatches 'loaded' after applying a RefreshVSObjectCommand", () => {
      const listener = vi.fn();
      host.addEventListener("loaded", listener);
      component.vsObject = text;

      component.processRefreshVSObjectCommand({ info: text });

      expect(component.cdRef.detectChanges).toHaveBeenCalled();
      expect(listener).toHaveBeenCalledTimes(1);
   });

   it("does not dispatch 'loaded' for a command addressed to a different assembly", () => {
      const listener = vi.fn();
      host.addEventListener("loaded", listener);

      component.processAddVSObjectCommand({
         name: "SomeOtherText", mode: AddVsObjectMode.RUNTIME_MODE, model: text, parent: null
      });

      expect(listener).not.toHaveBeenCalled();
   });
});
