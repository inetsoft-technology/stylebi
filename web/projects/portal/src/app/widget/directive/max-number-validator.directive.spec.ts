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
import { MaxNumberDirective } from "./max-number-validator.directive";

describe("MaxNumberDirective", () => {
   let directive: MaxNumberDirective;
   let elementRef: ElementRef;

   beforeEach(() => {
      elementRef = new ElementRef(document.createElement("input"));
      directive = new MaxNumberDirective(elementRef);
      directive.maxNumber = 100;
   });

   it("should return null for values at or below the maximum", () => {
      expect(directive.validate(new FormControl(100))).toBeNull();
      expect(directive.validate(new FormControl(0))).toBeNull();
   });

   it("should return an error for values above the maximum", () => {
      const errors = directive.validate(new FormControl(101));
      expect(errors).not.toBeNull();
      expect(errors["max"]).toBeDefined();
   });

   it("should set the max attribute on number inputs during ngAfterViewInit", () => {
      const input = document.createElement("input");
      input.type = "number";
      const ref = new ElementRef(input);
      const dir = new MaxNumberDirective(ref);
      dir.maxNumber = 50;
      dir.ngAfterViewInit();
      expect(input.max).toBe("50");
   });

   it("should not set max attribute on non-number inputs", () => {
      const input = document.createElement("input");
      input.type = "text";
      const ref = new ElementRef(input);
      const dir = new MaxNumberDirective(ref);
      dir.maxNumber = 50;
      dir.ngAfterViewInit();
      expect(input.max).toBe("");
   });
});
