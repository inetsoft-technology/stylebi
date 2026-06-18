# Epic-74519 Merge Loss Recovery — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-apply every epic-74519-specific Angular frontend change that was silently dropped by the merge commit `563ab05dd`, and verify the branch builds, lints, and tests cleanly.

**Architecture:** Three phases — (1) fix the 9 confirmed losses in-place, (2) run an audit workflow across all 634 conflict-zone files to find any further losses and fix them, (3) verify with `npm run build`, `npm run lint`, `npm run test`. All Angular changes must use standalone components, `@if`/`@for` control flow, and Vitest APIs.

**Tech Stack:** Angular 21, TypeScript 5.9, Vitest 4, ngBootstrap, Angular Material 21. Working directory for npm commands: `community/web/`. Git is at `community/`.

---

## Key References (DO NOT MODIFY — used across all tasks)

| Label | Commit | Role |
|---|---|---|
| BASE | `298d23f34` | Merge base |
| EPIC | `19387c26c` | Epic-74519 tip before the merge |
| MAIN | `4e1a6d45e` | Main tip merged in |
| HEAD | current working tree | Target to fix |

All `git show` commands run from `community/`. All `npm run` commands run from `community/web/`.

---

## Task 1: Fix `tip-customize-dialog` — tooltipStyle and snapTooltip feature (Feature #74894)

**Files:**
- Modify: `web/projects/portal/src/app/widget/dialog/tip-customize-dialog/tip-customize-dialog-model.ts`
- Modify: `web/projects/portal/src/app/widget/dialog/tip-customize-dialog/tip-customize-dialog.component.ts`
- Modify: `web/projects/portal/src/app/widget/dialog/tip-customize-dialog/tip-customize-dialog.component.html`

The Java backend (`TipCustomizeDialogModel.java`) sends `combinedSupported`, `tooltipStyle`, `snapTooltip`, and `snapSupported`. The TypeScript model was reverted to the main-branch version which only has `lineChart`. The HTML lost the "Tooltip Style" fieldset and the snap tooltip checkbox.

- [ ] **Step 1: Update the model interface**

Replace the content of `tip-customize-dialog-model.ts` with:

```typescript
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
export interface TipCustomizeDialogModel {
   customRB: "DEFAULT"|"CUSTOM"|"NONE";
   combinedTip: boolean;
   combinedSupported?: boolean;
   customTip: string;
   dataRefList: String[];
   availableTipValues: String[];
   chart?: boolean;
   tooltipStyle?: "DEFAULT"|"CARD";
   snapTooltip?: boolean;
   snapSupported?: boolean;
}
```

Note: `lineChart` is replaced by `combinedSupported` to match what the Java backend sends.

- [ ] **Step 2: Update the component TypeScript**

Replace `initForm()` and its related methods in `tip-customize-dialog.component.ts`. The full new `initForm()` method, `ngOnChanges`, and the class body must reference `combinedSupported` instead of `lineChart` and wire up `tooltipStyle` and `snapTooltip` form controls:

```typescript
private initForm(): void {
   const combinedActive = this.model.combinedTip &&
      this.model.customRB != "CUSTOM" && this.model.customRB != "NONE";
   const snapDisabled = this.model.customRB == "NONE" || !this.model.snapSupported;

   this.form = new UntypedFormGroup({
      customRB: new UntypedFormControl(this.model.customRB),
      customTip: new UntypedFormControl(
         {value: this.model.customTip, disabled: this.model.customRB != "CUSTOM"},
         [Validators.required]),
      combinedTip: new UntypedFormControl({
         value: combinedActive,
         disabled: this.model.customRB == "CUSTOM" || this.model.customRB == "NONE" || !this.model.combinedSupported}),
      tooltipStyle: new UntypedFormControl(this.model.tooltipStyle || "DEFAULT"),
      snapTooltip: new UntypedFormControl({
         value: !!this.model.snapTooltip && !snapDisabled,
         disabled: snapDisabled}),
   });

   this.form.get("customRB").valueChanges.subscribe(custom => {
      const snap = this.form.get("snapTooltip");

      if(custom == "CUSTOM") {
         this.form.get("customTip").enable();
         this.form.get("combinedTip").disable();
         this.form.get("combinedTip").setValue(false);
         if(this.model.snapSupported) {
            snap.enable();
         }
      }
      else if(custom == "NONE") {
         this.form.get("customTip").disable();
         this.form.get("combinedTip").disable();
         this.form.get("combinedTip").setValue(false);
         snap.disable();
         snap.setValue(false);
      }
      else {
         this.form.get("customTip").disable();
         if(this.model.combinedSupported) {
            this.form.get("combinedTip").enable();
         }
         if(this.model.snapSupported) {
            snap.enable();
         }
      }
   });

   this.form.get("combinedTip").valueChanges.subscribe(combined => {
      if(combined && this.model.snapSupported) {
         const snap = this.form.get("snapTooltip");
         snap.enable();
         snap.setValue(true);
      }
   });

   this.form.valueChanges.subscribe(_ => {
      const value = this.form.getRawValue();
      this.model.customRB = value["customRB"];
      this.model.customTip = value["customTip"];
      this.model.combinedTip = value["combinedTip"];
      this.model.tooltipStyle = value["tooltipStyle"];
      this.model.snapTooltip = value["snapTooltip"];
   });
}
```

- [ ] **Step 3: Update the HTML template**

