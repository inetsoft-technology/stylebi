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
import { FollowFocusService } from "./follow-focus.service";

describe("FollowFocusService", () => {
   let service: FollowFocusService;
   let mockStompConnection: { subscribe: ReturnType<typeof vi.fn>; send: ReturnType<typeof vi.fn> };
   let mockSocketConnection: any;

   beforeEach(() => {
      service = new FollowFocusService();
      mockStompConnection = { subscribe: vi.fn(), send: vi.fn() };
      mockSocketConnection = { whenConnected: vi.fn(() => of(mockStompConnection)) };
   });

   describe("isEnabled / setEnabled", () => {
      it("defaults to disabled for a runtime nothing has touched", () => {
         expect(service.isEnabled("rt-1")).toBe(false);
      });

      it("is per-runtime, not global", () => {
         service.setEnabled("rt-1", mockSocketConnection, true);

         expect(service.isEnabled("rt-1")).toBe(true);
         expect(service.isEnabled("rt-2")).toBe(false);
      });

      it("sends the toggle over the follow-focus destination", () => {
         service.setEnabled("rt-1", mockSocketConnection, true);

         expect(mockStompConnection.send).toHaveBeenCalledWith(
            "/events/wiz/pairing/follow-focus", {},
            JSON.stringify({ runtimeId: "rt-1", enabled: true }));
      });

      it("can be turned back off", () => {
         service.setEnabled("rt-1", mockSocketConnection, true);
         service.setEnabled("rt-1", mockSocketConnection, false);

         expect(service.isEnabled("rt-1")).toBe(false);
      });

      it("is a no-op with no runtimeId or no socket connection", () => {
         expect(() => service.setEnabled("", mockSocketConnection, true)).not.toThrow();
         expect(() => service.setEnabled("rt-1", undefined as any, true)).not.toThrow();
         expect(mockStompConnection.send).not.toHaveBeenCalled();
      });
   });

   describe("pushFocus", () => {
      it("does nothing, and returns false, when Follow Focus is not enabled", () => {
         const pushed = service.pushFocus(
            "rt-1", mockSocketConnection, { kind: "assemblyMain", assembly: "Chart1" });

         expect(pushed).toBe(false);
         expect(mockStompConnection.send).not.toHaveBeenCalled();
      });

      it("sends a retarget request and returns true when enabled", () => {
         service.setEnabled("rt-1", mockSocketConnection, true);

         const pushed = service.pushFocus(
            "rt-1", mockSocketConnection, { kind: "assemblyMain", assembly: "Chart1" });

         expect(pushed).toBe(true);
         expect(mockStompConnection.send).toHaveBeenCalledWith(
            "/events/wiz/pairing/retarget", {},
            JSON.stringify({
               runtimeId: "rt-1",
               editorContext: { kind: "assemblyMain", assembly: "Chart1" }
            }));
      });

      it("is a no-op with no editorContext", () => {
         service.setEnabled("rt-1", mockSocketConnection, true);

         const pushed = service.pushFocus("rt-1", mockSocketConnection, undefined as any);

         expect(pushed).toBe(false);
         expect(mockStompConnection.send).not.toHaveBeenCalledWith(
            "/events/wiz/pairing/retarget", expect.anything(), expect.anything());
      });
   });

   describe("popFocus", () => {
      it("sends a pop request naming just the runtimeId", () => {
         service.setEnabled("rt-1", mockSocketConnection, true);
         service.pushFocus("rt-1", mockSocketConnection, { kind: "assemblyMain", assembly: "Chart1" });

         service.popFocus("rt-1", mockSocketConnection, { kind: "assemblyMain", assembly: "Chart1" });

         expect(mockStompConnection.send).toHaveBeenCalledWith(
            "/events/wiz/pairing/pop-focus", {}, JSON.stringify({ runtimeId: "rt-1" }));
      });

      it("still sends the pop request even when the closing pane does not match the tracked top", () => {
         service.setEnabled("rt-1", mockSocketConnection, true);
         service.pushFocus("rt-1", mockSocketConnection, { kind: "assemblyMain", assembly: "Chart1" });

         service.popFocus("rt-1", mockSocketConnection, { kind: "assemblyMain", assembly: "Chart2" });

         expect(mockStompConnection.send).toHaveBeenCalledWith(
            "/events/wiz/pairing/pop-focus", {}, JSON.stringify({ runtimeId: "rt-1" }));
      });

      it("surfaces a mismatch loudly via the errors observable instead of silently guessing", () => {
         const messages: string[] = [];
         service.errors.subscribe(m => messages.push(m));
         const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});

         service.setEnabled("rt-1", mockSocketConnection, true);
         service.pushFocus("rt-1", mockSocketConnection, { kind: "assemblyMain", assembly: "Chart1" });
         service.popFocus("rt-1", mockSocketConnection, { kind: "assemblyMain", assembly: "Chart2" });

         expect(messages.length).toBe(1);
         expect(messages[0]).toContain("Chart1");
         expect(messages[0]).toContain("Chart2");
         expect(errorSpy).toHaveBeenCalled();

         errorSpy.mockRestore();
      });

      it("reports no mismatch when the closing pane matches the tracked top", () => {
         const messages: string[] = [];
         service.errors.subscribe(m => messages.push(m));

         service.setEnabled("rt-1", mockSocketConnection, true);
         service.pushFocus("rt-1", mockSocketConnection, { kind: "assemblyMain", assembly: "Chart1" });
         service.popFocus("rt-1", mockSocketConnection, { kind: "assemblyMain", assembly: "Chart1" });

         expect(messages.length).toBe(0);
      });

      it("unwinds nesting in reverse order without a mismatch", () => {
         const messages: string[] = [];
         service.errors.subscribe(m => messages.push(m));

         service.setEnabled("rt-1", mockSocketConnection, true);
         service.pushFocus("rt-1", mockSocketConnection, { kind: "assemblyMain", assembly: "Chart1" });
         service.pushFocus("rt-1", mockSocketConnection,
                            { kind: "calcField", assembly: "Query1", name: "Margin" });

         // Closing the inner (most recently pushed) frame first is not a mismatch.
         service.popFocus("rt-1", mockSocketConnection,
                           { kind: "calcField", assembly: "Query1", name: "Margin" });
         // Then the outer frame.
         service.popFocus("rt-1", mockSocketConnection, { kind: "assemblyMain", assembly: "Chart1" });

         expect(messages.length).toBe(0);
      });

      it("is a no-op with no runtimeId or no socket connection", () => {
         expect(() => service.popFocus("", mockSocketConnection,
            { kind: "assemblyMain" })).not.toThrow();
         expect(() => service.popFocus("rt-1", undefined as any,
            { kind: "assemblyMain" })).not.toThrow();
         expect(mockStompConnection.send).not.toHaveBeenCalled();
      });
   });

   describe("toggling off mid-session (spec design question 3)", () => {
      it("still pops an already-pushed pane after Follow Focus is disabled", () => {
         service.setEnabled("rt-1", mockSocketConnection, true);
         service.pushFocus("rt-1", mockSocketConnection, { kind: "assemblyMain", assembly: "Chart1" });

         service.setEnabled("rt-1", mockSocketConnection, false);
         service.popFocus("rt-1", mockSocketConnection, { kind: "assemblyMain", assembly: "Chart1" });

         expect(mockStompConnection.send).toHaveBeenCalledWith(
            "/events/wiz/pairing/pop-focus", {}, JSON.stringify({ runtimeId: "rt-1" }));
      });

      it("does not auto-push the next pane once disabled", () => {
         service.setEnabled("rt-1", mockSocketConnection, true);
         service.setEnabled("rt-1", mockSocketConnection, false);

         const pushed = service.pushFocus(
            "rt-1", mockSocketConnection, { kind: "assemblyMain", assembly: "Chart2" });

         expect(pushed).toBe(false);
      });
   });
});
