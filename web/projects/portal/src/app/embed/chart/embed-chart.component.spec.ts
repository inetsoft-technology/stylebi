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
import {
   ApplicationRef,
   ComponentRef,
   NO_ERRORS_SCHEMA,
   provideZonelessChangeDetection
} from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { provideHttpClientTesting } from "@angular/common/http/testing";
import { provideRouter } from "@angular/router";
import { BehaviorSubject, NEVER, ReplaySubject, Subject, Subscription } from "rxjs";
import { StompClientService, ViewsheetClientService } from "../../common/viewsheet-client";
import { embedElementConfig } from "../embed-element.config";
import { EmbedChartComponent } from "./embed-chart.component";
import { EMBED_CHART_ROUTE_PROVIDERS } from "./embed-chart.route-providers";

declare const window: any;

// Bug #77291: the <inetsoft-chart> element app (createApplication() in main-elements.ts) is
// zoneless and its view is attached to ApplicationRef by @angular/elements. A connection error
// (or its recovery) must refresh the view without relying on a command or a user event.
describe("EmbedChartComponent connection error", () => {
   let connectionError: ReplaySubject<string>;
   let fixture: ComponentFixture<EmbedChartComponent>;

   const timeoutShown = () =>
      fixture.nativeElement.textContent.includes("vs.viewsheet.chart.timeout");

   beforeEach(() => {
      connectionError = new ReplaySubject<string>(1);
      window.inetsoftConnected = new BehaviorSubject<boolean>(true);

      const viewsheetClient = {
         commands: new Subject<any>(),
         onHeartbeat: NEVER,
         connectionError: () => connectionError.asObservable(),
         whenConnected: () => NEVER,
         connect: () => {},
         sendEvent: () => {},
         beforeDestroy: null
      };

      TestBed.configureTestingModule({
         providers: [
            provideZonelessChangeDetection(),
            ...embedElementConfig.providers,
            provideRouter([]),
            ...EMBED_CHART_ROUTE_PROVIDERS,
            provideHttpClientTesting()
         ]
      });
      TestBed.overrideComponent(EmbedChartComponent, {
         set: {
            imports: [],
            schemas: [NO_ERRORS_SCHEMA],
            providers: [{ provide: ViewsheetClientService, useValue: viewsheetClient }]
         }
      });

      fixture = TestBed.createComponent(EmbedChartComponent);
      const ref: ComponentRef<EmbedChartComponent> = fixture.componentRef;
      ref.setInput("url", "global/folder/chart/Chart1");
      // mirror @angular/elements: the host view is a root view of the ApplicationRef
      TestBed.inject(ApplicationRef).attachView(ref.hostView);
      fixture.detectChanges();
   });

   afterEach(() => {
      fixture.destroy();
      delete window.inetsoftConnected;
   });

   it("shows the timeout message on error and clears it on recovery", async () => {
      expect(timeoutShown()).toBe(false);

      connectionError.next("timeout");
      await fixture.whenStable();
      expect(fixture.componentInstance.timeoutError).toBe(true);
      expect(timeoutShown()).toBe(true);

      connectionError.next(null);
      await fixture.whenStable();
      expect(fixture.componentInstance.timeoutError).toBe(false);
      expect(timeoutShown()).toBe(false);
   });

   it("clears the timeout message on recovery after the view was refreshed during the outage", async () => {
      connectionError.next("timeout");
      // stands in for an unrelated refresh during the outage (e.g. the user hovering the chart)
      fixture.detectChanges();
      expect(timeoutShown()).toBe(true);

      connectionError.next(null);
      await fixture.whenStable();
      expect(fixture.componentInstance.timeoutError).toBe(false);
      expect(timeoutShown()).toBe(false);
   });
});

