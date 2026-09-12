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
import { KeepAwakeService } from "./keep-awake.service";

describe("KeepAwakeService", () => {
   let service: KeepAwakeService;
   let requestSpy: ReturnType<typeof vi.fn>;

   beforeEach(() => {
      TestBed.configureTestingModule({ providers: [KeepAwakeService] });
      service = TestBed.inject(KeepAwakeService);
      // jsdom leaves navigator.locks undefined; install a mock for the
      // positive paths. request() returns a never-settling promise so the
      // service's .catch() never fires.
      requestSpy = vi.fn().mockReturnValue(new Promise(() => {}));
      (navigator as any).locks = { request: requestSpy };
   });

   afterEach(() => {
      delete (navigator as any).locks;
   });

   it("should request a uniquely-named lock when keepAwake is called", () => {
      service.keepAwake("rt-1");
      expect(requestSpy).toHaveBeenCalledWith(
         "viewsheet-keep-awake-rt-1",
         expect.objectContaining({ signal: expect.anything() }),
         expect.any(Function));
   });

   it("should hold the lock until release aborts it", () => {
      service.keepAwake("rt-1");
      const signal = requestSpy.mock.calls[0][1].signal;
      expect(signal.aborted).toBe(false);
      service.release();
      expect(signal.aborted).toBe(true);
   });

   it("should release a prior lock when keepAwake is called again", () => {
      service.keepAwake("rt-1");
      const firstSignal = requestSpy.mock.calls[0][1].signal;
      service.keepAwake("rt-2");
      expect(firstSignal.aborted).toBe(true);
      expect(requestSpy).toHaveBeenLastCalledWith(
         "viewsheet-keep-awake-rt-2", expect.anything(), expect.any(Function));
   });

   it("should release the lock on destroy", () => {
      service.keepAwake("rt-1");
      const signal = requestSpy.mock.calls[0][1].signal;
      service.ngOnDestroy();
      expect(signal.aborted).toBe(true);
   });

   it("should be a no-op when the Web Locks API is unavailable", () => {
      delete (navigator as any).locks;
      expect(() => service.keepAwake("rt-1")).not.toThrow();
   });
});
