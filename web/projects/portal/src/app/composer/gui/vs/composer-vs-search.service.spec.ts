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
import { ComposerVsSearchService } from "./composer-vs-search.service";

describe("ComposerVsSearchService", () => {
  let service: ComposerVsSearchService;

  const obj = (props: any): any => ({ objectType: "VSChart", absoluteName: "Chart1", ...props });

  beforeEach(() => {
    service = new ComposerVsSearchService();
    service.changeSearchMode();
  });

  it("matches the assembly name", () => {
    service.searchString = "chart";
    expect(service.matchObject(obj({}))).toBe(true);
  });

  it("matches a visible title", () => {
    service.searchString = "pie - 2";
    expect(service.matchObject(obj({ title: "Pie - 2 slices", titleVisible: true }))).toBe(true);
  });

  it("does not match a hidden title", () => {
    service.searchString = "pie - 2";
    expect(service.matchObject(obj({ title: "Pie - 2 slices", titleVisible: false }))).toBe(false);
  });

  it("matches the content of a text object only", () => {
    service.searchString = "hello";
    expect(service.matchObject(obj({ objectType: "VSText", absoluteName: "Text1", text: "Hello world" })))
      .toBe(true);
    expect(service.matchObject(obj({ text: "Hello world" }))).toBe(false);
  });

  it("does not match unrelated objects", () => {
    service.searchString = "zzz";
    expect(service.matchObject(obj({ title: "Pie" }))).toBe(false);
  });

  it("lets title matches through assemblyVisible", () => {
    service.searchString = "pie";
    expect(service.assemblyVisible(obj({ title: "Pie", titleVisible: true }))).toBe(true);
  });

  it("matches everything outside search mode", () => {
    service.changeSearchMode();
    expect(service.matchObject(obj({}))).toBe(true);
  });
});
