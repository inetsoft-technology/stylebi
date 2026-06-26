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
 * FixedDropdownComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — dropdownBounds: position, opacity, container clamping
 *   Group 2 [Risk 3] — outside mousedown / click close contract
 *   Group 3 [Risk 3] — ngOnDestroy clears timeout and document listeners
 *
 * Direct instantiation — dropdown content not rendered.
 */

import { ElementRef, Renderer2 } from "@angular/core";
import { Rectangle } from "../../common/data/rectangle";
import { DropdownStackService } from "./dropdown-stack.service";
import { FixedDropdownComponent } from "./fixed-dropdown.component";

function createDropdown() {
   const cleanup = vi.fn();
   const renderer = { listen: vi.fn(() => cleanup) };
   const dropdownService = { isCurrent: vi.fn(() => true) };
   const element = document.createElement("div");
   const comp = new FixedDropdownComponent(
      { nativeElement: element } as ElementRef,
      renderer as unknown as Renderer2,
      document,
      dropdownService as unknown as DropdownStackService,
   );
   return { comp, renderer, cleanup, dropdownService, element };
}

describe("FixedDropdownComponent — dropdownBounds [Group 1, Risk 3]", () => {

   it("should set position and reveal dropdown after bounds are applied", () => {
      const { comp } = createDropdown();

      comp.dropdownBounds = new Rectangle(40, 60, 120, 80);

      expect(comp.leftPosition).toBe(40);
      expect(comp.topPosition).toBe(60);
      expect(comp._opacity).toBe(1);
   });

   it("should clamp position inside container when dropdown overflows", () => {
      const { comp } = createDropdown();
      const container = document.createElement("div");
      vi.spyOn(container, "getBoundingClientRect").mockReturnValue({
         x: 0, y: 0, left: 0, top: 0, right: 200, bottom: 200,
         width: 200, height: 200, toJSON: () => ({}),
      } as DOMRect);
      comp.container = container;

      comp.dropdownBounds = new Rectangle(180, 190, 50, 50);

      expect(comp.leftPosition).toBe(150);
      expect(comp.topPosition).toBe(150);
   });
});

describe("FixedDropdownComponent — close handlers [Group 2, Risk 3]", () => {

   it("should emit onClose for outside mousedown when this is the current dropdown", () => {
      const { comp, dropdownService, element } = createDropdown();
      const outside = document.createElement("span");
      const emitSpy = vi.spyOn(comp.onClose, "emit");
      vi.mocked(dropdownService.isCurrent).mockReturnValue(true);

      comp["documentMousedown0"]({ stopPropagation: vi.fn() } as unknown as MouseEvent, outside);

      expect(emitSpy).toHaveBeenCalled();
      expect(element.contains(outside)).toBe(false);
   });

   it("should emit onClose on document click when autoClose is enabled", () => {
      const { comp } = createDropdown();
      comp.autoClose = true;
      const emitSpy = vi.spyOn(comp.onClose, "emit");

      comp["documentClick"]({ button: 0, stopPropagation: vi.fn(), target: document.body } as unknown as MouseEvent);

      expect(emitSpy).toHaveBeenCalled();
   });

   it("should emit onClose from tryClose when autoClose is enabled", () => {
      const { comp } = createDropdown();
      const emitSpy = vi.spyOn(comp.onClose, "emit");

      comp.tryClose();

      expect(emitSpy).toHaveBeenCalled();
   });
});

describe("FixedDropdownComponent — destroy cleanup [Group 3, Risk 3]", () => {

   // 🔁 Regression-sensitive: leaked document listeners close unrelated dropdowns and hurt scroll UX
   it("should remove document listeners and clear listener timeout on destroy", () => {
      const { comp, renderer, cleanup } = createDropdown();
      vi.useFakeTimers();
      comp.ngOnInit();
      vi.runAllTimers();

      comp.ngOnDestroy();

      expect(renderer.listen).toHaveBeenCalled();
      expect(cleanup).toHaveBeenCalled();
      vi.useRealTimers();
   });
});
