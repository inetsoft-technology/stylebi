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

import { TestBed } from "@angular/core/testing";
import { HeartbeatWorkerService } from "./heartbeat-worker.service";

describe("HeartbeatWorkerService", () => {
   let service: HeartbeatWorkerService;
   let messageHandlers: ((e: MessageEvent) => void)[];
   let postedMessages: any[];

   afterEach(() => {
      vi.useRealTimers();
   });

   beforeEach(() => {
      postedMessages = [];
      messageHandlers = [];
      TestBed.configureTestingModule({});
      service = TestBed.inject(HeartbeatWorkerService);
      // Inject MockWorker directly — bypasses URL resolution which fails in Vitest/Node
      const mockWorker = new (class MockWorker {
         postMessage(data: any): void { postedMessages.push(data); }
         addEventListener(_type: string, handler: (e: MessageEvent) => void): void { messageHandlers.push(handler); }
         removeEventListener(_type: string, handler: (e: MessageEvent) => void): void { messageHandlers = messageHandlers.filter(h => h !== handler); }
         terminate(): void {}
      })();
      service["worker"] = mockWorker as any;
   });

   it("should post start message when subscribed", () => {
      const sub = service.createHeartbeat("test", 1000).subscribe();
      expect(postedMessages).toContainEqual({ type: "start", id: "test", intervalMs: 1000 });
      sub.unsubscribe();
   });

   it("should post stop message when unsubscribed", () => {
      const sub = service.createHeartbeat("test", 1000).subscribe();
      sub.unsubscribe();
      expect(postedMessages).toContainEqual({ type: "stop", id: "test" });
   });

   it("should emit when worker posts a matching tick", () => {
      const ticks: void[] = [];
      const sub = service.createHeartbeat("test", 1000).subscribe(() => ticks.push(undefined as void));
      messageHandlers[0](new MessageEvent("message", { data: { type: "tick", id: "test" } }));
      expect(ticks.length).toBe(1);
      sub.unsubscribe();
   });

   it("should not emit for a tick with a different id", () => {
      const ticks: void[] = [];
      const sub = service.createHeartbeat("test", 1000).subscribe(() => ticks.push(undefined as void));
      messageHandlers[0](new MessageEvent("message", { data: { type: "tick", id: "other" } }));
      expect(ticks.length).toBe(0);
      sub.unsubscribe();
   });

   it("should fall back to setInterval when Worker is unavailable", () => {
      // Use a fresh service instance with no worker injected so the fallback path is exercised
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({});
      const fallbackService = TestBed.inject(HeartbeatWorkerService);
      vi.useFakeTimers();
      const ticks: void[] = [];
      const sub = fallbackService.createHeartbeat("test", 1000).subscribe(() => ticks.push(undefined as void));
      vi.advanceTimersByTime(1000);
      expect(ticks.length).toBe(1);
      vi.advanceTimersByTime(1000);
      expect(ticks.length).toBe(2);
      sub.unsubscribe();
   });
});
