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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatTableModule } from "@angular/material/table";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { RouterTestingModule } from "@angular/router/testing";
import { ScheduleCycleListViewComponent } from "./schedule-cycle-list-view.component";

describe("ScheduleCycleListViewComponent", () => {
   let component: ScheduleCycleListViewComponent;
   let fixture: ComponentFixture<ScheduleCycleListViewComponent>;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [
            MatCardModule,
            MatTableModule,
            MatCheckboxModule,
            MatButtonModule,
            MatFormFieldModule,
            MatInputModule,
            NoopAnimationsModule,
            MatIconModule,
            RouterTestingModule
         ],
         declarations: [
            ScheduleCycleListViewComponent
         ]
      })
         .compileComponents();

      fixture = TestBed.createComponent(ScheduleCycleListViewComponent);
      component = fixture.componentInstance;
      component.dataSource = [];
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
