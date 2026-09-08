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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule } from "@angular/forms";
import { AlphaDropdown } from "../../../widget/format/alpha-dropdown.component";
import { ColorEditor } from "../../../widget/color-picker/color-editor.component";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ShadowPropPaneModel } from "../../data/vs/shadow-prop-pane-model";
import { ShadowPropPane } from "./shadow-prop-pane.component";

let createModel: () => ShadowPropPaneModel = () => {
   return {
      apply: true,
      color: "#000000",
      alpha: 30,
      direction: "SE",
      distance: 50,
      blur: 0
   };
};

describe("shadow prop pane unit case: ", () => {
   let fixture: ComponentFixture<ShadowPropPane>;
   let shadowPropPane: ShadowPropPane;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [FormsModule, ShadowPropPane, AlphaDropdown, ColorEditor],
         providers: [DebounceService],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(ShadowPropPane);
      shadowPropPane = fixture.componentInstance;
      shadowPropPane.model = createModel();
      fixture.detectChanges();
   });

   // Bug #76416 (#2): typing another out-of-range value into a field already
   // at the clamp boundary is a no-op transition for [ngModel], so without a
   // forced reflow the <input> keeps showing the stale, out-of-range text
   // even though model.distance is already correctly clamped.
   it("re-clamps and forces the distance input to redisplay the clamped value", () => {
      const input: HTMLInputElement =
         fixture.nativeElement.querySelector("input[data-test=shadowDistance]");

      // model.distance is already at the clamp boundary (50); typing another
      // out-of-range value clamps back to the same 50.
      shadowPropPane.clamp("distance", "999");
      fixture.detectChanges();

      expect(shadowPropPane.model.distance).toBe(50);
      expect(input.value).toBe("50");
   });

   it("re-clamps and forces the blur input to redisplay the clamped value", () => {
      const input: HTMLInputElement =
         fixture.nativeElement.querySelector("input[data-test=shadowBlur]");

      // model.blur is already at the clamp boundary (0); typing another
      // out-of-range negative value clamps back to the same 0.
      shadowPropPane.clamp("blur", "-5");
      fixture.detectChanges();

      expect(shadowPropPane.model.blur).toBe(0);
      expect(input.value).toBe("0");
   });

   it("clamps an in-range value without forcing a redundant reflow", () => {
      const changeRef = (shadowPropPane as any).changeRef;
      const detectSpy = vi.spyOn(changeRef, "detectChanges");

      shadowPropPane.clamp("distance", "20");

      expect(shadowPropPane.model.distance).toBe(20);
      expect(detectSpy).not.toHaveBeenCalled();
   });

   it("treats a cleared field as 0 and forces the reflow", () => {
      const changeRef = (shadowPropPane as any).changeRef;
      const detectSpy = vi.spyOn(changeRef, "detectChanges");

      shadowPropPane.clamp("distance", "");

      expect(shadowPropPane.model.distance).toBe(0);
      expect(detectSpy).toHaveBeenCalled();
   });
});
