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
describe("EmbedChartComponent connection to the client", () => {
   let connected: BehaviorSubject<boolean>;
   let fixture: ComponentFixture<EmbedChartComponent>;

   function create(url = "global/folder/chart/Chart1"): void {
      fixture = TestBed.createComponent(EmbedChartComponent);
      fixture.componentRef.setInput("url", url);
      TestBed.inject(ApplicationRef).attachView(fixture.componentRef.hostView);
      fixture.detectChanges();
   }

   beforeEach(() => {
      // ngOnInit schedules a real 1 s timer while the client is not connected
      vi.useFakeTimers();
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
   });

   afterEach(() => {
      fixture?.destroy();
      // drop the pending "not connected" timer so it cannot fire in a later spec
      vi.clearAllTimers();
      vi.useRealTimers();
      delete window.inetsoftConnected;
      vi.restoreAllMocks();
   });

   describe("while the client is not connected", () => {
      it("is waiting for the connection while it is on the page", () => {
         create();

         expect(connected.observed).toBe(true);
      });

      it("reports a missing connection after 1 second", () => {
         const error = vi.spyOn(console, "error").mockImplementation(() => {});
         create();
         const component = fixture.componentInstance;

         vi.advanceTimersByTime(999);
         expect(component.showError).toBeFalsy();
         expect(error).not.toHaveBeenCalled();

         vi.advanceTimersByTime(1);
         expect(component.showError).toBe(true);
         expect(error).toHaveBeenCalledWith(
            "InetSoft client not connected. Please make sure to login first.");
      });

      it("opens the viewsheet and cancels the error once the client connects", () => {
         const error = vi.spyOn(console, "error").mockImplementation(() => {});
         create();
         const component = fixture.componentInstance;
         const open = vi.spyOn(component as any, "openViewsheet").mockImplementation(() => {});

         connected.next(true);
         vi.advanceTimersByTime(5000);

         expect(open).toHaveBeenCalledTimes(1);
         expect(component.showError).toBe(false);
         expect(error).not.toHaveBeenCalled();
      });
   });

   describe("when the client is already connected", () => {
      it("opens the viewsheet right away", () => {
         connected.next(true);
         const open = vi.spyOn(EmbedChartComponent.prototype as any, "openViewsheet")
            .mockImplementation(() => {});

         create();

         expect(open).toHaveBeenCalledTimes(1);
      });
   });

   // Bug #81644: the element subscribes to window.inetsoftConnected in ngOnInit and never unsubscribes, so after
   // it is removed the login still runs its callback, which calls openViewsheet() on a destroyed
   // component (this.subscriptions is null by then). The "not connected" timer is not cleared on
   // destroy either.
   describe("removed before the client is connected", () => {
      beforeEach(() => create());

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

      it.skip("does not report a missing connection after it was removed", () => {
         const error = vi.spyOn(console, "error").mockImplementation(() => {});

         fixture.destroy();
         vi.advanceTimersByTime(2000);

         expect(error).not.toHaveBeenCalled();
      });
   });

   // Bug #81645: EMBED_CHART_URL_MATCHER returns null for a url without path segments, and ngOnInit
   // reads result.posParams without a null check on result, so it throws a TypeError (the viewer
   // component has the same line).
   describe("url without a path", () => {
      const urls = ["?region=east", "/", "/?region=east"];

      urls.forEach((url) => {
         it.skip(`does not fail for the url '${url}'`, () => {
            expect(() => create(url)).not.toThrow();
            expect(fixture.componentInstance.assetId).toBeUndefined();
         });
      });
   });
});
