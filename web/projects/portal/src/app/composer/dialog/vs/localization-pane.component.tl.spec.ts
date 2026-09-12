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
 * LocalizationPane — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — selectComponent: only leaf nodes are added to selectedComponents;
 *     non-leaf (folder) nodes are silently ignored
 *   Group 2 [Risk 3] — add(): adds leaf node to model.localized using node.data as name when
 *     available; falls back to node.label; deduplicates via find() so the same component is
 *     not added twice
 *   Group 3 [Risk 2] — selectLocalized: plain-click replaces the selection; ctrl-click appends;
 *     shift-click range-selects from last selected; shift-click with empty selection behaves
 *     like plain-click
 *   Group 4 [Risk 2] — isLocalizedSelected: returns true when component is in selectedLocalized;
 *     false otherwise
 *   Group 5 [Risk 2] — remove(): removes all items in selectedLocalized from model.localized
 *     and resets selectedLocalized to empty
 *   Group 6 [Risk 2] — isAddEnabled / isRemoveEnabled: boolean getters reflect selection state
 *     in both directions
 *   Group 7 [Risk 1] — getDisplayName: replaces the first "^_^" with "." via
 *     String.prototype.replace; backend stores names as assemblyName^_^childName with
 *     exactly one separator, so normal data displays correctly
 *   Group 8 [Risk 1] — find(): returns true when a localized item with the same name exists;
 *     returns false when no matching name is found
 *
 * Confirmed bugs (it.fails):
 *   None.
 *
 * Out of scope:
 *   Template rendering — LocalizationPane uses NO_ERRORS_SCHEMA; DOM assertions are
 *     integration-level.
 *   TreeComponent interaction — tree selection event plumbing is integration-level; unit tests
 *     call selectComponent() directly.
 *   ngOnInit / ngOnDestroy — component has neither; no lifecycle logic to test.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { LocalizationPane } from "./localization-pane.component";
import { LocalizationPaneModel } from "../../data/vs/localization-pane-model";
import { LocalizationComponent } from "../../data/vs/localization-component";

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

function makeLocalized(name: string, textId: string = name): LocalizationComponent {
   const c = new LocalizationComponent();
   c.name = name;
   c.textId = textId;
   return c;
}

function makeNode(label: string, leaf: boolean, data?: string): any {
   return { label, leaf, data: data ?? null, children: [] };
}

function createModel(overrides: Partial<LocalizationPaneModel> = {}): LocalizationPaneModel {
   return {
      components: { label: "root", leaf: false, children: [] } as any,
      localized: [],
      ...overrides,
   };
}

async function renderComponent(modelOverrides: Partial<LocalizationPaneModel> = {}) {
   const model = createModel(modelOverrides);
   const { fixture } = await render(LocalizationPane, {
      schemas: [NO_ERRORS_SCHEMA],
      componentInputs: { model },
   });
   const comp = fixture.componentInstance as LocalizationPane;
   return { comp, fixture, model };
}

// ---------------------------------------------------------------------------
// Group 1: selectComponent [Risk 3]
// ---------------------------------------------------------------------------

