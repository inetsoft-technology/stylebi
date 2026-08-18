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

import { Worksheet } from "./worksheet";

describe("Sheet copy constructor", () => {
   // closeOnServer defaults to true and is set to false only for a sheet whose runtime the
   // SERVER opened (open_base_worksheet). The copy constructor carries runtimeId, so a copy that
   // drops closeOnServer still points at the server's runtime while claiming the right to close
   // it -- and the copies are made constantly: processUpdateUndoStateCommand builds one on every
   // UpdateUndoStateCommand, which the agent broadcasts after every edit it makes. The flag is
   // correct when the tab opens and wrong from the first edit onward, so closing the tab then
   // destroys the runtime the agent is paired to.
   it("preserves closeOnServer, which decides whether closing a tab kills the runtime", () => {
      const original = new Worksheet();
      original.runtimeId = "ws-server-1";
      original.closeOnServer = false;

      const copy = new Worksheet(original);

      expect(copy.runtimeId).toBe("ws-server-1");
      expect(copy.closeOnServer).toBe(false);
   });

   it("preserves closedOnServer, the guard against closing a runtime twice", () => {
      const original = new Worksheet();
      original.closedOnServer = true;

      const copy = new Worksheet(original);

      expect(copy.closedOnServer).toBe(true);
   });

   it("still defaults closeOnServer to true for a browser-opened sheet", () => {
      expect(new Worksheet().closeOnServer).toBe(true);
      expect(new Worksheet(new Worksheet()).closeOnServer).toBe(true);
   });
});
