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
import { Subject, Subscriber, Subscription } from "rxjs";

/**
 * Subject implementation that queues values until the first call to subscribe. All queued
 * values are replayed to the first subscriber. Once the queued values are replayed, they
 * are discarded and the subject behaves like a BehaviorSubject. This implementation differs from
 * QueuedSubject in that it guarantees that the first subscriber receives each queued value in a
 * separate macrotask and preserves the order of additional values received after those initial
 * values are scheduled.
 */
export class AsyncQueuedSubject<T> extends Subject<T> {
   forceAsync = false;
   private queue: T[] = [];
   private subscribed = false;
   private scheduled = false;

   next(value?: T): void {
      if(!this.subscribed) {
         this.queue.push(value);
         super.next(value);
      }
      else if(this.forceAsync || this.scheduled) {
         setTimeout(() => super.next(value));
      }
      else {
         super.next(value);
      }
   }

   public _subscribe(subscriber: Subscriber<T>): Subscription {
      let subscription: Subscription = super._subscribe(subscriber);

      if(!this.subscribed) {
         this.subscribed = true;
         const values = this.queue;
         this.queue = null;
         const len = values.length;

         if(len > 0) {
            subscriber.next(values[0]);
         }

         if(len > 1) {
            this.scheduled = true;

            for(let i = 1; i < len && !subscriber.closed; i++) {
               setTimeout(() => subscriber.next(values[i]), 0);
            }

            if(this.hasError) {
               setTimeout(() => subscriber.error(this.thrownError), 0);
            }
            else if(this.isStopped) {
               setTimeout(() => subscriber.complete(), 0);
            }

            setTimeout(() => this.scheduled = false, 0);

            return subscription;
         }
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
