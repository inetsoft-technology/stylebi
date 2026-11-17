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
 * Opening the Database Query dialog for an EXISTING SQL query table hands it an `agentPairing`
 * (bug #80459, kind `worksheetSqlQuery`): the worksheet's own runtime and client, the table's
 * name, `pending: false`. Without a runtime or a client there is no pairing at all.
 */
import { ComponentTool } from "../../../../common/util/component-tool";
import { makeMocks, renderComponent } from "./ws-pane.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

async function openDialog(mocks = makeMocks(), table: any = { name: "Orders SQL", info: {} }) {
   const { comp } = await renderComponent(mocks);
   const dialog: any = {};
   vi.spyOn(ComponentTool, "showDialog").mockReturnValue(dialog);
   comp.openSqlQueryDialog(table);
   return { comp, dialog, mocks };
}

describe("WSPaneComponent - SQL query dialog pairing (bug #80459)", () => {
   it("sets agentPairing for the existing table: runtime, client, name, pending false", async () => {
      const { comp, dialog } = await openDialog();

      expect(dialog.agentPairing).toEqual({
         runtimeId: comp.worksheet.runtimeId,
         socketConnection: comp.worksheet.socketConnection,
         table: "Orders SQL",
         pending: false
      });
      expect(dialog.agentPairing.socketConnection).toBeTruthy();
   });

   it("uses the table's name at open, not another table's", async () => {
      const { dialog } = await openDialog(makeMocks(), { name: "SQL Query7", info: {} });

      expect(dialog.agentPairing.table).toBe("SQL Query7");
      expect(dialog.agentPairing.pending).toBe(false);
   });

   it("sets no pairing when the worksheet has no runtime id", async () => {
      const mocks = makeMocks();
      const { comp, dialog } = await openDialog(mocks);
      comp.worksheet.runtimeId = null;
      vi.mocked(ComponentTool.showDialog).mockReturnValue(dialog);
      comp.openSqlQueryDialog({ name: "Orders SQL", info: {} } as any);

      expect(dialog.agentPairing).toBeNull();
   });
});
