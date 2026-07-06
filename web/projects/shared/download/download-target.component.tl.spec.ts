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

/**
 * DownloadTargetComponent — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3]  — ngOnDestroy: subscription unsubscribed; URL emissions after destroy
 *                        do not trigger download or downloadStarted
 *   Group 2 [Risk 3]  — download(url, true): HTTP error paths — status=200 treated as success
 *                        (iframe src set); status≠200 + emitError=true → onError emitted;
 *                        status≠200 + emitError=false → alert() called
 *   Group 3 [baseline] — ngOnInit URL subscription: downloadStarted @Output emitted with URL;
 *                         null URL skipped
 *   Group 4 [baseline] — download(url, false): iframe src set directly without HTTP request
 *   Group 5 [baseline] — download(url, true): HTTP success (200) → iframe src set
 *   Group 6 [baseline] — same URL repeated: reload counter appended to prevent iframe cache
 *
 * HTTP mocking: MSW inline server.use() per test. The status=200 error branch is triggered
 * by returning a 200 response whose body is not valid JSON — Angular's HttpClient (responseType
 * "json") fails to parse it and calls the error handler with HttpErrorResponse.status === 200.
 *
 * Out of scope this pass: none (single pass)
 */

import { provideHttpClient } from "@angular/common/http";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { Subject } from "rxjs";

import { server } from "@test-mocks/server";
import { DownloadTargetComponent } from "./download-target.component";
import { DownloadService } from "./download.service";

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

function makeDownloadService() {
   const url$ = new Subject<string>();
   return { service: { url: url$.asObservable() }, url$ };
}

async function renderComponent(inputs: { emitError?: boolean } = {}) {
   const { service, url$ } = makeDownloadService();

   const result = await render(DownloadTargetComponent, {
      providers: [
         { provide: DownloadService, useValue: service },
         provideHttpClient(),
      ],
   });

   const comp = result.fixture.componentInstance as DownloadTargetComponent;

   if(inputs.emitError !== undefined) {
      comp.emitError = inputs.emitError;
   }

   return { fixture: result.fixture, comp, url$ };
}

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: ngOnDestroy — subscription cleanup (memory leak)
// ---------------------------------------------------------------------------

describe("DownloadTargetComponent — ngOnDestroy", () => {
   // 🔁 Regression-sensitive: a leaked subscription keeps firing download() on every URL
   // emission after the component is gone, creating phantom HTTP requests in the session.
   it("should not emit downloadStarted after the component is destroyed", async () => {
      const { fixture, comp, url$ } = await renderComponent();
      const emitted: string[] = [];
      comp.downloadStarted.subscribe((u: string) => emitted.push(u));

      fixture.destroy();
      url$.next("/api/export/after-destroy");

      expect(emitted).toHaveLength(0);
   });

   it("should not make an HTTP request after the component is destroyed", async () => {
      // Register a handler so any accidental request gets a reply rather than hanging;
      // the real assertion is that no iframe src change occurs.
      server.use(http.get("*/after-destroy-check*", () => HttpResponse.json(null)));
      const { fixture, comp, url$ } = await renderComponent();

      fixture.destroy();
      url$.next("/api/export/after-destroy-check?checkForResponse=true");

      // No src change — iframe stays on about:blank
      expect(comp.frame.nativeElement.getAttribute("src")).toBe("about:blank");
   });
});

// ---------------------------------------------------------------------------
// Group 2: download(url, true) — HTTP error handling
// ---------------------------------------------------------------------------

