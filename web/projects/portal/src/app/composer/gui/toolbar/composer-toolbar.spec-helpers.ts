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
 * Shared test fixtures for ComposerToolbarComponent spec files.
 *
 * Imported by the three split spec files (interaction / risk / display) so that
 * makeMocks() and renderComponent() stay in sync across all three suites.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render } from "@testing-library/angular";
import { NgbModal, NgbTooltipConfig } from "@ng-bootstrap/ng-bootstrap";
import { of, Subject } from "rxjs";

import { ModelService } from "../../../widget/services/model.service";
import { ScaleService } from "../../../widget/services/scale/scale-service";
import { FullScreenService } from "../../../common/services/full-screen.service";
import { ComposerToolbarService } from "../composer-toolbar.service";
import { EventQueueService } from "../vs/event-queue.service";
import { DropdownObserver } from "../../../widget/services/dropdown-observer.service";
import { ChatService } from "../../../common/chat/chat.service";
import { AiAssistantDialogService } from "../../../common/services/ai-assistant-dialog.service";
import { Viewsheet } from "../../data/vs/viewsheet";
import { ComposerTabModel } from "../composer-tab-model";
import { ComposerToolbarComponent } from "./composer-toolbar.component";

export function makeMocks() {
   return {
      modelService: {
         getModel: vi.fn(() => of([])),
         sendModel: vi.fn(() => of({ body: null })),
         errorHandler: null as any,
      },
      modalService: {
         open: vi.fn().mockReturnValue({
            result: new Promise<never>((_, reject) => reject("cancel")),
            componentInstance: {
               onCommit: { subscribe: vi.fn(() => ({ unsubscribe: vi.fn() })) },
               onCancel: { subscribe: vi.fn(() => ({ unsubscribe: vi.fn() })) },
            },
         }),
      },
      fullScreenService: {
         fullScreenChange: new Subject<void>(),
         fullScreenMode: false,
         enterFullScreen: vi.fn(),
         exitFullScreen: vi.fn(),
      },
      composerToolbarService: {
         jdbcExists: true,
         sqlEnabled: true,
         crossJoinEnabled: false,
      },
      eventQueueService: { addResizeEvent: vi.fn() },
      scaleService: {
         getScale: vi.fn(() => of(1)),
         setScale: vi.fn(),
      },
      chatService: {
         isChatOngoing: vi.fn(() => false),
         openSession: vi.fn(),
      },
      dropdownObserver: {
         onDropdownOpened: vi.fn(),
         onDropdownClosed: vi.fn(),
      },
   };
}

export type ComposerToolbarMocks = ReturnType<typeof makeMocks>;

export async function renderComponent(
   componentProperties: Record<string, any> = {},
   mocks = makeMocks()
) {
   const defaultVs = new Viewsheet();
   defaultVs.localId = 1;

   const result = await render(ComposerToolbarComponent, {
      componentProperties: {
         focusedTab: new ComposerTabModel("viewsheet", defaultVs),
         ...componentProperties,
      },
      componentImports: [],
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

   const comp = result.fixture.componentInstance as ComposerToolbarComponent;
   return { fixture: result.fixture, comp, mocks };
}
