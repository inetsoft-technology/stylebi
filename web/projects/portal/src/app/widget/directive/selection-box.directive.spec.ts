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
import "@angular/compiler";
import { SelectionBoxDirective } from "./selection-box.directive";

describe("SelectionBoxDirective", () => {
   function classesOf(selectionBoxClass?: string): string[] {
      const added: string[] = [];
      const renderer: any = {
         createElement: () => ({}),
         addClass: (_el: any, name: string) => added.push(name),
         setStyle: () => {},
         appendChild: () => {},
      };
      const domService: any = { requestWrite: (fn: () => void) => fn() };
      const directive = new SelectionBoxDirective(null, { nativeElement: {} }, renderer, domService);

      if(selectionBoxClass !== undefined) {
         directive.selectionBoxClass = selectionBoxClass;
      }

      directive.ngOnInit();
      return added;
   }

   it("defaultClassUnchanged", () => {
      expect(classesOf()).toEqual(["selection-box"]);
   });

   // the composer's authoring panes pass a second class for the two-tone marquee (PR8)
   it("addsEveryClassInSelectionBoxClass", () => {
      expect(classesOf("selection-box composer-selection-box")).toEqual(["selection-box", "composer-selection-box"]);
      expect(classesOf("  selection-box   composer-selection-box ")).toEqual(["selection-box", "composer-selection-box"]);
   });
});
