/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
import { ApplicationRef, NO_ERRORS_SCHEMA, provideZonelessChangeDetection } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { provideHttpClientTesting } from "@angular/common/http/testing";
import { provideRouter } from "@angular/router";
import { BehaviorSubject, NEVER, Subject } from "rxjs";
import { ViewsheetClientService } from "../../common/viewsheet-client";
import { embedElementConfig } from "../embed-element.config";
import { EmbedChartComponent } from "./embed-chart.component";
import { EMBED_CHART_ROUTE_PROVIDERS } from "./embed-chart.route-providers";

declare const window: any;

// The embed host page logs in with window.inetsoftLogin(), which completes after the elements may
// already be on the page. An element that is removed before the login completes must not react to
// the login any more.
describe("EmbedChartComponent removed before the client is connected", () => {
   let connected: BehaviorSubject<boolean>;
   let fixture: ComponentFixture<EmbedChartComponent>;

   beforeEach(() => {
      connected = new BehaviorSubject<boolean>(false);
      window.inetsoftConnected = connected;

      const viewsheetClient = {
         commands: new Subject<any>(),
         onHeartbeat: NEVER,
         connectionError: () => NEVER,
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
      fixture.componentRef.setInput("url", "global/folder/chart/Chart1");
      TestBed.inject(ApplicationRef).attachView(fixture.componentRef.hostView);
      fixture.detectChanges();
   });

   afterEach(() => {
      delete window.inetsoftConnected;
      vi.restoreAllMocks();
   });

   it("is waiting for the connection while it is on the page", () => {
      expect(connected.observed).toBe(true);
   });

   // Bug #81644: the element subscribes to window.inetsoftConnected in ngOnInit and never unsubscribes, so after
   // it is removed the login still runs its callback, which calls openViewsheet() on a destroyed
   // component (this.subscriptions is null by then).
   it.skip("stops listening for the connection when destroyed", () => {
      fixture.destroy();

      expect(connected.observed).toBe(false);
   });

   it.skip("does not open the viewsheet when the login completes after it was removed", () => {
      const open = vi.spyOn(fixture.componentInstance as any, "openViewsheet")
         .mockImplementation(() => {});

      fixture.destroy();
      connected.next(true);

      expect(open).not.toHaveBeenCalled();
   });
});
