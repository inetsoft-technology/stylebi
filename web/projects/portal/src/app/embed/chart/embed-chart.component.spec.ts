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
import { BehaviorSubject, NEVER, ReplaySubject, Subject } from "rxjs";
import { ViewsheetClientService } from "../../common/viewsheet-client";
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
