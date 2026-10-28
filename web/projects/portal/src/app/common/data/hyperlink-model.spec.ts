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
import { HyperlinkModel, HyperlinkViewModel, LinkType, ParameterValueModel } from "./hyperlink-model";

describe("HyperlinkViewModel.fromHyperlinkModel embedded placeholders", () => {
   function urlOf(link: string, parameterValues: ParameterValueModel[] = []): string {
      const model = <HyperlinkModel> {
         name: "link",
         link,
         linkType: LinkType.WEB_LINK,
         parameterValues,
         disablePrompting: false
      };

      return HyperlinkViewModel.fromHyperlinkModel(model, "", []).url;
   }

   // These inputs used to spin forever in a synchronous loop. A Vitest timeout cannot
   // interrupt that, so a regression shows up as a hung run rather than a failed test.
   it("should leave an unclosed placeholder literal", () => {
      expect(urlOf("http://h/a$(b")).toBe("http://h/a$(b");
      expect(urlOf("http://h/a/$(b")).toBe("http://h/a/$(b");
      expect(urlOf("http://h/a$(")).toBe("http://h/a$(");
   });

   it("should leave an unclosed placeholder literal when a ) precedes it", () => {
      expect(urlOf("http://h/a)b$(c")).toBe("http://h/a)b$(c");
   });

   it("should strip balanced placeholders and keep a trailing unclosed one", () => {
      expect(urlOf("http://h/a$(b)$(c")).toBe("http://h/a$(c");
   });

   it("should keep a trailing unclosed placeholder after parameter substitution", () => {
      expect(urlOf("http://h/u?a=$(p)$(q", [{ name: "p", type: "", value: "v" }])).toBe("http://h/u?a=v$(q");
   });

   it("should strip closed placeholders as before", () => {
      expect(urlOf("http://h/a$(b)")).toBe("http://h/a");
      expect(urlOf("http://h/a/$(b)/x")).toBe("http://h/a/x");
      expect(urlOf("http://h/a$(b)$(c)/d")).toBe("http://h/a/d");
      expect(urlOf("http://h/p?x=$(p)&y=$(q)")).toBe("http://h/p?x=&y=");
   });
});
