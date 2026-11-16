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
import { Subject } from "rxjs";
import { ViewsheetCommandMessage } from "../../../common/viewsheet-client";
import { AgentScriptPush, sqlAgentContext } from "./agent-script-push";
import { EditorContext } from "./editor-context";

const CONTEXT: EditorContext = { kind: "worksheetSqlQuery", assembly: "SQL Query1", pending: true };

function setup(initial = "select 1", context: EditorContext = CONTEXT) {
   const commands = new Subject<ViewsheetCommandMessage>();
   const box = { text: initial };
   const apply = vi.fn((t: string) => { box.text = t; });
   const push = new AgentScriptPush({ commands } as any, () => context, () => box.text, apply);
   const send = (command: any) => commands.next(
      new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand", command));
   return { push, box, apply, send, commands };
}

const cmd = (text: string, overrides: any = {}) =>
   ({ kind: "worksheetSqlQuery", assembly: "SQL Query1", text, ...overrides });

describe("AgentScriptPush", () => {
   it("builds the worksheetSqlQuery context of a pairing", () => {
      expect(sqlAgentContext({ runtimeId: "W", socketConnection: null, table: "T", pending: false }))
         .toEqual({ kind: "worksheetSqlQuery", assembly: "T", pending: false });
   });

   it("applies a push for its exact context when the text is untouched", () => {
      const { push, box, apply, send } = setup();

      send(cmd("select 2"));

      expect(apply).toHaveBeenCalledWith("select 2");
      expect(box.text).toBe("select 2");
      expect(push.staleText).toBeNull();
   });

   it("keeps applying consecutive pushes (the last applied text is the new baseline)", () => {
      const { box, send } = setup();

      send(cmd("select 2"));
      send(cmd("select 3"));

      expect(box.text).toBe("select 3");
   });

   it.each([
      ["another assembly", { assembly: "Other" }],
      ["another kind", { kind: "dynamicValue" }],
      ["a name", { name: "x" }]
   ])("ignores a push for %s", (_label, overrides) => {
      const { push, box, apply, send } = setup();

      send(cmd("select 2", overrides));

      expect(apply).not.toHaveBeenCalled();
      expect(box.text).toBe("select 1");
      expect(push.staleText).toBeNull();
   });

   it("ignores other command types", () => {
      const { apply, commands } = setup();

      commands.next(new ViewsheetCommandMessage(null, "RefreshBindingTreeCommand", cmd("x")));

      expect(apply).not.toHaveBeenCalled();
   });

   it("parks the push in staleText when the user typed, keeping their text", () => {
      const { push, box, apply, send } = setup();
      box.text = "select typed";

      send(cmd("select agent"));

      expect(apply).not.toHaveBeenCalled();
      expect(box.text).toBe("select typed");
      expect(push.staleText).toBe("select agent");
   });

   it("a newer parked push replaces the older one", () => {
      const { push, box, send } = setup();
      box.text = "select typed";

      send(cmd("agent 1"));
      send(cmd("agent 2"));

      expect(push.staleText).toBe("agent 2");
   });

   it("reload applies the parked text, clears it and resets the baseline", () => {
      const { push, box, apply, send } = setup();
      box.text = "select typed";
      send(cmd("select agent"));

      push.reload();

      expect(apply).toHaveBeenCalledWith("select agent");
      expect(box.text).toBe("select agent");
      expect(push.staleText).toBeNull();

      send(cmd("select next"));

      expect(box.text).toBe("select next");
   });

   it("reload with nothing parked does nothing", () => {
      const { push, apply } = setup();

      push.reload();

      expect(apply).not.toHaveBeenCalled();
   });

   it("reads the context live: a changed context changes what is accepted", () => {
      let context: EditorContext = CONTEXT;
      const commands = new Subject<ViewsheetCommandMessage>();
      const apply = vi.fn();
      const push = new AgentScriptPush({ commands } as any, () => context, () => "a", apply);
      context = { kind: "worksheetSqlQuery", assembly: "SQL Query2", pending: true };

      commands.next(new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand", cmd("x")));
      expect(apply).not.toHaveBeenCalled();

      commands.next(new ViewsheetCommandMessage(null, "RefreshAgentScriptCommand",
         cmd("y", { assembly: "SQL Query2" })));
      expect(apply).toHaveBeenCalledWith("y");
      push.destroy();
   });

   it("stops applying after destroy()", () => {
      const { push, apply, send } = setup();

      push.destroy();
      send(cmd("select late"));

      expect(apply).not.toHaveBeenCalled();
   });

   it("tolerates a socket without a commands stream", () => {
      expect(() => new AgentScriptPush({} as any, () => CONTEXT, () => "", vi.fn())).not.toThrow();
   });
});
