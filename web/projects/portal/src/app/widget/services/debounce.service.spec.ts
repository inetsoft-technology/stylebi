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
import { fakeAsync, TestBed, tick } from "@angular/core/testing";
import { DebounceService } from "./debounce.service";

describe("DebounceService", () => {
   let service: DebounceService;

   beforeEach(() => {
      TestBed.configureTestingModule({
         providers: [DebounceService]
      });
      service = TestBed.inject(DebounceService);
   });

   it("should call the function after the delay", fakeAsync(() => {
      const fn = jest.fn();
      service.debounce("key1", fn, 200, ["arg1"]);
      tick(200);
      expect(fn).toHaveBeenCalledWith("arg1");
   }));

   it("should not call the function before the delay", fakeAsync(() => {
      const fn = jest.fn();
      service.debounce("key1", fn, 200, []);
      tick(199);
      expect(fn).not.toHaveBeenCalled();
      tick(1);
   }));

   it("should only call the function once when debounced multiple times with the same key", fakeAsync(() => {
      const fn = jest.fn();
      service.debounce("key1", fn, 100, ["first"]);
      tick(50);
      service.debounce("key1", fn, 100, ["second"]);
      tick(100);
      expect(fn).toHaveBeenCalledTimes(1);
      expect(fn).toHaveBeenCalledWith("second");
   }));

   it("should handle separate keys independently", fakeAsync(() => {
      const fn1 = jest.fn();
      const fn2 = jest.fn();
      service.debounce("key1", fn1, 100, []);
      service.debounce("key2", fn2, 100, []);
      tick(100);
      expect(fn1).toHaveBeenCalledTimes(1);
      expect(fn2).toHaveBeenCalledTimes(1);
   }));

   it("should not call the function after cancel", fakeAsync(() => {
      const fn = jest.fn();
      service.debounce("key1", fn, 100, []);
      service.cancel("key1");
      tick(100);
      expect(fn).not.toHaveBeenCalled();
   }));

   it("should do nothing when canceling a non-existent key", () => {
      expect(() => service.cancel("nonexistent")).not.toThrow();
   });
});
