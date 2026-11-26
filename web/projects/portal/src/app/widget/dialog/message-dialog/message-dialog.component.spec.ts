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
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { MessageDialog } from "./message-dialog.component";

describe("MessageDialog", () => {
   let fixture: ComponentFixture<MessageDialog>;

   beforeEach(() => {
      TestBed.configureTestingModule({ imports: [MessageDialog] });
      fixture = TestBed.createComponent(MessageDialog);
      fixture.componentInstance.title = "Confirm";
      fixture.componentInstance.message = "Restore?";
      fixture.componentInstance.options = [
         { label: "Yes", value: "yes" } as any,
         { label: "No", value: "no" } as any
      ];
   });

   afterEach(() => vi.restoreAllMocks());

   it("focuses the default button without showing keyboard focus", () => {
      const focus = vi.spyOn(HTMLElement.prototype, "focus");
      document.body.appendChild(fixture.nativeElement);
      fixture.detectChanges();

      const buttons = fixture.nativeElement.querySelectorAll("button");
      expect(buttons.length).toBe(2);
      expect(document.activeElement).toBe(buttons[0]);
      expect(focus).toHaveBeenCalledTimes(1);
      expect(focus.mock.instances[0]).toBe(buttons[0]);
      expect(focus).toHaveBeenCalledWith({ focusVisible: false });

      fixture.nativeElement.remove();
   });
});
