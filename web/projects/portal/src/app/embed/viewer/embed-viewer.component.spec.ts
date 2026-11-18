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
import { NO_ERRORS_SCHEMA, provideZonelessChangeDetection } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { provideRouter } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { BehaviorSubject } from "rxjs";
import { DownloadService } from "../../../../../shared/download/download.service";
import { FullScreenService } from "../../common/services/full-screen.service";
import { ComponentTool } from "../../common/util/component-tool";
import { ViewsheetClientService } from "../../common/viewsheet-client";
import { ShowHyperlinkService } from "../../vsobjects/show-hyperlink.service";
import { FixedDropdownService } from "../../widget/fixed-dropdown/fixed-dropdown.service";
import { InteractService } from "../../widget/interact/interact.service";
import { DebounceService } from "../../widget/services/debounce.service";
import { DialogService } from "../../widget/slide-out/dialog-service.service";
import { TooltipService } from "../../widget/tooltip/tooltip.service";
import { EmbedViewerComponent } from "./embed-viewer.component";

declare const window: any;

const OVERLAY_ID = "inetsoft-viewer-overlay";

describe("EmbedViewerComponent", () => {
   let fixture: ComponentFixture<EmbedViewerComponent>;
   let component: EmbedViewerComponent;
   let connected: BehaviorSubject<boolean>;
   let debounceCallbacks: Array<() => void>;
   let debounceService: { debounce: ReturnType<typeof vi.fn> };
   let dialogService: { ngOnDestroy: ReturnType<typeof vi.fn>, container: any };
   let tooltipService: { container: any };
   let dropdownService: { container: any, allowPositionOutsideContainer: boolean };
   let fullScreenService: { fullScreenMode: boolean };
   let showHyperlinkService: { inEmbed: boolean };

   function create(inputs: { [name: string]: any } = {}): void {
      fixture = TestBed.createComponent(EmbedViewerComponent);
      component = fixture.componentInstance;

      Object.keys(inputs).forEach(name => fixture.componentRef.setInput(name, inputs[name]));
   }

   beforeEach(() => {
      connected = new BehaviorSubject<boolean>(true);
      window.inetsoftConnected = connected;
      debounceCallbacks = [];
      debounceService = {
         debounce: vi.fn((key: string, callback: () => void) => debounceCallbacks.push(callback))
      };
      dialogService = { ngOnDestroy: vi.fn(), container: null };
      tooltipService = { container: null };
      dropdownService = { container: null, allowPositionOutsideContainer: false };
      fullScreenService = { fullScreenMode: false };
      showHyperlinkService = { inEmbed: false };

      TestBed.configureTestingModule({
         providers: [
            provideZonelessChangeDetection(),
            provideRouter([]),
            { provide: ViewsheetClientService, useValue: {} },
            { provide: FullScreenService, useValue: fullScreenService },
            { provide: ShowHyperlinkService, useValue: showHyperlinkService }
         ]
      });
      // the real viewer-app, loading display and download target are covered by their own specs
      TestBed.overrideComponent(EmbedViewerComponent, {
         set: {
            imports: [],
            schemas: [NO_ERRORS_SCHEMA],
            providers: [
               { provide: DownloadService, useValue: {} },
               { provide: TooltipService, useValue: tooltipService },
               { provide: NgbModal, useValue: {} },
               { provide: DialogService, useValue: dialogService },
               { provide: FixedDropdownService, useValue: dropdownService },
               { provide: InteractService, useValue: {} },
               { provide: DebounceService, useValue: debounceService }
            ]
         }
      });
   });

   afterEach(() => {
      fixture?.destroy();
      document.getElementById(OVERLAY_ID)?.remove();
      delete window.inetsoftConnected;
      vi.useRealTimers();
      vi.restoreAllMocks();
   });

   describe("boolean inputs", () => {
      it("has the embed defaults", () => {
         create();

         expect(component.hideToolbar).toBe(true);
         expect(component.hideMiniToolbar).toBe(true);
         expect(component.globalLoadingIndicator).toBe(true);
         expect(component.hideLoadingIndicator).toBe(false);
         expect(component.scale).toBe(false);
      });

      it.each(["hideToolbar", "hideMiniToolbar", "globalLoadingIndicator",
         "hideLoadingIndicator", "scale"])("reads %s from an html attribute string", (name) => {
         create();

         // html attributes are always strings: "" is a bare attribute such as <embed-viewer scale>
         for(const [value, expected] of [[true, true], ["true", true], ["", true],
            [false, false], ["false", false], ["no", false]] as [any, boolean][])
         {
            fixture.componentRef.setInput(name, value);
            expect((component as any)[name]).toBe(expected);
         }
      });
   });

   describe("url", () => {
      it("opens the dashboard asset given in the url", () => {
         create({ url: "global/folder/dashboard" });
         fixture.detectChanges();

         expect(component.assetId).toBe("1^128^__NULL__^folder/dashboard");
      });

      it("always disables the parameter sheet", () => {
         create({ url: "global/dashboard" });
         fixture.detectChanges();

         expect(component.queryParams.get("disableParameterSheet")).toEqual(["true"]);
      });

      it("passes the query parameters of the url to the viewer, keeping repeated values", () => {
         create({ url: "global/dashboard?region=east&region=west&year=2024" });
         fixture.detectChanges();

         expect(component.queryParams.get("region")).toEqual(["east", "west"]);
         expect(component.queryParams.get("year")).toEqual(["2024"]);
      });

      it("scales the viewer to the screen only when scale is set", () => {
         create({ url: "global/dashboard" });
         fixture.detectChanges();
         expect(component.queryParams.has("__scaleToScreen__")).toBe(false);

         fixture.destroy();
         create({ url: "global/dashboard", scale: "" });
         fixture.detectChanges();
         expect(component.queryParams.get("__scaleToScreen__")).toEqual(["true"]);
      });

      it("does not open anything and does not wait for the connection without a url", () => {
         delete window.inetsoftConnected;
         create();

         expect(() => fixture.detectChanges()).not.toThrow();
         expect(component.assetId).toBeUndefined();
         expect(component.queryParams).toBeUndefined();
      });
   });

   // Bug #81645: EMBED_VIEWER_URL_MATCHER returns null for a url without path segments, and ngOnInit reads
   // result.posParams without a null check on result, so it throws a TypeError. The chart
   // component has the same line.
   describe("url without a path", () => {
      ["?region=east", "/", "/?region=east"].forEach((url) => it.skip(`does not fail for the url '${url}'`, () => {
         create({ url });

         expect(() => fixture.detectChanges()).not.toThrow();
         expect(component.assetId).toBeUndefined();
      }));
   });

   describe("connection", () => {
      beforeEach(() => vi.useFakeTimers());

      it("is connected right away when the client is already connected", () => {
         create({ url: "global/dashboard" });
         fixture.detectChanges();

         expect(component.connected).toBe(true);
         expect(component.showError).toBe(false);
      });

      it("shows an error after two seconds without a connection", () => {
         const error = vi.spyOn(console, "error").mockImplementation(() => {});
         connected.next(false);
         create({ url: "global/dashboard" });
         fixture.detectChanges();

         vi.advanceTimersByTime(1999);
         expect(component.showError).toBeFalsy();

         vi.advanceTimersByTime(1);
         expect(component.showError).toBe(true);
         expect(component.connected).toBe(false);
         expect(error).toHaveBeenCalledWith(
            "InetSoft client not connected. Please make sure to login first.");
      });

      it("shows no error when the connection arrives in time", () => {
         const error = vi.spyOn(console, "error").mockImplementation(() => {});
         connected.next(false);
         create({ url: "global/dashboard" });
         fixture.detectChanges();

         vi.advanceTimersByTime(1000);
         connected.next(true);
         vi.advanceTimersByTime(5000);

         expect(component.connected).toBe(true);
         expect(component.showError).toBe(false);
         expect(error).not.toHaveBeenCalled();
      });

      it("hides the error once the connection arrives late", () => {
         vi.spyOn(console, "error").mockImplementation(() => {});
         connected.next(false);
         create({ url: "global/dashboard" });
         fixture.detectChanges();
         vi.advanceTimersByTime(2000);
         expect(component.showError).toBe(true);

         connected.next(true);

         expect(component.connected).toBe(true);
         expect(component.showError).toBe(false);
      });
   });

   // Bug #81644: the viewer subscribes to window.inetsoftConnected in ngOnInit and never unsubscribes, and its
   // 2 s "not connected" timer is not cleared in ngOnDestroy, so a removed viewer still logs the
   // error and refreshes its destroyed view.
   describe("removed before the client is connected", () => {
      beforeEach(() => vi.useFakeTimers());

      it.skip("stops listening for the connection when destroyed", () => {
         connected.next(false);
         create({ url: "global/dashboard" });
         fixture.detectChanges();
         expect(connected.observed).toBe(true);

         fixture.destroy();

         expect(connected.observed).toBe(false);
      });

      it.skip("does not report a missing connection after it was removed", () => {
         const error = vi.spyOn(console, "error").mockImplementation(() => {});
         connected.next(false);
         create({ url: "global/dashboard" });
         fixture.detectChanges();

         vi.advanceTimersByTime(1000);
         fixture.destroy();
         vi.advanceTimersByTime(5000);

         expect(error).not.toHaveBeenCalled();
      });
   });

   describe("errors from the viewer", () => {
      beforeEach(() => {
         create();
         fixture.detectChanges();
      });

      it("shows the message and stops the loading indicator", () => {
         const error = vi.spyOn(console, "error").mockImplementation(() => {});
         component.loading = true;

         component.onEmbedError("could not open dashboard");

         expect(component.showError).toBe(true);
         expect(component.loading).toBe(false);
         expect(error).toHaveBeenCalledWith("could not open dashboard");
      });

      it("clears the error when the message is empty", () => {
         const error = vi.spyOn(console, "error").mockImplementation(() => {});
         component.showError = true;

         component.onEmbedError("");

         expect(component.showError).toBe(false);
         expect(error).not.toHaveBeenCalled();
      });
   });

   describe("loading indicator", () => {
      beforeEach(() => {
         create();
         fixture.detectChanges();
      });

      it("shows the indicator immediately when something starts loading", () => {
         component.onLoadingStateChanged({ name: "chart1", loading: true });

         expect(component.loading).toBe(true);
         expect(debounceService.debounce).not.toHaveBeenCalled();
      });

      it("keeps the indicator until the last object has finished, debouncing the hide", () => {
         component.onLoadingStateChanged({ name: "chart1", loading: true });
         component.onLoadingStateChanged({ name: "table1", loading: true });

         component.onLoadingStateChanged({ name: "chart1", loading: false });
         expect(debounceService.debounce).toHaveBeenLastCalledWith(
            "embed-viewer-loading", expect.any(Function), 500, []);
         debounceCallbacks[debounceCallbacks.length - 1]();
         expect(component.loading).toBe(true);

         component.onLoadingStateChanged({ name: "table1", loading: false });
         debounceCallbacks[debounceCallbacks.length - 1]();
         expect(component.loading).toBe(false);
      });

      it("ignores a finished event for an object that was never loading", () => {
         component.onLoadingStateChanged({ name: "chart1", loading: false });

         expect(component.loading).toBeFalsy();
      });
   });

   describe("overlay container", () => {
      it("creates a single overlay container in the document body", () => {
         create();
         fixture.detectChanges();

         const overlay = document.getElementById(OVERLAY_ID);
         expect(overlay).not.toBeNull();
         expect(overlay.parentElement).toBe(document.body);
         expect(component.overlayContainer).toBe(overlay);
      });

      it("reuses the overlay container of another viewer", () => {
         create();
         fixture.detectChanges();
         const first = component.overlayContainer;

         const second = TestBed.createComponent(EmbedViewerComponent);
         second.detectChanges();

         expect(second.componentInstance.overlayContainer).toBe(first);
         expect(document.querySelectorAll("#" + OVERLAY_ID).length).toBe(1);
         second.destroy();
      });

      it("shows dialogs, dropdowns and tooltips inside the overlay container", () => {
         create();
         fixture.detectChanges();

         const overlay = document.getElementById(OVERLAY_ID);
         expect(tooltipService.container).toBe(overlay);
         expect(dropdownService.container).toBe(overlay);
         expect(dropdownService.allowPositionOutsideContainer).toBe(true);
         expect(dialogService.container).toBe(overlay);
      });
   });

   describe("host setup and teardown", () => {
      it("tells the hyperlink service that the viewer is embedded", () => {
         create();

         expect(showHyperlinkService.inEmbed).toBe(true);
      });

      it("closes open dialogs when destroyed", () => {
         create();
         fixture.detectChanges();

         fixture.destroy();

         expect(dialogService.ngOnDestroy).toHaveBeenCalledTimes(1);
      });
   });

   describe("sizing", () => {
      beforeEach(() => {
         create();
         fixture.detectChanges();
      });

      it("uses the size reported by the viewer", () => {
         component.onViewerSizeChanged({ width: 640, height: 480 });

         expect(component.width).toBe(640);
         expect(component.height).toBe(480);
      });

      it("offsets popups by the position of the embedded viewer in the page", () => {
         component.embedViewer = {
            nativeElement: { getBoundingClientRect: () => ({ left: 100, top: 40 }) }
         } as any;

         expect(component.viewerOffsetFunc()).toEqual({
            x: 100,
            y: 40,
            width: window.innerWidth - 100,
            height: window.innerHeight - 40,
            scrollLeft: 0,
            scrollTop: 0
         });
      });

      it("provides the offset function only outside of full screen mode", () => {
         fullScreenService.fullScreenMode = false;
         expect(component.getViewerOffsetFunc()).toBe(component.viewerOffsetFunc);

         fullScreenService.fullScreenMode = true;
         expect(component.getViewerOffsetFunc()).toBeNull();
      });
   });

   describe("data tip and downloads", () => {
      beforeEach(() => {
         create();
         fixture.detectChanges();
      });

      it("tracks whether a data tip popup is visible", () => {
         component.onDataTipPopComponentVisible(true);
         expect(component.dataTipPopComponentVisible).toBe(true);

         component.onDataTipPopComponentVisible(false);
         expect(component.dataTipPopComponentVisible).toBe(false);
      });

      it("clears data tip popups when the mouse leaves the element", () => {
         const clear = vi.fn();
         component.viewerApp = { clearDataTipPopComponents: clear } as any;

         component.onMouseLeave(new MouseEvent("mouseleave"));

         expect(clear).toHaveBeenCalledTimes(1);
      });

      it("does not fail when the mouse leaves before the viewer has loaded", () => {
         component.viewerApp = undefined;

         expect(() => component.onMouseLeave(new MouseEvent("mouseleave"))).not.toThrow();
      });

      it("tells the user that the download has started", () => {
         const show = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue(null);

         component.downloadStarted("download/url");

         expect(show).toHaveBeenCalledTimes(1);
      });
   });
});
