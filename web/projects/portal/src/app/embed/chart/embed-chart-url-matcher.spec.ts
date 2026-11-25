/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
import { UrlSegment } from "@angular/router";
import { EMBED_CHART_URL_MATCHER } from "./embed-chart-url-matcher";

function segments(...paths: string[]): UrlSegment[] {
   return paths.map(p => new UrlSegment(p, {}));
}

function param(result: any, name: string): string {
   return result.posParams[name]?.path;
}

describe("EMBED_CHART_URL_MATCHER", () => {
   it("does not match a missing or empty url", () => {
      expect(EMBED_CHART_URL_MATCHER(null)).toBeNull();
      expect(EMBED_CHART_URL_MATCHER(undefined)).toBeNull();
      expect(EMBED_CHART_URL_MATCHER([])).toBeNull();
   });

   it("consumes every segment of the url", () => {
      const url = segments("global", "folder", "dashboard", "Chart1");
      const result = EMBED_CHART_URL_MATCHER(url);

      expect(result.consumed).toBe(url);
   });

   it("takes the last segment as the chart name and the rest as the dashboard path (global)", () => {
      const result = EMBED_CHART_URL_MATCHER(segments("global", "folder", "dashboard", "Chart1"));

      expect(param(result, "assetScope")).toBe("global");
      expect(param(result, "assetPath")).toBe("folder/dashboard");
      expect(param(result, "assetId")).toBe("1^128^__NULL__^folder/dashboard");
      expect(param(result, "assemblyName")).toBe("Chart1");
   });

   it("takes the owner, the dashboard path and the chart name for the user scope", () => {
      const result = EMBED_CHART_URL_MATCHER(segments("user", "alice", "dashboard", "Chart1"));

      expect(param(result, "assetScope")).toBe("user");
      expect(param(result, "assetOwner")).toBe("alice");
      expect(param(result, "assetPath")).toBe("dashboard");
      expect(param(result, "assetId")).toBe("4^128^alice^dashboard");
      expect(param(result, "assemblyName")).toBe("Chart1");
   });

   it("parses a global asset id followed by the chart name", () => {
      const result = EMBED_CHART_URL_MATCHER(segments("1^128^__NULL__^Examples", "Dashboard", "Chart1"));

      expect(param(result, "assetScope")).toBe("global");
      expect(param(result, "assetOwner")).toBeUndefined();
      expect(param(result, "assetPath")).toBe("Examples/Dashboard");
      expect(param(result, "assetId")).toBe("1^128^__NULL__^Examples/Dashboard");
      expect(param(result, "assemblyName")).toBe("Chart1");
   });

   it("parses a user asset id with its owner followed by the chart name", () => {
      const result = EMBED_CHART_URL_MATCHER(segments("4^128^bob^Folder", "Report", "Chart2"));

      expect(param(result, "assetScope")).toBe("user");
      expect(param(result, "assetOwner")).toBe("bob");
      expect(param(result, "assetPath")).toBe("Folder/Report");
      expect(param(result, "assetId")).toBe("4^128^bob^Folder/Report");
      expect(param(result, "assemblyName")).toBe("Chart2");
   });
});
