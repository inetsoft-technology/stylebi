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
import { SizeCell } from "./size-cell.component";
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

// Records every Image the size cell creates so a test can fire its load.
class FakeImage {
   static created: FakeImage[] = [];
   src: string = "";
   onload: (e?: any) => void;

   constructor() {
      FakeImage.created.push(this);
   }
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

describe("SizeFieldMc size icon refresh (Bug #77017)", () => {
   let fixture: ComponentFixture<SizeFieldMcHost>;
   let host: SizeFieldMcHost;
   let ctx: any;
   let originalImage: any;

   beforeEach(() => {
      ctx = { clearRect: vi.fn(), fillRect: vi.fn(), drawImage: vi.fn(), fillStyle: "" };
      vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(ctx);
      FakeImage.created = [];
      originalImage = (window as any).Image;
      (window as any).Image = FakeImage;

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

   afterEach(() => {
      (window as any).Image = originalImage;
      vi.restoreAllMocks();
   });

   function sizeCell(): SizeCell {
      const cells = fixture.debugElement.queryAll(By.directive(SizeCell));
      expect(cells.length).toBe(1);
      return cells[0].componentInstance;
   }

   function replaceBinding(sizeBound: boolean): void {
      ctx.clearRect.mockClear();
      ctx.fillRect.mockClear();
      ctx.drawImage.mockClear();
      FakeImage.created = [];
      host.bindingModel = serverBinding(sizeBound);
      fixture.detectChanges();
   }

   it("shows the static size bar when no field is bound", () => {
      expect(host.mc.frames[0].clazz).toContain("StaticSizeModel");
      // 20px cell, size 15 -> ceil(20 * 15 / 30) = 10px wide bar
      expect(ctx.fillRect).toHaveBeenCalledWith(5, 0, 10, 20);
      expect(FakeImage.created.length).toBe(0);
   });

   it("repaints the reused cell as the linear icon after a field is bound (Error 1)", () => {
      const cell = sizeCell();

      replaceBinding(true);

      expect(sizeCell()).toBe(cell);
      expect(host.mc.frames[0].clazz).toContain("LinearSizeModel");
      expect(ctx.clearRect).toHaveBeenCalled();
      expect(ctx.fillRect).not.toHaveBeenCalled();
      expect(FakeImage.created.length).toBe(1);
      expect(FakeImage.created[0].src).toBe("assets/size_linear.png");

      FakeImage.created[0].onload();
      expect(ctx.drawImage).toHaveBeenCalledTimes(1);
   });

   it("repaints the reused cell as the static bar after the field is removed (Error 2)", () => {
      replaceBinding(true);
      const cell = sizeCell();
      const linearImage = FakeImage.created[0];

      replaceBinding(false);

      expect(sizeCell()).toBe(cell);
      expect(ctx.clearRect).toHaveBeenCalled();
      expect(ctx.fillRect).toHaveBeenCalledWith(5, 0, 10, 20);
      expect(FakeImage.created.length).toBe(0);

      // a late load of the superseded linear image must not draw over the static bar
      linearImage.onload();
      expect(ctx.drawImage).not.toHaveBeenCalled();
   });

   it("does not repaint when an unrelated binding refresh delivers an equal size frame", () => {
      replaceBinding(false);

      expect(ctx.clearRect).not.toHaveBeenCalled();
      expect(ctx.fillRect).not.toHaveBeenCalled();
   });
});
