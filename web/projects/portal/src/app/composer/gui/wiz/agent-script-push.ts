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
import { Subscription } from "rxjs";
import { ViewsheetClientService } from "../../../common/viewsheet-client/viewsheet-client.service";
import { ViewsheetCommandMessage } from "../../../common/viewsheet-client/viewsheet-command-message";
import { RefreshAgentScriptCommand } from "../../../vsobjects/command/refresh-agent-script-command";
import { EditorContext, SqlAgentPairing } from "./editor-context";
import { matchesAgentScriptRefresh, shouldApplyAgentScriptPush } from "./agent-script-refresh";

/** The `worksheetSqlQuery` editor context of a Database Query dialog pairing. */
export function sqlAgentContext(pairing: SqlAgentPairing): EditorContext {
   return { kind: "worksheetSqlQuery", assembly: pairing.table, pending: pairing.pending };
}

/**
 * Receives the text an agent pushes for ONE editor (matched by its exact editor context) and
 * applies it to a plain text box, or, when the human has typed since the last applied push, parks
 * it in `staleText` for an explicit `reload()`. The same two functions and the same rule as
 * `FormulaEditorDialog.onAgentScriptRefresh`, which this deliberately duplicates instead of
 * refactoring.
 */
export class AgentScriptPush {
   staleText: string | null = null;
   private lastKnown: string;
   private readonly subscription = new Subscription();

   constructor(socket: ViewsheetClientService,
               private readonly context: () => EditorContext,
               private readonly text: () => string,
               private readonly apply: (text: string) => void)
   {
      this.lastKnown = text();

      if(socket?.commands) {
         this.subscription.add(socket.commands.subscribe((message: ViewsheetCommandMessage) => {
            if(message.type === "RefreshAgentScriptCommand") {
               this.onRefresh(message.command as RefreshAgentScriptCommand);
            }
         }));
      }
   }

   private onRefresh(command: RefreshAgentScriptCommand): void {
      if(!matchesAgentScriptRefresh(command, this.context())) {
         return;
      }

      if(!shouldApplyAgentScriptPush(this.text(), this.lastKnown)) {
         this.staleText = command.text;
         return;
      }

      this.applyText(command.text);
   }

   /** The human's explicit choice to accept a parked push, discarding what they typed. */
   reload(): void {
      if(this.staleText == null) {
         return;
      }

      this.applyText(this.staleText);
   }

   private applyText(text: string): void {
      this.apply(text);
      this.lastKnown = this.text();
      this.staleText = null;
   }

   destroy(): void {
      this.subscription.unsubscribe();
   }
}