Replace the entire content of `tip-customize-dialog.component.html` with the following (this merges the EPIC content with Angular 21 `@if`/`@for` syntax):

```html
<!--
~ This file is part of StyleBI.
~ Copyright (C) 2024  InetSoft Technology
~
~ This program is free software: you can redistribute it and/or modify
~ it under the terms of the GNU Affero General Public License as published by
~ the Free Software Foundation, either version 3 of the License, or
~ (at your option) any later version.
~
~ This program is distributed in the hope that it will be useful,
~ but WITHOUT ANY WARRANTY; without even the implied warranty of
~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
~ GNU Affero General Public License for more details.
~
~ You should have received a copy of the GNU Affero General Public License
~ along with this program.  If not, see <https://www.gnu.org/licenses/>.
-->
<modal-header
  [title]="'_#(Customize Tooltip)'"
  (onCancel)="cancelChanges()"
  [cshid]="cshid">
</modal-header>
<div class="modal-body">
  <form [formGroup]="form" (submit)="$event.preventDefault()" class="container-fluid">
    <fieldset>
      <legend>_#(Tooltip Format)</legend>
      <div class="shell-form-row shell-form-row--checkbox">
        <div class="col">
          <div class="form-check">
            <input type="radio" class="form-check-input" id="default"
              formControlName="customRB" value="DEFAULT">
            <label class="form-check-label" for="default">
              _#(Default)
            </label>
          </div>
        </div>
        <div class="col">
          <div class="form-check">
            <input type="radio" class="form-check-input" id="custom"
              formControlName="customRB" value="CUSTOM">
            <label class="form-check-label" for="custom">
              _#(Custom)
            </label>
          </div>
        </div>
        @if (model.chart) {
          <div class="col">
            <div class="form-check">
              <input type="radio" class="form-check-input" id="none"
                formControlName="customRB" value="NONE">
              <label class="form-check-label" for="none">
                _#(None)
              </label>
            </div>
          </div>
        }
      </div>
      @if (model.combinedSupported) {
        <div class="shell-form-row shell-form-row--checkbox">
          <div class="col">
            <div class="form-check">
              <input type="checkbox" class="form-check-input" id="combinedTooltip"
                formControlName="combinedTip" [value]="false">
              <label class="form-check-label" for="combinedTooltip">
                _#(viewer.viewsheet.chart.tooltip.combine)
              </label>
            </div>
            @if (model.snapSupported) {
              <div class="form-check">
                <input type="checkbox" class="form-check-input" id="snapTooltip"
                  formControlName="snapTooltip" [value]="false">
                <label class="form-check-label" for="snapTooltip">
                  _#(viewer.viewsheet.chart.tooltip.snap)
                </label>
              </div>
            }
          </div>
        </div>
      }
      <textarea class="form-control" rows="5" required name="customText"
        formControlName="customTip"></textarea>
      <ngb-alert type="danger" [hidden]="model.customRB != 'CUSTOM' || form.get('customTip').valid" [dismissible]="false">
        _#(viewer.viewsheet.chart.tooltip.valid)</ngb-alert>
    </fieldset>
    @if (model.chart) {
      <fieldset>
        <legend>_#(Tooltip Style)</legend>
        <div class="shell-form-row shell-form-row--checkbox">
          <div class="col">
            <div class="form-check">
              <input type="radio" class="form-check-input" id="styleDefault"
                formControlName="tooltipStyle" value="DEFAULT">
              <label class="form-check-label" for="styleDefault">
                _#(Default)
              </label>
            </div>
          </div>
          <div class="col">
            <div class="form-check">
              <input type="radio" class="form-check-input" id="styleCard"
                formControlName="tooltipStyle" value="CARD">
              <label class="form-check-label" for="styleCard">
                _#(Card)
              </label>
            </div>
          </div>
        </div>
      </fieldset>
    }
  </form>
  @if (!!model.availableTipValues && model.availableTipValues.length > 0) {
    <div>
      <hr/>
      @if (!tooltipOnly) {
        <div>
          _#(hide.mark.column.tooltip)
        </div>
      }
      <div>
        _#(viewer.viewsheet.chart.tooltip.index):
      </div>
      <ul class="list-unstyled">
        @for (dataRef of model.availableTipValues; track dataRef) {
          <li>
            <span>{{dataRef}}</span>
          </li>
        }
      </ul>
      <hr/>
    </div>
  }
</div>
<div class="modal-footer">
  <button type="button" class="btn btn-primary" [disabled]="model.customRB == 'CUSTOM' && !form.get('customTip').valid" (click)="saveChanges()">_#(OK)</button>
  <button type="button" class="btn btn-default" data-dismiss="modal" (click)="cancelChanges()">_#(Cancel)</button>
</div>
```

- [ ] **Step 4: Commit**

```bash
cd /home/jasonshobe/work/stylebi/community
git add web/projects/portal/src/app/widget/dialog/tip-customize-dialog/
git commit -m "Restore tooltipStyle and snapTooltip lost in 563ab05dd merge"
```

---

## Task 2: Fix `chart-plot-options-pane.component.html` — smoothLines and treeLayout

**Files:**
- Modify: `web/projects/portal/src/app/graph/dialog/chart-plot-options-pane.component.html`

