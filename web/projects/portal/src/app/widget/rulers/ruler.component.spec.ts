/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { Ruler } from "./ruler.component";

describe("Ruler", () => {
   beforeEach(() => {
      TestBed.configureTestingModule({
         declarations: [Ruler],
         schemas: [NO_ERRORS_SCHEMA]
      });
      TestBed.compileComponents();
   });

   it("Should create ruler component", () => {
      const fixture: ComponentFixture<Ruler> = TestBed.createComponent(Ruler);
      fixture.detectChanges();
      expect(fixture.componentInstance).toBeTruthy();
   });

   it("should change orientation", () => {
      const fixture: ComponentFixture<Ruler> = TestBed.createComponent(Ruler);
      const ruler: Ruler = fixture.componentInstance;

      ruler.horizontal = true;
      fixture.detectChanges();

      expect(ruler.rulerTopStyle).toBe(0);
      expect(ruler.rulerLeftStyle).toBe(18);
      expect(ruler.rulerBottomStyle).toBe("auto");
      expect(ruler.rulerRightStyle).toBe("0px");

      ruler.horizontal = false;
      fixture.detectChanges();

      expect(ruler.rulerTopStyle).toBe(18);
      expect(ruler.rulerLeftStyle).toBe(0);
      expect(ruler.rulerBottomStyle).toBe("0px");
      expect(ruler.rulerRightStyle).toBe("auto");
   });
});
