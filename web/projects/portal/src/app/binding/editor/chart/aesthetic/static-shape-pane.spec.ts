/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { async, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf } from "rxjs";
import { ShapeItem } from "./shape-item.component";
import { StaticShapePane } from "./static-shape-pane.component";
import { HttpClient } from "@angular/common/http";
import { FeatureFlagsService } from "../../../../../../../shared/feature-flags/feature-flags.service";
import { ModelService } from "../../../../widget/services/model.service";

describe("Static Shape Pane Unit Test", () => {
   let fixture: ComponentFixture<StaticShapePane>;
   let shapePane: StaticShapePane;
   let httpService = { get: jest.fn(), post: jest.fn() };
   let featureFlagsService = { isFeatureEnabled: jest.fn() };
   let modelService = { getModel: jest.fn(() => observableOf({})) };

   beforeEach(async(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule, ReactiveFormsModule, NgbModule
         ],
         declarations: [
            StaticShapePane, ShapeItem
         ],
         providers: [
            { provide: HttpClient, useValue: httpService },
            { provide: ModelService, useValue: modelService },
            { provide: FeatureFlagsService, useValue: featureFlagsService }
         ]
      }).compileComponents();
   }));

   //for Bug #19629, Bug #19749
   it("should load current shape page when initialization", () => {
      fixture = TestBed.createComponent(StaticShapePane);
      shapePane = <StaticShapePane>fixture.componentInstance;
      shapePane.shapeStr = "113Face.svg";
      fixture.detectChanges();

      expect(shapePane.currentPage).toEqual(1);
   });
});
