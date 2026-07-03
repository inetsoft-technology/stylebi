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
 * CkeditorWrapperComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — ControlValueAccessor: writeValue dedup, registerOnChange, setDisabledState
 *   Group 2 [Risk 2] — ngAfterViewInit: basic vs advanced toolbar config branches
 *   Group 3 [Risk 2] — fonts/language inputs applied to editor config
 *   Group 4 [Risk 1] — onEditorReady: emit editor instance to parent
 *   Group 5 [Risk 2] — handleEnter: delegate Enter key to editor.execute('enter')
 *
 * HTTP: no HTTP — CKEditor loaded as static asset, no REST calls
 *
 * Out of scope:
 *   registerOnTouched — no observable side effect beyond CVA contract
 *   ngOnDestroy — editor destroy delegated to CKEditor Angular wrapper
 */

import { ComponentFixture } from "@angular/core/testing";
import { By } from "@angular/platform-browser";
import { CKEditorComponent } from "@ckeditor/ckeditor5-angular";
import { fireEvent, render, waitFor } from "@testing-library/angular";
import { CkeditorWrapperComponent } from "./ckeditor-wrapper.component";

function ckeditorInstance(fixture: ComponentFixture<CkeditorWrapperComponent>) {
   return fixture.debugElement.query(By.directive(CKEditorComponent))?.componentInstance as CKEditorComponent | undefined;
}

async function renderWrapper(props: Partial<CkeditorWrapperComponent> = {}) {
   return render(CkeditorWrapperComponent, {
      componentProperties: props
   });
}

describe("CkeditorWrapperComponent — ControlValueAccessor [Group 1, Risk 2]", () => {
   it("should write value and notify onChange only when content changes", async () => {
      const onChange = vi.fn();
      const { fixture } = await renderWrapper();
      fixture.componentInstance.registerOnChange(onChange);

      fixture.componentInstance.writeValue("<p>hello</p>");
      fixture.detectChanges();

      expect(onChange).toHaveBeenCalledWith("<p>hello</p>");

      onChange.mockClear();
      fixture.componentInstance.writeValue("<p>hello</p>");

      expect(onChange).not.toHaveBeenCalled();
   });

   it("should notify onChange when value changes from empty to defined content", async () => {
      const onChange = vi.fn();
      const { fixture } = await renderWrapper();
      fixture.componentInstance.registerOnChange(onChange);

      fixture.componentInstance.writeValue("text");

      expect(onChange).toHaveBeenCalledWith("text");

      onChange.mockClear();
      fixture.componentInstance.writeValue("text");

      expect(onChange).not.toHaveBeenCalled();
   });

   it("should update disabled state via setDisabledState", async () => {
      const { fixture, container } = await renderWrapper();

      await waitFor(() => expect(container.querySelector("ckeditor")).toBeTruthy());

      fixture.componentInstance.setDisabledState(true);
      fixture.detectChanges();

      expect(fixture.componentInstance.disabled).toBe(true);
      expect(ckeditorInstance(fixture)?.disabled).toBe(true);

      fixture.componentInstance.setDisabledState(false);
      fixture.detectChanges();

      expect(fixture.componentInstance.disabled).toBe(false);
      expect(ckeditorInstance(fixture)?.disabled).toBe(false);
   });

   it("should register onTouched callback", async () => {
      const { fixture } = await renderWrapper();
      const onTouched = vi.fn();
      fixture.componentInstance.registerOnTouched(onTouched);

      fixture.componentInstance.onTouched();

      expect(onTouched).toHaveBeenCalled();
   });
});

describe("CkeditorWrapperComponent — ngAfterViewInit config [Group 2, Risk 2]", () => {
   it("should use basic toolbar when advanced is false", async () => {
      const { fixture, container } = await renderWrapper({ advanced: false });

      await waitFor(() => expect(container.querySelector("ckeditor")).toBeTruthy());

      const items = (fixture.componentInstance.config.toolbar as { items: string[] }).items;
      expect(items).not.toContain("link");
      expect(items).not.toContain("insertImage");
   });

   it("should include link and insertImage in advanced toolbar", async () => {
      const { fixture, container } = await renderWrapper({ advanced: true });

      await waitFor(() => expect(container.querySelector("ckeditor")).toBeTruthy());

      const items = (fixture.componentInstance.config.toolbar as { items: string[] }).items;
      expect(items).toContain("link");
      expect(items).toContain("insertImage");
   });
});

describe("CkeditorWrapperComponent — fonts and language [Group 3, Risk 2]", () => {
   it("should restrict fontFamily options when fonts input is provided", async () => {
      const { fixture, container } = await renderWrapper({ fonts: ["Arial", "Helvetica"] });

      await waitFor(() => expect(container.querySelector(".editor-container")).toBeTruthy());
      expect(fixture.componentInstance.config.fontFamily?.options).toEqual(["default", "Arial", "Helvetica"]);
      expect(fixture.componentInstance.config.fontFamily?.supportAllValues).toBe(false);
   });

   it("should set config.language when language input is provided", async () => {
      const { fixture, container } = await renderWrapper({ language: "de" });

      await waitFor(() => expect(container.querySelector(".editor-container")).toBeTruthy());
      expect(fixture.componentInstance.config.language).toBe("de");
   });
});

describe("CkeditorWrapperComponent — onEditorReady [Group 4, Risk 1]", () => {
   it("should emit ready with the editor instance", async () => {
      const ready = vi.fn();
      const { fixture } = await renderWrapper();
      fixture.componentInstance.ready.subscribe(ready);
      const editor = { model: { change: vi.fn() }, execute: vi.fn() };

      fixture.componentInstance.onEditorReady(editor as any);

      expect(ready).toHaveBeenCalledWith(editor);
   });
});

describe("CkeditorWrapperComponent — handleEnter [Group 5, Risk 2]", () => {
   it("should prevent default and execute enter on the editor", async () => {
      const execute = vi.fn();
      const changeFn = vi.fn((cb: (writer: unknown) => void) => cb({}));
      const { fixture, container } = await renderWrapper();
      await waitFor(() => expect(container.querySelector("ckeditor")).toBeTruthy());
      fixture.componentInstance.onEditorReady({ model: { change: changeFn }, execute } as any);

      const event = new KeyboardEvent("keydown", { key: "Enter", bubbles: true });
      vi.spyOn(event, "preventDefault");
      fireEvent(container.querySelector("ckeditor")!, event);

      expect(event.preventDefault).toHaveBeenCalled();
      expect(execute).toHaveBeenCalledWith("enter");
   });

   it("should ignore non-Enter keys", async () => {
      const execute = vi.fn();
      const { fixture, container } = await renderWrapper();
      await waitFor(() => expect(container.querySelector("ckeditor")).toBeTruthy());
      fixture.componentInstance.onEditorReady({ model: { change: vi.fn() }, execute } as any);

      const event = new KeyboardEvent("keydown", { key: "Tab", bubbles: true });
      vi.spyOn(event, "preventDefault");
      fireEvent(container.querySelector("ckeditor")!, event);

      expect(event.preventDefault).not.toHaveBeenCalled();
      expect(execute).not.toHaveBeenCalled();
   });
});
