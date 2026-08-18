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

         expect(mockSocketConnection.whenConnected).not.toHaveBeenCalled();
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

});
