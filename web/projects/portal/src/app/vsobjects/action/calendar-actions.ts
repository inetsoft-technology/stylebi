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
import { ContextProvider } from "../context-provider.service";
import { VSCalendarModel } from "../model/calendar/vs-calendar-model";
import { AbstractVSActions } from "./abstract-vs-actions";
import { ActionStateProvider } from "./action-state-provider";
import { AssemblyActionGroup } from "../../common/action/assembly-action-group";
import { DataTipService } from "../objects/data-tip/data-tip.service";
import { PopComponentService } from "../objects/data-tip/pop-component.service";
import { MiniToolbarService } from "../objects/mini-toolbar/mini-toolbar.service";

export class CalendarActions extends AbstractVSActions<VSCalendarModel> {
   constructor(model: VSCalendarModel, contextProvider: ContextProvider,
               securityEnabled: boolean = false,
               stateProvider: ActionStateProvider = null,
               dataTipService: DataTipService = null,
               popService: PopComponentService,
               miniToolbarService: MiniToolbarService = null)
   {
      super(model, contextProvider, securityEnabled, stateProvider,
            dataTipService, popService, miniToolbarService);
   }

   protected createMenuActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      groups.push(new AssemblyActionGroup([
         {
            id: () => "calendar properties",
            label: () => "_#(js:Properties)...",
            icon: () => "fa fa-sliders",
            enabled: () => true,
            visible: () => this.composer
         },
         {
            id: () => "calendar show-format-pane",
            label: () => "_#(js:Format)...",
            icon: () => "fa fa-format",
            enabled: () => true,
            visible: () => this.composer
         },
      ]));
      groups.push(this.createDefaultEditMenuActions());
      groups.push(this.createDefaultOrderMenuActions());
      return super.createMenuActions(groups);
   }

   protected createToolbarActions(groups: AssemblyActionGroup[]): AssemblyActionGroup[] {
      groups.push(new AssemblyActionGroup([
         {
            id: () => "calendar toggle-year",
            label: () => this.model.yearView ? "_#(js:Switch To Month View)"
               : "_#(js:Switch To Year View)",
            icon: () => this.model.yearView ? "calendar-plus-icon" : "calendar-minus-icon",
            enabled: () => true,
            visible: () => (!this.model.dropdownCalendar || this.model.calendarsShown)
              && (this.isActionVisibleInViewer("Switch To Month View") &&
                  this.isActionVisibleInViewer("Switch To Year View"))
         },
         {
            id: () => "calendar toggle-double-calendar",
            label: () => this.model.doubleCalendar ? "_#(js:Switch To Simple View)"
               : (this.model.comparisonVisible || this.model.period) ? "_#(js:Switch To Range)"
               : "_#(js:Switch To Range)",
            icon: () => this.model.doubleCalendar ? "calendar-icon" : "calendar-double-icon",
            enabled: () => true,
            visible: () => (!this.model.dropdownCalendar || this.model.calendarsShown)
               && (this.isActionVisibleInViewer("Switch To Simple View") &&
                   this.isActionVisibleInViewer("Switch To Range") &&
                   this.isActionVisibleInViewer("Switch To Single Calendar") &&
                   this.isActionVisibleInViewer("Switch To Double Calendar"))
         },
         {
            id: () => "calendar clear",
            label: () => "_#(js:Clear Calendar)",
            icon: () => "eraser-icon",
            enabled: () => true,
            visible: () => this.isActionVisibleInViewer("Clear Calendar")
         },
         {
            id: () => "calendar toggle-range-comparison",
            label: () => this.model.period ? "_#(js:Switch To Date Range Mode)"
               : "_#(js:Switch To Comparison Mode)",
            // use different appropriate icon for switch to date range mode
            icon: () => this.model.period ? "date-range-icon" : "compare-icon",
            enabled: () => (this.model.comparisonVisible || this.model.period),
            visible: () => this.model.doubleCalendar
                           && (!this.model.dropdownCalendar || this.model.calendarsShown) &&
               (this.isActionVisibleInViewer("Switch To Date Range Mode") &&
                   this.isActionVisibleInViewer("Switch To Comparison Mode")) &&
               (this.model.comparisonVisible || this.model.period)
         },
         {
            id: () => "calendar multi-select",
            label: () => this.model.multiSelect ? "_#(js:Change to Single-select)"
               : "_#(js:Change to Multi-select)",
            icon: () => this.model.multiSelect ? "select-multi-icon" : "select-single-icon",
            enabled: () => true,
            visible: () => this.mobileDevice && !this.model.singleSelection &&
               this.isActionVisibleInViewer("Change to Single-select") &&
               this.isActionVisibleInViewer("Change to Multi-select")
         },
         {
            id: () => "calendar apply",
            label: () => "_#(js:Apply)",
            icon: () => "submit-icon",
            enabled: () => true,
            visible: () => (this.model.doubleCalendar || !this.model.submitOnChange)
                           && this.isActionVisibleInViewer("Apply")
         },
      ]));
      return super.createToolbarActions(groups, true);
   }

   protected getEditScriptActionId(): string {
      return "calendar edit-script";
   }
}
