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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { MatCardModule } from "@angular/material/card";
import { MatTableModule } from "@angular/material/table";
import { MatToolbarModule } from "@angular/material/toolbar";
import { of as observableOf } from "rxjs";
import { FavoritesService } from "../favorites/favorites.service";
import { ManageFavoritesComponent } from "./manage-favorites.component";

describe("ManageFavoritesComponent", () => {
   let component: ManageFavoritesComponent;
   let fixture: ComponentFixture<ManageFavoritesComponent>;

   beforeEach(async(() => {
      const favoritesService = {
         favorites: observableOf([]),
         removeFavorite: jest.fn()
      };

      TestBed.configureTestingModule({
         imports: [
            MatCardModule,
            MatTableModule,
            MatToolbarModule
         ],
         declarations: [
            ManageFavoritesComponent
         ],
         providers: [
            { provide: FavoritesService, useValue: favoritesService }
         ],
         schemas: [
            NO_ERRORS_SCHEMA
         ]
      })
      .compileComponents();
   }));

   beforeEach(() => {
      fixture = TestBed.createComponent(ManageFavoritesComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
