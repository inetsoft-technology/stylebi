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

/**
 * SecurityTreeDataService — unit tests
 *
 * Risk-first coverage (4 groups, 8 cases):
 *   Group 1 [Risk 3, 2]    — initialize (2 cases)
 *   Group 2 [Risk 3, 3, 2] — filter (3 cases)
 *   Group 3 [Risk 2, 2]    — refreshTreeData (2 cases)
 *   Group 4 [Risk 2]       — ngOnDestroy (1 case)
 *
 * Confirmed bugs (it.failing — remove wrapper once fixed):
 *   - none; the stale-filter bug (initialize not resetting _filter) has been fixed — a regression
 *     guard now lives in the "Regression guard" block below.
 *
 * KEY contracts:
 *   - initialize() always sets filterChange to false and replaces the active subscription
 *   - filter("") is falsy → the combineLatest map returns the original nodes (no filtering)
 *   - filter(nonEmpty) emits only SecurityTreeNode entries matching the string
 *   - ngOnDestroy() unsubscribes the internal data feed; _data stops being updated
 *
 * Design gaps:
 *   - Relative sort order between matched nodes not tested — tightly coupled to SearchComparator
 *   - SecurityTreeNode.filter() recursive child-traversal logic belongs to that class's own tests
 *   - filter(null) not tested — API signature is string; callers are expected to guard null
 */

import { SecurityTreeDataService } from "./security-tree-data.service";
import { SecurityTreeNode } from "./security-tree-node";

// type=1 (GROUP) satisfies the `type >= 0` guard in SecurityTreeNode.filter() so leaf
// matching works; avoids importing IdentityType just for a constant
function makeNode(name: string, children?: SecurityTreeNode[]): SecurityTreeNode {
   return new SecurityTreeNode({ name, orgID: null }, 1, children);
}

describe("SecurityTreeDataService", () => {
   let service: SecurityTreeDataService;

   beforeEach(() => {
      service = new SecurityTreeDataService();
   });

   afterEach(() => {
      service.ngOnDestroy();
   });

   // ---------------------------------------------------------------------------
   // Regression guard — initialize() must reset _filter so stale values don't leak
   // ---------------------------------------------------------------------------
   it("should emit unfiltered roots on re-initialize even when _filter retains a previous value", () => {
      // Regression: initialize() previously forgot to call _filter.next(""), so filter("alice")
      // followed by initialize([bob]) would leave _filter="alice" and emit [] instead of [bob].
      service.initialize([makeNode("alice")]);
      service.filter("alice");
      service.initialize([makeNode("bob")]); // _filter must be reset to ""

      expect(service.data).toHaveLength(1);
      expect(service.data[0].identityID.name).toBe("bob");
   });

   // ---------------------------------------------------------------------------
   // Group 1 [Risk 3, 2] — initialize
   // ---------------------------------------------------------------------------
   describe("initialize", () => {
      it("[Risk 3] should cancel the previous internal subscription so one filter change triggers exactly one _data emission", () => {
         // 🔁 Regression-sensitive: without unsubscribe() each re-initialize stacks a new internal
         //    subscriber; a single filter() call then drives N _data.next() calls, causing N
         //    redundant tree re-renders in the component
         service.initialize([makeNode("alice")]);
         service.initialize([makeNode("bob")]); // old subscription must be cancelled here

         const emitted: SecurityTreeNode[][] = [];
         service.dataChange.subscribe(nodes => emitted.push(nodes)); // BehaviorSubject: 1 sync emit

         service.filter("bob"); // one combineLatest emission → one _data.next()

         // If old subscription were still alive: filter("bob") would cause 2 _data.next() calls:
         //   old subscriber → alice-nodes filtered by "bob" → [] → _data.next([])
         //   new subscriber → bob-nodes filtered by "bob" → [bob] → _data.next([bob])
         //   subscriber would receive 3 total emissions instead of 2
         expect(emitted.length).toBe(2);                           // (a) no double-emission
         expect(emitted[1][0].identityID.name).toBe("bob");        // (b) correct new data
      });

      it("[Risk 2] should emit the provided roots and set filterChange to false", () => {
         const roots = [makeNode("alice"), makeNode("bob")];

         service.initialize(roots);

         expect(service.data).toHaveLength(2);    // (a) all roots present in _data
         expect(service.filterChange).toBe(false); // (b) filterChange starts as false
      });
   });

   // ---------------------------------------------------------------------------
   // Group 2 [Risk 3, 3, 2] — filter
   // ---------------------------------------------------------------------------
   describe("filter", () => {
      it("[Risk 3] should emit only nodes whose identityID.name matches the filter string", () => {
         // 🔁 Regression-sensitive: wrong filter output means users see nodes that don't match
         //    their search term — a silent data-correctness failure in the security permission tree
         service.initialize([makeNode("alice"), makeNode("bob")]);

         service.filter("alice");

         const result = service.data;
         expect(result).toHaveLength(1);                   // (a) exactly one match
         expect(result[0].identityID.name).toBe("alice");  // (b) correct node returned
      });

      it("[Risk 3] should restore all original nodes when filterString is an empty string", () => {
         // empty string is falsy in JS; the combineLatest map uses
         //`if(filterString)` — if this guard changes to strict equality or trims whitespace,
         //clearing the search input would stop restoring the full list
         service.initialize([makeNode("alice"), makeNode("bob")]);
         service.filter("alice"); // narrows visible nodes to 1

         service.filter(""); // clear filter — falsy branch returns closure's original nodes array

         expect(service.data).toHaveLength(2); // both nodes restored
      });

      it("[Risk 2] should set filterChange to true", () => {
         service.initialize([makeNode("alice")]);

         service.filter("x");

         expect(service.filterChange).toBe(true);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 3 [Risk 2, 2] — refreshTreeData
   // ---------------------------------------------------------------------------
   describe("refreshTreeData", () => {
      it("[Risk 2] should reset filterChange to false", () => {
         service.initialize([makeNode("alice")]);
         service.filter("alice"); // set filterChange = true
         expect(service.filterChange).toBe(true); // precondition

         service.refreshTreeData();

         expect(service.filterChange).toBe(false);
      });

      it("[Risk 2] should re-emit the current _data value to dataChange subscribers", () => {
         service.initialize([makeNode("alice")]);
         const emitted: SecurityTreeNode[][] = [];
         service.dataChange.subscribe(nodes => emitted.push(nodes)); // 1 sync emit from BehaviorSubject

         service.refreshTreeData(); // _data.next(current value) → 1 more emit

         expect(emitted).toHaveLength(2);                       // (a) two total emissions
         expect(emitted[1][0].identityID.name).toBe("alice");   // (b) same data re-emitted
      });
   });

   // ---------------------------------------------------------------------------
   // Group 4 [Risk 2] — ngOnDestroy
   // ---------------------------------------------------------------------------
   describe("ngOnDestroy", () => {
      it("[Risk 2] should unsubscribe the internal data feed so filter() no longer updates _data", () => {
         service.initialize([makeNode("alice")]);
         const emitted: SecurityTreeNode[][] = [];
         service.dataChange.subscribe(nodes => emitted.push(nodes));
         const countBeforeDestroy = emitted.length; // 1: initial BehaviorSubject replay

         service.ngOnDestroy();
         service.filter("alice"); // _filter.next() fires, but internal dataSubscription is gone

         expect(emitted.length).toBe(countBeforeDestroy); // no new emission after destroy
      });
   });
});
