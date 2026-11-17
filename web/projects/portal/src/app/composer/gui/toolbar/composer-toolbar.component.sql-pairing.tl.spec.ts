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
 * The NEW database query dialog the toolbar opens (bug #80459, kind `worksheetSqlQuery`) is bound
 * to `sqlAgentPairing`: the worksheet's runtime and client, the pre-assigned table name, `pending:
 * true`. The binding sits in an ng-template, so the getter must hand back the SAME object until
 * something it is built from changes (a fresh object per change-detection pass is NG0100).
 */
import { SQLQueryDialog } from "../../../widget/dialog/sql-query-dialog/sql-query-dialog.component";
import { Worksheet } from "../../data/ws/worksheet";
import { provideHttpClient } from "@angular/common/http";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { NgbModal, NgbTooltipConfig } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { ChatService } from "../../../common/chat/chat.service";
import { AiAssistantDialogService } from "../../../common/services/ai-assistant-dialog.service";
import { FullScreenService } from "../../../common/services/full-screen.service";
import { DropdownObserver } from "../../../widget/services/dropdown-observer.service";
import { ModelService } from "../../../widget/services/model.service";
import { ScaleService } from "../../../widget/services/scale/scale-service";
import { ComposerToolbarService } from "../composer-toolbar.service";
import { EventQueueService } from "../vs/event-queue.service";
import { ComposerToolbarComponent } from "./composer-toolbar.component";
import { makeMocks, renderComponent } from "./composer-toolbar.spec-helpers";

// Same providers as renderComponent, but WITHOUT `componentImports: []`, so the template's real
// child components (<sql-query-dialog>) are instantiated instead of ignored as unknown elements.
async function renderWithRealChildren() {
   const mocks = makeMocks();
   const result = await render(ComposerToolbarComponent, {
      componentProviders: [
         { provide: FullScreenService, useValue: mocks.fullScreenService },
         { provide: NgbTooltipConfig, useValue: {} },
      ],
      providers: [
         { provide: ModelService, useValue: mocks.modelService },
         { provide: NgbModal, useValue: mocks.modalService },
         { provide: EventQueueService, useValue: mocks.eventQueueService },
         { provide: ComposerToolbarService, useValue: mocks.composerToolbarService },
         { provide: ScaleService, useValue: mocks.scaleService },
         { provide: ChatService, useValue: mocks.chatService },
         { provide: DropdownObserver, useValue: mocks.dropdownObserver },
         { provide: AiAssistantDialogService, useValue: {} },
         provideHttpClient(),
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });
   return { comp: result.fixture.componentInstance as ComposerToolbarComponent };
}

function worksheet(overrides: Record<string, any> = {}): Worksheet {
   const ws = new Worksheet();
   (ws as any).init = true;
   ws.runtimeId = "Worksheet1";
   ws.socketConnection = { sendEvent: vi.fn() } as any;
   Object.assign(ws, overrides);
   return ws;
}

describe("ComposerToolbarComponent - new database query pairing (bug #80459)", () => {
   it("is null before any query dialog was opened (no table name yet)", async () => {
      const { comp } = await renderComponent();
      comp.sheet = worksheet();

      expect(comp.sqlAgentPairing).toBeNull();
   });

   it("opening a new query sets the pairing with the pre-assigned name and pending true", async () => {
      const { comp } = await renderComponent();
      const ws = worksheet();
      comp.sheet = ws;

      comp.newDatabaseQuery("ds1", false);

      expect(comp.newTableName).toBe("SQL Query1");
      expect(comp.sqlAgentPairing).toEqual({
         runtimeId: "Worksheet1",
         socketConnection: ws.socketConnection,
         table: "SQL Query1",
         pending: true
      });
   });

   it("returns the same object on every read while nothing changed (no NG0100)", async () => {
      const { comp } = await renderComponent();
      comp.sheet = worksheet();
      comp.newDatabaseQuery("ds1", false);

      expect(comp.sqlAgentPairing).toBe(comp.sqlAgentPairing);
   });

   it("builds a new pairing when the next query gets another pre-assigned name", async () => {
      const { comp } = await renderComponent();
      comp.sheet = worksheet();
      comp.newDatabaseQuery("ds1", false);
      const first = comp.sqlAgentPairing;

      comp.newTableName = "SQL Query2";

      expect(comp.sqlAgentPairing).not.toBe(first);
      expect(comp.sqlAgentPairing.table).toBe("SQL Query2");
   });

   it("is null when the worksheet has no runtime id or no client", async () => {
      const { comp } = await renderComponent();
      comp.sheet = worksheet({ runtimeId: null });
      comp.newTableName = "SQL Query1";

      expect(comp.sqlAgentPairing).toBeNull();

      comp.sheet = worksheet({ socketConnection: null });

      expect(comp.sqlAgentPairing).toBeNull();
   });

   it("the <sql-query-dialog> of the New Database Query template receives the toolbar's pairing", async () => {
      const captured: SQLQueryDialog[] = [];
      vi.spyOn(SQLQueryDialog.prototype, "ngOnInit").mockImplementation(function(this: SQLQueryDialog) {
         captured.push(this);
      });
      const { comp } = await renderWithRealChildren();
      const ws = worksheet();
      comp.sheet = ws;
      comp.newDatabaseQuery("ds1", false);

      const view = comp.sqlQueryDialog.createEmbeddedView({ close: vi.fn(), dismiss: vi.fn() });
      view.detectChanges();

      expect(captured).toHaveLength(1);
      expect(captured[0].agentPairing).toBe(comp.sqlAgentPairing);
      expect(captured[0].agentPairing).toEqual({
         runtimeId: "Worksheet1", socketConnection: ws.socketConnection,
         table: "SQL Query1", pending: true
      });
      view.destroy();
   });
});
