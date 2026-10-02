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
import { NgZone } from "@angular/core";

/**
 * The latest selection made by the user in a list input (radio button, check box) that
 * has not been acknowledged by the server yet (Bug #76959). While it is pending, a model
 * carrying a different value is stale (e.g. the in-flight refresh of the previous apply)
 * and must not revert the displayed selection.
 *
 * The owning component decides how the pending value is displayed and calls the methods
 * below at the matching points of its apply flow. When the pending selection times out,
 * the release callback is called, which must clear it and show the latest server model.
 */
export class PendingInputSelection<V> {
   // delay before the selection is sent to the server
   static readonly APPLY_DELAY = 500;
   // How long a sent selection is protected after the send or after the last stale model,
   // before the latest server model is shown regardless of its value. Stale models keep
   // arriving while the server is still processing the previous apply, so each of them
   // restarts this deadline. It is also how late a server side override (e.g. by script)
   // of the selection is shown.
   static readonly PENDING_TIMEOUT = 2000;
   // Absolute limit for protecting a sent selection, measured from the send, so a stream of
   // non-matching models (e.g. a server override refreshed again and again) is eventually
   // shown. It also limits waiting for the form data check before the selection is sent.
   static readonly PENDING_MAX_TIMEOUT = 10000;

   private pending: boolean = false;
   private pendingValue: V;
   // true once the pending selection has actually been sent to the server
   private sent: boolean = false;
   private timer: any = null;
   private maxTimer: any = null;

   /**
    * @param zone    the zone of the owning component.
    * @param equals  compares two values the same way the component matches its options.
    * @param release called when the pending selection times out.
    */
   constructor(private zone: NgZone,
               private equals: (value1: V, value2: V) => boolean,
               private release: () => void)
   {
   }

   get active(): boolean {
      return this.pending;
   }

   get value(): V {
      return this.pendingValue;
   }

   /**
    * Protect a locally applied selection from being reverted by stale models until the
    * server acknowledges it.
    */
   start(value: V): void {
      this.pending = true;
      this.pendingValue = value;
      this.sent = false;
      // safety net in case the form data check never confirms the selection (e.g. the
      // dialog is dismissed or the check fails), replaced once it is confirmed
      this.timer = this.scheduleTimeout(this.timer, PendingInputSelection.PENDING_MAX_TIMEOUT);
   }

   /**
    * Called when the form data check has confirmed the selection and its send is debounced.
    */
   confirmed(value: V): void {
      if(this.isPending(value) && !this.sent) {
         // safety net in case the selection is never sent
         this.timer = this.scheduleTimeout(
            this.timer, PendingInputSelection.APPLY_DELAY + PendingInputSelection.PENDING_TIMEOUT);
      }
   }

   /**
    * Called when the (debounced) selection event has been sent to the server.
    */
   markSent(value: V): void {
      if(this.isPending(value)) {
         this.sent = true;
         this.timer = this.scheduleTimeout(this.timer, PendingInputSelection.PENDING_TIMEOUT);
         this.maxTimer = this.scheduleTimeout(
            this.maxTimer, PendingInputSelection.PENDING_MAX_TIMEOUT);
      }
   }

   isPending(value: V): boolean {
      return this.pending && this.equals(value, this.pendingValue);
   }

   /**
    * Check the value of an incoming model against the pending selection. The component
    * keeps displaying the pending value, unless it has released it.
    */
   received(value: V): void {
      if(this.sent) {
         // the server has applied the pending selection
         if(this.isPending(value)) {
            this.clear();
         }
         // the server is still sending models built before the selection was applied, so
         // keep protecting it (up to PENDING_MAX_TIMEOUT from the send)
         else {
            this.timer = this.scheduleTimeout(this.timer, PendingInputSelection.PENDING_TIMEOUT);
         }
      }
   }

   clear(): void {
      this.pending = false;
      this.pendingValue = undefined;
      this.sent = false;

      if(this.timer != null) {
         clearTimeout(this.timer);
         this.timer = null;
      }

      if(this.maxTimer != null) {
         clearTimeout(this.maxTimer);
         this.maxTimer = null;
      }
   }

   /**
    * Replace the given timer with one that releases the pending selection after the delay.
    */
   private scheduleTimeout(timer: any, delay: number): any {
      if(timer != null) {
         clearTimeout(timer);
      }

      // the timer should not cause an extra change detection, only the release does
      return this.zone.runOutsideAngular(() => setTimeout(() => {
         this.zone.run(() => this.release());
      }, delay));
   }
}
