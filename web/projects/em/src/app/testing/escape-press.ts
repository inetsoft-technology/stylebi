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
import { ApplicationRef } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { MatDialogRef } from "@angular/material/dialog";

/**
 * Test helpers for Escape handling in stacked EM dialogs (Bug #78722).
 *
 * A key press is sent the way a browser sends it: the keydown to the element focused when the
 * key goes down, the keyup to the element focused when it is released. Closing an inner overlay
 * on the keydown moves focus back to the dialog below, so the keyup must be sent to the element
 * focused at that point. Sending it to the node that had focus inside the closed overlay would
 * never reach window and would hide the bug.
 */
export interface DialogState {
   closed: boolean;
   result: any;
}

export function trackDialog(ref: MatDialogRef<any>): DialogState {
   const state: DialogState = { closed: false, result: undefined };
   ref.afterClosed().subscribe(result => {
      state.closed = true;
      state.result = result;
   });
   return state;
}

export async function settle(rounds = 6): Promise<void> {
   const app = TestBed.inject(ApplicationRef);

   for(let i = 0; i < rounds; i++) {
      app.tick();
      await new Promise(resolve => setTimeout(resolve, 5));
   }
}

export function focused(): Element {
   return document.activeElement || document.body;
}

export function sendKey(target: EventTarget, type: "keydown" | "keyup",
                        init: KeyboardEventInit & { keyCode?: number } = {}): KeyboardEvent
{
   const keyCode = init.keyCode ?? 27;
   const event = new KeyboardEvent(type, {
      key: "Escape", code: "Escape", bubbles: true, cancelable: true, ...init
   });
   Object.defineProperty(event, "keyCode", { get: () => keyCode });
   target.dispatchEvent(event);
   return event;
}

/** Presses and releases Escape, each half sent to the element focused at that time. */
export async function pressEscape(init: KeyboardEventInit & { keyCode?: number } = {}): Promise<void> {
   sendKey(focused(), "keydown", init);
   await settle();
   sendKey(focused(), "keyup", init);
   await settle();
}
