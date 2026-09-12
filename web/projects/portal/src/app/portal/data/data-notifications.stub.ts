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
 * Shared Angular stub for DataNotificationsComponent.
 *
 * Import this instead of defining a local copy in test-helpers files. All four
 * notification methods (info, success, danger, warning) are present so any call
 * path in the component under test does not throw.
 *
 * Usage:
 *   importOverrides: [{ replace: DataNotificationsComponent, with: StubDataNotificationsComponent }]
 */

import { Component } from "@angular/core";
import { vi } from "vitest";

@Component({ selector: "data-notifications", template: "", standalone: true })
export class StubDataNotificationsComponent {
   notifications = {
      info: vi.fn(),
      success: vi.fn(),
      danger: vi.fn(),
      warning: vi.fn(),
   };
}