describe("DownloadTargetComponent — download: HTTP error handling", () => {
   // 🔁 Regression-sensitive: the status=200 error branch handles binary/streaming responses
   // that Angular cannot parse as JSON. The download is still valid — iframe must load the URL.
   it("should set iframe src when the HTTP error response has status 200", async () => {
      const url = "/api/export/binary-download";
      // Return a 200 with a non-JSON body; Angular's JSON parser fails and calls the error
      // handler with HttpErrorResponse.status === 200.
      server.use(
         http.get("*/binary-download*", () =>
            new Response("%%binary%%", {
               status: 200,
               headers: { "Content-Type": "application/json" },
            })
         )
      );
      const { fixture, comp } = await renderComponent();

      comp.download(url, true);

      await waitFor(() =>
         expect(comp.frame.nativeElement.getAttribute("src")).toBe(url)
      );
   });

   // 🔁 Regression-sensitive: onError @Output contract — callers subscribe to onError to show
   // a dialog; a missing emit leaves the user with no feedback on export failure.
   it("should emit onError with the error message when emitError is true and status is 500", async () => {
      const url = "/api/export/fail-emit";
      server.use(
         http.get("*/fail-emit*", () =>
            HttpResponse.json({ message: "Export failed" }, { status: 500 })
         )
      );
      const { fixture, comp } = await renderComponent({ emitError: true });
      const errors: string[] = [];
      comp.onError.subscribe((msg: string) => errors.push(msg));

      comp.download(url, true);

      await waitFor(() => expect(errors).toEqual(["Export failed"]));
   });

   it("should call alert() with the error message when emitError is false and status is 500", async () => {
      const alertSpy = vi.spyOn(window, "alert").mockImplementation(() => {});
      const url = "/api/export/fail-alert";
      server.use(
         http.get("*/fail-alert*", () =>
            HttpResponse.json({ message: "Server error" }, { status: 500 })
         )
      );
      const { fixture, comp } = await renderComponent({ emitError: false });

      comp.download(url, true);

      await waitFor(() => expect(alertSpy).toHaveBeenCalledWith("Server error"));
   });
});

// ---------------------------------------------------------------------------
// Group 3: ngOnInit — URL subscription → downloadStarted @Output (baseline)
// ---------------------------------------------------------------------------

describe("DownloadTargetComponent — ngOnInit: URL subscription", () => {
   it("should emit downloadStarted with the URL when a URL is received", async () => {
      const { comp, url$ } = await renderComponent();
      const emitted: string[] = [];
      comp.downloadStarted.subscribe((u: string) => emitted.push(u));

      url$.next("/api/export/report.pdf");

      expect(emitted).toEqual(["/api/export/report.pdf"]);
   });

   it("should not emit downloadStarted when null is received", async () => {
      const { comp, url$ } = await renderComponent();
      const emitted: string[] = [];
      comp.downloadStarted.subscribe((u: string) => emitted.push(u));

      url$.next(null as any);

      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 4: download(url, false) — direct iframe src (baseline)
// ---------------------------------------------------------------------------

describe("DownloadTargetComponent — download: checkForResponse=false", () => {
   it("should set iframe src directly without making an HTTP request", async () => {
      const { fixture, comp } = await renderComponent();
      const url = "/api/export/direct.pdf";

      comp.download(url, false);
      fixture.detectChanges();

      expect(comp.frame.nativeElement.getAttribute("src")).toBe(url);
   });
});

// ---------------------------------------------------------------------------
// Group 5: download(url, true) — HTTP success → iframe src (baseline)
// ---------------------------------------------------------------------------

describe("DownloadTargetComponent — download: checkForResponse=true success", () => {
   // 🔁 Regression-sensitive: the HTTP GET validates the export before loading it in the
   // iframe; if the success path is broken the iframe never loads even on a valid export.
   it("should set iframe src after a successful HTTP 200 response", async () => {
      const url = "/api/export/checked.pdf";
      server.use(http.get("*/checked.pdf*", () => HttpResponse.json(null)));
      const { fixture, comp } = await renderComponent();

      comp.download(url, true);

      await waitFor(() =>
         expect(comp.frame.nativeElement.getAttribute("src")).toBe(url)
      );
   });
});

// ---------------------------------------------------------------------------
// Group 6: same URL repeated — reload counter (baseline)
// ---------------------------------------------------------------------------

describe("DownloadTargetComponent — ngOnInit: same URL repeated", () => {
   // 🔁 Regression-sensitive: browsers cache iframe src; without the counter the second
   // download of the same file silently does nothing.
   it("should append a reload counter when the same URL is emitted a second time", async () => {
      const { comp, url$ } = await renderComponent();
      const url = "/api/export/report.pdf";

      url$.next(url);
      url$.next(url);

      expect(comp.frame.nativeElement.getAttribute("src")).toContain("downloadServiceReloadCounter=1");
   });

   it("should not append a reload counter when a different URL is emitted", async () => {
      const { comp, url$ } = await renderComponent();

      url$.next("/api/export/a.pdf");
      url$.next("/api/export/b.pdf");

      const src = comp.frame.nativeElement.getAttribute("src");
      expect(src).toBe("/api/export/b.pdf");
      expect(src).not.toContain("downloadServiceReloadCounter");
   });
});
