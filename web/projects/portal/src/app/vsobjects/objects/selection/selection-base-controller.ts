/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Tool } from "../../../../../../shared/util/tool";
import { XConstants } from "../../../common/util/xconstants";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { SelectionValue } from "../../../composer/data/vs/selection-value";
import { SelectionValueModel } from "../../model/selection-value-model";
import { VSFormatModel } from "../../model/vs-format-model";
import { VSSelectionBaseModel } from "../../model/vs-selection-base-model";
import { Observable, Subject } from "rxjs";

export interface SelectionStateModel {
   value: string[];
   selected: boolean;
}

/**
 * Base class for selection view controllers.
 */
export abstract class SelectionBaseController<T extends VSSelectionBaseModel> {
   private _model: T;
   private _unappliedSubject: Subject<SelectionStateModel[]> = new Subject<SelectionStateModel[]>();
   protected showAll: boolean = false;
   showOther: boolean = false;
   visibleValues: SelectionValueModel[];
   indent: number;
   _unappliedSelections: SelectionStateModel[] = <SelectionStateModel[]> [];
   protected toggle: boolean = false;
   protected toggleAll = false;

   constructor(protected viewsheetClient: ViewsheetClientService) {
   }

   get unappliedSelections(): SelectionStateModel[] {
      return this._unappliedSelections;
   }

   set unappliedSelections(unappliedSelections: SelectionStateModel[]) {
      this._unappliedSelections = unappliedSelections;
      this.fireChange();
   }

   public fireChange(): void {
      this._unappliedSubject.next(this._unappliedSelections);
   }

   get unappliedSubject(): Observable<SelectionStateModel[]> {
      return this._unappliedSubject.asObservable();
   }

   public get model(): T {
      return this._model;
   }

   public set model(model: T) {
      this._model = model;
      this.resetValues();
   }

   /**
    * Gets the format descriptor of a selection item.
    *
    * @param value the selection item.
    */
   public abstract getCellFormat(value: SelectionValueModel): VSFormatModel;

   /**
    * Sets visible selection values.
    */
   public abstract setVisibleValues(): void;
   public abstract resetValues(): void;

   /**
    *
    * Check whether any visibleValues are selected
    */
   public anyValuesSelected(): boolean {
      return this.visibleValues.some((value) => {
         return SelectionValue.isSelected(value.state);
      });
   }

   /**
    * Check whether the dropdown is hidden.
    */
   public static isHidden(model: VSSelectionBaseModel): boolean {
      if(model.containerType === "VSSelectionContainer") {
         return model.dropdown;
      }
      else if(model.dropdown) {
         return model.hidden && !model.maxMode;
      }
      else {
         return false;
      }
   }

   /**
    * Updates the selection in response to a user interaction.
    *
    * @param values the selection items to update.
    * @param eventSource event source
    * @param toggle toggle selection mode. for selection tree, only toggle current selection level.
    * @param toggleAll toggle selection mode. for selection tree, toggle all level.
    */
   public abstract updateSelection(values: SelectionStateModel[],
                                   eventSource?: string,
                                   toggle?: boolean,
                                   toggleAll?: boolean): void;
   public abstract selectionStateUpdated(value: SelectionValueModel,
                                         state: number,
                                         toggle?: boolean,
                                         toggleAll?: boolean): void;
   public abstract reverseSelections(): void;
   public abstract clearSelections(): void;
   public abstract sortSelections(): void;
   public abstract searchSelections(search: string): void;
   public abstract hideChild(): void;
   public abstract showChild(): void;

   public toggleStyle(): void {
      this.viewsheetClient.sendEvent(
         "/events/selectionList/toggle/" + this.model.absoluteName);
   }

   /**
    * Hide dropdown.
    */
   public hideSelf(): void {
      this.model.hidden = true;
   }

   /**
    * Show dropdown.
    */
   public showSelf(): void {
      this.model.hidden = false;
   }

   /**
    * Determines if a folder is open.
    *
    * @param node the folder node to check.
    *
    * @returns <tt>true</tt> if open; <tt>false</tt> otherwise.
    */
   public isNodeOpen(node: SelectionValueModel): boolean {
      return false;
   }

   /**
    * Toggles a folder node.
    *
    * @param node the node to toggle.
    */
   public toggleNode(node: SelectionValueModel): void {
      // NO-OP
   }

   /**
    * Show all values, even excluded.
    */
   public abstract showAllValues(): void;

   /**
    * Hide excluded values.
    */
   public hideExcludedValues(): void {
      this.showAll = false;
      this.setVisibleValues();
   }

   public filterSelectionValue(svalue: SelectionValueModel): SelectionValueModel {
      if(this.showAll || this.model.sortType != XConstants.SORT_SPECIFIC) {
         this.showOther = false;
         svalue.excluded = false;
         return svalue;
      }
      else {
         svalue.excluded = SelectionValue.isExcluded(svalue.state);
         const selected = SelectionValue.isSelected(svalue.state);

         if(svalue.level === 0 && svalue.excluded && !selected) {
            this.showOther = true;
         }

         // Filter and remove excluded cells from visibleValues if
         // the show others button was not just pressed (showAll) and
         // the list is not being sorted
         return !svalue.excluded || selected ? svalue : null;
      }
   }

   public applySelections(eventSource?: string): void {
      if(this.unappliedSelections.length > 0) {
         this.updateSelection(Tool.clone(this.unappliedSelections),
            eventSource, this.toggle, this.toggleAll);
         this.unappliedSelections = [];
         this.toggle = false;
         this.toggleAll = false;
      }
   }

   public abstract isAdhocFilter(): boolean;

   public abstract updateStatusByValues(values: SelectionStateModel[]): void;
}