describe("LocalizationPane — selectComponent", () => {
   // 🔁 Regression-sensitive: only leaf nodes represent actual localizable components; folder
   //    (non-leaf) nodes must not be added or add() will create garbage localization entries.
   it("should add only leaf nodes to selectedComponents", async () => {
      const { comp } = await renderComponent();
      const leaf1 = makeNode("leaf1", true);
      const leaf2 = makeNode("leaf2", true);
      const folder = makeNode("folder", false);

      comp.selectComponent([folder, leaf1, leaf2]);

      expect(comp.selectedComponents).toHaveLength(2);
      expect(comp.selectedComponents.map(n => n.label)).toEqual(["leaf1", "leaf2"]);
   });

   it("should set selectedComponents to empty when all nodes are non-leaf", async () => {
      const { comp } = await renderComponent();
      const folder1 = makeNode("folder1", false);
      const folder2 = makeNode("folder2", false);

      comp.selectComponent([folder1, folder2]);

      expect(comp.selectedComponents).toHaveLength(0);
   });

   it("should replace previous selection on each call", async () => {
      const { comp } = await renderComponent();
      comp.selectComponent([makeNode("first", true)]);
      comp.selectComponent([makeNode("second", true)]);

      expect(comp.selectedComponents).toHaveLength(1);
      expect(comp.selectedComponents[0].label).toBe("second");
   });

   it("should set selectedComponents to empty when event array is empty", async () => {
      const { comp } = await renderComponent();
      comp.selectComponent([makeNode("leaf", true)]);
      comp.selectComponent([]);

      expect(comp.selectedComponents).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 2: add() [Risk 3]
// ---------------------------------------------------------------------------

describe("LocalizationPane — add", () => {
   // 🔁 Regression-sensitive: add() must use node.data as the name when it is available so that
   //    the internal identifier (data) is stored rather than the display label; if the wrong
   //    field is used deduplication breaks and re-export produces wrong output.
   it("should add a leaf node to model.localized using node.data as name", async () => {
      const { comp, model } = await renderComponent();
      comp.selectComponent([makeNode("MyLabel", true, "my.data.key")]);
      comp.add();

      expect(model.localized).toHaveLength(1);
      expect(model.localized[0].name).toBe("my.data.key");
      expect(model.localized[0].textId).toBe("MyLabel");
   });

   it("should use node.label as name when node.data is null", async () => {
      const { comp, model } = await renderComponent();
      comp.selectComponent([makeNode("MyLabel", true, null)]);
      comp.add();

      expect(model.localized[0].name).toBe("MyLabel");
      expect(model.localized[0].textId).toBe("MyLabel");
   });

   it("should not add a duplicate when the same name already exists in localized", async () => {
      const existing = makeLocalized("my.data.key");
      const { comp, model } = await renderComponent({ localized: [existing] });
      comp.selectComponent([makeNode("MyLabel", true, "my.data.key")]);
      comp.add();

      expect(model.localized).toHaveLength(1);
   });

   it("should add multiple selected leaf nodes", async () => {
      const { comp, model } = await renderComponent();
      comp.selectComponent([
         makeNode("Label1", true, "key1"),
         makeNode("Label2", true, "key2"),
      ]);
      comp.add();

      expect(model.localized).toHaveLength(2);
      expect(model.localized.map(l => l.name)).toEqual(["key1", "key2"]);
   });

   it("should not add anything when selectedComponents is empty", async () => {
      const { comp, model } = await renderComponent();
      comp.selectComponent([]);
      comp.add();

      expect(model.localized).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 3: selectLocalized [Risk 2]
// ---------------------------------------------------------------------------

describe("LocalizationPane — selectLocalized", () => {
   it("should replace selection on plain click", async () => {
      const c1 = makeLocalized("comp1");
      const c2 = makeLocalized("comp2");
      const { comp } = await renderComponent({ localized: [c1, c2] });

      comp.selectLocalized({ ctrlKey: false, shiftKey: false } as MouseEvent, c1);
      expect(comp.selectedLocalized).toEqual([c1]);

      comp.selectLocalized({ ctrlKey: false, shiftKey: false } as MouseEvent, c2);
      expect(comp.selectedLocalized).toEqual([c2]);
      expect(comp.selectedLocalized).not.toContain(c1);
   });

   it("should append to selection on ctrl+click", async () => {
      const c1 = makeLocalized("comp1");
      const c2 = makeLocalized("comp2");
      const { comp } = await renderComponent({ localized: [c1, c2] });

      comp.selectLocalized({ ctrlKey: false, shiftKey: false } as MouseEvent, c1);
      comp.selectLocalized({ ctrlKey: true, shiftKey: false } as MouseEvent, c2);

      expect(comp.selectedLocalized).toContain(c1);
      expect(comp.selectedLocalized).toContain(c2);
   });

   it("should range-select on shift+click from last selected to current", async () => {
      const c1 = makeLocalized("comp1");
      const c2 = makeLocalized("comp2");
      const c3 = makeLocalized("comp3");
      const { comp } = await renderComponent({ localized: [c1, c2, c3] });

      comp.selectLocalized({ ctrlKey: false, shiftKey: false } as MouseEvent, c1);
      comp.selectLocalized({ ctrlKey: false, shiftKey: true } as MouseEvent, c3);

      expect(comp.selectedLocalized).toContain(c1);
      expect(comp.selectedLocalized).toContain(c2);
      expect(comp.selectedLocalized).toContain(c3);
   });

   it("should behave like plain click on shift+click when selectedLocalized is empty", async () => {
      const c1 = makeLocalized("comp1");
      const c2 = makeLocalized("comp2");
      const { comp } = await renderComponent({ localized: [c1, c2] });

      comp.selectLocalized({ ctrlKey: false, shiftKey: true } as MouseEvent, c2);

      expect(comp.selectedLocalized).toEqual([c2]);
   });

   it("should range-select in reverse order (shift+click on earlier item)", async () => {
      const c1 = makeLocalized("comp1");
      const c2 = makeLocalized("comp2");
      const c3 = makeLocalized("comp3");
      const { comp } = await renderComponent({ localized: [c1, c2, c3] });

      comp.selectLocalized({ ctrlKey: false, shiftKey: false } as MouseEvent, c3);
      comp.selectLocalized({ ctrlKey: false, shiftKey: true } as MouseEvent, c1);

      expect(comp.selectedLocalized).toContain(c1);
      expect(comp.selectedLocalized).toContain(c2);
      expect(comp.selectedLocalized).toContain(c3);
   });
});

// ---------------------------------------------------------------------------
// Group 4: isLocalizedSelected [Risk 2]
// ---------------------------------------------------------------------------

describe("LocalizationPane — isLocalizedSelected", () => {
   it("should return true when the component is in selectedLocalized", async () => {
      const c1 = makeLocalized("comp1");
      const { comp } = await renderComponent({ localized: [c1] });
      comp.selectLocalized({ ctrlKey: false, shiftKey: false } as MouseEvent, c1);

      expect(comp.isLocalizedSelected(c1)).toBe(true);
   });

   it("should return false when the component is not in selectedLocalized", async () => {
      const c1 = makeLocalized("comp1");
      const c2 = makeLocalized("comp2");
      const { comp } = await renderComponent({ localized: [c1, c2] });
      comp.selectLocalized({ ctrlKey: false, shiftKey: false } as MouseEvent, c1);

      expect(comp.isLocalizedSelected(c2)).toBe(false);
   });

   it("should return false when selectedLocalized is empty", async () => {
      const c1 = makeLocalized("comp1");
      const { comp } = await renderComponent({ localized: [c1] });

      expect(comp.isLocalizedSelected(c1)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 5: remove() [Risk 2]
// ---------------------------------------------------------------------------

describe("LocalizationPane — remove", () => {
   it("should remove the selected localized component from model.localized", async () => {
      const c1 = makeLocalized("comp1");
      const c2 = makeLocalized("comp2");
      const { comp, model } = await renderComponent({ localized: [c1, c2] });
      comp.selectLocalized({ ctrlKey: false, shiftKey: false } as MouseEvent, c1);
      comp.remove();

      expect(model.localized).toHaveLength(1);
      expect(model.localized[0]).toBe(c2);
   });

   it("should clear selectedLocalized after remove", async () => {
      const c1 = makeLocalized("comp1");
      const { comp } = await renderComponent({ localized: [c1] });
      comp.selectLocalized({ ctrlKey: false, shiftKey: false } as MouseEvent, c1);
      comp.remove();

      expect(comp.selectedLocalized).toHaveLength(0);
   });

   it("should remove multiple selected localized components", async () => {
      const c1 = makeLocalized("comp1");
      const c2 = makeLocalized("comp2");
      const c3 = makeLocalized("comp3");
      const { comp, model } = await renderComponent({ localized: [c1, c2, c3] });
      comp.selectLocalized({ ctrlKey: false, shiftKey: false } as MouseEvent, c1);
      comp.selectLocalized({ ctrlKey: true, shiftKey: false } as MouseEvent, c2);
      comp.remove();

      expect(model.localized).toHaveLength(1);
      expect(model.localized[0]).toBe(c3);
   });

   it("should not mutate model.localized when selectedLocalized is empty", async () => {
      const c1 = makeLocalized("comp1");
      const { comp, model } = await renderComponent({ localized: [c1] });
      comp.remove(); // nothing selected

      expect(model.localized).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 6: isAddEnabled / isRemoveEnabled [Risk 2]
// ---------------------------------------------------------------------------

describe("LocalizationPane — isAddEnabled / isRemoveEnabled", () => {
   it("should return true from isAddEnabled when selectedComponents has entries", async () => {
      const { comp } = await renderComponent();
      comp.selectComponent([makeNode("leaf", true)]);
      expect(comp.isAddEnabled()).toBe(true);
   });

   it("should return false from isAddEnabled when selectedComponents is empty", async () => {
      const { comp } = await renderComponent();
      comp.selectComponent([]);
      expect(comp.isAddEnabled()).toBe(false);
   });

   it("should return false from isAddEnabled initially (no selection)", async () => {
      const { comp } = await renderComponent();
      expect(comp.isAddEnabled()).toBe(false);
   });

   it("should return true from isRemoveEnabled when selectedLocalized has entries", async () => {
      const c1 = makeLocalized("comp1");
      const { comp } = await renderComponent({ localized: [c1] });
      comp.selectLocalized({ ctrlKey: false, shiftKey: false } as MouseEvent, c1);
      expect(comp.isRemoveEnabled()).toBe(true);
   });

   it("should return false from isRemoveEnabled when selectedLocalized is empty", async () => {
      const { comp } = await renderComponent();
      expect(comp.isRemoveEnabled()).toBe(false);
   });

   it("should return false from isRemoveEnabled after remove clears selectedLocalized", async () => {
      const c1 = makeLocalized("comp1");
      const { comp } = await renderComponent({ localized: [c1] });
      comp.selectLocalized({ ctrlKey: false, shiftKey: false } as MouseEvent, c1);
      comp.remove();
      expect(comp.isRemoveEnabled()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 7: getDisplayName [Risk 1]
// ---------------------------------------------------------------------------

describe("LocalizationPane — getDisplayName", () => {
   it("should replace the first '^_^' with '.' in the display name", async () => {
      const { comp } = await renderComponent();
      // Backend stores assemblyName^_^childName with one separator; replace() is sufficient.
      expect(comp.getDisplayName("Chart1^_^Title")).toBe("Chart1.Title");
   });

   it("should return the string unchanged when '^_^' is not present", async () => {
      const { comp } = await renderComponent();
      expect(comp.getDisplayName("com.example.MyClass")).toBe("com.example.MyClass");
   });

   it("should return an empty string unchanged", async () => {
      const { comp } = await renderComponent();
      expect(comp.getDisplayName("")).toBe("");
   });

   it("should replace only the first occurrence of '^_^' (String.replace replaces first match)", async () => {
      const { comp } = await renderComponent();
      expect(comp.getDisplayName("a^_^b^_^c")).toBe("a.b^_^c");
   });
});

// ---------------------------------------------------------------------------
// Group 8: find() [Risk 1]
// ---------------------------------------------------------------------------

describe("LocalizationPane — find", () => {
   it("should return true when a localized item with the same name exists", async () => {
      const c1 = makeLocalized("com.example.MyClass");
      const { comp } = await renderComponent({ localized: [c1] });

      expect(comp.find({ name: "com.example.MyClass", textId: "MyClass" })).toBe(true);
   });

   it("should return false when no localized item matches the name", async () => {
      const c1 = makeLocalized("com.example.MyClass");
      const { comp } = await renderComponent({ localized: [c1] });

      expect(comp.find({ name: "com.example.OtherClass", textId: "OtherClass" })).toBe(false);
   });

   it("should return false when model.localized is empty", async () => {
      const { comp } = await renderComponent({ localized: [] });
      expect(comp.find({ name: "anything", textId: "anything" })).toBe(false);
   });

   it("should match by name only, not textId", async () => {
      const c1 = makeLocalized("myName", "differentTextId");
      const { comp } = await renderComponent({ localized: [c1] });

      expect(comp.find({ name: "myName", textId: "someOtherTextId" })).toBe(true);
   });
});
