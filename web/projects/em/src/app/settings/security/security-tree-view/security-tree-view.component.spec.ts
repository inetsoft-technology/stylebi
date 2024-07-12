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
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { MatButtonModule } from "@angular/material/button";
import { MatIconModule } from "@angular/material/icon";
import { MatTreeModule } from "@angular/material/tree";
import { SecurityTreeViewComponent } from "./security-tree-view.component";
import { ScrollingModule } from "@angular/cdk/scrolling";
import { MatProgressBarModule } from "@angular/material/progress-bar";

describe("SecurityTreeViewComponent", () => {
   let component: SecurityTreeViewComponent;
   let fixture: ComponentFixture<SecurityTreeViewComponent>;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            MatTreeModule,
            MatButtonModule,
            MatIconModule,
            ScrollingModule,
            MatProgressBarModule
         ],
         declarations: [SecurityTreeViewComponent],
         schemas: [NO_ERRORS_SCHEMA]
      })
         .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(SecurityTreeViewComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
