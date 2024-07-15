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
import { Component, Input, OnInit, ViewChild, TemplateRef } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { ValueMode } from "../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { LegendFormatGeneralPaneModel } from "../model/dialog/legend-format-general-pane-model";
import { FormValidators } from "../../../../../shared/util/form-validators";
import { StyleConstants } from "../../common/util/style-constants";
import { LineStyle } from "../../common/data/line-style";
import { UIContextService } from "../../common/services/ui-context.service";

@Component({
   selector: "legend-format-general-pane",
   templateUrl: "legend-format-general-pane.component.html",
})
export class LegendFormatGeneralPane implements OnInit {
   public mode: ValueMode = ValueMode.TEXT;
   @Input() variableValues: string[] = [];
   @Input() model: LegendFormatGeneralPaneModel;
   @Input() form: UntypedFormGroup;
   @Input() vsId: string = null;

   constructor(private modalService: NgbModal,
               private uiContextService: UIContextService)
   {
   }

   get borderStyle(): string {
      let borderStyle: number = this.model.style;
      let styleStr: string = LineStyle.NONE;

      if(borderStyle == StyleConstants.THIN_THIN_LINE) {
         styleStr = LineStyle.THIN_THIN_LINE;
      }
      else if(borderStyle == StyleConstants.ULTRA_THIN_LINE) {
         styleStr = LineStyle.ULTRA_THIN_LINE;
      }
      else if(borderStyle == StyleConstants.THIN_LINE) {
         styleStr = LineStyle.THIN_LINE;
      }
      else if(borderStyle == StyleConstants.MEDIUM_LINE) {
         styleStr = LineStyle.MEDIUM_LINE;
      }
      else if(borderStyle == StyleConstants.THICK_LINE) {
         styleStr = LineStyle.THICK_LINE;
      }
      else if(borderStyle == StyleConstants.DOUBLE_LINE) {
         styleStr = LineStyle.DOUBLE_LINE;
      }
      else if(borderStyle == StyleConstants.DOT_LINE) {
         styleStr = LineStyle.DOT_LINE;
      }
      else if(borderStyle == StyleConstants.DASH_LINE) {
         styleStr = LineStyle.DASH_LINE;
      }
      else if(borderStyle == StyleConstants.MEDIUM_DASH) {
         styleStr = LineStyle.MEDIUM_DASH;
      }
      else if(borderStyle == StyleConstants.LARGE_DASH) {
         styleStr = LineStyle.LARGE_DASH;
      }

      return styleStr;
   }

   set borderStyle(styleStr: string) {
      let borderStyle: number = StyleConstants.NONE;

      if(styleStr == LineStyle.THIN_THIN_LINE) {
         borderStyle = StyleConstants.THIN_THIN_LINE;
      }
      else if(styleStr == LineStyle.ULTRA_THIN_LINE) {
         borderStyle = StyleConstants.ULTRA_THIN_LINE;
      }
      else if(styleStr == LineStyle.THIN_LINE) {
         borderStyle = StyleConstants.THIN_LINE;
      }
      else if(styleStr == LineStyle.MEDIUM_LINE) {
         borderStyle = StyleConstants.MEDIUM_LINE;
      }
      else if(styleStr == LineStyle.THICK_LINE) {
         borderStyle = StyleConstants.THICK_LINE;
      }
      else if(styleStr == LineStyle.DOUBLE_LINE) {
         borderStyle = StyleConstants.DOUBLE_LINE;
      }
      else if(styleStr == LineStyle.DOT_LINE) {
         borderStyle = StyleConstants.DOT_LINE;
      }
      else if(styleStr == LineStyle.DASH_LINE) {
         borderStyle = StyleConstants.DASH_LINE;
      }
      else if(styleStr == LineStyle.MEDIUM_DASH) {
         borderStyle = StyleConstants.MEDIUM_DASH;
      }
      else if(styleStr == LineStyle.LARGE_DASH) {
         borderStyle = StyleConstants.LARGE_DASH;
      }

      this.model.style = borderStyle;
   }

   positionChoices: string[] = ["Top", "Bottom", "Right", "Left", "In Place"];
   positionLabels: string[] = ["_#(js:Top)", "_#(js:Bottom)", "_#(js:Right)",
                               "_#(js:Left)", "_#(js:In Place)"];

   initForm(): void {
      this.form.addControl("title", new UntypedFormControl(this.model.titleValue, [
         FormValidators.containsSpecialChars,
         Validators.required,
         FormValidators.notWhiteSpace
      ]));
   }

   ngOnInit(): void {
      this.initForm();
   }

   onValueChange(value: string) {
      this.model.title = value;
   }
}
