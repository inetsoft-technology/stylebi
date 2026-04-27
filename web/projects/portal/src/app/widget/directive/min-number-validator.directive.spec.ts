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
import { ElementRef } from "@angular/core";
import { FormControl } from "@angular/forms";
import { MinNumberDirective } from "./min-number-validator.directive";

describe("MinNumberDirective", () => {
   let directive: MinNumberDirective;
   let elementRef: ElementRef;

   beforeEach(() => {
      elementRef = new ElementRef(document.createElement("input"));
      directive = new MinNumberDirective(elementRef);
      directive.minNumber = 5;
   });

   it("should return null for values at or above the minimum", () => {
      expect(directive.validate(new FormControl(5))).toBeNull();
      expect(directive.validate(new FormControl(10))).toBeNull();
   });

   it("should return an error for values below the minimum", () => {
      const errors = directive.validate(new FormControl(4));
      expect(errors).not.toBeNull();
      expect(errors["min"]).toBeDefined();
   });

   it("should set the min attribute on number inputs during ngAfterViewInit", () => {
      const input = document.createElement("input");
      input.type = "number";
      const ref = new ElementRef(input);
      const dir = new MinNumberDirective(ref);
      dir.minNumber = 3;
      dir.ngAfterViewInit();
      expect(input.min).toBe("3");
   });

   it("should not set min attribute on non-number inputs", () => {
      const input = document.createElement("input");
      input.type = "text";
      const ref = new ElementRef(input);
      const dir = new MinNumberDirective(ref);
      dir.minNumber = 3;
      dir.ngAfterViewInit();
      expect(input.min).toBe("");
   });
});
