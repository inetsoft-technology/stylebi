/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
import { Component, ViewChild } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { DndService } from "../../../../common/dnd/dnd.service";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { TestUtils } from "../../../../common/test/test-utils";
import { LinearSizeModel, StaticSizeModel } from "../../../../common/data/visual-frame-model";
import { ChartBindingModel } from "../../../data/chart/chart-binding-model";
import { ChartEditorService } from "../../../services/chart/chart-editor.service";
import { FIELD_MC_PROVIDERS } from "./field-mc-test-helpers";
import { SizeFieldMc } from "./size-field-mc.component";

// Host that replaces the whole binding model, the way vs-binding-pane handles
// SetVSBindingModelCommand (this.bindingModel = command.binding).
@Component({
   template: `<size-field-mc [bindingModel]="bindingModel" assemblyName="Chart1"></size-field-mc>`,
   standalone: true,
   imports: [SizeFieldMc]
})
class SizeFieldMcHost {
   bindingModel: ChartBindingModel;
   @ViewChild(SizeFieldMc) mc: SizeFieldMc;
}

/**
 * Builds a server-shaped binding model (plain JSON, as deserialized from the command):
 * one measure on Y whose static size is 15, optionally with a measure bound to Size.
 */
function serverBinding(sizeBound: boolean): ChartBindingModel {
   const aggr: any = TestUtils.createMockChartAggregateRef("Sum(TotalPurchased)");
   aggr.view = "Sum(TotalPurchased)";
   const sframe = new StaticSizeModel();
   sframe.size = 15;
   aggr.sizeFrame = sframe;

   const model: any = TestUtils.createMockChartBindingModel();
   model.yfields = [aggr];

   if(sizeBound) {
      const info: any = TestUtils.createMockAestheticInfo("Sum(TotalPurchased)");
      info.classType = "AestheticInfo";
      info.frame = new LinearSizeModel();
      model.sizeField = info;
   }

   return JSON.parse(JSON.stringify(model));
}

describe("SizeFieldMc size tile icon", () => {
   let fixture: ComponentFixture<SizeFieldMcHost>;
   let host: SizeFieldMcHost;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [SizeFieldMcHost],
         providers: [
            ...FIELD_MC_PROVIDERS,
            {
               provide: ChartEditorService,
               useValue: {
                  bindingModel: null,
                  changeChartAesthetic: vi.fn(),
                  isDropPaneAccept: vi.fn().mockReturnValue(true),
                  getDNDType: vi.fn().mockReturnValue(0)
               }
            },
            { provide: DndService, useValue: {} },
            { provide: UIContextService, useValue: { isVS: () => true } }
         ]
      });

      fixture = TestBed.createComponent(SizeFieldMcHost);
      host = fixture.componentInstance;
      host.bindingModel = serverBinding(false);
      fixture.detectChanges();
   });

   function circles(): Element[] {
      return Array.from(
         fixture.nativeElement.querySelectorAll(".visual-edit-icon svg.size-glyph"));
   }

   // which rings are drawn dark, inner ring first
   function darkRings(): boolean[] {
      const rings = Array.from(
         fixture.nativeElement.querySelectorAll("svg.size-glyph circle.size-glyph__ring"));
      return rings.reverse().map((r: Element) =>
         !r.classList.contains("size-glyph__ring--off"));
   }

   function tooltip(): string {
      return fixture.nativeElement.querySelector(".visual-edit-icon").getAttribute("title");
   }

   function replaceBinding(sizeBound: boolean): void {
      host.bindingModel = serverBinding(sizeBound);
      fixture.detectChanges();
   }

   it("shows the circles glyph when no field is bound", () => {
      expect(host.mc.frames[0].clazz).toContain("StaticSizeModel");
      expect(circles().length).toBe(1);
      expect(fixture.debugElement.queryAll(By.css("size-cell")).length).toBe(0);
   });

   it("keeps the circles glyph, not a frame preview, after a field is bound", () => {
      replaceBinding(true);

      expect(host.mc.frames[0].clazz).toContain("LinearSizeModel");
      expect(circles().length).toBe(1);
      expect(fixture.debugElement.queryAll(By.css("size-cell")).length).toBe(0);
   });

   it("keeps the circles glyph after the field is removed again", () => {
      replaceBinding(true);
      replaceBinding(false);

      expect(circles().length).toBe(1);
      expect(fixture.debugElement.queryAll(By.css("size-cell")).length).toBe(0);
   });

   it("enables the glyph once the frame is editable", () => {
      expect(host.mc.isFrameEditEnabled()).toBe(true);
      expect(circles()[0].classList.contains("icon-disabled")).toBe(false);
   });

   // serverBinding gives a static size of 15, so the middle ring
   it("emphasises the ring a static size falls in", () => {
      expect(darkRings()).toEqual([false, true, false]);
      expect(tooltip()).toContain("(_#(js:Static size) 15)");

      for(const [size, rings] of [[1, [true, false, false]], [10, [true, false, false]],
         [11, [false, true, false]], [20, [false, true, false]], [21, [false, false, true]],
         [30, [false, false, true]]] as [number, boolean[]][])
      {
         (host.mc.frames[0] as any).size = size;
         fixture.detectChanges();
         expect(darkRings()).toEqual(rings);
      }
   });

   it("emphasises all three rings for a size driven by a field", () => {
      replaceBinding(true);

      expect(darkRings()).toEqual([true, true, true]);
      expect(tooltip()).toContain("(_#(js:Size from data))");
   });
});
