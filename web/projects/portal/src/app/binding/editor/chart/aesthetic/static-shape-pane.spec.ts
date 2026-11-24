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
import { waitForAsync, ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf } from "rxjs";
import { ChartConfig } from "../../../../common/util/chart-config";
import { ShapeItem } from "./shape-item.component";
import { StaticShapePane } from "./static-shape-pane.component";
import { HttpClient } from "@angular/common/http";
import { ModelService } from "../../../../widget/services/model.service";

describe("Static Shape Pane Unit Test", () => {
   let fixture: ComponentFixture<StaticShapePane>;
   let shapePane: StaticShapePane;
   let httpService = { get: vi.fn(), post: vi.fn() };
   let modelService = { getModel: vi.fn() };

   beforeEach(waitForAsync(() => {
      modelService.getModel.mockClear();
      modelService.getModel.mockImplementation((controller, params) => {
         if(controller === "../api/composer/imageShapes") {
            return observableOf([]);
         }
         else {
            return observableOf({});
         }
      });
      TestBed.configureTestingModule({
         imports: [
            FormsModule,
            ReactiveFormsModule,
            NgbModule,
            StaticShapePane,
            ShapeItem,
         ],
         
         providers: [
            { provide: HttpClient, useValue: httpService },
            { provide: ModelService, useValue: modelService },
         ]
      }).compileComponents();
   }));

   //for Bug #19629, Bug #19749
   it("should load current shape page when initialization", () => {
      fixture = TestBed.createComponent(StaticShapePane);
      shapePane = <StaticShapePane>fixture.componentInstance;
      shapePane.shapeStr = "113Face.svg";

      expect(shapePane.currentPage).toEqual(1);
   });

   it("should request shapes without an orgId when no asset ID is supplied", () => {
      fixture = TestBed.createComponent(StaticShapePane);
      shapePane = <StaticShapePane>fixture.componentInstance;
      fixture.detectChanges();

      const params = modelService.getModel.mock.calls
         .find(([controller]) => controller === "../api/composer/imageShapes")[1];
      expect(params.has("orgId")).toBe(false);
   });

   it("should request shapes with the organization from the asset ID", () => {
      fixture = TestBed.createComponent(StaticShapePane);
      shapePane = <StaticShapePane>fixture.componentInstance;
      shapePane.assetId = "1^128^__NULL__^TEST^my-org";
      fixture.detectChanges();

      const params = modelService.getModel.mock.calls
         .find(([controller]) => controller === "../api/composer/imageShapes")[1];
      expect(params.get("orgId")).toBe("my-org");
   });

   describe("range text, pager and selected cell", () => {
      // i18n keys are not resolved in unit tests
      const plain = (s: string) => (s || "").replace(/_#\((.*?)\)/g, "$1");

      function create(count: number, shapeStr: string = null): void {
         fixture = TestBed.createComponent(StaticShapePane);
         shapePane = <StaticShapePane>fixture.componentInstance;
         fixture.detectChanges();
         shapePane.shapes = Array.from(new Array(count), (x, i) => "shape" + (i + 1) + ".png");
         shapePane.currentPage = 0;

         if(shapeStr) {
            shapePane.shapeStr = shapeStr;
         }

         fixture.detectChanges();
      }

      const range = () => fixture.nativeElement.querySelector(".sp-range").textContent.trim();
      const pagers = () => fixture.nativeElement.querySelectorAll(".sp-toolbar > .sp-btn");

      it("should show first-last on the first and the second page and a partial last page", () => {
         create(38);
         expect(range()).toBe("1-16");

         shapePane.showNext();
         fixture.detectChanges();
         expect(range()).toBe("17-32");

         shapePane.showNext();
         fixture.detectChanges();
         expect(range()).toBe("33-38");
      });

      it("should show just the number when the last page has one shape", () => {
         create(33);
         shapePane.currentPage = 2;
         fixture.detectChanges();
         expect(range()).toBe("33");
      });

      it("should grow the range and the page count with custom shapes", () => {
         create(32);
         expect(shapePane.nextEnabled).toBe(true);
         shapePane.currentPage = 1;
         fixture.detectChanges();
         expect(range()).toBe("17-32");
         expect(shapePane.nextEnabled).toBe(false);

         shapePane.shapes = shapePane.shapes.concat(["custom1.png", "custom2.png"]);
         fixture.detectChanges();
         expect(range()).toBe("17-32");
         expect(shapePane.nextEnabled).toBe(true);
         shapePane.showNext();
         fixture.detectChanges();
         expect(range()).toBe("33-34");
      });

      it("should disable the pager buttons at the ends", () => {
         create(38);
         expect(pagers()[0].disabled).toBe(true);
         expect(pagers()[1].disabled).toBe(false);

         shapePane.currentPage = 2;
         fixture.detectChanges();
         expect(pagers()[0].disabled).toBe(false);
         expect(pagers()[1].disabled).toBe(true);
      });

      it("should mark only the current shape with aria-pressed", () => {
         create(38, "shape3.png");
         const cells = fixture.nativeElement.querySelectorAll(".sp-cell");
         expect(cells.length).toBe(16);
         expect(cells[2].getAttribute("aria-pressed")).toBe("true");
         expect(cells[2].classList).toContain("sp-cell--selected");
         expect(fixture.nativeElement.querySelectorAll("[aria-pressed=true]").length).toBe(1);
         expect(cells[0].getAttribute("aria-pressed")).toBe("false");
      });

      it("should name custom cells by file name and built-in cells by position", () => {
         create(38);
         const cells = fixture.nativeElement.querySelectorAll(".sp-cell");
         expect(cells[0].getAttribute("aria-label")).toBe("shape1.png");
         expect(cells[0].getAttribute("title")).toBe("shape1.png");

         shapePane.shapes = ChartConfig.SHAPE_STYLES.concat(ChartConfig.IMAGE_SHAPES);
         fixture.detectChanges();
         const builtin = fixture.nativeElement.querySelectorAll(".sp-cell");
         expect(plain(builtin[4].getAttribute("aria-label"))).toBe("Shape 5");
      });

      it("should emit the shape and no longer render a clickable icon", () => {
         create(38);
         let emitted: string[] = [];
         shapePane.shapeChanged.subscribe(s => emitted.push(s));
         fixture.nativeElement.querySelectorAll(".sp-cell")[1].click();
         expect(emitted).toEqual(["shape2.png"]);
         expect(fixture.nativeElement.querySelectorAll("i.chevron-circle-arrow-left-icon").length)
            .toBe(0);
      });

      it("should show Clear only when nil is supported", () => {
         create(38);
         const clear = () => fixture.nativeElement.querySelector("button .eraser-icon");
         expect(clear()).toBeNull();
         shapePane.nilSupported = true;
         fixture.detectChanges();
         expect(clear()).not.toBeNull();
      });
   });
});
