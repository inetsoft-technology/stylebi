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
import { Component, Output, EventEmitter, Input, OnInit } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { LegendFormatDialogModel } from "../model/dialog/legend-format-dialog-model";
import { UIContextService } from "../../common/services/ui-context.service";

@Component({
   selector: "legend-format-dialog",
   templateUrl: "legend-format-dialog.component.html",
})
export class LegendFormatDialog implements OnInit {
   @Input() model: LegendFormatDialogModel;
   @Input() variableValues: string[] = [];
   @Input() vsId: string = null;
   @Output() onCommit: EventEmitter<LegendFormatDialogModel> =
      new EventEmitter<LegendFormatDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @Output() onApply = new EventEmitter<{collapse: boolean, result: any}>();
   form: UntypedFormGroup;

   constructor(private uiContextService: UIContextService) {
      this.form = new UntypedFormGroup({
         formatForm: new UntypedFormGroup({}),
      });
   }

   ngOnInit(): void {
      this.updateLastTab();
   }

   close(): void {
      this.onCancel.emit("cancel");
   }

   ok() {
      this.onCommit.emit(this.model);
   }

   apply(event: boolean): void {
      this.onApply.emit({collapse: event, result: this.model});
   }

   updateLastTab() {
      if(this.defaultTab == "scaleTab" && (this.model.dimension || this.model.time) ||
         this.defaultTab == "aliasTab" && !this.model.dimension)
      {
         this.defaultTab = null;
      }
   }

   get defaultTab(): string {
      return this.uiContextService.getDefaultTab("legend-property-dialog", "generalTab");
   }

   set defaultTab(tab: string) {
      this.uiContextService.setDefaultTab("legend-property-dialog", tab);
   }
}
