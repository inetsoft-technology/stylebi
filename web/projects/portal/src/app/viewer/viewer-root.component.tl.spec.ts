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
 * ViewerRootComponent — single pass (+memory-leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — ngOnInit: inPortal from route snapshot; app-loaded CSS on body;
 *                       STOMP connection stored from socket.connect()
 *   Group 2 [Risk 2] — ngOnDestroy: disconnects stored connection; safe when null
 *   Group 3 [Risk 2] — downloadStarted: opens info message dialog via ComponentTool
 *
 * Suspected bugs (header only):
 *   memory-leak — viewer-root.component.ts:51: the subscription returned by
 *     this.socket.connect().subscribe() is never stored, so ngOnDestroy() cannot
 *     call unsubscribe(). After destroy, a new connection emission would still update
 *     this.connection on the dead component instance.
 *
 * Out of scope:
 *   - splash transitionend listener: requires real DOM transitionend event; jsdom
 *     does not fire CSS transition events, so no observable side effect is assertable.
 */

import { Component, EventEmitter, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { ActivatedRoute } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render, waitFor } from "@testing-library/angular";
import { Subject } from "rxjs";

import { MessageDialog } from "../widget/dialog/message-dialog/message-dialog.component";
import { ViewerRootComponent } from "./viewer-root.component";
import { StompClientService } from "../common/viewsheet-client";
import { DownloadTargetComponent } from "../../../../shared/download/download-target.component";
import { AiAssistantPanelComponent } from "../../../../shared/ai-assistant/ai-assistant-panel.component";

// ---------------------------------------------------------------------------
// Stubs for child components declared in ViewerRootComponent.imports[]
// ---------------------------------------------------------------------------

@Component({ selector: "dl-download-target", template: "", standalone: true })
class DownloadTargetStub {
   @Output() downloadStarted = new EventEmitter<string>();
}

@Component({ selector: "ai-assistant-panel", template: "", standalone: true })
class AiAssistantPanelStub {}

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

let socketSubject: Subject<any>;

const STOMP_MOCK = { connect: vi.fn() };

const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

async function renderComponent(inPortal = false) {
   socketSubject = new Subject<any>();
   STOMP_MOCK.connect.mockReturnValue(socketSubject.asObservable());

   const result = await render(ViewerRootComponent, {
      providers: [
         { provide: StompClientService, useValue: STOMP_MOCK },
         { provide: NgbModal, useValue: MODAL_MOCK },
         { provide: ActivatedRoute, useValue: { snapshot: { data: { inPortal } } } },
      ],
      importOverrides: [
         { replace: DownloadTargetComponent, with: DownloadTargetStub },
         { replace: AiAssistantPanelComponent, with: AiAssistantPanelStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });
   return result.fixture.componentInstance;
}

beforeEach(() => {
   MODAL_MOCK.open.mockClear().mockImplementation(() => ({
      result: new Promise<any>(() => {}),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
   MessageDialog.lastMessage = null;
   MessageDialog.lastMessageTS = 0;
   // Ensure each test starts without the app-loaded class so addition tests are meaningful.
   document.body.className = document.body.className.replace(/\bapp-loaded\b/g, "").trim();
});

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit
// ---------------------------------------------------------------------------

describe("ViewerRootComponent — ngOnInit()", () => {
   it("should set inPortal=true when route snapshot has inPortal=true", async () => {
      const comp = await renderComponent(true);
      expect(comp.inPortal).toBe(true);
   });

   it("should set inPortal=false when route snapshot has inPortal=false", async () => {
      const comp = await renderComponent(false);
      expect(comp.inPortal).toBe(false);
   });

   // 🔁 Regression-sensitive: app-loaded hides the loading splash overlay; missing it
   // leaves the splash blocking all UI interaction on the first page load.
   it("should add app-loaded class to document.body when not already present", async () => {
      await renderComponent();
      expect(document.body.className).toContain("app-loaded");
   });

   it("should not duplicate app-loaded class when already present on body", async () => {
      document.body.className = "app-loaded";
      await renderComponent();
      const count = (document.body.className.match(/\bapp-loaded\b/g) || []).length;
      expect(count).toBe(1);
   });

   it("should call socket.connect with the vs-events endpoint", async () => {
      await renderComponent();
      expect(STOMP_MOCK.connect).toHaveBeenCalledWith("../vs-events");
   });

   // 🔁 Regression-sensitive: the stored connection is the only handle used by
   // ngOnDestroy to disconnect — if not stored, the server session leaks after navigate-away.
   it("should store the STOMP connection emitted by socket.connect", async () => {
      const comp = await renderComponent();
      const conn = { disconnect: vi.fn() };
      socketSubject.next(conn);
      expect((comp as any).connection).toBe(conn);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — ngOnDestroy
// ---------------------------------------------------------------------------

describe("ViewerRootComponent — ngOnDestroy()", () => {
   // 🔁 Regression-sensitive: failing to disconnect leaves a dangling STOMP session
   // open on the server, consuming connection slots indefinitely.
   it("should disconnect the stored STOMP connection on destroy", async () => {
      const comp = await renderComponent();
      const conn = { disconnect: vi.fn() };
      socketSubject.next(conn);
      comp.ngOnDestroy();
      expect(conn.disconnect).toHaveBeenCalled();
   });

   it("should not throw when no connection has been established yet", async () => {
      const comp = await renderComponent();
      // socketSubject never emits — connection stays undefined
      expect(() => comp.ngOnDestroy()).not.toThrow();
   });
});

// ---------------------------------------------------------------------------
// Group 3 — downloadStarted
// ---------------------------------------------------------------------------

describe("ViewerRootComponent — downloadStarted()", () => {
   it("should open an info message dialog when a download begins", async () => {
      const comp = await renderComponent();
      comp.downloadStarted("/report/file.xlsx");
      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalledWith(MessageDialog, expect.any(Object)));
   });
});

// ---------------------------------------------------------------------------
// Group 4 — memory-leak (it.fails — confirmed missing unsubscribe)
// ---------------------------------------------------------------------------

describe("ViewerRootComponent — memory-leak", () => {
   // Expected failure: the final expect(comp["connection"]).toBe(firstConn) fails
   // because ViewerRootComponent never stores the Subscription returned by
   // socket.connect().subscribe(), so ngOnDestroy() has no reference to call
   // unsubscribe(). After destroy, emitting a new connection from socketSubject
   // still overwrites comp.connection — the subscription is alive on a dead component.
   // If the test fails for another reason (e.g. fixture throws on render), check the
   // error message to distinguish from the expected failure.
   it.fails("should not update connection after ngOnDestroy when socket emits again", async () => {
      const comp = await renderComponent();

      const firstConn = { disconnect: vi.fn() };
      socketSubject.next(firstConn);
      expect((comp as any).connection).toBe(firstConn);

      comp.ngOnDestroy();

      const newConn = { disconnect: vi.fn() };
      socketSubject.next(newConn);

      // Fails: socket subscription was never unsubscribed, so comp.connection becomes newConn.
      expect((comp as any).connection).toBe(firstConn);
   });
});
