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
import { Component, QueryList, ViewChildren } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import {
   CategoricalSizeModel,
   LinearSizeModel,
   StaticSizeModel,
   VisualFrameModel
} from "../../../../common/data/visual-frame-model";
import { SizeCell } from "./size-cell.component";

// Mirrors size-field-mc: the cell instance is reused across frame changes (track $index).
@Component({
   template: `
      @for (f of frames; track $index) {
         <size-cell [frameModel]="f" [isMixed]="isMixed"></size-cell>
      }`,
   standalone: true,
   imports: [SizeCell]
})
class SizeCellHost {
   frames: VisualFrameModel[] = [];
   isMixed: boolean = false;
   @ViewChildren(SizeCell) cells: QueryList<SizeCell>;
}

function staticSize(size: number): StaticSizeModel {
   const frame = new StaticSizeModel();
   frame.size = size;
   return frame;
}

describe("SizeCell repaint (Bug #77017)", () => {
   let fixture: ComponentFixture<SizeCellHost>;
   let host: SizeCellHost;
   let paintSpy: any;
   let ctx: any;

   beforeEach(() => {
      // JSDOM does not implement canvas
      ctx = { clearRect: vi.fn(), fillRect: vi.fn(), drawImage: vi.fn(), fillStyle: "" };
      vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(ctx);
      paintSpy = vi.spyOn(SizeCell.prototype as any, "paintsizeCell");

      TestBed.configureTestingModule({ imports: [SizeCellHost] });
      fixture = TestBed.createComponent(SizeCellHost);
      host = fixture.componentInstance;
      host.frames = [staticSize(15)];
      fixture.detectChanges();
   });

   afterEach(() => {
      vi.restoreAllMocks();
   });

   function setFrame(frame: VisualFrameModel): void {
      host.frames = [frame];
      fixture.detectChanges();
   }

   function lastPaintedFrame(): any {
      return paintSpy.mock.calls[paintSpy.mock.calls.length - 1][0];
   }

   it("paints once on init", () => {
      expect(paintSpy).toHaveBeenCalledTimes(1);
      expect(ctx.fillRect).toHaveBeenCalledTimes(1);
   });

   it("repaints when a static frame is replaced by a linear frame", () => {
      const cell = host.cells.first;
      paintSpy.mockClear();
      ctx.fillRect.mockClear();

      const linear = new LinearSizeModel();
      setFrame(linear);

      expect(host.cells.first).toBe(cell);
      expect(paintSpy).toHaveBeenCalledTimes(1);
      expect(lastPaintedFrame()).toBe(linear);
      expect(ctx.fillRect).not.toHaveBeenCalled();
   });

   it("repaints when a linear frame is replaced by a static frame", () => {
      setFrame(new LinearSizeModel());
      paintSpy.mockClear();
      ctx.fillRect.mockClear();

      const frame = staticSize(15);
      setFrame(frame);

      expect(paintSpy).toHaveBeenCalledTimes(1);
      expect(lastPaintedFrame()).toBe(frame);
      expect(ctx.fillRect).toHaveBeenCalledTimes(1);
   });

   it("repaints when a static frame is replaced by a categorical frame", () => {
      paintSpy.mockClear();

      setFrame(new CategoricalSizeModel());

      expect(paintSpy).toHaveBeenCalledTimes(1);
   });

   it("does not repaint for an equal but new frame object", () => {
      paintSpy.mockClear();

      setFrame(staticSize(15));

      expect(paintSpy).not.toHaveBeenCalled();
   });

   it("repaints when the static size is changed in place", () => {
      paintSpy.mockClear();

      (host.frames[0] as StaticSizeModel).size = 20;
      fixture.detectChanges();

      expect(paintSpy).toHaveBeenCalledTimes(1);
   });

   it("repaints when isMixed changes", () => {
      paintSpy.mockClear();

      host.isMixed = true;
      fixture.detectChanges();

      expect(paintSpy).toHaveBeenCalledTimes(1);
   });
});
