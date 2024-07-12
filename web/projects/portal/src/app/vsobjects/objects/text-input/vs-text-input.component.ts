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
import {
   Component,
   ElementRef,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   OnInit,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { NgbModal, NgbDateStruct } from "@ng-bootstrap/ng-bootstrap";
import { Observable ,  Subscription } from "rxjs";
import { Tool } from "../../../../../../shared/util/tool";
import { GuiTool } from "../../../common/util/gui-tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ContextProvider } from "../../context-provider.service";
import { VSInputSelectionEvent } from "../../event/vs-input-selection-event";
import { FormInputService } from "../../util/form-input.service";
import { VSTextInputModel } from "../../model/vs-text-input-model";
import { AppErrorMessage } from "../app-error-message.component";
import { NavigationKeys } from "../navigation-keys";
import { NavigationComponent } from "../abstract-nav-component";
import { ComponentTool } from "../../../common/util/component-tool";
import { FirstDayOfWeekService } from "../../../common/services/first-day-of-week.service";
import { DataTipService } from "../data-tip/data-tip.service";
import { DateTypeFormatter } from "../../../../../../shared/util/date-type-formatter";

enum FocusRegions {
   NONE,
   INPUT,
   CALENDAR
}

@Component({
   selector: "vs-text-input",
   templateUrl: "vs-text-input.component.html",
   styleUrls: ["vs-text-input.component.scss"]
})
export class VSTextInput extends NavigationComponent<VSTextInputModel>
   implements OnInit, OnChanges, OnDestroy
{
   @Input() set model(value: VSTextInputModel) {
      this._model = value;
      this.updateSelectedDate();
   }

   get model(): VSTextInputModel {
      return this._model;
   }

   @Input() submitted: Observable<boolean>;
   @ViewChild(AppErrorMessage) errorMsg: AppErrorMessage;
   @ViewChild("thisTextArea") textAreaElementRef: ElementRef;
   @ViewChild("calendarButton") calendarButton: ElementRef;
   @ViewChild(FixedDropdownDirective) dropdown: FixedDropdownDirective;
   validInput: boolean = true;
   errorString: string;
   patternRegExp: RegExp;
   date: any;
   submittedForm: Subscription;
   private invalidMessageShown = false;
   private unappliedSelection = false;
   FocusRegions = FocusRegions;
   focused: FocusRegions = FocusRegions.NONE;
   minDate: NgbDateStruct = {year: 1900, month: 1, day: 1};
   maxDate: NgbDateStruct = {year: 2050, month: 12, day: 31};
   firstDayOfWeek: number = 1;
   firefox = false;
   private _selected: boolean;

   @Input() set selected(selected: boolean) {
      this._selected = selected;

      if(!selected && this.model) {
         this.model.editing = false;
      }
   }

   get selected(): boolean {
      return this._selected;
   }

   get wrapped(): boolean {
      return this.model.objectFormat.wrapping.whiteSpace === "normal";
   }

   constructor(private viewsheetClientService: ViewsheetClientService,
               private modalService: NgbModal,
               private formInputService: FormInputService,
               private debounceService: DebounceService,
               zone: NgZone,
               protected context: ContextProvider,
               protected dataTipService: DataTipService,
               private firstDayOfWeekService: FirstDayOfWeekService)
   {
      super(viewsheetClientService, zone, context, dataTipService);
   }

   public ngOnInit(): void {
      this.firstDayOfWeekService.getFirstDay().subscribe((model) => {
         this.firstDayOfWeek = model.isoFirstDay;
      });

      this.firefox = GuiTool.isFF();
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.hasOwnProperty("model") && this.model?.pattern != null) {
         this.patternRegExp = new RegExp(this.model.pattern);
      }

      if(this.viewer && changes.submitted && this.submitted) {
         if(this.submittedForm) {
            this.submittedForm.unsubscribe();
            this.submittedForm = null;
         }

         this.submittedForm = this.submitted.subscribe((isSubmitted) => {
            if(isSubmitted && this.unappliedSelection) {
               this.applySelection();
            }
         });
      }
   }

   ngOnDestroy() {
      super.ngOnDestroy();

      if(this.submittedForm) {
         this.submittedForm.unsubscribe();
      }
   }

   onKey(event: KeyboardEvent) {
      if(this.focused == FocusRegions.NONE || event.keyCode !== 9) {
         this.checkValidity();
      }

      let hasScript = !!this.model.script || (<any>this.model).hasOnClick;

      // enter key
      if(event.keyCode == 13 && (!this.model.multiLine || event.ctrlKey)) {
         this.submit();

         if(this.viewer && hasScript) {
            event.preventDefault();
            this.executeScript();
         }
      }
   }

   /* called on every focus out */
   onBlur() {
      this.checkValidity();
      this.submit();
   }

   onTextChange() {
      // Bug #56581: Addressed issue in FF where clicking the number spinner doesn't focus the text
      // input.
      this.textAreaElementRef.nativeElement.focus();
   }

   private submit() {
      if(!this.validInput && !this.invalidMessageShown) {
         this.invalidMessageShown = true;
         ComponentTool.showMessageDialog(this.modalService,
            "_#(js:Warning)",
            "_#(js:viewer.viewsheet.textInput.validError1) " + this.model.absoluteName +
               ": " + this.errorString).then(
            () => {
               this.model.text = "";
               this.invalidMessageShown = false;
               this.validInput = true;
            },
            () => {}
         );
      }

      this.unappliedSelection = true;

      if(this.model.refresh || this.model.writeBackDirectly) {
         this.applySelection();
      }
      else {
         this.formInputService.addPendingValue(this.model.absoluteName, this.model.text);
      }

      this.updateSelectedDate();
   }

   setDateFromDatepicker(date: any) {
      if(date.year && date.month && date.day) {
         this.model.text = date.year + "-" + date.month + "-" + date.day;
      }
      else {
         this.model.text = "";
      }

      this.checkValidity();
      this.onBlur();
      this.dropdown.close();
   }

   private isValid(): boolean {
      if(this.patternRegExp != undefined && !this.patternRegExp.test(this.model.text) ||
         this.model.option === "Integer" && !/^[-+]?[0-9]*$/g.test(this.model.text))
      {
         return false;
      }

      if(this.model.max != null || this.model.min != null) {
         let isDate: boolean = this.model.option === "Date";
         let input = isDate ? Date.parse(this.model.text) : Number(this.model.text);
         let max = this.model.max != null
            ? (isDate ? Date.parse(this.model.max) : Number(this.model.max))
            : null;
         let min = this.model.min != null
            ? (isDate ? Date.parse(this.model.min) : Number(this.model.min))
            : null;

         if(Tool.isNumber(max) && input > max || Tool.isNumber(min) && input < min) {
            return false;
         }
      }

      return true;
   }

   private checkValidity(): void {
      this.validInput = this.isValid();

      if(!this.validInput) {
         if(this.model.message != undefined) {
            this.errorString = this.model.message.split("{0}").join(this.model.text);
         }
         else {
            this.errorString = "_#(js:viewer.viewsheet.textInput.validError2)_*" +
               this.model.text;
         }
      }
   }

   getType(): string {
      switch(this.model.option) {
         case "Text":
            return "text";
         case "Password":
            return "password";
         case "Integer":
            return "number";
         case "Float":
            return "number";
         default:
            return "text";
      }
   }

   getBorderStyle(): string {
      if(this.model.insetStyle) {
         return "inset";
      }

      return "solid";
   }

   private executeScript(): void {
      setTimeout(() => {
         this.viewsheetClientService.sendEvent("/events/onclick/"
            + this.model.absoluteName + "/" + 0 + "/" + 0);
      }, 500);
   }

   private applySelection(): void {
      this.unappliedSelection = false;
      const event = new VSInputSelectionEvent(this.model.absoluteName,
         this.validInput ? this.model.text : "");
      this.debounceService.debounce(
         `InputSelectionEvent.${this.model.absoluteName}`,
         (evt, socket) => socket.sendEvent("/events/textInput/applySelection", evt),
         500, [event, this.viewsheetClientService]);
   }

   /**
    * Keyboard navigation for this component.
    * @param {NavigationKeys} key
    */
   protected navigate(key: NavigationKeys): void {
      if(this.focused == FocusRegions.NONE && !!this.textAreaElementRef) {
         this.textAreaElementRef.nativeElement.focus();
         this.focused = FocusRegions.INPUT;
      }
      else {
         const date: boolean = this.model.option == "Date";

         if(date && key == NavigationKeys.RIGHT) {
            if(this.focused == FocusRegions.INPUT && !!this.textAreaElementRef) {
               this.textAreaElementRef.nativeElement.blur();
               this.focused = FocusRegions.CALENDAR;
            }
         }
         else if(date && key == NavigationKeys.LEFT) {
            if(this.focused == FocusRegions.CALENDAR && !!this.textAreaElementRef) {
               this.textAreaElementRef.nativeElement.focus();
               this.focused = FocusRegions.INPUT;
            }
         }
         else if(key == NavigationKeys.SPACE && this.focused == FocusRegions.CALENDAR &&
            !!this.calendarButton)
         {
            this.calendarButton.nativeElement.click();
         }
      }
   }

   /**
    * Clear selection made by navigating.
    */
   protected clearNavSelection(): void {
      this.focused = FocusRegions.NONE;

      if(!!this.textAreaElementRef) {
         this.textAreaElementRef.nativeElement.blur();
      }
   }

   enableEditing(): void {
      this.model.editing = !this.viewer;

      if(this.model.editing && this.textAreaElementRef != null) {
         this.textAreaElementRef.nativeElement.focus();
      }
   }

   isInputDisabled(): boolean {
      return !this.viewer && (!this.selected || !this.model?.editing);
   }

   updateSelectedDate(): void {
      if(!!this.model && !!this.model.text && this.model.option === "Date") {
         const date =
            DateTypeFormatter.toTimeInstant(this.model.text, DateTypeFormatter.ISO_8601_DATE_FORMAT);

         if(!!date && !!date.years && !!date.months && !!date.date) {
            this.date = {year: date.years, month: date.months + 1, day: date.date};
         }
      }
   }
}
