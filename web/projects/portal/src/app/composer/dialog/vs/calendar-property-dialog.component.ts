/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, OnInit, Input, Output, EventEmitter } from "@angular/core";
import { CalendarPropertyDialogModel } from "../../data/vs/calendar-property-dialog-model";
import { CalendarGeneralPaneModel } from "../../data/vs/calendar-general-pane-model";
import { CalendarDataPaneModel } from "../../data/vs/calendar-data-pane-model";
import { CalendarAdvancedPaneModel } from "../../data/vs/calendar-advanced-pane-model";
import { VSAssemblyScriptPaneModel } from "../../../widget/dialog/vsassembly-script-pane/vsassembly-script-pane-model";
import { TitlePropPaneModel } from "../../../vsobjects/model/title-prop-pane-model";
import { GeneralPropPaneModel } from "../../../vsobjects/model/general-prop-pane-model";
import { BasicGeneralPaneModel } from "../../../vsobjects/model/basic-general-pane-model";
import { UntypedFormGroup } from "@angular/forms";
import { ScriptPaneTreeModel } from "../../../widget/dialog/script-pane/script-pane-tree-model";
import { UIContextService } from "../../../common/services/ui-context.service";
import { PropertyDialogService } from "../../../vsobjects/util/property-dialog.service";
import { PropertyDialog } from "./property-dialog.component";

const SINGLE_CALENDAR_MODE: number = 1;
const DOUBLE_CALENDAR_MODE: number = 2;
const CALENDAR_SHOW_TYPE: number = 1;
const DROPDOWN_SHOW_TYPE: number = 2;
const DEFAULT_HEIGHT = 20;

@Component({
   selector: "calendar-property-dialog",
   templateUrl: "calendar-property-dialog.component.html",
})
export class CalendarPropertyDialog extends PropertyDialog implements OnInit {
   @Input() model: CalendarPropertyDialogModel;
   @Input() scriptTreeModel: ScriptPaneTreeModel;
   @Output() onApply = new EventEmitter<{collapse: boolean, result: any}>();
   controller: string = "../api/composer/vs/calendar-property-dialog-model/";
   form: UntypedFormGroup;
   generalTab: string = "calendar-property-dialog-general-tab";
   scriptTab: string = "calendar-property-dialog-script-tab";
   formValid = () => this.form && this.form.valid;

   public constructor(protected uiContextService: UIContextService,
                      protected propertyDialogService: PropertyDialogService)
   {
      super(uiContextService, null, propertyDialogService);
   }

   ngOnInit(): void {
      super.ngOnInit();
      this.initForm();
   }

   initForm(): void {
      this.form = new UntypedFormGroup({
         calendarGeneralPaneForm: new UntypedFormGroup({
            generalPropPaneForm: new UntypedFormGroup({})
         }),
         calendarAdvancedPaneForm: new UntypedFormGroup({})
      });
   }

   get defaultTab(): string {
      return this.openToScript ? this.scriptTab
         : this.uiContextService.getDefaultTab("calendar-property-dialog", this.generalTab);
   }

   set defaultTab(tab: string) {
      this.uiContextService.setDefaultTab("calendar-property-dialog", tab);
   }

   changeShowType(type: number) {
      // default height when switching from dropdown mode is 9 rows
      this.model.calendarGeneralPaneModel.sizePositionPaneModel.height =
         type == DROPDOWN_SHOW_TYPE ? DEFAULT_HEIGHT : DEFAULT_HEIGHT * 9;
   }

   changeViewMode(mode: number) {
      let width: number = this.model.calendarGeneralPaneModel.sizePositionPaneModel.width;
      width = mode == DOUBLE_CALENDAR_MODE ? width * 2 : Math.ceil(width / 2);
      this.model.calendarGeneralPaneModel.sizePositionPaneModel.width = width;
   }

   protected closing(isApply: boolean, collapse: boolean = false) {
      const payload = {collapse: collapse, result: this.model};
      isApply ? this.onApply.emit(payload) : this.onCommit.emit(this.model);
   }

   protected getScripts(): string[] {
      return [this.model.vsAssemblyScriptPaneModel.expression];
   }
}
