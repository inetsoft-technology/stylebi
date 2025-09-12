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
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { TestBed, inject } from "@angular/core/testing";
import { MatDialog, MatDialogModule } from "@angular/material/dialog";
import { SecurityActionService } from "./security-action.service";

describe("SecurityActionService", () => {
   beforeEach(() => {
      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule, MatDialogModule],
         providers: [SecurityActionService]
      });
   });

   it("should be created", inject([SecurityActionService], (service: SecurityActionService) => {
      expect(service).toBeTruthy();
   }));
});
