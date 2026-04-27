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
import { TreeNodeModel } from "./tree-node-model";
import { TreeSearchPipe } from "./tree-search.pipe";

describe("TreeSearchPipe", () => {
   let pipe: TreeSearchPipe;

   const leaf = (label: string): TreeNodeModel => ({
      label,
      leaf: true,
      children: []
   } as unknown as TreeNodeModel);

   const folder = (label: string, children: TreeNodeModel[]): TreeNodeModel => ({
      label,
      leaf: false,
      children
   } as unknown as TreeNodeModel);

   const unloaded = (label: string): TreeNodeModel => ({
      label,
      leaf: false,
      children: null
   } as unknown as TreeNodeModel);

   beforeEach(() => {
      pipe = new TreeSearchPipe();
   });

   it("should return all nodes when input is empty", () => {
      const nodes = [leaf("Alpha"), leaf("Beta")];
      expect(pipe.transform(nodes, "")).toEqual(nodes);
      expect(pipe.transform(nodes, null)).toEqual(nodes);
      expect(pipe.transform(nodes, "   ")).toEqual(nodes);
   });

   it("should filter nodes whose label matches the search string (case-insensitive)", () => {
      const nodes = [leaf("Alpha"), leaf("Beta"), leaf("Gamma")];
      const result = pipe.transform(nodes, "alp");
      expect(result.length).toBe(1);
      expect(result[0].label).toBe("Alpha");
   });

   it("should include a parent folder when a child matches", () => {
      const child = leaf("MatchMe");
      const parent = folder("Parent", [child]);
      const nodes = [parent, leaf("NoMatch")];
      const result = pipe.transform(nodes, "matchme");
      expect(result.length).toBe(1);
      expect(result[0].label).toBe("Parent");
   });

   it("should include an unloaded folder (no children array) unconditionally", () => {
      const nodes = [unloaded("NotYetLoaded"), leaf("Other")];
      const result = pipe.transform(nodes, "xyz");
      expect(result.some(n => n.label === "NotYetLoaded")).toBe(true);
   });

   it("should exclude a folder whose leaf children do not match", () => {
      const parent = folder("Parent", [leaf("NoMatch")]);
      const nodes = [parent];
      const result = pipe.transform(nodes, "xyz");
      expect(result.length).toBe(0);
   });

   it("should match only leaf nodes when onlySearchLeaf is true", () => {
      const parent = folder("Alpha", [leaf("Beta")]);
      const nodes = [parent];

      // Without onlySearchLeaf: parent label "Alpha" matches "alp"
      expect(pipe.transform(nodes, "alp", false).length).toBe(1);

      // With onlySearchLeaf: parent label is not searched; child "Beta" doesn't contain "alp"
      expect(pipe.transform(nodes, "alp", true).length).toBe(0);

      // With onlySearchLeaf: child "Beta" matches "bet"
      expect(pipe.transform(nodes, "bet", true).length).toBe(1);
   });

   it("should stop descending into nodes where searchEndNode returns true", () => {
      const child = leaf("MatchMe");
      const parent = folder("Parent", [child]);
      const nodes = [parent];
      // searchEndNode stops at parent — its children are not searched, and
      // "matchme" does not appear in parent's own label
      const result = pipe.transform(nodes, "matchme", false, n => n.label === "Parent");
      expect(result.length).toBe(0);
   });

   it("nodeMatch should return false for a node with a null label", () => {
      const node = { label: null } as TreeNodeModel;
      expect(TreeSearchPipe.nodeMatch(node, "any")).toBe(false);
   });
});
