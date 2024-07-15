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
import { DebugElement, NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { Ruler } from "./ruler.component";
import { Rulers } from "./rulers.component";

describe("Rulers", () => {
   beforeEach(() => {
      TestBed.configureTestingModule({
         declarations: [ Rulers, Ruler ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();
   });

   it("should create rulers component", () => {
      const fixture: ComponentFixture<Rulers> = TestBed.createComponent(Rulers);
      fixture.detectChanges();
      expect(fixture.componentInstance).toBeTruthy();
   });

   it("should propagate property changes", () => {
      const fixture: ComponentFixture<Rulers> = TestBed.createComponent(Rulers);
      const rulers: Rulers = fixture.componentInstance;

      fixture.detectChanges();

      const rulerElements: DebugElement[] = fixture.debugElement.queryAll(By.css("w-ruler"));
      expect(rulerElements).toBeTruthy();
      expect(rulerElements.length).toBe(2);

      const horizontalRuler: Ruler = rulerElements[0].componentInstance;
      const verticalRuler: Ruler = rulerElements[1].componentInstance;

      expect(horizontalRuler.horizontal).toBe(true);
      expect(horizontalRuler.top).toBe(0);
      expect(horizontalRuler.left).toBe(0);
      expect(horizontalRuler.bottom).toBe(0);
      expect(horizontalRuler.right).toBe(0);
      expect(horizontalRuler.showGuides).toBe(false);
      expect(horizontalRuler.guideTop).toBe(0);
      expect(horizontalRuler.guideLeft).toBe(0);
      expect(horizontalRuler.guideWidth).toBe(0);
      expect(horizontalRuler.guideHeight).toBe(0);

      expect(verticalRuler.horizontal).toBe(false);
      expect(verticalRuler.top).toBe(0);
      expect(verticalRuler.left).toBe(0);
      expect(verticalRuler.bottom).toBe(0);
      expect(verticalRuler.right).toBe(0);
      expect(verticalRuler.showGuides).toBe(false);
      expect(verticalRuler.guideTop).toBe(0);
      expect(verticalRuler.guideLeft).toBe(0);
      expect(verticalRuler.guideWidth).toBe(0);
      expect(verticalRuler.guideHeight).toBe(0);

      rulers.top = 1;
      rulers.left = 2;
      rulers.bottom = 3;
      rulers.right = 4;
      rulers.showGuides = true;
      rulers.guideTop = 5;
      rulers.guideLeft = 6;
      rulers.guideWidth = 7;
      rulers.guideHeight = 8;

      fixture.detectChanges();

      expect(horizontalRuler.horizontal).toBe(true);
      expect(horizontalRuler.top).toBe(1);
      expect(horizontalRuler.left).toBe(2);
      expect(horizontalRuler.bottom).toBe(3);
      expect(horizontalRuler.right).toBe(4);
      expect(horizontalRuler.showGuides).toBe(true);
      expect(horizontalRuler.guideTop).toBe(5);
      expect(horizontalRuler.guideLeft).toBe(6);
      expect(horizontalRuler.guideWidth).toBe(7);
      expect(horizontalRuler.guideHeight).toBe(8);

      expect(verticalRuler.horizontal).toBe(false);
      expect(verticalRuler.top).toBe(1);
      expect(verticalRuler.left).toBe(2);
      expect(verticalRuler.bottom).toBe(3);
      expect(verticalRuler.right).toBe(4);
      expect(verticalRuler.showGuides).toBe(true);
      expect(verticalRuler.guideTop).toBe(5);
      expect(verticalRuler.guideLeft).toBe(6);
      expect(verticalRuler.guideWidth).toBe(7);
      expect(verticalRuler.guideHeight).toBe(8);
   });
});
