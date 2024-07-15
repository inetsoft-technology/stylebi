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
import { Subject ,  Subscriber ,  Subscription } from "rxjs";

/**
 * Subject implementation that queues values until the first call to subscribe. All queued
 * values are replayed to the first subscriber. Once the queued values are replayed, they
 * are discarded and the subject behaves like a BehaviorSubject.
 */
export class QueuedSubject<T> extends Subject<T> {
   private queue: T[] = [];
   private subscribed: boolean = false;

   next(value: T): void {
      if(!this.subscribed) {
         this.queue.push(value);
      }

      super.next(value);
   }

   _subscribe(subscriber: Subscriber<T>): Subscription {
      let subscription: Subscription = super._subscribe(subscriber);

      if(!this.subscribed) {
         this.subscribed = true;
         const len = this.queue.length;

         for(let i = 0; i < len && !subscriber.closed; i++) {
            subscriber.next(this.queue[i]);
         }

         this.queue = null;
      }

      if(this.hasError) {
         subscriber.error(this.thrownError);
      }
      else if(this.isStopped) {
         subscriber.complete();
      }

      return subscription;
   }
}
