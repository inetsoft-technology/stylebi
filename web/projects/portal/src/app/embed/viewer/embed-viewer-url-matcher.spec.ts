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
import { EMBED_VIEWER_URL_MATCHER } from "./embed-viewer-url-matcher";

function segments(...paths: string[]): UrlSegment[] {
   return paths.map(p => new UrlSegment(p, {}));
}

function param(result: any, name: string): string {
   return result.posParams[name]?.path;
}

describe("EMBED_VIEWER_URL_MATCHER", () => {
   it("does not match a missing or empty url", () => {
      expect(EMBED_VIEWER_URL_MATCHER(null)).toBeNull();
      expect(EMBED_VIEWER_URL_MATCHER(undefined)).toBeNull();
      expect(EMBED_VIEWER_URL_MATCHER([])).toBeNull();
   });

   it("consumes every segment of the url", () => {
      const url = segments("global", "folder", "dashboard");
      const result = EMBED_VIEWER_URL_MATCHER(url);

      expect(result.consumed).toBe(url);
   });

   describe("global scope", () => {
      it("builds the asset id from the whole path after the scope", () => {
         const result = EMBED_VIEWER_URL_MATCHER(segments("global", "folder", "dashboard"));

         expect(param(result, "assetScope")).toBe("global");
         expect(param(result, "assetPath")).toBe("folder/dashboard");
         expect(param(result, "assetId")).toBe("1^128^__NULL__^folder/dashboard");
         expect(param(result, "assetOwner")).toBeUndefined();
      });

      it("treats the last segment as part of the path, not as an assembly name", () => {
         const result = EMBED_VIEWER_URL_MATCHER(segments("global", "Dashboard"));

         expect(param(result, "assetPath")).toBe("Dashboard");
         expect(param(result, "assetId")).toBe("1^128^__NULL__^Dashboard");
         expect(param(result, "assemblyName")).toBeUndefined();
      });

      it("sets only the scope when there is no path", () => {
         const result = EMBED_VIEWER_URL_MATCHER(segments("global"));

         expect(param(result, "assetScope")).toBe("global");
         expect(param(result, "assetPath")).toBeUndefined();
         expect(param(result, "assetId")).toBeUndefined();
      });
   });

   describe("user scope", () => {
      it("builds the asset id from the owner and the path", () => {
         const result = EMBED_VIEWER_URL_MATCHER(segments("user", "alice", "folder", "dashboard"));

         expect(param(result, "assetScope")).toBe("user");
         expect(param(result, "assetOwner")).toBe("alice");
         expect(param(result, "assetPath")).toBe("folder/dashboard");
         expect(param(result, "assetId")).toBe("4^128^alice^folder/dashboard");
      });

      it("sets the scope and owner but no id when the path is missing", () => {
         const result = EMBED_VIEWER_URL_MATCHER(segments("user", "alice"));

         expect(param(result, "assetScope")).toBe("user");
         expect(param(result, "assetOwner")).toBe("alice");
         expect(param(result, "assetPath")).toBeUndefined();
         expect(param(result, "assetId")).toBeUndefined();
      });

      it("sets only the scope when there is no owner", () => {
         const result = EMBED_VIEWER_URL_MATCHER(segments("user"));

         expect(param(result, "assetScope")).toBe("user");
         expect(param(result, "assetOwner")).toBeUndefined();
         expect(param(result, "assetId")).toBeUndefined();
      });
   });

   describe("full asset id", () => {
      it("parses a global asset id", () => {
         const result = EMBED_VIEWER_URL_MATCHER(segments("1^128^__NULL__^Examples", "Dashboard"));

         expect(param(result, "assetScope")).toBe("global");
         expect(param(result, "assetOwner")).toBeUndefined();
         expect(param(result, "assetPath")).toBe("Examples/Dashboard");
         expect(param(result, "assetId")).toBe("1^128^__NULL__^Examples/Dashboard");
      });

      it("parses a user asset id with its owner", () => {
         const result = EMBED_VIEWER_URL_MATCHER(segments("4^128^bob^Folder", "Report"));

         expect(param(result, "assetScope")).toBe("user");
         expect(param(result, "assetOwner")).toBe("bob");
         expect(param(result, "assetPath")).toBe("Folder/Report");
         expect(param(result, "assetId")).toBe("4^128^bob^Folder/Report");
      });

      it("keeps a url that is not an asset id as the id without a scope or path", () => {
         const result = EMBED_VIEWER_URL_MATCHER(segments("some", "other", "path"));

         expect(param(result, "assetId")).toBe("some/other/path");
         expect(param(result, "assetScope")).toBeUndefined();
         expect(param(result, "assetPath")).toBeUndefined();
      });
   });
});
