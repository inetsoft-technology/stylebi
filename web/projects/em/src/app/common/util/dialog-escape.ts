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
import { ESCAPE, hasModifierKey } from "@angular/cdk/keycodes";
import { MatDialogRef } from "@angular/material/dialog";
import { Subscription } from "rxjs";
import { filter } from "rxjs/operators";

/**
 * Calls handler when Escape is pressed in the dialog, for a dialog opened with disableClose.
 *
 * Bug #78722: the overlay keyboard dispatcher gives an Escape keydown only to the topmost
 * overlay (an open select list, menu, autocomplete or inner dialog), so the dialog's own keydown
 * stream sees nothing while one is open. A window keyup listener fires for every stacked dialog
 * instead.
 */
export function onDialogEscape(dialogRef: MatDialogRef<unknown>,
                               handler: () => void): Subscription
{
   return dialogRef.keydownEvents()
      .pipe(filter(event => event.keyCode === ESCAPE && !hasModifierKey(event) &&
         !event.isComposing))
      .subscribe(() => handler());
}
