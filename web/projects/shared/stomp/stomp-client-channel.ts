/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Observable, ReplaySubject, Subject, Subscription } from "rxjs";
import { StompMessage } from "./stomp-message";
import { StompTopic } from "./stomp-topic";

interface PendingMessage {
   destination: string;
   headers: any;
   body: string;
}

/**
 * Wrapper for the StompJs client that handles multiplexing topics, queueing messages while
 * reconnecting, and resuming operation after reconnecting.
 */
export class StompClientChannel {
   private client: Stomp.Client = null;
   private pendingMessages: PendingMessage[] = [];
   private topics = new Map<string, StompTopic>();

   get transport(): string {
      return (this.client.ws as any).transport;
   }

   constructor(client: Observable<Stomp.Client>) {
      client.subscribe((c) => {
         this.client = c;

         if(this.client) {
            this.topics.forEach((topic, destination) => {
               topic.clientSubscription = this.subscribeToTopic(destination, topic);
            });
            this.pendingMessages.forEach((message) => {
               this.sendToQueue(message.destination, message.headers, message.body);
            });
            this.pendingMessages = [];
         }
      });
   }

   subscribe(destination: string,
             next?: (value: StompMessage) => void,
             error?: (error: any) => void,
             complete?: () => void,
             replay: boolean = false): Subscription
   {
      let topic = this.topics.get(destination);

      if(!topic) {
         topic = {
            referenceCount: 0,
            subject: replay ? new ReplaySubject<StompMessage>(1) : new Subject<StompMessage>()
         };
         this.topics.set(destination, topic);

         if(this.client) {
            topic.clientSubscription = this.subscribeToTopic(destination, topic);
         }
      }

      topic.referenceCount = topic.referenceCount + 1;
      const subscription = topic.subject.subscribe(next, error, complete);
      subscription.add(() => {
         topic.referenceCount = topic.referenceCount - 1;

         if(topic.referenceCount == 0) {
            topic.subject.complete();
            subscription.add(topic.clientSubscription);
            this.topics.delete(destination);
         }
      });

      return subscription;
   }

   send(destination: string, headers: any, body: string): void {
      if(this.client) {
         this.sendToQueue(destination, headers, body);
      }
      else {
         this.pendingMessages.push({destination, headers, body});
      }
   }

   private sendToQueue(destination: string, headers: any, body: string): void {
      if(body && body.length >= 32768) {
         try {
            const bodyObject = JSON.parse(body);
            console.warn(
               "Sending large STOMP message body (" + body.length + " bytes): ", bodyObject);
         }
         catch(ignore) {
            // not JSON
            console.warn("Sending large STOMP message body (" + body.length + " bytes)");
         }
      }

      this.client.send(destination, headers, body);
   }

   private subscribeToTopic(destination: string, topic: StompTopic): Stomp.Subscription {
      return this.client.subscribe(destination, (message: Stomp.Frame) => {
         topic.subject.next({ frame: message });
      });
   }
}
