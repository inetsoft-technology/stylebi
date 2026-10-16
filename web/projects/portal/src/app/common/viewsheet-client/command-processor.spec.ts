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

import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../util/component-tool";
import { CommandProcessor } from "./command-processor";
import { MessageCommand } from "./message-command";

class TestCommandProcessor extends CommandProcessor {
   constructor() {
      super(null, null);
   }

   getAssemblyName(): string {
      return null;
   }

   processMessage(command: MessageCommand): void {
      this.processMessageCommand0(command, {} as NgbModal, null);
   }
}

describe("CommandProcessor.processMessageCommand0", () => {
   let processor: TestCommandProcessor;

   beforeEach(() => {
      processor = new TestCommandProcessor();
   });

   afterEach(() => {
      vi.restoreAllMocks();
   });

   // Bug #78557: the server sent a MessageCommand without a message, and the client
   // threw "Cannot read properties of null (reading 'replace')"
   it("ignores a command with a null message instead of throwing", () => {
      const dialog = vi.spyOn(ComponentTool, "showMessageDialog").mockReturnValue(null);
      const command = { type: "INFO", message: null } as MessageCommand;

      expect(() => processor.processMessage(command)).not.toThrow();
      expect(dialog).not.toHaveBeenCalled();
   });

   it("shows a dialog for an error message", () => {
      const dialog = vi.spyOn(ComponentTool, "showMessageDialog").mockReturnValue(null);
      const command = { type: "ERROR", message: "boom" } as MessageCommand;

      processor.processMessage(command);

      expect(dialog).toHaveBeenCalledTimes(1);
      expect(dialog.mock.calls[0][2]).toBe("boom");
   });
});
