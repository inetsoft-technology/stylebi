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

/**
 * Performance timing baselines for VirtualScrollTreeDatasource.filterValues().
 *
 * VirtualScrollService was the previous implementation (Array.includes, O(n²)).
 * It has been fully replaced by VirtualScrollTreeDatasource (Set.has, O(n)) and
 * removed from the codebase. These benches establish the O(n) baseline so that
 * any future regression back toward quadratic behaviour is detectable.
 *
 * This file contains ONLY bench() blocks — a manual developer tool.
 * bench() is only available in benchmark mode and cannot run via `vitest run`.
 *
 * Run:
 *   npx vitest bench projects/portal/src/app/widget/tree/virtual-scroll.bench.ts
 *
 * Correctness tests and the O(n) ratio assertion live in:
 *   virtual-scroll-datasource.perf.spec.ts  (runs via `npm run test:perf`)
 *
 * Baseline recorded 2026-06-23 on this machine (Windows 11, Node 24):
 *
 *   Flat tree (100% match)
 *     N=100    hz=18,284  mean=0.055ms  p99=0.120ms  rme=0.84%
 *     N=500    hz= 2,864  mean=0.349ms  p99=0.749ms  rme=1.25%
 *     N=2000   hz=   384  mean=2.600ms  p99=3.883ms  rme=1.86%
 *     N=5000   hz=    70  mean=14.14ms  p99=19.83ms  rme=3.33%
 *
 *   Mixed tree (~50% match)
 *     500 nodes   hz=1,646  mean=0.607ms  p99=1.012ms  rme=1.06%
 *     2000 nodes  hz=  373  mean=2.674ms  p99=3.676ms  rme=1.30%
 *     5000 nodes  hz=  138  mean=7.208ms  p99=8.055ms  rme=1.23%
 *
 *   Deep tree (parent traversal)
 *     depth=100  hz=8,009  mean=0.125ms  p99=0.195ms  rme=0.53%
 *     depth=200  hz=2,369  mean=0.422ms  p99=0.751ms  rme=0.93%
 *
 * If your numbers are dramatically worse, the algorithm may have regressed.
 * The automated O(n) guard in virtual-scroll-datasource.perf.spec.ts will also catch this.
 */
import { bench, describe } from "vitest";
import { VirtualScrollTreeDatasource } from "./virtual-scroll-tree-datasource";
import { TreeNodeModel } from "./tree-node-model";

// ─────────────────────────────────────────────────────────────────────────────
// Tree builders
// ─────────────────────────────────────────────────────────────────────────────

function buildFlatTree(n: number): TreeNodeModel {
   return {
      label: "root",
      leaf: false,
      expanded: true,
      children: Array.from({ length: n }, (_, i) => ({
         label: `item-${i}`,
         leaf: true,
         expanded: false,
         children: null,
      })),
   };
}

function buildMixedTree(folders: number, itemsPerFolder: number): TreeNodeModel {
   return {
      label: "root",
      leaf: false,
      expanded: true,
      children: Array.from({ length: folders }, (_, f) => ({
         label: `folder-${f}`,
         leaf: false,
         expanded: true,
         children: Array.from({ length: itemsPerFolder }, (_, i) => ({
            label: i % 2 === 0 ? `item-${f}-${i}` : `other-${f}-${i}`,
            leaf: true,
            expanded: false,
            children: null,
         })),
      })),
   };
}

function buildDeepTree(depth: number): TreeNodeModel {
   let current: TreeNodeModel = { label: "item-leaf", leaf: true, expanded: false, children: null };
   for(let i = depth - 1; i >= 0; i--) {
      current = { label: `item-${i}`, leaf: false, expanded: true, children: [current] };
   }
   return current;
}

// ─────────────────────────────────────────────────────────────────────────────
// Benchmarks
// ─────────────────────────────────────────────────────────────────────────────

describe("Flat tree — 100% match rate (worst case)", () => {
   describe("N = 100", () => {
      const root = buildFlatTree(100);
      const ds = new VirtualScrollTreeDatasource();
      bench("filterValues", () => { ds.filterValues(root, "item"); });
   });

   describe("N = 500", () => {
      const root = buildFlatTree(500);
      const ds = new VirtualScrollTreeDatasource();
      bench("filterValues", () => { ds.filterValues(root, "item"); });
   });

   describe("N = 2000", () => {
      const root = buildFlatTree(2000);
      const ds = new VirtualScrollTreeDatasource();
      bench("filterValues", () => { ds.filterValues(root, "item"); });
   });

   describe("N = 5000", () => {
      const root = buildFlatTree(5000);
      const ds = new VirtualScrollTreeDatasource();
      bench("filterValues", () => { ds.filterValues(root, "item"); });
   });
});

describe("Mixed tree (~50% match) — realistic repository tree", () => {
   describe("500 total nodes", () => {
      const root = buildMixedTree(10, 50);
      const ds = new VirtualScrollTreeDatasource();
      bench("filterValues", () => { ds.filterValues(root, "item"); });
   });

   describe("2000 total nodes", () => {
      const root = buildMixedTree(20, 100);
      const ds = new VirtualScrollTreeDatasource();
      bench("filterValues", () => { ds.filterValues(root, "item"); });
   });

   describe("5000 total nodes", () => {
      const root = buildMixedTree(25, 200);
      const ds = new VirtualScrollTreeDatasource();
      bench("filterValues", () => { ds.filterValues(root, "item"); });
   });
});

describe("Deep tree — parent traversal per match node", () => {
   describe("depth = 100", () => {
      const root = buildDeepTree(100);
      const ds = new VirtualScrollTreeDatasource();
      bench("filterValues", () => { ds.filterValues(root, "item"); });
   });

   describe("depth = 200", () => {
      const root = buildDeepTree(200);
      const ds = new VirtualScrollTreeDatasource();
      bench("filterValues", () => { ds.filterValues(root, "item"); });
   });
});
