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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatDialogModule } from "@angular/material/dialog";
import { MatIconModule } from "@angular/material/icon";
import { MatListModule } from "@angular/material/list";
import { ContentDriversAndPluginsViewComponent } from "./content-drivers-and-plugins-view.component";

describe("ContentDriversAndPluginsViewComponent", () => {
   let component: ContentDriversAndPluginsViewComponent;
   let fixture: ComponentFixture<ContentDriversAndPluginsViewComponent>;

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            HttpClientTestingModule,
            MatCardModule,
            MatDialogModule,
            MatButtonModule,
            MatIconModule,
            MatListModule,
         ],
         declarations: [ContentDriversAndPluginsViewComponent],
         schemas: [NO_ERRORS_SCHEMA]
      })
         .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(ContentDriversAndPluginsViewComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
