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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule } from "@angular/forms";
import { LinearShapePane } from "./linear-shape-pane.component";
import { CombinedShapePane } from "./combined-shape-pane.component";

describe("Shape pop-up chrome", () => {
   describe("Linear shape pane", () => {
      let fixture: ComponentFixture<LinearShapePane>;

      beforeEach(() => {
         TestBed.configureTestingModule({ imports: [FormsModule, LinearShapePane] });
         fixture = TestBed.createComponent(LinearShapePane);
         fixture.componentInstance.frameModel = <any> {
            clazz: "inetsoft.web.binding.model.graph.aesthetic.FillShapeModel"
         };
         fixture.detectChanges();
      });

      it("should render four rows, each wrapping its radio, label and glyph strip", () => {
         const rows = fixture.nativeElement.querySelectorAll("label.lsp-row");
         expect(rows.length).toBe(4);

         for(const row of Array.from<HTMLElement>(rows)) {
            expect(row.querySelector("input[type=radio][name=lsgroup]")).not.toBeNull();
            expect(row.querySelectorAll(".lsp-glyphs .lsp-cell").length).toBe(5);
         }
      });

      it("should select the shape when the glyph strip is clicked", async () => {
         let changed: any[] = [];
         fixture.componentInstance.frameModelChange.subscribe(v => changed.push(v));
         const strip = fixture.nativeElement.querySelectorAll(".lsp-row")[1]
            .querySelector(".lsp-glyphs") as HTMLElement;
         strip.click();
         fixture.detectChanges();
         await fixture.whenStable();
         expect(changed.length).toBe(1);
         expect(changed[0].clazz).toBe(fixture.componentInstance.triangleClazz);
      });

      it("should mark only the checked row", () => {
         const rows = fixture.nativeElement.querySelectorAll(".lsp-row");
         expect(rows[0].classList).toContain("lsp-row--checked");
         expect(fixture.nativeElement.querySelectorAll(".lsp-row--checked").length).toBe(1);
      });
   });

   describe("Combined shape pane", () => {
      let fixture: ComponentFixture<CombinedShapePane>;

      beforeEach(() => {
         TestBed.configureTestingModule({
            imports: [CombinedShapePane],
            schemas: [NO_ERRORS_SCHEMA]
         });
         fixture = TestBed.createComponent(CombinedShapePane);
         fixture.componentInstance.frameInfos = [
            { name: "Sum(a)", frame: { shape: "909" } },
            { name: "Sum(b)", frame: { shape: "910" } }
         ];
         fixture.detectChanges();
      });

      it("should list one row per measure above a Done footer", () => {
         expect(fixture.nativeElement.querySelectorAll(".csp-list .csp-row").length).toBe(2);
         const buttons = fixture.nativeElement.querySelectorAll(".sp-footer button");
         expect(buttons.length).toBe(1);
         expect(buttons[0].classList).toContain("btn-primary");
         expect(buttons[0].getAttribute("title")).toBeNull();
      });

      it("should emit apply(false) once when Done is clicked", () => {
         let applied: any[] = [];
         fixture.componentInstance.apply.subscribe(v => applied.push(v));
         fixture.nativeElement.querySelector(".sp-footer button").click();
         expect(applied).toEqual([false]);
      });
   });
});
