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

import { HttpClient, provideHttpClient } from "@angular/common/http";
import { TestBed } from "@angular/core/testing";
import { UntypedFormBuilder } from "@angular/forms";
import { Observable } from "rxjs";

import { DriverWizardComponent } from "./driver-wizard.component";

type DriverWizardPrivateApi = {
   createDriver(): void;
   scanDrivers(id: string): Observable<string[]>;
   searchMaven(query: string): Observable<string[]>;
   uploadDrivers(): void;
   uploadId: string | null;
};

type DriverWizardNotifications = {
   notifications: {
      danger: ReturnType<typeof vi.fn>;
   };
};

type DriverWizardFileLike = {
   name: string;
};

type DriverWizardFileEvent = {
   target: {
      files: DriverWizardFileLike[];
   };
};

type DriverWizardMouseEventOverrides = Pick<MouseEvent, "ctrlKey" | "metaKey" | "shiftKey">;

export function createDriverWizardComponent(): DriverWizardComponent {
   TestBed.resetTestingModule();
   TestBed.configureTestingModule({
      providers: [provideHttpClient(), UntypedFormBuilder],
   });

   return new DriverWizardComponent(
      TestBed.inject(HttpClient),
      TestBed.inject(UntypedFormBuilder),
   );
}

export function asDriverWizardPrivateApi(comp: DriverWizardComponent): DriverWizardPrivateApi {
   // TL coverage needs direct access to private HTTP flows without changing production visibility.
   return comp as unknown as DriverWizardPrivateApi;
}

export function attachDriverWizardNotifications(
   comp: DriverWizardComponent,
   notifications: DriverWizardNotifications,
): void {
   // The component writes to the @ViewChild field; tests inject the minimal notification surface directly.
   (comp as unknown as { dataNotifications: DriverWizardNotifications }).dataNotifications = notifications;
}

export function makeDriverWizardNotificationsStub(): DriverWizardNotifications {
   return {
      notifications: {
         danger: vi.fn(),
      },
   };
}

export function makeDriverWizardFile(name: string): DriverWizardFileLike {
   return { name };
}

export function makeDriverWizardFileEvent(
   files: DriverWizardFileLike[],
): DriverWizardFileEvent {
   return {
      target: {
         files,
      },
   };
}

export function makeDriverWizardMouseEvent(
   overrides: Partial<DriverWizardMouseEventOverrides> = {},
): MouseEvent {
   return {
      ctrlKey: false,
      metaKey: false,
      shiftKey: false,
      ...overrides,
   } as MouseEvent;
}
