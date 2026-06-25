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
 * TreeComponent — Pass 3: Display
 *
 * Coverage plan:
 *   Group 1 [Risk 1] — showHelpLink getter: true when helpURL is non-empty
 *   Group 2 [Risk 1] — treeContainerMaxHeight getter: various maxHeight / searchEnabled combinations
 */

import { screen } from "@testing-library/angular";
import { vi } from "vitest";
import { renderTree } from "./tree-node.component.test-helpers";

afterEach(() => {
   vi.restoreAllMocks();
});

// ===========================================================================
// Group 0 — Bug #17336: no-results message (DOM)
// ===========================================================================

describe("Group 0 — Bug #17336: no-results message", () => {
   it("should render searchFailed message when empty is true and searchStr is non-empty", async () => {
      // With NO_ERRORS_SCHEMA, rootNode ViewChild is null → ngAfterViewChecked sets empty=true
      await renderTree({ componentProperties: { searchStr: "aaa" } });

      // Template: @if (empty && searchStr && searchStr.trim() && (!root || !root.loading))
      //   <div>_#(common.searchFailed)</div>
      // In test env the _#() token is preserved as-is or stripped to just the key
      expect(screen.getByText(/common\.searchFailed/)).toBeInTheDocument();
   });
});

// ===========================================================================
// Group 1 — showHelpLink
// ===========================================================================

describe("Group 1 — showHelpLink getter", () => {
   it("should return true when helpURL is set", async () => {
      const { comp } = await renderTree({ componentProperties: { helpURL: "https://example.com/help" } });
      expect(comp.showHelpLink).toBe(true);
   });

   it("should return false when helpURL is empty", async () => {
      const { comp } = await renderTree({ componentProperties: { helpURL: "" } });
      expect(comp.showHelpLink).toBe(false);
   });
});

// ===========================================================================
// Group 2 — treeContainerMaxHeight
// ===========================================================================

describe("Group 2 — treeContainerMaxHeight getter", () => {
   it("should return null when searchEnabled is false and fillHeight is false and maxHeight <= 0", async () => {
      const { comp } = await renderTree({
         componentProperties: { fillHeight: false, maxHeight: -1 },
      });
      // Bypass searchEnabled setter (which re-initialises search state) — this test targets treeContainerMaxHeight only.
      comp["_searchEnabled"] = false;
      expect(comp.treeContainerMaxHeight).toBeNull();
   });

   it("should return 100% when fillHeight is true and searchEnabled is false", async () => {
      const { comp } = await renderTree({ componentProperties: { fillHeight: true } });
      // Bypass searchEnabled setter (which re-initialises search state) — this test targets treeContainerMaxHeight only.
      comp["_searchEnabled"] = false;
      expect(comp.treeContainerMaxHeight).toBe("100%");
   });

   it("should return maxHeight in px when maxHeight > 0, fillHeight is true, and searchEnabled is false", async () => {
      const { comp } = await renderTree({ componentProperties: { maxHeight: 400, fillHeight: true } });
      // Bypass searchEnabled setter (which re-initialises search state) — this test targets treeContainerMaxHeight only.
      comp["_searchEnabled"] = false;
      expect(comp.treeContainerMaxHeight).toBe("400px");
   });

   it("should return calc expression when searchEnabled is true and maxHeight <= 0", async () => {
      const { comp } = await renderTree({ componentProperties: { maxHeight: -1 } });
      // Bypass searchEnabled setter (which re-initialises search state) — this test targets treeContainerMaxHeight only.
      comp["_searchEnabled"] = true;
      expect(comp.treeContainerMaxHeight).toBe("calc(100% + 30px)");
   });

   it("should include maxHeight in calc expression when searchEnabled and maxHeight > 0", async () => {
      const { comp } = await renderTree({ componentProperties: { maxHeight: 300 } });
      // Bypass searchEnabled setter (which re-initialises search state) — this test targets treeContainerMaxHeight only.
      comp["_searchEnabled"] = true;
      expect(comp.treeContainerMaxHeight).toBe("calc(300px + 30px)");
   });
});
