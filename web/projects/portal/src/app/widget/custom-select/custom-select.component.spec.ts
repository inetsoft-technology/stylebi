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
import { ComponentFixture, fakeAsync, TestBed, tick } from "@angular/core/testing";
import { FormsModule } from "@angular/forms";
import { By } from "@angular/platform-browser";
import { DropDownTestModule } from "../../common/test/test-module";
import { CustomSelectComponent, CustomSelectOption } from "./custom-select.component";

// ---- Test host components ----

@Component({
   template: `<custom-select [options]="options" [(ngModel)]="value"></custom-select>`
})
class TestHostComponent {
   value = "one";
   options: CustomSelectOption<string>[] = [
      { label: "One", value: "one" },
      { label: "Two", value: "two" },
   ];
}

@Component({
   template: `<custom-select [options]="options" [(ngModel)]="value" placeholder="Choose one"></custom-select>`
})
class PlaceholderHostComponent {
   value: string = null;
   options: CustomSelectOption<string>[] = [
      { label: "One", value: "one" },
      { label: "Two", value: "two" },
   ];
}

@Component({
   template: `<custom-select [options]="options" [(ngModel)]="value" [disabled]="true"></custom-select>`
})
class DisabledHostComponent {
   value: string = null;
   options: CustomSelectOption<string>[] = [
      { label: "One", value: "one" },
      { label: "Two", value: "two" },
   ];
}

@Component({
   template: `<custom-select [options]="options" [(ngModel)]="value" [closeOnSelect]="false"></custom-select>`
})
class KeepOpenHostComponent {
   value: string = null;
   options: CustomSelectOption<string>[] = [
      { label: "One", value: "one" },
      { label: "Two", value: "two" },
   ];
}

// ---- Shared helpers ----

function openDropdown(f: ComponentFixture<any>): void {
   const trigger: HTMLButtonElement = f.debugElement.query(By.css(".custom-select-trigger")).nativeElement;
   trigger.click();
   f.detectChanges();
   tick();
   f.detectChanges();
}

function getOptions(): NodeListOf<HTMLButtonElement> {
   return document.querySelectorAll<HTMLButtonElement>(".custom-select-option");
}

// ---- Specs ----

