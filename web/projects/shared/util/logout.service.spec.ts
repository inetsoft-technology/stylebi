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
import { beforeEach, describe, expect, it } from "vitest";
import { LogoutService } from "./logout.service";

// Bug #81146: a forced logout caused by the session limit tells the login page why, with a fixed
// reason code; every other forced logout keeps the generic (inactivity) message.
describe("LogoutService.sessionExpired", () => {
   let service: LogoutService;

   beforeEach(() => {
      window.sessionStorage.clear();
      service = new LogoutService(document as any);
   });

   it("marks the session as expired without a reason by default", () => {
      service.sessionExpired();
      expect(window.sessionStorage.getItem("inetsoftSessionExpired")).toBe("true");
      expect(window.sessionStorage.getItem("inetsoftSessionEndReason")).toBeNull();
   });

   it("records the limit reason", () => {
      service.sessionExpired("limit");
      expect(window.sessionStorage.getItem("inetsoftSessionExpired")).toBe("true");
      expect(window.sessionStorage.getItem("inetsoftSessionEndReason")).toBe("limit");
   });

   it("drops a stale reason when a later expiry has none", () => {
      service.sessionExpired("limit");
      service.sessionExpired();
      expect(window.sessionStorage.getItem("inetsoftSessionEndReason")).toBeNull();
   });

   it("ignores any reason that is not a known code", () => {
      service.sessionExpired("<b>x</b>" as any);
      expect(window.sessionStorage.getItem("inetsoftSessionEndReason")).toBeNull();
   });
});
