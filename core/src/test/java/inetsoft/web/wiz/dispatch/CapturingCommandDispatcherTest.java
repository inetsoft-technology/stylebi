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
package inetsoft.web.wiz.dispatch;

import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the Phase 0 spike finding: composer services report failure by SENDING a
 * MessageCommand of Type.ERROR and returning normally, never by throwing. Under
 * {@code CommandDispatcher.withDummyDispatcher} those commands are dropped, so every failure
 * becomes a silent success. This dispatcher captures instead of dropping so the failure can be
 * surfaced.
 */
@Tag("core")
class CapturingCommandDispatcherTest {
   @Test
   void capturesSentCommandInsteadOfDiscardingIt() throws Exception {
      MessageCommand info = message(MessageCommand.Type.INFO, "hello");

      List<CommandDispatcher.Command> captured =
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            dispatcher.sendCommand(info);
            return dispatcher.getCapturedCommands();
         });

      assertEquals(1, captured.size(), "the command should have been captured, not dropped");
      assertSame(info, captured.get(0).getCommand());
   }

   @Test
   void throwsWhenTheWrappedCallReportedAnError() {
      CommandErrorException thrown = assertThrows(CommandErrorException.class, () ->
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            // Exactly what a composer service does on failure: send an ERROR, then return
            // normally. Under withDummyDispatcher this is indistinguishable from success.
            dispatcher.sendCommand(message(MessageCommand.Type.ERROR, "dependency cycle"));
            return "service returned normally";
         }));

      assertTrue(thrown.getMessage().contains("dependency cycle"),
                 "the reported failure should reach the caller, got: " + thrown.getMessage());
   }

   @Test
   void surfacesWarningsWithoutFailingTheCall() throws Exception {
      List<String> warnings =
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            dispatcher.sendCommand(message(MessageCommand.Type.WARNING, "ranking is global"));
            return dispatcher.getWarnings();
         });

      assertEquals(List.of("ranking is global"), warnings);
   }

   private static MessageCommand message(MessageCommand.Type type, String text) {
      MessageCommand command = new MessageCommand();
      command.setType(type);
      command.setMessage(text);
      return command;
   }

   /**
    * A minimal principal. {@code SRPrincipal} pulls in the Spring context via
    * {@code XSessionService}, and the dispatcher only ever reads {@code getName()}.
    */
   private static Principal principal() {
      return () -> "admin";
   }
}
