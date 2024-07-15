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
import { TestBed, ComponentFixture } from "@angular/core/testing";
import { NO_ERRORS_SCHEMA} from "@angular/core";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { LinearColorPane } from "./linear-color-pane.component";
import { GradientColorEditor } from "./gradient-color-editor.component";
import { StaticColorEditor } from "./static-color-editor.component";
import { ColorCell } from "./color-cell.component";
import { ColorFieldPane } from "../../../widget/color-field-pane.component";
import { ColorPane } from "../../../../widget/color-picker/cp-color-pane.component";
import { HslColorEditor } from "./hsl-color-editor.component";
import * as V from "../../../../common/data/visual-frame-model";

describe("linear color pane componnet unit case", () => {
   let fixture: ComponentFixture<LinearColorPane>;
   let linearColorPane: LinearColorPane;

   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule],
         declarations: [LinearColorPane, GradientColorEditor, HslColorEditor, StaticColorEditor, ColorCell, ColorFieldPane, ColorFieldPane, ColorPane],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(LinearColorPane);
      linearColorPane = <LinearColorPane>fixture.componentInstance;
   });

   //Bug #19192
   it("check set the customer color", () => {
      fixture.detectChanges();
      expect(linearColorPane.gradientModel.fromColor).toBeUndefined();

      let gradientColor = new GradientColorEditor();
      gradientColor.frame = new V.GradientColorModel();
      gradientColor.changeColor("#d84d3f", true);
      linearColorPane.gradientModel = gradientColor.frame;
      fixture.detectChanges();
      expect(linearColorPane.gradientModel.fromColor).toBe("#d84d3f");
   });
});