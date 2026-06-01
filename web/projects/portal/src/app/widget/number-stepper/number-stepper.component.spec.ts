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
import { Component } from "@angular/core";
import { ComponentFixture, fakeAsync, flushMicrotasks, TestBed, tick } from "@angular/core/testing";
import { FormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { NumberStepperComponent } from "./number-stepper.component";

@Component({
   template: `
      <number-stepper [(ngModel)]="value" [min]="min" [max]="max" [step]="step">
      </number-stepper>
   `,
})
class TestHostComponent {
   value: number = 5;
   min: number = null;
   max: number = null;
   step: number = 1;
}

function getButtons(fixture: ComponentFixture<TestHostComponent>): HTMLButtonElement[] {
   return fixture.debugElement.queryAll(By.css(".ns-btn")).map(de => de.nativeElement);
}

function getInput(fixture: ComponentFixture<TestHostComponent>): HTMLInputElement {
   return fixture.debugElement.query(By.css("input")).nativeElement;
}

describe("NumberStepperComponent", () => {
   let fixture: ComponentFixture<TestHostComponent>;
   let host: TestHostComponent;

   beforeEach(async () => {
      await TestBed.configureTestingModule({
         imports: [FormsModule],
         declarations: [TestHostComponent, NumberStepperComponent],
      }).compileComponents();

      fixture = TestBed.createComponent(TestHostComponent);
      host = fixture.componentInstance;
      fixture.detectChanges();
   });

   it("renders the input and two buttons", () => {
      const buttons = getButtons(fixture);
      const input = getInput(fixture);
      expect(buttons.length).toBe(2);
      expect(input).toBeTruthy();
   });

   it("shows the bound value in the input", fakeAsync(() => {
      tick();
      fixture.detectChanges();
      expect(getInput(fixture).value).toBe("5");
   }));

   it("increments value when + is clicked", fakeAsync(() => {
      tick();
      fixture.detectChanges();
      const [, increment] = getButtons(fixture);
      increment.click();
      tick();
      fixture.detectChanges();
      expect(host.value).toBe(6);
   }));

   it("decrements value when − is clicked", fakeAsync(() => {
      tick();
      fixture.detectChanges();
      const [decrement] = getButtons(fixture);
      decrement.click();
      tick();
      fixture.detectChanges();
      expect(host.value).toBe(4);
   }));

   it("does not go below min", fakeAsync(() => {
      const stepperEl = fixture.debugElement.query(By.directive(NumberStepperComponent));
      const stepperInstance = stepperEl.componentInstance as NumberStepperComponent;
      host.min = 0;
      stepperInstance.writeValue(0);
      tick();
      fixture.detectChanges();
      expect(stepperInstance.isDecrementDisabled).toBe(true);
      const [decrement] = getButtons(fixture);
      decrement.click();
      tick();
      fixture.detectChanges();
      expect(stepperInstance.value).toBe(0);
   }));

   it("does not go above max", fakeAsync(() => {
      const stepperEl = fixture.debugElement.query(By.directive(NumberStepperComponent));
      const stepperInstance = stepperEl.componentInstance as NumberStepperComponent;
      host.max = 10;
      stepperInstance.writeValue(10);
      tick();
      fixture.detectChanges();
      expect(stepperInstance.isIncrementDisabled).toBe(true);
      const [, increment] = getButtons(fixture);
      increment.click();
      tick();
      fixture.detectChanges();
      expect(stepperInstance.value).toBe(10);
   }));

   it("restores last valid value on blur with NaN input", fakeAsync(() => {
      tick();
      fixture.detectChanges();
      const input = getInput(fixture);
      const stepper = fixture.debugElement.query(By.directive(NumberStepperComponent)).componentInstance as NumberStepperComponent;
      stepper.displayValue = "abc";
      input.dispatchEvent(new Event("blur"));
      fixture.detectChanges();
      expect(stepper.displayValue).toBe("5");
   }));

   it("clamps typed value to max on blur", fakeAsync(() => {
      host.max = 10;
      fixture.detectChanges();
      tick();
      fixture.detectChanges();
      const input = getInput(fixture);
      const stepper = fixture.debugElement.query(By.directive(NumberStepperComponent)).componentInstance as NumberStepperComponent;
      stepper.displayValue = "999";
      input.dispatchEvent(new Event("blur"));
      fixture.detectChanges();
      expect(host.value).toBe(10);
   }));

   it("increments by step×10 on Shift+ArrowUp", fakeAsync(() => {
      host.step = 1;
      fixture.detectChanges();
      tick();
      fixture.detectChanges();
      const input = getInput(fixture);
      input.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowUp", shiftKey: true, bubbles: true }));
      tick();
      fixture.detectChanges();
      expect(host.value).toBe(15);
   }));

   it("jumps to min on Home key", fakeAsync(() => {
      host.min = 0;
      host.value = 5;
      fixture.detectChanges();
      tick();
      fixture.detectChanges();
      const input = getInput(fixture);
      input.dispatchEvent(new KeyboardEvent("keydown", { key: "Home", bubbles: true }));
      tick();
      fixture.detectChanges();
      expect(host.value).toBe(0);
   }));

   it("jumps to max on End key", fakeAsync(() => {
      host.max = 100;
      host.value = 5;
      fixture.detectChanges();
      tick();
      fixture.detectChanges();
      const input = getInput(fixture);
      input.dispatchEvent(new KeyboardEvent("keydown", { key: "End", bubbles: true }));
      tick();
      fixture.detectChanges();
      expect(host.value).toBe(100);
   }));
});
