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
import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { ScrollableTableDirective } from "./scrollable-table.directive";
import { By } from "@angular/platform-browser";

@Component({
   selector: "test-component",
   template: `
   <table class="table table-bordered table-hover" wScrollableTable>
     <thead>
       <tr>
         <th>Column 1</th>
         <th>Column 2</th>
         <th>Column 3</th>
         <th>Column 4</th>
         <th>Column 5</th>
       </tr>
     </thead>
     <tbody>
       <tr>
         <td>Short Value</td>
         <td>A slightly longer value.</td>
         <td>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus vestibulum libero in
             dolor rhoncus bibendum. Praesent maximus convallis tempor.
         </td>
         <td>80.25</td>
         <td>Longer than the label.</td>
       </tr>
       <tr>
         <td>Short Value</td>
         <td>A slightly longer value.</td>
         <td>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus vestibulum libero in
           dolor rhoncus bibendum. Praesent maximus convallis tempor.
         </td>
         <td>80.25</td>
         <td>Longer than the label.</td>
       </tr>
       <tr>
         <td>Short Value</td>
         <td>A slightly longer value.</td>
         <td>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus vestibulum libero in
           dolor rhoncus bibendum. Praesent maximus convallis tempor.
         </td>
         <td>80.25</td>
         <td>Longer than the label.</td>
       </tr>
       <tr>
         <td>Short Value</td>
         <td>A slightly longer value.</td>
         <td>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus vestibulum libero in
           dolor rhoncus bibendum. Praesent maximus convallis tempor.
         </td>
         <td>80.25</td>
         <td>Longer than the label.</td>
       </tr>
     </tbody>
   </table>
   `,
   styles: [
      `table.scrollable-table {
         width: 100%;
         max-height: 150px;
       }`
   ]
})
class TestComponent {
}

describe("ScrollableTableDirective", () => {
   let fixture: ComponentFixture<TestComponent>;

   beforeEach(() => {
      TestBed.configureTestingModule({
         declarations: [ TestComponent, ScrollableTableDirective ],
         schemas: [ NO_ERRORS_SCHEMA ]
      });
      TestBed.compileComponents();
      fixture = TestBed.createComponent(TestComponent);
   });

   it("should apply styles", () => {
      fixture.detectChanges();

      let element = fixture.debugElement.query(By.css("table")).nativeElement;
      expect(element).toBeTruthy();
      expect(element.classList).toBeTruthy();
      expect(element.classList.contains("scrollable-table"))
         .toBe(true);
   });
});
