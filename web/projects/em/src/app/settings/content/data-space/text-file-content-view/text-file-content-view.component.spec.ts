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
import { CommonModule } from "@angular/common";
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule } from "@angular/forms";
import { MatCardModule } from "@angular/material/card";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatTooltipModule } from "@angular/material/tooltip";
import { of as observableOf } from "rxjs";
import { DataSpaceTreeDataSource } from "../data-space-tree-data-source";
import { TextFileContentViewComponent } from "./text-file-content-view.component";

describe("TextFileContentViewComponent", () => {
   let component: TextFileContentViewComponent;
   let fixture: ComponentFixture<TextFileContentViewComponent>;

   beforeEach(async(() => {
      const dataSpaceTreeDataSource = {
         nodeSelected: jest.fn(() => observableOf())
      };

      TestBed.configureTestingModule({
         imports: [
            CommonModule,
            FormsModule,
            HttpClientTestingModule,
            MatCardModule,
            MatIconModule,
            MatInputModule,
            MatFormFieldModule,
            MatTooltipModule
         ],
         declarations: [
            TextFileContentViewComponent
         ],
         providers: [
            { provide: DataSpaceTreeDataSource, useValue: dataSpaceTreeDataSource }
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      })
      .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(TextFileContentViewComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