describe("CustomSelectComponent", () => {
   beforeEach(async () => {
      await TestBed.configureTestingModule({
         imports: [FormsModule, DropDownTestModule],
         declarations: [
            TestHostComponent,
            PlaceholderHostComponent,
            DisabledHostComponent,
            KeepOpenHostComponent,
         ],
      }).compileComponents();
   });

   // ---- existing smoke test ----

   describe("basic click-to-select", () => {
      let fixture: ComponentFixture<TestHostComponent>;

      beforeEach(() => {
         fixture = TestBed.createComponent(TestHostComponent);
         fixture.detectChanges();
      });

      it("updates the value when an option is clicked", fakeAsync(() => {
         const trigger = fixture.debugElement.query(By.css(".custom-select-trigger")).nativeElement;

         trigger.click();
         fixture.detectChanges();
         tick();
         fixture.detectChanges();

         const options = document.querySelectorAll<HTMLButtonElement>(".custom-select-option");
         expect(options.length).toBe(2);

         options.item(1).dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
         fixture.detectChanges();
         tick();
         fixture.detectChanges();

         expect(fixture.componentInstance.value).toBe("two");
      }));
   });

   // ---- placeholder ----

   describe("placeholder", () => {
      let fixture: ComponentFixture<PlaceholderHostComponent>;

      beforeEach(() => {
         fixture = TestBed.createComponent(PlaceholderHostComponent);
         fixture.detectChanges();
      });

      it("shows placeholder text when no value is selected", () => {
         const triggerText = fixture.debugElement.query(By.css(".custom-select-trigger__text")).nativeElement;
         expect(triggerText.textContent.trim()).toBe("Choose one");
      });

      it("shows the selected label once a value is set", fakeAsync(() => {
         fixture.componentInstance.value = "two";
         fixture.detectChanges();
         tick();
         fixture.detectChanges();

         const triggerText = fixture.debugElement.query(By.css(".custom-select-trigger__text")).nativeElement;
         expect(triggerText.textContent.trim()).toBe("Two");
      }));
   });

   // ---- disabled state ----

   describe("disabled state", () => {
      let fixture: ComponentFixture<DisabledHostComponent>;
      let selectEl: HTMLElement;
      let trigger: HTMLButtonElement;

      beforeEach(() => {
         fixture = TestBed.createComponent(DisabledHostComponent);
         fixture.detectChanges();
         selectEl = fixture.debugElement.query(By.directive(CustomSelectComponent)).nativeElement;
         trigger = fixture.debugElement.query(By.css(".custom-select-trigger")).nativeElement;
      });

      it("adds the is-disabled class to the host element", () => {
         expect(selectEl.classList).toContain("is-disabled");
      });

      it("sets the disabled property on the trigger button", () => {
         expect(trigger.disabled).toBe(true);
      });

      it("does not open the dropdown when the trigger is clicked", fakeAsync(() => {
         trigger.click();
         fixture.detectChanges();
         tick();
         fixture.detectChanges();

         expect(getOptions().length).toBe(0);
      }));
   });

   // ---- keyboard navigation ----

   describe("keyboard navigation", () => {
      let fixture: ComponentFixture<TestHostComponent>;
      let component: CustomSelectComponent;
      let trigger: HTMLButtonElement;

      beforeEach(() => {
         fixture = TestBed.createComponent(TestHostComponent);
         fixture.detectChanges();
         component = fixture.debugElement.query(By.directive(CustomSelectComponent)).componentInstance;
         trigger = fixture.debugElement.query(By.css(".custom-select-trigger")).nativeElement;
      });

      function key(target: Element, k: string): void {
         target.dispatchEvent(new KeyboardEvent("keydown", { key: k, bubbles: true }));
         fixture.detectChanges();
      }

      describe("trigger (dropdown closed)", () => {
         it("ArrowDown opens the dropdown", fakeAsync(() => {
            key(trigger, "ArrowDown");
            tick();
            fixture.detectChanges();
            expect(getOptions().length).toBeGreaterThan(0);
         }));

         it("ArrowUp opens the dropdown", fakeAsync(() => {
            key(trigger, "ArrowUp");
            tick();
            fixture.detectChanges();
            expect(getOptions().length).toBeGreaterThan(0);
         }));

         it("Enter opens the dropdown", fakeAsync(() => {
            key(trigger, "Enter");
            tick();
            fixture.detectChanges();
            expect(getOptions().length).toBeGreaterThan(0);
         }));
      });

      describe("trigger (dropdown open)", () => {
         beforeEach(fakeAsync(() => {
            openDropdown(fixture);
         }));

         it("ArrowDown advances activeIndex to the next option", fakeAsync(() => {
            const before = component.activeIndex; // 0 — "one" is selected
            key(trigger, "ArrowDown");
            tick();
            fixture.detectChanges();
            expect(component.activeIndex).toBe(before + 1);
         }));

         it("ArrowUp wraps activeIndex to the last option", fakeAsync(() => {
            // activeIndex starts at 0; wrapping backwards lands on the last item
            key(trigger, "ArrowUp");
            tick();
            fixture.detectChanges();
            expect(component.activeIndex).toBe(fixture.componentInstance.options.length - 1);
         }));

         it("Enter selects the currently active option", fakeAsync(() => {
            component.setActiveIndex(1);
            fixture.detectChanges();
            key(trigger, "Enter");
            tick();
            fixture.detectChanges();
            expect(fixture.componentInstance.value).toBe("two");
         }));

         it("Escape closes the dropdown", fakeAsync(() => {
            key(trigger, "Escape");
            tick();
            fixture.detectChanges();
            expect(getOptions().length).toBe(0);
         }));
      });

      describe("option buttons (dropdown open)", () => {
         beforeEach(fakeAsync(() => {
            openDropdown(fixture);
         }));

         it("ArrowDown on an option moves to the next option", fakeAsync(() => {
            component.setActiveIndex(0);
            fixture.detectChanges();
            key(getOptions().item(0), "ArrowDown");
            tick();
            fixture.detectChanges();
            expect(component.activeIndex).toBe(1);
         }));

         it("ArrowUp on an option moves to the previous option", fakeAsync(() => {
            component.setActiveIndex(1);
            fixture.detectChanges();
            key(getOptions().item(1), "ArrowUp");
            tick();
            fixture.detectChanges();
            expect(component.activeIndex).toBe(0);
         }));

         it("Home moves activeIndex to the first enabled option", fakeAsync(() => {
            component.setActiveIndex(1);
            fixture.detectChanges();
            key(getOptions().item(1), "Home");
            tick();
            fixture.detectChanges();
            expect(component.activeIndex).toBe(0);
         }));

         it("End moves activeIndex to the last enabled option", fakeAsync(() => {
            component.setActiveIndex(0);
            fixture.detectChanges();
            key(getOptions().item(0), "End");
            tick();
            fixture.detectChanges();
            expect(component.activeIndex).toBe(fixture.componentInstance.options.length - 1);
         }));

         it("Enter selects the option and closes the dropdown", fakeAsync(() => {
            key(getOptions().item(1), "Enter");
            tick();
            fixture.detectChanges();
            expect(fixture.componentInstance.value).toBe("two");
            expect(getOptions().length).toBe(0);
         }));

         it("Escape closes the dropdown without changing the value", fakeAsync(() => {
            key(getOptions().item(1), "Escape");
            tick();
            fixture.detectChanges();
            expect(getOptions().length).toBe(0);
            expect(fixture.componentInstance.value).toBe("one"); // unchanged
         }));

         it("Tab closes the dropdown without changing the value", fakeAsync(() => {
            key(getOptions().item(1), "Tab");
            tick();
            fixture.detectChanges();
            expect(getOptions().length).toBe(0);
            expect(fixture.componentInstance.value).toBe("one"); // unchanged
         }));
      });
   });

   // ---- closeOnSelect: false ----

   describe("closeOnSelect: false", () => {
      let fixture: ComponentFixture<KeepOpenHostComponent>;

      beforeEach(() => {
         fixture = TestBed.createComponent(KeepOpenHostComponent);
         fixture.detectChanges();
      });

      it("keeps the dropdown open after an option is selected", fakeAsync(() => {
         openDropdown(fixture);
         expect(getOptions().length).toBeGreaterThan(0);

         getOptions().item(0).dispatchEvent(new MouseEvent("mousedown", { bubbles: true }));
         fixture.detectChanges();
         tick();
         fixture.detectChanges();

         expect(fixture.componentInstance.value).toBe("one");
         expect(getOptions().length).toBeGreaterThan(0);
      }));
   });
});
