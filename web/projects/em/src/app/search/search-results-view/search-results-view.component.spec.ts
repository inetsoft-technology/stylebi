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
import { MatPaginatorModule } from "@angular/material/paginator";
import { MatTableModule } from "@angular/material/table";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { ActivatedRoute } from "@angular/router";
import { ActivatedRouteStub } from "../../../../../shared/testing/activated-route-stub";
import { SearchResultsComponent } from "../search-results/search-results.component";
import { SearchResultsViewComponent } from "./search-results-view.component";

describe("SearchResultsViewComponent", () => {
   let component: SearchResultsViewComponent;
   let fixture: ComponentFixture<SearchResultsViewComponent>;

   beforeEach(async(() => {
      const route = new ActivatedRouteStub({}, {}, {});
      TestBed.configureTestingModule({
         imports: [
            NoopAnimationsModule,
            MatPaginatorModule,
            MatTableModule
         ],
         declarations: [SearchResultsComponent, SearchResultsViewComponent],
         providers: [
            { provide: ActivatedRoute, useValue: route }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(SearchResultsViewComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
   }));

   it("should create", () => {
      expect(component).toBeTruthy();
   });
});
