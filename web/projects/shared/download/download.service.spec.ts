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
import { DownloadService } from "./download.service";

describe("DownloadService", () => {
   let service: DownloadService;

   beforeEach(() => {
      service = new DownloadService();
   });

   it("should emit the URL when download() is called", () => {
      const received: string[] = [];
      service.url.subscribe(u => received.push(u));

      service.download("https://example.com/file.csv");

      expect(received).toEqual(["https://example.com/file.csv"]);
   });

   it("should emit each URL in order for multiple downloads", () => {
      const received: string[] = [];
      service.url.subscribe(u => received.push(u));

      service.download("/api/export/report1");
      service.download("/api/export/report2");

      expect(received).toEqual(["/api/export/report1", "/api/export/report2"]);
   });

   it("should not emit before download() is called", () => {
      const received: string[] = [];
      service.url.subscribe(u => received.push(u));

      expect(received).toHaveLength(0);
   });

   it("ngOnDestroy completes the url subject", () => {
      let completed = false;
      service.url.subscribe({ complete: () => { completed = true; } });

      service.ngOnDestroy();

      expect(completed).toBe(true);
   });

   it("should not emit after ngOnDestroy", () => {
      const received: string[] = [];
      service.url.subscribe({ next: u => received.push(u), complete: () => {} });

      service.ngOnDestroy();
      // Calling download after destroy should be a no-op (subject is completed)
      expect(() => service.download("/api/file")).not.toThrow();
      expect(received).toHaveLength(0);
   });
});