// Bug #77292: removing the element while the viewsheet is opening must not leave the runtime
// viewsheet open on the server. Uses the real ViewsheetClientService over a fake connection.
describe("EmbedChartComponent close before the viewsheet is open", () => {
   const OPEN = "/events/open";
   const CLOSE = "/events/composer/viewsheet/close";
   const RID = "Examples/Sales Summary-128";

   let fixture: ComponentFixture<EmbedChartComponent>;
   let client: ViewsheetClientService;
   let connection: {
      listeners: {destination: string, next: (message: any) => void, sub: Subscription}[],
      sent: {destination: string, headers: any, body: string}[],
      disconnects: number,
      transport: string,
      onHeartbeat: Subject<any>,
      subscribe: (destination: string, next: (message: any) => void) => Subscription,
      send: (destination: string, headers: any, body: string) => void,
      disconnect: () => void
   };

   const sentTo = (destination: string) =>
      connection.sent.filter((f) => f.destination === destination);
   const macrotask = () => new Promise((resolve) => setTimeout(resolve, 0));

   beforeEach(() => {
      window.inetsoftConnected = new BehaviorSubject<boolean>(true);
      connection = {
         listeners: [],
         sent: [],
         disconnects: 0,
         transport: "websocket",
         onHeartbeat: new Subject<any>(),
         subscribe(destination, next) {
            const entry = {destination, next, sub: null as Subscription};
            entry.sub = new Subscription(() => {
               connection.listeners = connection.listeners.filter((l) => l !== entry);
            });
            connection.listeners.push(entry);
            return entry.sub;
         },
         send(destination, headers, body) {
            connection.sent.push({destination, headers: {...headers}, body});
         },
         disconnect() {
            [...connection.listeners].forEach((l) => l.sub.unsubscribe());
            connection.disconnects++;
         }
      };
      const stomp = {
         connect: () => new BehaviorSubject<any>(connection),
         whenDisconnected: () => new Subject<void>(),
         reconnectError: () => new Subject<string>()
      };

      TestBed.configureTestingModule({
         providers: [
            provideZonelessChangeDetection(),
            ...embedElementConfig.providers,
            provideRouter([]),
            ...EMBED_CHART_ROUTE_PROVIDERS,
            provideHttpClientTesting(),
            { provide: StompClientService, useValue: stomp }
         ]
      });
      TestBed.overrideComponent(EmbedChartComponent, {
         set: {
            imports: [],
            schemas: [NO_ERRORS_SCHEMA],
            providers: [ViewsheetClientService]
         }
      });

      fixture = TestBed.createComponent(EmbedChartComponent);
      fixture.componentRef.setInput("url", "global/folder/chart/Chart1");
      fixture.detectChanges();
      client = fixture.debugElement.injector.get(ViewsheetClientService);
   });

   afterEach(() => {
      delete window.inetsoftConnected;
   });

   it("never sends the open when removed before the scheduled open runs", async () => {
      const open0 = vi.spyOn(fixture.componentInstance as any, "openViewsheet0");

      fixture.destroy();
      await macrotask();

      expect(open0).not.toHaveBeenCalled();
      expect(sentTo(OPEN).length).toBe(0);
      expect(sentTo(CLOSE).length).toBe(0);
      expect(connection.disconnects).toBe(1);
   });

   it("closes with the runtime id that arrives after the element is removed", async () => {
      await macrotask();
      expect(sentTo(OPEN).length).toBe(1);

      fixture.destroy();
      expect(sentTo(CLOSE).length).toBe(0);

      [...connection.listeners]
         .filter((l) => l.destination === "/user/commands")
         .forEach((l) => l.next({frame: {
            command: "MESSAGE",
            headers: {inetsoftClientId: client.clientId, commandType: "SetRuntimeIdCommand"},
            body: JSON.stringify({runtimeId: RID})
         }}));

      const close = sentTo(CLOSE);
      expect(close.length).toBe(1);
      expect(close[0].headers.sheetRuntimeId).toBe(RID);
      expect(connection.disconnects).toBe(1);
   });

   it("closes right away when the runtime id is known before the element is removed", async () => {
      await macrotask();
      [...connection.listeners]
         .filter((l) => l.destination === "/user/commands")
         .forEach((l) => l.next({frame: {
            command: "MESSAGE",
            headers: {inetsoftClientId: client.clientId, commandType: "SetRuntimeIdCommand"},
            body: JSON.stringify({runtimeId: RID})
         }}));
      expect(client.runtimeId).toBe(RID);

      fixture.destroy();

      const close = sentTo(CLOSE);
      expect(close.length).toBe(1);
      expect(close[0].headers.sheetRuntimeId).toBe(RID);
      expect(connection.disconnects).toBe(1);
   });
});
