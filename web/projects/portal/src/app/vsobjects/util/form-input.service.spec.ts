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
import { FormInputService } from "./form-input.service";

describe("FormInputService", () => {
   let service: FormInputService;

   beforeEach(() => {
      service = new FormInputService();
   });

   it("should start with empty pending values", () => {
      expect(service.getPendingValues()).toEqual([]);
   });

   it("should add a pending value", () => {
      service.addPendingValue("Table1", "someValue");
      expect(service.getPendingValues()).toEqual([
         { assemblyName: "Table1", value: "someValue" }
      ]);
   });

   it("should replace existing entry for same assembly name", () => {
      service.addPendingValue("Table1", "first");
      service.addPendingValue("Table1", "second");
      const pending = service.getPendingValues();
      expect(pending.length).toBe(1);
      expect(pending[0].value).toBe("second");
   });

   it("should keep separate entries for different assemblies", () => {
      service.addPendingValue("Table1", "value1");
      service.addPendingValue("Table2", "value2");
      expect(service.getPendingValues().length).toBe(2);
   });

   it("should clear all pending values", () => {
      service.addPendingValue("Table1", "value1");
      service.addPendingValue("Table2", "value2");
      service.clear();
      expect(service.getPendingValues()).toEqual([]);
   });
});