The `smoothLines` checkbox (Feature #74783) and `treeLayout` custom-select (Feature #74789) sections are missing. The model TypeScript files already have `smoothLinesVisible`, `smoothLines`, `treeLayoutVisible`, `treeLayout` fields and `treeLayoutOptions` getter.

- [ ] **Step 1: Add the missing sections after the fillGapWithDash block**

In `chart-plot-options-pane.component.html`, locate the block ending at:
```html
        }
        @if (model.wordCloud) {
```

Insert these two blocks between the `fillGapWithDash` closing `}` and the `@if (model.wordCloud)`:

```html
        @if (model.smoothLinesVisible) {
          <div class="checkbox col-auto">
            <div class="form-check">
              <input type="checkbox" class="form-check-input"
                [(ngModel)]="model.smoothLines" id="smoothLines"
                [ngModelOptions]="{standalone: true}">
              <label class="form-check-label" for="smoothLines">
                _#(Smooth Lines)
              </label>
            </div>
          </div>
        }
        @if (model.treeLayoutVisible) {
          <div class="col-auto form-floating">
            <custom-select class="tree-layout" id="treeLayout"
                           [options]="treeLayoutOptions"
                           [(ngModel)]="model.treeLayout"
                           [ngModelOptions]="{standalone: true}"
                           ariaLabel="_#(Layout Direction)">
            </custom-select>
            <label><span>_#(Layout Direction)</span></label>
          </div>
        }
```

- [ ] **Step 2: Verify CustomSelectComponent is in imports[]**

Open `web/projects/portal/src/app/graph/dialog/chart-plot-options-pane.component.ts`. Confirm `CustomSelectComponent` is in the `imports[]` array of the `@Component` decorator (it should already be, since `treeLayoutOptions` getter already exists). If missing, add it.

- [ ] **Step 3: Commit**

```bash
git add web/projects/portal/src/app/graph/dialog/chart-plot-options-pane.component.html
git commit -m "Restore smoothLines and treeLayout sections lost in 563ab05dd merge"
```

---

## Task 3: Fix `calculate-pane-dialog.component.html` — 9 native selects → custom-select

**Files:**
- Modify: `web/projects/portal/src/app/binding/editor/chart/field/calculate-pane-dialog.component.html`

The TypeScript file already has all required `*Options` getters (`percentageOfOptions`, `valueOfOptions`, `fromOptions`, `aggregateOptions`, `breakByOptions`, `resetOptions`, `movingDimensionOptions`).

- [ ] **Step 1: Replace percOfValue native select**

Locate:
```html
                <select class="form-control" [(ngModel)]="percOfValue" placeholder="_#(calculationOf)" id="percOfValue">
```
Replace the entire `<select>...</select>` block with:
```html
               <custom-select [options]="percentageOfOptions"
                              [(ngModel)]="percOfValue"
                              placeholder="_#(calculationOf)"
                              id="percOfValue">
               </custom-select>
```

- [ ] **Step 2: Replace changeColumn native select**

Locate:
```html
                <select class="form-control" [(ngModel)]="changeColumn" id="changeColumn"
```
Replace the entire `<select>...</select>` block with:
```html
                <custom-select [options]="valueOfOptions"
                               [(ngModel)]="changeColumn"
                               id="changeColumn"
                               placeholder="_#(Value of)">
                </custom-select>
```

- [ ] **Step 3: Replace changeCalculator.from native select**

Locate:
```html
                <select class="form-control" [(ngModel)]="changeCalculator.from"
```
Replace the entire `<select>...</select>` block with:
```html
               <custom-select [options]="fromOptions"
                              [(ngModel)]="changeCalculator.from"
                              placeholder="_#(From)"
                              id="calculatorFrom">
               </custom-select>
```

- [ ] **Step 4: Replace aggregateCalculator.aggregate native select**

Locate:
```html
                <select class="form-control" [(ngModel)]="aggregateCalculator.aggregate"
```
Replace the entire `<select>...</select>` block with:
```html
               <custom-select [options]="aggregateOptions"
                              [(ngModel)]="aggregateCalculator.aggregate"
                              placeholder="_#(Aggregate)">
               </custom-select>
```

- [ ] **Step 5: Replace valueOfColumn native select**

Locate:
```html
                <select class="form-control" [(ngModel)]="valueOfColumn"
```
Replace the entire `<select>...</select>` block with:
```html
               <custom-select [options]="valueOfOptions"
                              [(ngModel)]="valueOfColumn"
                              placeholder="_#(Value of)">
               </custom-select>
```

- [ ] **Step 6: Replace valueOfCalculator.from native select**

Locate:
```html
                <select class="form-control" [(ngModel)]="valueOfCalculator.from"
```
Replace the entire `<select>...</select>` block with:
```html
               <custom-select [options]="fromOptions"
                              [(ngModel)]="valueOfCalculator.from"
                              placeholder="_#(From)">
               </custom-select>
```

- [ ] **Step 7: Replace breakBy native select**

Locate:
```html
              <select class="form-control" [(ngModel)]="breakBy" placeholder="_#(Break By)">
```
Replace the entire `<select>...</select>` block with:
```html
             <custom-select [options]="breakByOptions"
                            [(ngModel)]="breakBy"
                            placeholder="_#(Break By)">
             </custom-select>
```

- [ ] **Step 8: Replace runningTotalCalculator.resetLevel native select**

Locate:
```html
              <select class="form-control" [(ngModel)]="runningTotalCalculator.resetLevel"
```
Replace the entire `<select>...</select>` block with:
```html
            <custom-select [options]="resetOptions"
                           [(ngModel)]="runningTotalCalculator.resetLevel"
                           placeholder="_#(Reset at)">
            </custom-select>
```

- [ ] **Step 9: Replace movingCalculator.innerDim native select**

Locate:
```html
                <select class="form-control" [(ngModel)]="movingCalculator.innerDim"
```
Replace the entire `<select>...</select>` block with:
```html
               <custom-select [options]="movingDimensionOptions"
                              [(ngModel)]="movingCalculator.innerDim"
                              placeholder="_#(Moving Of)">
               </custom-select>
```

- [ ] **Step 10: Verify no native selects remain**

```bash
grep -c "<select" web/projects/portal/src/app/binding/editor/chart/field/calculate-pane-dialog.component.html
```
Expected output: `0`

- [ ] **Step 11: Commit**

```bash
git add web/projects/portal/src/app/binding/editor/chart/field/calculate-pane-dialog.component.html
git commit -m "Replace remaining native selects with custom-select in calculate-pane-dialog"
```

---

## Task 4: Fix `action-accordion.component.html` — BEM class replacements + accessibility

**Files:**
- Modify: `web/projects/portal/src/app/portal/schedule/schedule-task-editor/actions/action-accordian/action-accordion.component.html`

All occurrences of the old class names must be replaced with the BEM equivalents.

- [ ] **Step 1: Replace align-items-start rows**

Replace all occurrences of `"form-row-float-label row align-items-start"` with `"action-accordion__row-start row"`:
```
Old: class="form-row-float-label row align-items-start"
New: class="action-accordion__row-start row"
```
There are 2 occurrences (one is also `action-accordion-bookmark-row`).

- [ ] **Step 2: Replace section-toggle checkboxes**

For every `<div class="form-check form-check-inline form-row-float-label">` that wraps a section-level checkbox (`id="notification"`, `id="deliverEmail"`, `id="saveToServer"`, `name="underHighlightCondition"`):
```
Old: <div class="form-check form-check-inline form-row-float-label">
     <input class="form-check-input" type="checkbox" ...
New: <div class="form-check form-switch action-accordion__section-toggle">
     <input class="form-check-input" type="checkbox" role="switch" ...
```
There are 3 such occurrences plus 1 `form-check` without `form-check-inline`.

- [ ] **Step 3: Replace plain rows**

Replace all remaining occurrences of `class="form-row-float-label row"` with `class="action-accordion__row row"`:
```
Old: class="form-row-float-label row"
New: class="action-accordion__row row"
```
This includes the `*ngIf` variant: `class="form-row-float-label row"` — replace those too.

- [ ] **Step 4: Replace inline-check col-auto divs**

Replace all occurrences of `form-row-float-label col-auto` with `action-accordion__inline-check col-auto`:
```
Old: class="form-check form-check-inline form-row-float-label col-auto"
New: class="form-check form-check-inline action-accordion__inline-check col-auto"
```

- [ ] **Step 5: Replace conditional-field class**

Locate:
```html
<div class="col-10 form-floating" [class.form-row-float-label]="saveFormat === '3'">
```
Replace with:
```html
<div class="col-10 form-floating" [class.action-accordion__conditional-field]="saveFormat === '3'">
```

- [ ] **Step 6: Replace alert class**

Locate:
```html
<div class="alert alert-danger"
```
Replace with:
```html
<div class="shell-alert shell-alert--danger"
```

- [ ] **Step 7: Update table class**

Locate:
```html
<table class="table table-sm w-100 action-accordion-alert-table">
```
Replace with:
```html
<table class="table table-hover table-sm w-100 action-accordion-alert-table">
```

- [ ] **Step 8: Verify no form-row-float-label remains**

```bash
grep -c "form-row-float-label" web/projects/portal/src/app/portal/schedule/schedule-task-editor/actions/action-accordian/action-accordion.component.html
```
Expected output: `0`

- [ ] **Step 9: Commit**

```bash
git add web/projects/portal/src/app/portal/schedule/schedule-task-editor/actions/action-accordian/action-accordion.component.html
git commit -m "Restore BEM class replacements and accessibility attrs in action-accordion"
```

---

## Task 5: Fix `task-action-pane.component.html` and `task-options-pane.component.html`

**Files:**
- Modify: `web/projects/portal/src/app/portal/schedule/schedule-task-editor/actions/task-action-pane.component.html`
- Modify: `web/projects/portal/src/app/portal/schedule/schedule-task-editor/options/task-options-pane.component.html`

### task-action-pane.component.html

- [ ] **Step 1: Remove form-row-float-label from selector row**

Locate:
```html
<div class="form-row-float-label row task-action-pane__selector-row">
```
Replace with:
```html
<div class="row task-action-pane__selector-row">
```

### task-options-pane.component.html

- [ ] **Step 2: Fix the enabled-row class**

Locate (near the top of the form body):
```html
    <div class="form-row-float-label row mt-2">
```
Replace with:
```html
    <div class="row shell-form-row--field">
```

- [ ] **Step 3: Fix the datepicker placement and attributes**

Locate the start-date datepicker:
```html
ngbDatepicker #startDatePicker="ngbDatepicker" placement="bottom-right"
              container="body" [formControl]="form.get('start')">
```
Replace with:
```html
ngbDatepicker #startDatePicker="ngbDatepicker" placement="bottom-start"
              container="body" [navigation]="'none'" [contentTemplate]="scheduleDatepickerContent"
              [formControl]="form.get('start')">
```

Locate the end-date datepicker:
```html
ngbDatepicker #endDatePicker="ngbDatepicker" placement="bottom-right"
              container="body" [formControl]="form.get('stop')"
```
Replace with:
```html
ngbDatepicker #endDatePicker="ngbDatepicker" placement="bottom-start"
              container="body" [navigation]="'none'" [contentTemplate]="scheduleDatepickerContent"
              [formControl]="form.get('stop')"
```

- [ ] **Step 4: Add the scheduleDatepickerContent template**

Append the following before the closing `</form>` tag at the very end of `task-options-pane.component.html`:

```html
<ng-template #scheduleDatepickerContent let-datepicker>
  <div class="schedule-datepicker__calendar">
    <div class="ngb-dp-header schedule-datepicker__calendar-header">
      <div class="schedule-datepicker__calendar-nav">
        <button type="button"
                class="btn btn-light-no-bg schedule-datepicker__nav-button schedule-datepicker__nav-button--prev"
                [disabled]="!canNavigateMonth(datepicker, -1)"
                (click)="navigateMonth(datepicker, -1)"
                aria-label="_#(Previous month)">
          <i class="chevron-left-icon icon-size0" aria-hidden="true"></i>
        </button>
        <custom-select class="schedule-datepicker__nav-select schedule-datepicker__nav-select--month"
                       [options]="getMonthSelectOptions(datepicker)"
                       [ngModel]="datepicker.state.firstDate.month"
                       [ngModelOptions]="{standalone: true}"
                       ariaLabel="_#(Select month)"
                       (selectionChange)="selectMonth(datepicker, $event)">
        </custom-select>
        <custom-select class="schedule-datepicker__nav-select schedule-datepicker__nav-select--year"
                       [options]="getYearSelectOptions(datepicker)"
                       [ngModel]="datepicker.state.firstDate.year"
                       [ngModelOptions]="{standalone: true}"
                       ariaLabel="_#(Select year)"
                       (selectionChange)="selectYear(datepicker, $event)">
        </custom-select>
        <button type="button"
                class="btn btn-light-no-bg schedule-datepicker__nav-button"
                [disabled]="!canNavigateMonth(datepicker, 1)"
                (click)="navigateMonth(datepicker, 1)"
                aria-label="_#(Next month)">
          <i class="chevron-right-icon icon-size0" aria-hidden="true"></i>
        </button>
      </div>
    </div>
    <div class="ngb-dp-content ngb-dp-months">
      @for (month of datepicker.state.months; track month) {
        <div class="ngb-dp-month">
          @if (datepicker.displayMonths > 1) {
            <div class="ngb-dp-month-name">
              {{ datepicker.i18n.getMonthLabel(month) }}
            </div>
          }
          <ngb-datepicker-month [month]="month"></ngb-datepicker-month>
        </div>
      }
    </div>
  </div>
</ng-template>
```

Note: This uses `@for`/`@if` instead of `*ngFor`/`*ngIf` as required by Angular 21.

- [ ] **Step 5: Verify CustomSelectComponent in task-options-pane imports[]**

In `task-options-pane.component.ts`, confirm `CustomSelectComponent` is in `imports[]`.

- [ ] **Step 6: Verify no form-row-float-label remains**

```bash
grep -c "form-row-float-label" web/projects/portal/src/app/portal/schedule/schedule-task-editor/actions/task-action-pane.component.html
grep -c "form-row-float-label" web/projects/portal/src/app/portal/schedule/schedule-task-editor/options/task-options-pane.component.html
```
Both expected: `0`

- [ ] **Step 7: Commit**

```bash
git add web/projects/portal/src/app/portal/schedule/schedule-task-editor/actions/task-action-pane.component.html
git add web/projects/portal/src/app/portal/schedule/schedule-task-editor/options/task-options-pane.component.html
git commit -m "Restore schedule task action/options form-row-float-label and datepicker template"
```

---

## Task 6: Fix `add-parameter-dialog.component.html` — parameter name picker

**Files:**
- Modify: `web/projects/portal/src/app/portal/schedule/schedule-task-editor/add-parameter-dialog/add-parameter-dialog.component.html`

The native `<select>` for parameter name selection must be replaced with a `<custom-select>` inside an `input-group` wrapper. The TypeScript already has `parameterNameSelectOptions`, `selectedParameterName`, and `selectParameterName()`.

- [ ] **Step 1: Replace parameter name input and select**

Locate the `<div class="form-floating">` block that contains the parameter name `<input>` and the conditional `<select>` (lines ~26-43 in the current file). Replace it with:

```html
          <div class="form-floating">
            <div class="input-group">
              <input class="form-control" type="text" placeholder="_#(Parameter Name)"
                [(ngModel)]="model.name" formControlName="name"
                [class.is-invalid]="!form.controls['name'].valid"/>
              @if (parameterNameSelectOptions.length > 0) {
                <custom-select class="parameter-name-picker"
                               [options]="parameterNameSelectOptions"
                               [ngModel]="selectedParameterName"
                               [ngModelOptions]="{standalone: true}"
                               (selectionChange)="selectParameterName($event)"
                               ariaLabel="_#(Parameter Name)"
                               placeholder="_#(Select)">
                </custom-select>
              }
            </div>
            <label>_#(Parameter Name)</label>
            @if (form && form.controls['name'].errors
              && form.controls['name'].errors['required']) {
              <span class="invalid-feedback">_#(parameter.name.emptyValid)</span>
            }
            @if (form && form.controls['name'].errors
              && form.controls['name'].errors['variableSpecialCharacters']) {
              <span class="invalid-feedback">_#(parameter.name.characterValid)
              </span>
            }
          </div>
```

- [ ] **Step 2: Verify CustomSelectComponent is in imports[]**

In `add-parameter-dialog.component.ts`, confirm `CustomSelectComponent` is in `imports[]`.

- [ ] **Step 3: Commit**

```bash
git add web/projects/portal/src/app/portal/schedule/schedule-task-editor/add-parameter-dialog/add-parameter-dialog.component.html
git commit -m "Replace native parameter-name select with custom-select in add-parameter-dialog"
```

---

## Task 7: Fix `sql-query-join-dialog.component.html` — 2 column selects

**Files:**
- Modify: `web/projects/portal/src/app/widget/dialog/sql-query-dialog/sql-query-join-dialog.component.html`

The TypeScript already has `column1SelectOptions` and `column2SelectOptions` getters. Two `<select>` elements must be replaced.

- [ ] **Step 1: Replace columns1 native select**

Locate:
```html
        <select class="form-control" [ngModel]="columns1?.indexOf(tempColumn1)" (ngModelChange)="tempColumn1 = columns1[$event]; validate()" [disabled]="columns1 == null">
```
Replace the entire `<select>...</select>` block with:
```html
        <custom-select [options]="column1SelectOptions"
                       [ngModel]="tempColumn1"
                       (selectionChange)="tempColumn1 = $event; validate()"
                       [disabled]="columns1 == null">
        </custom-select>
```

- [ ] **Step 2: Replace columns2 native select**

Locate:
```html
        <select class="form-control" [ngModel]="columns2?.indexOf(tempColumn2)" (ngModelChange)="tempColumn2 = columns2[$event]; validate()" [disabled]="columns2 == null">
```
Replace the entire `<select>...</select>` block with:
```html
        <custom-select [options]="column2SelectOptions"
                       [ngModel]="tempColumn2"
                       (selectionChange)="tempColumn2 = $event; validate()"
                       [disabled]="columns2 == null">
        </custom-select>
```

- [ ] **Step 3: Verify no index-based ngModel bindings remain for columns**

```bash
grep -n "columns1\?.indexOf\|columns2\?.indexOf" web/projects/portal/src/app/widget/dialog/sql-query-dialog/sql-query-join-dialog.component.html
```
Expected output: empty (no results)

- [ ] **Step 4: Commit**

```bash
git add web/projects/portal/src/app/widget/dialog/sql-query-dialog/sql-query-join-dialog.component.html
git commit -m "Replace index-based column selects with custom-select in sql-query-join-dialog"
```

---

## Task 8: Fix missing spec tests

**Files:**
- Modify: `web/projects/portal/src/app/graph/objects/chart-plot-area.component.spec.ts`
- Modify: `web/projects/portal/src/app/widget/color-picker/color-picker.spec.ts`

### chart-plot-area.component.spec.ts — 2 missing tests (Bug #75091)

These tests verify that snap state is cleared when `chartObject` is replaced.

- [ ] **Step 1: Add the missing import**

In `chart-plot-area.component.spec.ts`, add `ChartTool` to the imports if not present:
```typescript
import { ChartTool } from "../model/chart-tool";
```

- [ ] **Step 2: Add the 2 missing test cases**

Inside the `describe` block that contains the existing "should have valid regions" test, add the following two tests after the existing test. Use `vi.spyOn` (Vitest), not `jest.spyOn`:

```typescript
it("clears stale snap state when the Plot reference is replaced", () => {
   const fixture = TestBed.createComponent(TestApp);
   const debugEl = fixture.debugElement.query(By.css("chart-plot-area"));
   const component: ChartPlotArea = debugEl.componentInstance;
   vi.spyOn(component, "getSrc").mockImplementation(() => "");
   fixture.detectChanges();
   const oldPlot = component.chartObject;
   // Pretend a prior hover seeded the snap cache and a prior click left
   // a selection that still references the now-stale Plot.
   (component as any).snapXTicksFor = oldPlot;
   component.chartSelection = {
      chartObject: oldPlot,
      regions: [oldPlot.regions[0]]
   } as any;
   const clearSnapSpy = vi.spyOn(component as any, "clearSnapGuideline");
   const drawRegionsSpy = vi.spyOn(ChartTool, "drawRegions");
   // Swap in a new Plot reference (chart-type rebuild / data refresh).
   const newPlot: Plot = { ...oldPlot } as Plot;
   component.chartObject = newPlot;
   expect(clearSnapSpy).toHaveBeenCalled();
   expect((component as any).snapXTicksFor).toBeNull();
   // Stale-selection branch must not paint synchronously.
   expect(drawRegionsSpy).not.toHaveBeenCalled();
   drawRegionsSpy.mockRestore();
});

it("leaves snap state untouched when updateChartObject is called with no oldObj", () => {
   const fixture = TestBed.createComponent(TestApp);
   const debugEl = fixture.debugElement.query(By.css("chart-plot-area"));
   const component: ChartPlotArea = debugEl.componentInstance;
   vi.spyOn(component, "getSrc").mockImplementation(() => "");
   fixture.detectChanges();
   (component as any).snapXTicksFor = component.chartObject;
   const clearSnapSpy = vi.spyOn(component as any, "clearSnapGuideline");
   // Scroll-debounce path: updateChartObject() is called with no argument.
   component.updateChartObject();
   expect(clearSnapSpy).not.toHaveBeenCalled();
   expect((component as any).snapXTicksFor).toBe(component.chartObject);
});
```

- [ ] **Step 3: Run these tests to verify they pass**

```bash
cd web && npx vitest run projects/portal/src/app/graph/objects/chart-plot-area.component.spec.ts
```
Expected: 3 tests pass (the original "should have valid regions" + the 2 new ones).

### color-picker.spec.ts — 1 missing test

- [ ] **Step 4: Verify fakeAsync and tick are imported**

In `color-picker.spec.ts`, confirm the import line includes `fakeAsync` and `tick`:
```typescript
import { ComponentFixture, fakeAsync, TestBed, tick } from "@angular/core/testing";
```

- [ ] **Step 5: Add the missing focus-restore test**

Inside the `describe` block in `color-picker.spec.ts`, add:

```typescript
it("restores focus to the trigger after selecting a color", fakeAsync(() => {
   fixture.detectChanges();
   const trigger = fixture.debugElement.query(By.css(".color-picker button")).nativeElement as HTMLButtonElement;
   trigger.click();
   fixture.detectChanges();
   tick();
   fixture.detectChanges();
   const swatch = document.querySelector(".color-picker-palette-row:not(.color-picker-recent) .color-picker-swatch") as HTMLButtonElement;
   expect(swatch).toBeTruthy();
   swatch.dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
   fixture.detectChanges();
   tick();
   fixture.detectChanges();
   expect(document.activeElement).toBe(trigger);
}));
```

- [ ] **Step 6: Run the color-picker tests**

```bash
cd web && npx vitest run projects/portal/src/app/widget/color-picker/color-picker.spec.ts
```
Expected: all tests pass (including the new one).

- [ ] **Step 7: Commit**

```bash
git add web/projects/portal/src/app/graph/objects/chart-plot-area.component.spec.ts
git add web/projects/portal/src/app/widget/color-picker/color-picker.spec.ts
git commit -m "Restore 3 missing spec tests lost in 563ab05dd merge"
```

---

## Task 9: Audit all 634 conflict-zone files for additional losses

Run a workflow to analyze all remaining files in the conflict zone. Tasks 1–8 cover the known losses; this task finds anything else.

- [ ] **Step 1: Save the conflict zone file lists**

> ⚠️ **Updated after post-mortem** — use the full EPIC diff, not just the intersection with MAIN.
> The original `comm -12` approach excluded files changed only on the EPIC side, which the merge
> resolver could still have reverted. See the design spec post-mortem for full details.

From `community/`:
```bash
BASE=298d23f34; EPIC=19387c26c; MAIN=4e1a6d45e
# Audit ALL files EPIC changed (not just the intersection with MAIN)
git diff --name-only $BASE..$EPIC | grep -E '\.(html|ts)$' | grep 'web/' | sort > /tmp/epic_files.txt
wc -l /tmp/epic_files.txt
```

- [ ] **Step 2: Run the audit workflow**

Launch the workflow defined below. It fans out one agent per batch of 20 files to analyze the three-way diff (BASE → EPIC vs current HEAD) and report losses.

```javascript
export const meta = {
  name: 'epic-74519-audit',
  description: 'Audit 634 conflict-zone files for lost epic-74519 changes',
  phases: [
    { title: 'Audit', detail: 'Three-way diff: what epic-74519 added that HEAD lost' },
    { title: 'Fix', detail: 'Re-apply confirmed losses' },
  ],
}

const BASE = '298d23f34';
const EPIC = '19387c26c';

// Read the file list (generated in Step 1)
const fileListResult = await agent(
  'Run: cat /tmp/conflict_zone.txt and return all lines as a JSON array of strings.',
  { schema: { type: 'object', properties: { files: { type: 'array', items: { type: 'string' } } }, required: ['files'] } }
);
const allFiles = fileListResult.files;

// Batch into groups of 20
const batches = [];
for(let i = 0; i < allFiles.length; i += 20) {
  batches.push(allFiles.slice(i, i + 20));
}
log(`Auditing ${allFiles.length} files in ${batches.length} batches of 20`);

const LOSS_SCHEMA = {
  type: 'object',
  properties: {
    losses: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          file: { type: 'string' },
          description: { type: 'string' },
          category: { type: 'string', enum: ['html_missing_element', 'ts_missing_field', 'spec_missing_test', 'other'] },
          epicContent: { type: 'string' },
          insertionContext: { type: 'string' }
        },
        required: ['file', 'description', 'category', 'epicContent', 'insertionContext']
      }
    }
  },
  required: ['losses']
};

const auditResults = await pipeline(
  batches,
  (batch, _orig, idx) => agent(
    `You are auditing Angular frontend files for lost merge changes. Your default assumption is
that losses EXIST — your job is to find them, not explain them away.

Working directory: /home/jasonshobe/work/stylebi/community

Reference commits:
- BASE (merge base): ${BASE}
- EPIC (epic-74519 tip before merge): ${EPIC}
- HEAD: current working tree

For each file in this batch:

1. Run: git show ${EPIC}:<file> to get the EPIC version
2. Run: git show ${BASE}:<file> to get the BASE version
3. Read the current HEAD file from disk
4. Skip the file entirely if EPIC == BASE (epic made no changes)

5. Use a PATTERN-COUNT approach to detect losses:
   - Count occurrences of each design pattern in EPIC: shell-form-group, shell-form-row,
     custom-select, number-stepper, landing-card, landing-shell, enterClick, form-row-float-label
   - Count the same patterns in HEAD
   - If HEAD count < EPIC count for ANY pattern, that is a confirmed loss — report it
   - Also diff EPIC vs BASE to find any additions not covered by the pattern list

6. For each loss, verify it is not an intentional removal by checking:
   - NgModule declarations removed for standalone migration → skip
   - jest.* converted to vi.* → skip
   - *ngIf/*ngFor syntax replaced by @if/@for (same logic, different syntax) → skip
   NOTE: These are HUNK-level filters. Do not skip an entire file because it has some
   Angular migration changes — look for non-syntax losses in the same file.

7. Mark a file CLEAN only if:
   - Pattern counts match AND
   - A spot-check of 3 EPIC-specific additions confirms they are present in HEAD

Files to audit (batch of ${batch.length}):
${batch.join('\n')}`,
    { label: `audit-batch-${idx}`, phase: 'Audit', schema: LOSS_SCHEMA }
  )
);

const allLosses = auditResults.filter(Boolean).flatMap(r => r.losses);
log(`Audit complete. Found ${allLosses.length} losses across ${allFiles.length} files.`);

if(allLosses.length === 0) {
  return { message: 'No additional losses found. Proceed to Task 10 (verification).' };
}

// Fix phase: one agent per file with losses (grouped by file)
const byFile = {};
for(const loss of allLosses) {
  if(!byFile[loss.file]) byFile[loss.file] = [];
  byFile[loss.file].push(loss);
}

phase('Fix');
const fixResults = await pipeline(
  Object.entries(byFile),
  ([file, losses]) => agent(
    `Fix the following lost changes in: ${file}
Working directory: /home/jasonshobe/work/stylebi/community

Losses to apply:
${losses.map((l, i) => `${i+1}. ${l.description}\n   Content: ${l.epicContent}\n   Insert after: ${l.insertionContext}`).join('\n\n')}

Rules:
- Use @if/@for/@switch instead of *ngIf/*ngFor/*ngSwitch
- No NgModules — add components to the consuming @Component imports[] array
- Use vi.spyOn/vi.fn in spec files, not jest.*
- Do NOT change unrelated code
- After editing, run: cd community/web && npx vitest run <spec-file> if the file is a .spec.ts

Read the current file first, then apply each loss.`,
    { label: `fix:${file}`, phase: 'Fix' }
  )
);

return {
  audited: allFiles.length,
  lossesFound: allLosses.length,
  filesFixed: Object.keys(byFile).length
};
```

- [ ] **Step 3: Review workflow results**

After the workflow completes, check its output for any files that the fix agents could not handle (e.g., "could not locate insertion context"). Manually apply those fixes by reading the EPIC version (`git show 19387c26c:<file>`) and the current file, then applying the diff.

- [ ] **Step 4: Commit all workflow-applied fixes**

```bash
git add -p   # Review each change before staging
git commit -m "Apply additional merge-loss fixes discovered by audit workflow"
```

---

## Task 10: Full verification

- [ ] **Step 1: Build**

```bash
cd web && npm run build
```
Expected: exits 0 with no errors. If any error appears, fix the specific file before proceeding.

- [ ] **Step 2: Lint**

```bash
cd web && npm run lint
```
Expected: exits 0 with no errors.

- [ ] **Step 3: Run all tests**

```bash
cd web && npm run test
```
Expected: all tests pass with 0 failures.

---

## Task 11: Fix verification failures

Only needed if Task 10 has failures. For each failure:

- [ ] **Step 1: Identify the failing file and error**

Read the build/lint/test output carefully. Note the exact file and error message.

- [ ] **Step 2: Diagnose root cause**

Common causes:
- **Build error "Component X is not in imports[]"**: The HTML references `<custom-select>` or another component but the consuming `.component.ts` is missing `CustomSelectComponent` (or other) in its `imports[]` array. Add it.
- **Build error "Property X does not exist on type Y"**: A model field was restored in the interface but the corresponding TypeScript code was not updated. Check the EPIC version of the `.ts` file: `git show 19387c26c:<file>`.
- **Lint error "no-explicit-any"**: Replace `as any` casts with proper types where possible.
- **Test failure "Cannot find vi.spyOn"**: Ensure the test file does NOT import `jest` and DOES rely on Vitest globals from `tsconfig` (`vitest/globals`).
- **Test failure after adding template**: An `ng-template` was added to an HTML file but the component's `ngOnInit` or constructor references a variable only initialized in that template. Check the full EPIC component TS for related initialization.

- [ ] **Step 3: Apply the targeted fix**

Edit only the file(s) identified in Step 1. Do not refactor unrelated code.

- [ ] **Step 4: Re-run the failing check**

```bash
cd web && npm run build   # or npm run lint, or npx vitest run <specific-spec-file>
```

- [ ] **Step 5: Commit the fix**

```bash
git add <changed files>
git commit -m "Fix <specific error> after merge-loss recovery"
```

- [ ] **Step 6: Run the full suite once more**

```bash
cd web && npm run test
```
Expected: 0 failures.
