/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

/**
 * NotificationEmailsComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2]  — emails setter: guard `!!this.emails || !!val` — both-falsy is a no-op
 *   Group 2 [Risk 2]  — fireNotificationsChanged(): whitespace stripping + valid emission logic
 *   Group 3 [Risk 2]  — ngOnInit: emailControl sync + valueChanges subscription
 *
 * KEY contracts:
 *   - emails setter skips assignment when both `_emails` and incoming `val` are falsy.
 *   - emails setter applies when either current or incoming is truthy (preserves clear-to-empty
 *     when _emails was non-empty).
 *   - fireNotificationsChanged() strips all internal whitespace from emails before emitting.
 *   - valid emitted = `!this.enabled || (emailControl.value != null && emailControl.value !== 0
 *     && emailControl.valid)`.
 *   - ngOnInit subscribes emailControl.valueChanges → fireNotificationsChanged() on each change.
 */

import { Component, forwardRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR, ReactiveFormsModule } from "@angular/forms";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { render } from "@testing-library/angular";

import { NotificationEmailsComponent, NotificationEmails } from "./notification-emails.component";

// ---------------------------------------------------------------------------
// Stubs
// ---------------------------------------------------------------------------

// em-email-picker uses [formControl] and must implement ControlValueAccessor
// so Angular's FormControlDirective can find a value accessor.
@Component({
   selector: "em-email-picker",
   template: "",
   providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => EmailPickerStub), multi: true }],
})
class EmailPickerStub implements ControlValueAccessor {
   writeValue() {}
   registerOnChange() {}
   registerOnTouched() {}
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComp(props: Partial<{
   enabled: boolean;
   emails: string;
   notifyIfFailed: boolean;
   notifyLink: boolean;
}> = {}) {
   const result = await render(NotificationEmailsComponent, {
      imports: [FormsModule, ReactiveFormsModule, NoopAnimationsModule, MatCheckboxModule],
      declarations: [EmailPickerStub],
      schemas: [NO_ERRORS_SCHEMA],
      componentProperties: {
         enabled: props.enabled ?? false,
         emails: props.emails ?? "",
         notifyIfFailed: props.notifyIfFailed ?? false,
         notifyLink: props.notifyLink ?? false,
      },
   });

   result.fixture.detectChanges();
   await result.fixture.whenStable();

   return { comp: result.fixture.componentInstance as NotificationEmailsComponent };
}

// ════════════════════════════════════════════════════════════════════════════
// Group 1 [Risk 2] — emails setter: guard behavior
// ════════════════════════════════════════════════════════════════════════════

describe("NotificationEmailsComponent — emails setter: guard condition `!!this.emails || !!val`", () => {

   // Risk Point/Contract: both current (_emails="") and incoming ("") are falsy → setter is a
   // no-op. The emailControl is not updated, preserving its existing state (empty).
   it("should be a no-op when both current emails and incoming val are empty strings", async () => {
      const { comp } = await renderComp({ emails: "" });

      const ctrlSpy = vi.spyOn(comp.emailControl, "setValue");
      comp.emails = ""; // both _emails="" and val="" → both falsy → skip

      expect(ctrlSpy).not.toHaveBeenCalled();
      expect(comp.emails).toBe("");
   });

   // 🔁 Regression-sensitive: incoming non-empty value must be applied even when _emails
   // is still at its initial empty value. Without this, the first @Input() email binding
   // from a parent would be silently dropped.
   it("should apply incoming non-empty value when current _emails is empty", async () => {
      const { comp } = await renderComp({ emails: "" });

      comp.emails = "user@example.com";

      expect(comp.emails).toBe("user@example.com");
      expect(comp.emailControl.value).toBe("user@example.com");
   });

   // Risk Point/Contract: setting emails to "" after it was non-empty must apply (clear the control)
   // because `!!this.emails` = `!!"old@test.com"` = true → the setter runs.
   it("should clear _emails and the emailControl when val is '' and current _emails is non-empty", async () => {
      const { comp } = await renderComp({ emails: "" });

      comp.emails = "original@test.com"; // set non-empty first
      comp.emails = "";                   // now clear: !!this.emails=true → runs

      expect(comp.emails).toBe("");
      expect(comp.emailControl.value).toBe("");
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 2 [Risk 2] — fireNotificationsChanged(): whitespace stripping + valid
// ════════════════════════════════════════════════════════════════════════════

describe("NotificationEmailsComponent — fireNotificationsChanged(): emit content", () => {

   // 🔁 Regression-sensitive: whitespace (including newlines and tabs) in email addresses must
   // be stripped before emission. Backend typically splits on commas and rejects addresses
   // containing whitespace.
   it("should strip all whitespace from emails before emitting", async () => {
      const { comp } = await renderComp();
      const emitted: NotificationEmails[] = [];
      comp.notificationsChanged.subscribe(v => emitted.push(v));

      comp.emailControl.setValue("a @example.com, b @test.com");

      expect(emitted.length).toBeGreaterThan(0);
      const last = emitted[emitted.length - 1];
      expect(last.emails).toBe("a@example.com,b@test.com");
   });

   // Risk Point/Contract: when enabled=false, valid is always true regardless of emailControl state
   // (no email required if notifications are disabled).
   it("should emit valid=true when enabled is false, even when emailControl is empty", async () => {
      const { comp } = await renderComp({ enabled: false });
      const emitted: NotificationEmails[] = [];
      comp.notificationsChanged.subscribe(v => emitted.push(v));

      comp.emailControl.setValue("");
      comp.fireNotificationsChanged();

      expect(emitted[emitted.length - 1].valid).toBe(true);
   });

   // Risk Point/Contract: when enabled=true, valid requires emailControl.value to be non-null
   // and emailControl.valid to be true. An empty string control makes valid=false.
   it("should emit valid=false when enabled is true and emailControl value is null", async () => {
      const { comp } = await renderComp({ enabled: true });
      const emitted: NotificationEmails[] = [];
      comp.notificationsChanged.subscribe(v => emitted.push(v));

      comp.emailControl.setValue(null);
      comp.fireNotificationsChanged();

      const last = emitted[emitted.length - 1];
      expect(last.valid).toBe(false);
      expect(last.enabled).toBe(true);
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 3 [Risk 2] — ngOnInit: emailControl sync + valueChanges subscription
// ════════════════════════════════════════════════════════════════════════════

describe("NotificationEmailsComponent — ngOnInit: control sync and subscription", () => {

   // 🔁 Regression-sensitive: if emails is set before ngOnInit, the emailControl must be
   // pre-populated so it reflects the initial value when rendered.
   it("should sync emailControl to the emails @Input() value during ngOnInit", async () => {
      const { comp } = await renderComp({ emails: "init@test.com" });

      expect(comp.emailControl.value).toBe("init@test.com");
   });

   // Risk Point/Contract: valueChanges subscription must call fireNotificationsChanged() on
   // every control change. If the subscription is missing, the parent never learns the email
   // address was updated.
   it("should emit notificationsChanged when emailControl value changes after init", async () => {
      const { comp } = await renderComp();
      const emitted: NotificationEmails[] = [];
      comp.notificationsChanged.subscribe(v => emitted.push(v));

      comp.emailControl.setValue("new@example.com");

      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted[emitted.length - 1].emails).toBe("new@example.com");
   });

});
