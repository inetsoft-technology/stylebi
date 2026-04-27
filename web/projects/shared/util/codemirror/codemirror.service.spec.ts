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
import { CodemirrorService } from "./codemirror.service";

describe("CodemirrorService", () => {
   let service: CodemirrorService;

   beforeEach(() => {
      service = new CodemirrorService();
   });

   // The base CodemirrorService is a no-op stub; subclasses override these methods
   // to provide real CodeMirror integration. Tests verify the base contract.

   it("createTernServer returns null from base implementation", () => {
      expect(service.createTernServer({})).toBeNull();
   });

   it("getEcmaScriptDefs returns null from base implementation", () => {
      expect(service.getEcmaScriptDefs()).toBeNull();
   });

   it("createCodeMirrorInstance returns null from base implementation", () => {
      const el = document.createElement("textarea") as HTMLTextAreaElement;
      expect(service.createCodeMirrorInstance(el, {})).toBeNull();
   });

   it("hasToken returns false from base implementation", () => {
      const mockEditor = {} as any;
      expect(service.hasToken(mockEditor, "keyword", "if")).toBe(false);
      expect(service.hasToken(mockEditor, "comment", "//")).toBe(false);
      expect(service.hasToken(mockEditor, null, "any")).toBe(false);
   });
});
