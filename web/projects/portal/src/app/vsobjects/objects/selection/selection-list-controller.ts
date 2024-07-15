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
import { Tool } from "../../../../../../shared/util/tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client/index";
import { SelectionValue } from "../../../composer/data/vs/selection-value";
import { GetVSObjectModelEvent } from "../../../vsview/event/get-vs-object-model-event";
import { ApplySelectionListEvent } from "../../event/apply-selection-list-event";
import { HideSelectionListEvent } from "../../event/hide-selection-list-event";
import { SortSelectionListEvent } from "../../event/sort-selection-list-event";
import { SelectionValueModel } from "../../model/selection-value-model";
import { VSFormatModel } from "../../model/vs-format-model";
import { VSSelectionListModel } from "../../model/vs-selection-list-model";
import { CheckFormDataService } from "../../util/check-form-data.service";
import { SelectionBaseController, SelectionStateModel } from "./selection-base-controller";

/**
 * Class that acts as the view controller for a selection list assembly.
 */
export class SelectionListController extends SelectionBaseController<VSSelectionListModel> {
   constructor(protected viewsheetClient: ViewsheetClientService,
               private formDataService: CheckFormDataService,
               private assemblyName: string) {
      super(viewsheetClient);
   }

   public getCellFormat(value: SelectionValueModel): VSFormatModel {
      let result: VSFormatModel = null;

      if(value.formatIndex < 0) {
         if(this.model.selectionList.formats[0]) {
            result = this.model.selectionList.formats[0];
         }
         else {
            result = this.model.objectFormat;
         }
      }
      else {
         result = this.model.selectionList.formats[value.formatIndex];
      }

      if(result && value.label == this.visibleValues[0].label) {
         result.border.top = "none";
      }

      return result;
   }

   /**
    * Gets the selection list values.
    *
    * @returns the values.
    */
   public setVisibleValues(): void {
      this.showOther = false;
      this.visibleValues = this.model.selectionList.selectionValues
                           .filter(value => this.filterSelectionValue(value));

      // If all values are excluded, show the values and don't show others
      if(this.visibleValues.length == 0 &&
         this.model.selectionList.selectionValues.length > 0)
      {
         this.visibleValues = this.model.selectionList.selectionValues;
         this.showOther = false;
      }
   }

   public resetValues(): void {
      // NO-OP
   }

   public showAllValues(): void {
      this.showAll = true;
      this.visibleValues = this.model.selectionList.selectionValues;
   }

   public isAdhocFilter(): boolean {
      return this.model.adhocFilter;
   }

   /**
    * Updates the selection in response to a user interaction.
    *
    * @param selectedValue the selection list item to update.
    * @param state the new selection state.
    */
   public selectionStateUpdated(selectedValue: SelectionValueModel,
                                state: number,
                                toggle?: boolean,
                                toggleAll?: boolean): void
   {
      selectedValue.state = state;
      const selected = SelectionValue.isSelected(state);
      let selection: SelectionStateModel = {value: [selectedValue.value], selected};

      if(this.model.submitOnChange) {
         this.updateSelection([selection], null, toggle, toggleAll);
      }
      else {
         if(this.model.singleSelection || toggle || toggleAll) {
            this.unappliedSelections = [selection];
            this.visibleValues.forEach((visibleVal) =>
               visibleVal.state = visibleVal == selectedValue ? state : 0);
         }
         else {
            this.unappliedSelections.push(selection);
         }

         this.fireChange();

         if(toggle) {
            this.toggle = !this.toggle;
         }

         if(this.toggleAll) {
            this.toggleAll = !this.toggleAll;
         }
      }
   }

   /**
    * Updates the selection in response to a user interaction.
    *
    * @param values the selection list items to update.
    */
   public updateSelection(values: SelectionStateModel[],
                          eventSource?: string,
                          toggle?: boolean,
                          toggleAll?: boolean): void
   {
      this.formDataService.checkFormData(
         this.viewsheetClient.runtimeId, this.model.absoluteName, null,
         () => {
            this.viewsheetClient.sendEvent(
               "/events/selectionList/update/" + this.assemblyName,
               new ApplySelectionListEvent(values, ApplySelectionListEvent.APPLY, -1,
                  -1, eventSource ? eventSource : this.model.absoluteName,
                  toggle, toggleAll));
         },
         () => {
            let event: GetVSObjectModelEvent =
               new GetVSObjectModelEvent(this.model.absoluteName);
            this.viewsheetClient.sendEvent("/events/vsview/object/model", event);
         }
      );
   }

   public reverseSelections(): void {
      this.viewsheetClient.sendEvent(
         "/events/selectionList/update/" + this.assemblyName,
         new ApplySelectionListEvent(Tool.clone(this.unappliedSelections),
                                     ApplySelectionListEvent.REVERSE));
      this.unappliedSelections = [];
   }

   public clearSelections(): void {
      this.formDataService.checkFormData(
         this.viewsheetClient.runtimeId, this.model.absoluteName, null,
         () => {
            this.viewsheetClient.sendEvent(
               "/events/selectionList/update/" + this.assemblyName,
               new ApplySelectionListEvent(null, ApplySelectionListEvent.APPLY));
         }
      );
   }

   public sortSelections(): void {
      this.viewsheetClient.sendEvent(
         "/events/selectionList/sort/" + this.assemblyName,
         new SortSelectionListEvent());
   }

   public searchSelections(search: string): void {
      this.viewsheetClient.sendEvent(
         "/events/selectionList/sort/" + this.assemblyName,
         new SortSelectionListEvent(search));
   }

   public hideChild(): void {
      this.viewsheetClient.sendEvent(
         `/events/${this.getContainerTarget()}/update/${this.assemblyName}`,
         new HideSelectionListEvent(true));
   }

   public showChild(): void {
      this.viewsheetClient.sendEvent(
         `/events/${this.getContainerTarget()}/update/${this.assemblyName}`,
         new HideSelectionListEvent(false));
   }


   updateStatusByValues(values: SelectionStateModel[]): void {
      this.model.selectionList.selectionValues.forEach(val => {
         let stateModel = values.find(value => Tool.isEquals(value.value, [val.value]));

         if(!stateModel) {
            return;
         }

         this.selectionStateUpdated(val, stateModel.selected ? SelectionValue.STATE_SELECTED :
            SelectionValue.STATE_COMPATIBLE);
      });
   }

   private getContainerTarget(): string {
      switch(this.model.containerType) {
      case "VSSelectionContainer":
         return "selectionContainer";
      case "VSGroupContainer":
         return "groupContainer";
      default:
         return null;
      }
   }
}
