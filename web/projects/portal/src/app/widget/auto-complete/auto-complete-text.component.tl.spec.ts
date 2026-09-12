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
 * AutoCompleteText — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — showAutoComplete / matchSomeParameter: parameter prefix filtering
 *   Group 2 [Risk 3] — editorKeyDown: arrow/enter navigation when list is open
 *   Group 3 [Risk 2] — hideAutoList / changeText emit contract
 *
 * Direct instantiation — dropdown template not rendered; DOM-heavy paths stubbed.
 *
 * Out of scope: initCursorPosition / getTextSize (require live Selection/Range APIs).
 */

import { Renderer2 } from "@angular/core";
import { AutoCompleteModel } from "./auto-complete-model";
import { AutoCompleteText } from "./auto-complete-text.component";
import { FixedDropdownService } from "../fixed-dropdown/fixed-dropdown.service";
import { DropdownRef } from "../fixed-dropdown/fixed-dropdown-ref";

function createAutoComplete(parameters = ["region", "revenue", "paramX"]) {
   const dropdownClose = vi.fn();
   const dropdownService = {
      open: vi.fn(() => ({
         close: dropdownClose,
         viewRef: { rootNodes: [] },
      })),
   };
   const comp = new AutoCompleteText(
      {} as Renderer2,
      dropdownService as unknown as FixedDropdownService,
   );
   comp.model = { parameters, text: "" } as AutoCompleteModel;
   comp.textArea = { nativeElement: document.createElement("div") };
   return { comp, dropdownService, dropdownClose };
}

function keyEvent(key: string): KeyboardEvent {
   return {
      key,
      stopPropagation: vi.fn(),
      preventDefault: vi.fn(),
   } as unknown as KeyboardEvent;
}

describe("AutoCompleteText — showAutoComplete filtering [Group 1, Risk 3]", () => {

   it("should list all parameters when cursor ends with parameter.", () => {
      const { comp } = createAutoComplete();
      comp.cursorString = "viewsheet parameter.";

      expect(comp.showAutoComplete()).toBe(true);
      expect(comp.filterParameters).toEqual(["region", "revenue", "paramX"]);
   });

   it("should return false when cursor string has no parameter prefix", () => {
      const { comp } = createAutoComplete();
      comp.cursorString = "plain text";

      expect(comp.showAutoComplete()).toBe(false);
   });

   it("should filter parameters by typed prefix after parameter.", () => {
      const { comp } = createAutoComplete();
      comp.cursorString = "x parameter.re";

      expect(comp.matchSomeParameter("re")).toBe(true);
      expect(comp.filterParameters).toEqual(["region", "revenue"]);
   });
});

describe("AutoCompleteText — editorKeyDown navigation [Group 2, Risk 3]", () => {

   it("should decrement selIndex on ArrowUp when autocomplete is open", () => {
      const { comp } = createAutoComplete();
      comp.autoCompleteShow = true;
      comp.filterParameters = ["a", "b", "c"];
      comp.selIndex = 2;
      comp.scrollToSelectedItem = vi.fn();

      comp.editorKeyDown(keyEvent("ArrowUp"));

      expect(comp.selIndex).toBe(1);
      expect(comp.scrollToSelectedItem).toHaveBeenCalled();
   });

   it("should apply selected parameter and hide list on Enter", () => {
      const { comp } = createAutoComplete();
      comp.autoCompleteShow = true;
      comp.filterParameters = ["region"];
      comp.selIndex = 0;
      comp.cursorPos = { x: 10, y: 0 };
      const textNode = document.createTextNode("parameter.");
      comp.textArea.nativeElement.appendChild(textNode);
      comp.focusNode = textNode;
      comp.focusOffset = 10;
      const hideSpy = vi.spyOn(comp, "hideAutoList");

      comp.editorKeyDown(keyEvent("Enter"));

      expect(textNode.textContent).toContain("region");
      expect(hideSpy).toHaveBeenCalled();
      expect(comp.autoCompleteValid).toBe(false);
   });

   it("should mark autocomplete valid when period is typed", () => {
      const { comp } = createAutoComplete();

      comp.editorKeyDown(keyEvent("."));

      expect(comp.autoCompleteValid).toBe(true);
   });
});

describe("AutoCompleteText — hide and commit [Group 3, Risk 2]", () => {

   it("should close dropdown ref and clear autoCompleteShow on hideAutoList", () => {
      const { comp, dropdownClose } = createAutoComplete();
      comp["autoCompleteRef"] = { close: dropdownClose } as unknown as DropdownRef;
      comp.autoCompleteShow = true;

      comp.hideAutoList();

      expect(dropdownClose).toHaveBeenCalled();
      expect(comp["autoCompleteRef"]).toBeNull();
      expect(comp.autoCompleteShow).toBe(false);
   });

   it("should emit commitText with editor text on blur changeText", () => {
      const { comp } = createAutoComplete();
      comp.model.text = "old";
      const emitSpy = vi.spyOn(comp.commitText, "emit");
      const target = document.createElement("div");
      target.innerText = "new text";

      comp.changeText({ target, stopPropagation: vi.fn() } as unknown as FocusEvent);

      expect(comp.model.text).toBe("new text");
      expect(emitSpy).toHaveBeenCalledWith("new text");
      expect(comp.focusIn).toBe(false);
   });
});
