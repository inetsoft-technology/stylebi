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
import { of } from "rxjs";
import { AiAssistantService, ContextType } from "./ai-assistant.service";

function makeHttp(chatUrl = "", styleBIUrl = "") {
   return {
      get: jest.fn().mockImplementation((url: string) => {
         if(url.includes("get-chat-app-server-url")) return of(chatUrl);
         if(url.includes("get-stylebi-url")) return of(styleBIUrl);
         return of(null);
      })
   };
}

describe("AiAssistantService", () => {
   let service: AiAssistantService;

   beforeEach(() => {
      service = new AiAssistantService(makeHttp() as any);
   });

   // ── Constructor HTTP calls ──────────────────────────────────────────────────

   it("should populate chatAppServerUrl from HTTP response on construction", () => {
      const s = new AiAssistantService(makeHttp("https://chat.example.com") as any);
      expect(s.chatAppServerUrl).toBe("https://chat.example.com");
   });

   it("should default chatAppServerUrl to empty string when response is falsy", () => {
      const http = { get: jest.fn().mockReturnValue(of(null)) };
      const s = new AiAssistantService(http as any);
      expect(s.chatAppServerUrl).toBe("");
   });

   // ── panelOpen getter/setter and observable ──────────────────────────────────

   it("panelOpen is false by default", () => {
      expect(service.panelOpen).toBe(false);
   });

   it("setting panelOpen=true is reflected by getter and observable", () => {
      const values: boolean[] = [];
      service.panelOpen$.subscribe(v => values.push(v));

      service.panelOpen = true;

      expect(service.panelOpen).toBe(true);
      expect(values).toContain(true);
   });

   it("panelOpen$ replays the current value to new subscribers", () => {
      service.panelOpen = true;
      let latest: boolean;
      service.panelOpen$.subscribe(v => { latest = v; });
      expect(latest).toBe(true);
   });

   // ── lastBindingObject / createNewChat ────────────────────────────────────────

   it("createNewChat is false initially", () => {
      expect(service.createNewChat).toBe(false);
   });

   it("setting lastBindingObject to a new value sets createNewChat=true", () => {
      service.lastBindingObject = "Chart1";
      expect(service.createNewChat).toBe(true);
   });

   it("setting lastBindingObject to the same value leaves createNewChat=false", () => {
      service.lastBindingObject = "Chart1";
      service.resetNewChat();
      service.lastBindingObject = "Chart1";
      expect(service.createNewChat).toBe(false);
   });

   it("resetNewChat() clears the createNewChat flag", () => {
      service.lastBindingObject = "Chart1";
      service.resetNewChat();
      expect(service.createNewChat).toBe(false);
   });

   // ── Context map CRUD ─────────────────────────────────────────────────────────

   it("setContextField and getContextField round-trip a value", () => {
      service.setContextField("key1", "value1");
      expect(service.getContextField("key1")).toBe("value1");
   });

   it("getContextField returns empty string for unknown key", () => {
      expect(service.getContextField("missing")).toBe("");
   });

   it("removeContextField deletes the key", () => {
      service.setContextField("toRemove", "x");
      service.removeContextField("toRemove");
      expect(service.getContextField("toRemove")).toBe("");
   });

   it("getFullContext returns JSON of all context fields", () => {
      service.setContextField("a", "1");
      service.setContextField("b", "2");
      const ctx = JSON.parse(service.getFullContext());
      expect(ctx).toEqual({ a: "1", b: "2" });
   });

   it("resetContextMap clears all context fields", () => {
      service.setContextField("x", "y");
      service.resetContextMap();
      expect(service.getFullContext()).toBe("{}");
   });

   // ── setContextType ────────────────────────────────────────────────────────────

   it.each([
      ["VSChart",      ContextType.CHART],
      ["VSCrosstab",   ContextType.CROSSTAB],
      ["VSCalcTable",  ContextType.FREEHAND],
      ["VSTable",      ContextType.TABLE],
   ])("setContextType(%s) sets contextType to %s", (objectType, expected) => {
      service.setContextType(objectType);
      expect(service.getContextField("contextType")).toBe(expected);
   });

   it("setContextType with unknown type does not set contextType", () => {
      service.setContextType("VSGauge");
      expect(service.getContextField("contextType")).toBe("");
   });

   // ── setBindingContext guards ───────────────────────────────────────────────────

   it("setBindingContext does nothing when objectModel is null", () => {
      expect(() => service.setBindingContext(null)).not.toThrow();
      expect(service.getContextField("bindingContext")).toBe("");
   });

   // ── setDateComparisonContext guards ────────────────────────────────────────────

   it("setDateComparisonContext does nothing for non-chart/crosstab types", () => {
      const model = { objectType: "VSGauge" } as any;
      expect(() => service.setDateComparisonContext(model)).not.toThrow();
      expect(service.getContextField("dateComparisonContext")).toBe("");
   });

   it("setDateComparisonContext does nothing when dateComparisonEnabled is false", () => {
      const model = { objectType: "VSChart", dateComparisonEnabled: false } as any;
      service.setDateComparisonContext(model);
      expect(service.getContextField("dateComparisonContext")).toBe("");
   });

   it("setDateComparisonContext strips <b> tags and sets the context field", () => {
      const model = {
         objectType: "VSChart",
         dateComparisonEnabled: true,
         dateComparisonDescription: "Compare <b>2023</b> vs <b>2022</b>"
      } as any;
      service.setDateComparisonContext(model);
      expect(service.getContextField("dateComparisonContext")).toBe("Compare 2023 vs 2022");
   });

   // ── setScriptContext ──────────────────────────────────────────────────────────

   it("setScriptContext does nothing when scriptEnabled is false", () => {
      const model = { scriptEnabled: false, script: "var x = 1;" } as any;
      service.setScriptContext(model);
      expect(service.getContextField("scriptContext")).toBe("");
   });

   it("setScriptContext sets scriptContext when enabled", () => {
      const model = { scriptEnabled: true, script: "var x = 1;" } as any;
      service.setScriptContext(model);
      expect(service.getContextField("scriptContext")).toBe("var x = 1;");
   });
});
