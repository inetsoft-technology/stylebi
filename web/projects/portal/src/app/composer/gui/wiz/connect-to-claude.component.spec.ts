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
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed, waitForAsync } from "@angular/core/testing";
import { of } from "rxjs";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ConnectToClaudeComponent } from "./connect-to-claude.component";

describe("ConnectToClaudeComponent", () => {
   let fixture: ComponentFixture<ConnectToClaudeComponent>;
   let component: ConnectToClaudeComponent;
   let mockSocketConnection: any;
   let mockStompConnection: { subscribe: ReturnType<typeof vi.fn>; send: ReturnType<typeof vi.fn> };

   beforeEach(waitForAsync(() => {
      mockStompConnection = {
         subscribe: vi.fn(),
         send: vi.fn()
      };

      mockSocketConnection = {
         whenConnected: vi.fn(() => of(mockStompConnection))
      };

      // Default: subscribe returns a Subscription-like object
      mockStompConnection.subscribe.mockReturnValue({ unsubscribe: vi.fn() });

      TestBed.configureTestingModule({
         imports: [ConnectToClaudeComponent],
         providers: [
            { provide: ViewsheetClientService, useValue: mockSocketConnection }
         ],
         schemas: [NO_ERRORS_SCHEMA]
      });

      TestBed.compileComponents();

      fixture = TestBed.createComponent(ConnectToClaudeComponent);
      component = fixture.componentInstance;
      component.runtimeId = "rt-1";
      component.sheetType = "WORKSHEET";
      component.socketConnection = mockSocketConnection;
      fixture.detectChanges();
   }));

   it("Connect to Claude button calls requestCode", () => {
      const button: HTMLButtonElement = fixture.nativeElement.querySelector("button");
      expect(button).toBeTruthy();
      expect(button.disabled).toBeFalsy();

      button.click();
      fixture.detectChanges();

      expect(component.loading).toBe(true);
      expect(button.disabled).toBeTruthy();
      expect(mockSocketConnection.whenConnected).toHaveBeenCalled();
   });

   it("requestCode subscribes to /user/commands/wiz/pairing/mint and sends mint request", () => {
      component.requestCode();

      expect(mockStompConnection.subscribe).toHaveBeenCalledWith(
         "/user/commands/wiz/pairing/mint",
         expect.any(Function)
      );
      expect(mockStompConnection.send).toHaveBeenCalledWith(
         "/events/wiz/pairing/mint",
         {},
         JSON.stringify({ runtimeId: "rt-1", sheetType: "WORKSHEET" })
      );
   });

   it("displays code when response arrives", () => {
      let capturedHandler: ((msg: any) => void) | null = null;
      const subSpy = { unsubscribe: vi.fn() };
      mockStompConnection.subscribe.mockImplementation((_dest: string, handler: (msg: any) => void) => {
         capturedHandler = handler;
         return subSpy;
      });

      component.requestCode();
      expect(capturedHandler).not.toBeNull();

      capturedHandler!({ frame: { body: JSON.stringify({ code: "ABC123" }) } });
      fixture.detectChanges();

      expect(component.code).toBe("ABC123");
      expect(component.loading).toBe(false);
      expect(subSpy.unsubscribe).toHaveBeenCalled();

      const codeEl: HTMLElement = fixture.nativeElement.querySelector(".wiz-connect-value");
      expect(codeEl).toBeTruthy();
      expect(codeEl.textContent?.trim()).toBe("ABC123");
   });

   it("shows error when response has error field", () => {
      let capturedHandler: ((msg: any) => void) | null = null;
      mockStompConnection.subscribe.mockImplementation((_dest: string, handler: (msg: any) => void) => {
         capturedHandler = handler;
         return { unsubscribe: vi.fn() };
      });

      component.requestCode();
      capturedHandler!({ frame: { body: JSON.stringify({ error: "Feature disabled" }) } });
      fixture.detectChanges();

      expect(component.error).toBe("Feature disabled");
      expect(component.code).toBeNull();
      expect(component.loading).toBe(false);
   });

   it("copy button uses the ngxClipboard directive instead of navigator.clipboard", () => {
      // navigator.clipboard is undefined in insecure contexts (plain http on a
      // non-localhost host); the copy button must not depend on it directly.
      let capturedHandler: ((msg: any) => void) | null = null;
      mockStompConnection.subscribe.mockImplementation((_dest: string, handler: (msg: any) => void) => {
         capturedHandler = handler;
         return { unsubscribe: vi.fn() };
      });

      component.requestCode();
      capturedHandler!({ frame: { body: JSON.stringify({ code: "ABC123" }) } });
      fixture.detectChanges();

      const copyButton: HTMLButtonElement = fixture.nativeElement.querySelector(".wiz-connect-code button");
      expect(copyButton.hasAttribute("ngxclipboard")).toBe(true);
      expect((component as any).copyCode).toBeUndefined();
   });

   it("onCopySuccess shows the copied indicator then clears it after a delay", () => {
      vi.useFakeTimers();

      component.onCopySuccess();
      expect(component.copied).toBe(true);

      vi.advanceTimersByTime(2000);
      expect(component.copied).toBe(false);

      vi.useRealTimers();
   });

   it("onCopyError sets an error message", () => {
      component.onCopyError();
      expect(component.error).toBe("Could not copy to clipboard — please copy the code manually.");
   });

   it("ngOnDestroy unsubscribes pending mint", () => {
      const subSpy = { unsubscribe: vi.fn() };
      mockStompConnection.subscribe.mockReturnValue(subSpy);

      component.requestCode();
      fixture.destroy();

      expect(subSpy.unsubscribe).toHaveBeenCalled();
   });

   it("includes editorContext in the mint payload when one is supplied", () => {
      component.runtimeId = "vs-1";
      component.sheetType = "VIEWSHEET";
      component.editorContext = { kind: "assemblyMain", assembly: "Chart1" };
      component.requestCode();

      const sent = JSON.parse(mockStompConnection.send.mock.calls[0][2]);
      expect(sent.editorContext).toEqual({ kind: "assemblyMain", assembly: "Chart1" });
   });

   it("omits editorContext entirely for a toolbar mint", () => {
      component.runtimeId = "vs-1";
      component.sheetType = "VIEWSHEET";
      component.requestCode();

      const sent = JSON.parse(mockStompConnection.send.mock.calls[0][2]);
      expect("editorContext" in sent).toBe(false);
   });

   // JSON.stringify drops keys whose value is `undefined` on its own, so a test that
   // merely leaves editorContext unset (the previous test) would pass even without the
   // `if(this.editorContext)` guard in requestCode(). Setting it to `null` explicitly
   // forces the guard to do the work: without it, `payload.editorContext = null` would
   // survive JSON.stringify and this assertion would fail.
   it("omits editorContext when it is explicitly set to null", () => {
      component.runtimeId = "vs-1";
      component.sheetType = "VIEWSHEET";
      component.editorContext = null;
      component.requestCode();

      const sent = JSON.parse(mockStompConnection.send.mock.calls[0][2]);
      expect("editorContext" in sent).toBe(false);
   });

   describe("detach (G2 Task 9)", () => {
      it("sends a detach message naming this location's editorContext", () => {
         component.editorContext = { kind: "assemblyMain", assembly: "Chart1" };

         component.detach();

         expect(mockSocketConnection.whenConnected).toHaveBeenCalled();
         expect(mockStompConnection.send).toHaveBeenCalledWith(
            "/events/wiz/pairing/detach",
            {},
            JSON.stringify({ editorContext: { kind: "assemblyMain", assembly: "Chart1" } })
         );
      });

      it("is a no-op for a whole-sheet (toolbar) mint with no editorContext", () => {
         component.editorContext = undefined;

         component.detach();

         // Asserts the behaviour directly rather than via "no socket was opened": ngOnInit now
         // opens one for the join-notice subscription, so the old proxy assertion measured the
         // component's startup instead of detach(). What matters is that no detach was sent.
         expect(mockStompConnection.send).not.toHaveBeenCalledWith(
            "/events/wiz/pairing/detach", expect.anything(), expect.anything());
      });

      it("is a no-op when there is no socket connection", () => {
         component.editorContext = { kind: "assemblyMain", assembly: "Chart1" };
         component.socketConnection = undefined as any;

         expect(() => component.detach()).not.toThrow();
      });
   });

   /*
    * Whole-branch review finding 2. The existing detach tests above all vary the context
    * BEFORE minting, or not at all -- which is exactly why this survived a per-task review.
    * The failure needs the context to change AFTER a code came back.
    */
   describe("detach keys on the context the code was minted with", () => {
      function mintWith(context: any): void {
         let handler: ((msg: any) => void) | null = null;
         mockStompConnection.subscribe.mockImplementation(
            (_dest: string, h: (msg: any) => void) => {
               handler = h;
               return { unsubscribe: vi.fn() };
            });

         component.runtimeId = "vs-1";
         component.sheetType = "VIEWSHEET";
         component.editorContext = context;
         component.requestCode();
         handler!({ frame: { body: JSON.stringify({ code: "ABC123" }) } });
      }

      function lastDetachPayload(): any {
         const call = mockStompConnection.send.mock.calls
            .filter((c: any[]) => c[0] === "/events/wiz/pairing/detach")
            .pop();
         return call === undefined ? undefined : JSON.parse(call[2]);
      }

      /*
       * The reported failure, end to end: pair on Init, click Load to compare, Cancel. The host
       * (viewsheet-script-pane) derives editorContext from the onInit/onLoad radio, so its getter
       * now says viewsheetOnLoad -- which matches no session on the server, leaving the
       * viewsheetOnInit session live for its full 30-minute TTL with the editor gone.
       */
      it("detaches the minted context, not the host's current one", () => {
         mintWith({ kind: "viewsheetOnInit" });

         component.editorContext = { kind: "viewsheetOnLoad" };
         component.detach();

         expect(lastDetachPayload().editorContext).toEqual({ kind: "viewsheetOnInit" });
      });

      /* The same shape from the other host: formula-editor-dialog puts the user-editable
       * formulaName straight into the context. */
      it("detaches the minted name after the user renames the formula", () => {
         mintWith({ kind: "calcField", assembly: "Query1", name: "Margin" });

         component.editorContext = { kind: "calcField", assembly: "Query1", name: "Renamed" };
         component.detach();

         expect(lastDetachPayload().editorContext).toEqual(
            { kind: "calcField", assembly: "Query1", name: "Margin" });
      });

      /* The mint payload must be the same single read, so what was sent and what is remembered
       * cannot disagree even if the getter changes between the send and the response. */
      it("sends the same context it remembers", () => {
         mintWith({ kind: "calcField", assembly: "Query1", name: "Margin" });

         const mint = mockStompConnection.send.mock.calls
            .filter((c: any[]) => c[0] === "/events/wiz/pairing/mint").pop();

         component.detach();

         expect(lastDetachPayload().editorContext)
            .toEqual(JSON.parse(mint[2]).editorContext);
      });

      /* No code was ever minted, so there is no session of ours to end -- falling back to the
       * current value is what the pre-existing detach tests above rely on. */
      it("falls back to the current context when nothing was ever minted", () => {
         component.editorContext = { kind: "assemblyMain", assembly: "Chart1" };

         component.detach();

         expect(lastDetachPayload().editorContext)
            .toEqual({ kind: "assemblyMain", assembly: "Chart1" });
      });

      /* A new runtime resets the component's pairing state; a remembered context from the old
       * runtime would name a session that no longer exists. */
      it("forgets the minted context when the runtimeId changes", () => {
         mintWith({ kind: "viewsheetOnInit" });

         component.runtimeId = "vs-2";
         component.ngOnChanges(
            { runtimeId: { currentValue: "vs-2", previousValue: "vs-1",
                           firstChange: false, isFirstChange: () => false } } as any);
         component.editorContext = { kind: "viewsheetOnLoad" };
         component.detach();

         expect(lastDetachPayload().editorContext).toEqual({ kind: "viewsheetOnLoad" });
      });
   });

   describe("connected indicator", () => {
      /** Captures the handler for a given destination, since the component now holds two. */
      function handlerFor(dest: string): (msg: any) => void {
         const call = mockStompConnection.subscribe.mock.calls
            .filter((c: any[]) => c[0] === dest).pop();
         expect(call).toBeTruthy();
         return call[1];
      }

      function notice(body: any): any {
         return { frame: { body: JSON.stringify(body) } };
      }

      it("subscribes to the joined destination on init", () => {
         expect(mockStompConnection.subscribe).toHaveBeenCalledWith(
            "/user/commands/wiz/pairing/joined",
            expect.any(Function)
         );
      });

      it("shows connected and drops the consumed code for a matching notice", () => {
         component.code = "ABC123";

         handlerFor("/user/commands/wiz/pairing/joined")(
            notice({ runtimeId: "rt-1", sheetType: "WORKSHEET", editorContext: null }));
         fixture.detectChanges();

         expect(component.connected).toBe(true);
         expect(component.code).toBeNull();
         expect(fixture.nativeElement.querySelector(".wiz-connect-connected")).toBeTruthy();
         expect(fixture.nativeElement.querySelector(".wiz-connect-code")).toBeNull();
      });

      it("ignores a notice for another runtime", () => {
         handlerFor("/user/commands/wiz/pairing/joined")(
            notice({ runtimeId: "rt-other", sheetType: "WORKSHEET", editorContext: null }));

         expect(component.connected).toBe(false);
      });

      /*
       * The failure this filter exists for: the destination is per-user, so a pane pairing is
       * delivered to the toolbar instance too. Without the editorContext check the toolbar would
       * claim an agent joined it, which is a false statement rather than a missing feature.
       */
      it("ignores a pane notice on a toolbar instance", () => {
         handlerFor("/user/commands/wiz/pairing/joined")(
            notice({ runtimeId: "rt-1", sheetType: "WORKSHEET",
                     editorContext: { kind: "calcField", assembly: "Query1", name: "Margin" } }));

         expect(component.connected).toBe(false);
      });

      /*
       * Jackson serializes a Java record's absent components as explicit nulls, while the browser
       * never sent those keys at all. Comparing them naively (JSON.stringify, or === on each key)
       * makes every pane notice a mismatch and the indicator never appears.
       */
      it("matches a pane notice whose absent fields arrive as explicit nulls", () => {
         let handler: ((msg: any) => void) | null = null;
         mockStompConnection.subscribe.mockImplementation(
            (dest: string, h: (msg: any) => void) => {
               if(dest === "/user/commands/wiz/pairing/mint") { handler = h; }
               return { unsubscribe: vi.fn() };
            });
         component.editorContext = { kind: "assemblyMain", assembly: "Chart1" };
         component.requestCode();
         handler!(notice({ code: "ABC123" }));

         handlerFor("/user/commands/wiz/pairing/joined")(
            notice({ runtimeId: "rt-1", sheetType: "WORKSHEET",
                     editorContext: { kind: "assemblyMain", assembly: "Chart1",
                                      name: null, table: null } }));

         expect(component.connected).toBe(true);
      });

      it("clears the indicator when the runtimeId changes", () => {
         handlerFor("/user/commands/wiz/pairing/joined")(
            notice({ runtimeId: "rt-1", sheetType: "WORKSHEET", editorContext: null }));
         expect(component.connected).toBe(true);

         component.runtimeId = "rt-2";
         component.ngOnChanges(
            { runtimeId: { currentValue: "rt-2", previousValue: "rt-1",
                           firstChange: false, isFirstChange: () => false } } as any);

         expect(component.connected).toBe(false);
      });

      /*
       * The template renders the code with `*ngIf="code && !connected"`, so a stale connected flag
       * hides the next code completely. One sheet can be paired by more than one agent, so
       * re-pairing is an ordinary flow rather than an edge case.
       */
      it("clears the indicator when a new code is requested", () => {
         handlerFor("/user/commands/wiz/pairing/joined")(
            notice({ runtimeId: "rt-1", sheetType: "WORKSHEET", editorContext: null }));
         expect(component.connected).toBe(true);

         component.requestCode();

         expect(component.connected).toBe(false);
      });

      it("releases the joined subscription on destroy", () => {
         const subs: Array<{ unsubscribe: ReturnType<typeof vi.fn> }> = [];
         mockStompConnection.subscribe.mockImplementation(() => {
            const s = { unsubscribe: vi.fn() };
            subs.push(s);
            return s;
         });

         const f = TestBed.createComponent(ConnectToClaudeComponent);
         f.componentInstance.runtimeId = "rt-1";
         f.componentInstance.sheetType = "WORKSHEET";
         f.componentInstance.socketConnection = mockSocketConnection;
         f.detectChanges();
         f.destroy();

         expect(subs.some(s => s.unsubscribe.mock.calls.length > 0)).toBe(true);
      });

      /*
       * The distinction the context filter turns on. detach() shipped a real bug by reading the
       * live input instead of the minted one, which is why mintedEditorContext exists at all;
       * without these two cases, onJoined could regress to the live value and stay green.
       */
      describe("keys on the context the code was minted with", () => {
         /** Mints with one context, then leaves the live input pointing at another. */
         function mintThenDrift(minted: any, drifted: any): void {
            let mintHandler: ((msg: any) => void) | null = null;
            mockStompConnection.subscribe.mockImplementation(
               (dest: string, h: (msg: any) => void) => {
                  if(dest === "/user/commands/wiz/pairing/mint") {
                     mintHandler = h;
                  }

                  return { unsubscribe: vi.fn() };
               });

            component.editorContext = minted;
            component.requestCode();
            mintHandler!(notice({ code: "ABC123" }));
            component.editorContext = drifted;
         }

         it("accepts a notice for the minted context after the live input drifted", () => {
            mintThenDrift({ kind: "viewsheetOnInit" }, { kind: "viewsheetOnLoad" });

            handlerFor("/user/commands/wiz/pairing/joined")(
               notice({ runtimeId: "rt-1", sheetType: "WORKSHEET",
                        editorContext: { kind: "viewsheetOnInit", assembly: null,
                                         name: null, table: null } }));

            expect(component.connected).toBe(true);
         });

         it("rejects a notice matching the drifted live context but not the minted one", () => {
            mintThenDrift({ kind: "viewsheetOnInit" }, { kind: "viewsheetOnLoad" });

            handlerFor("/user/commands/wiz/pairing/joined")(
               notice({ runtimeId: "rt-1", sheetType: "WORKSHEET",
                        editorContext: { kind: "viewsheetOnLoad", assembly: null,
                                         name: null, table: null } }));

            expect(component.connected).toBe(false);
         });
      });

      /*
       * The joined destination is per-user, not per-runtime, so a runtimeId change must reset the
       * filter input and nothing else. Releasing the subscription here would leave the component
       * permanently deaf with no error anywhere.
       */
      it("keeps the joined subscription across a runtimeId change", () => {
         const byDest = new Map<string, { unsubscribe: ReturnType<typeof vi.fn> }>();
         mockStompConnection.subscribe.mockImplementation((dest: string) => {
            const sub = { unsubscribe: vi.fn() };
            byDest.set(dest, sub);
            return sub;
         });

         const f = TestBed.createComponent(ConnectToClaudeComponent);
         f.componentInstance.runtimeId = "rt-1";
         f.componentInstance.sheetType = "WORKSHEET";
         f.componentInstance.socketConnection = mockSocketConnection;
         f.detectChanges();

         const joined = byDest.get("/user/commands/wiz/pairing/joined");
         expect(joined).toBeTruthy();

         f.componentInstance.runtimeId = "rt-2";
         f.componentInstance.ngOnChanges(
            { runtimeId: { currentValue: "rt-2", previousValue: "rt-1",
                           firstChange: false, isFirstChange: () => false } } as any);

         expect(joined!.unsubscribe).not.toHaveBeenCalled();
      });
   });

});
