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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { Component } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NgbDropdownModule, NgbModalModule } from "@ng-bootstrap/ng-bootstrap";
import { LineStyle } from "../../../common/data/line-style";
import { DropDownTestModule } from "../../../common/test/test-module";
import { ColorComponentEditor } from "../../../widget/color-picker/color-component-editor.component";
import { ColorEditorDialog } from "../../../widget/color-picker/color-editor-dialog.component";
import { ColorEditor } from "../../../widget/color-picker/color-editor.component";
import { ColorMap } from "../../../widget/color-picker/color-map.component";
import { ColorPicker } from "../../../widget/color-picker/color-picker.component";
import { ColorSlider } from "../../../widget/color-picker/color-slider.component";
import { ColorPane } from "../../../widget/color-picker/cp-color-pane.component";
import { GradientColorItem } from "../../../widget/color-picker/gradient-color-item.component";
import { GradientColorPane } from "../../../widget/color-picker/gradient-color-pane.component";
import { GradientColorPicker } from "../../../widget/color-picker/gradient-color-picker.component";
import { RecentColorService } from "../../../widget/color-picker/recent-color.service";
import { EnterSubmitDirective } from "../../../widget/directive/enter-submit.directive";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { ActionsContextmenuAnchorDirective } from "../../../widget/fixed-dropdown/actions-contextmenu-anchor.directive";
import { AlphaDropdown } from "../../../widget/format/alpha-dropdown.component";
import { LineArrowTypeDropdown } from "../../../widget/format/line-arrow-type-dropdown.component";
import { RadiusDropdown } from "../../../widget/format/radius-dropdown.component";
import { StyleDropdown } from "../../../widget/format/style-dropdown.component";
import { AnnotationFormatDialogModel } from "./annotation-format-dialog-model";
import { AnnotationFormatDialog } from "./annotation-format-dialog.component";
import { DebounceService } from "../../../widget/services/debounce.service";

@Component({
   selector: "test-app",
   template: `
     <annotation-format-dialog
       [model]="dialogModel"
       (onCommit)="updateModel($event)"></annotation-format-dialog>`
})
class TestApp {
   public dialogModel: AnnotationFormatDialogModel = {
      boxAlpha: 100,
      boxBorderColor: "#ddb38e",
      boxBorderRadius: 12,
      boxBorderStyle: "THIN_LINE",
      boxFillColor: "#ffffff",
      lineColor: null,
      lineEnd: 1,
      lineStyle: null,
      lineVisible: false,
   };

   public updateModel(model: AnnotationFormatDialogModel): void {
      // nop for spy
   }
}

describe("Annotation Format Dialog Tests", () => {
   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [
            FormsModule,
            ReactiveFormsModule,
            NgbDropdownModule,
            NgbModalModule,
            DropDownTestModule,
            HttpClientTestingModule
         ],
         declarations: [
            AnnotationFormatDialog,
            AlphaDropdown,
            ColorComponentEditor,
            ColorEditor,
            ColorEditorDialog,
            ColorMap,
            ColorSlider,
            ColorPicker,
            ColorPane,
            GradientColorPicker,
            GradientColorPane,
            GradientColorItem,
            LineArrowTypeDropdown,
            RadiusDropdown,
            StyleDropdown,
            TestApp,
            FixedDropdownDirective,
            EnterSubmitDirective,
            ActionsContextmenuAnchorDirective
         ],
         providers: [
            RecentColorService,
            DebounceService
         ]
      });
   });

   it("should not have a none option for the style dropdowns", () => {
      const fixture = TestBed.createComponent(TestApp);
      fixture.detectChanges();

      const styleDropdowns = fixture.debugElement.queryAll(By.directive(StyleDropdown));

      styleDropdowns
         .map((styleDropdown) => styleDropdown.componentInstance)
         .forEach((styleDropdown: StyleDropdown) => {
            expect(styleDropdown.noneAvailable).toBeFalsy();
            expect(styleDropdown.lineStyles).not.toContain(LineStyle.NONE);
         });
   });

    // Bug #17950 the line and box style value load
   it("Test style dropdowns Of combobox value load", () => {
       const fixture = TestBed.createComponent(TestApp);
      fixture.detectChanges();

      const styleDropdowns = fixture.debugElement.queryAll(By.directive(StyleDropdown));

      styleDropdowns
         .map((styleDropdown) => styleDropdown.componentInstance)
         .forEach((styleDropdown: StyleDropdown) => {
            expect(styleDropdown.lineStyles.length).toEqual(8);
         });
   });
});
