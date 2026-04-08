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
import { GlobalSubmitService } from "./global-submit.service";

describe("GlobalSubmitService", () => {
   let service: GlobalSubmitService;

   beforeEach(() => {
      service = new GlobalSubmitService();
   });

   it("should start with hasUnapplyData false", () => {
      expect(service.hasUnapplyData).toBe(false);
   });

   it("should set hasUnapplyData to false when updateState is called with empty array", () => {
      service.updateState("assembly1", []);
      expect(service.hasUnapplyData).toBe(false);
   });

   it("should set hasUnapplyData to true when updateState is called with non-empty array", () => {
      service.updateState("assembly1", ["selection"]);
      expect(service.hasUnapplyData).toBe(true);
   });

   it("should set hasUnapplyData to false when last pending assembly is cleared", () => {
      service.updateState("assembly1", ["selection"]);
      service.updateState("assembly1", []);
      expect(service.hasUnapplyData).toBe(false);
   });

   it("should keep hasUnapplyData true if any assembly still has pending state", () => {
      service.updateState("assembly1", ["selection"]);
      service.updateState("assembly2", ["selection"]);
      service.updateState("assembly1", []);
      expect(service.hasUnapplyData).toBe(true);
   });

   it("should not update hasUnapplyData when setPending is false", () => {
      service.updateState("assembly1", ["selection"], false);
      expect(service.hasUnapplyData).toBe(false);
   });

   it("should emit event source on submitGlobal", () => {
      const received: string[] = [];
      service.globalSubmit().subscribe(v => received.push(v));
      service.submitGlobal("mySource");
      expect(received).toEqual(["mySource"]);
   });

   it("should emit changes on emitUpdateSelections", () => {
      const received: Map<string, any>[] = [];
      service.updateSelections().subscribe(v => received.push(v));
      const changes = new Map<string, any>();
      changes.set("sel1", []);
      service.emitUpdateSelections(changes);
      expect(received.length).toBe(1);
      expect(received[0]).toBe(changes);
   });
});
