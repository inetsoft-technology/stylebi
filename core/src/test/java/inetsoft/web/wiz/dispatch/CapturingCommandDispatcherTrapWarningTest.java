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

import inetsoft.web.binding.command.VSBindingTrapCommand;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.wiz.viewsheet.AgentTrapWarning;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #80476: a binding write sends a {@code VSBindingTrapCommand} (post-apply) that the capturing
 * dispatcher used to drop. It is now reported as at most one {@code Data-model trap:} warning per
 * call, only when the command's model is non-null (a NEW trap, as in the Composer).
 */
@Tag("core")
class CapturingCommandDispatcherTrapWarningTest {
   @Test
   void aNewBindingTrapIsOneWarning() throws Exception {
      List<String> warnings = run(d -> d.sendCommand("Table1", newTrap()));

      assertEquals(1, warnings.size(), warnings.toString());
      assertTrue(warnings.get(0).startsWith("Data-model trap: "), warnings.get(0));
      assertTrue(warnings.get(0).contains("The write was applied"), warnings.get(0));
      assertTrue(warnings.get(0).contains("undo"), warnings.get(0));
   }

   @Test
   void aCommandWithoutAModelIsNotATrap() throws Exception {
      assertTrue(run(d -> d.sendCommand("Table1", new VSBindingTrapCommand(null, null)))
                    .isEmpty());
   }

   @Test
   void noTrapCommandMeansNoWarning() throws Exception {
      assertTrue(run(d -> d.sendCommand(message(MessageCommand.Type.INFO, "hello"))).isEmpty());
   }

   @Test
   void severalTrapCommandsCollapseToOne() throws Exception {
      List<String> warnings = run(d -> {
         d.sendCommand("Chart1", newTrap());
         d.sendCommand("Chart1", new VSBindingTrapCommand(null, null));
         d.sendCommand("Chart1", newTrap());
      });

      assertEquals(1, warnings.size(), warnings.toString());
   }

   /** A table-naming warning from the property hook is kept; the generic one is dropped. */
   @Test
   void aTableNamingWarningWinsOverTheGenericOne() throws Exception {
      String naming = AgentTrapWarning.forCondition("Chasm Trap is on Orders, Returns, Customer");

      List<String> warnings = run(d -> {
         d.sendCommand(message(MessageCommand.Type.WARNING, naming));
         d.sendCommand("Table1", newTrap());
         d.sendCommand(message(MessageCommand.Type.WARNING, naming + " again"));
      });

      assertEquals(List.of(naming), warnings);
   }

   @Test
   void otherWarningsAreLeftAlone() throws Exception {
      List<String> warnings = run(d -> {
         d.sendCommand(message(MessageCommand.Type.WARNING, "something else"));
         d.sendCommand("Table1", newTrap());
      });

      assertEquals(2, warnings.size(), warnings.toString());
      assertEquals("something else", warnings.get(0));
      assertTrue(AgentTrapWarning.isTrapWarning(warnings.get(1)));
   }

   /** The Composer's own dispatcher keeps the confirm dialogs, so it does not report. */
   @Test
   void onlyTheCapturingDispatcherReportsModelTraps() throws Exception {
      assertTrue(CapturingCommandDispatcher.withCapturingDispatcher(
         principal(), CapturingCommandDispatcher::reportsModelTrap));
      assertFalse(mock(inetsoft.web.viewsheet.service.CommandDispatcher.class,
                       org.mockito.Mockito.CALLS_REAL_METHODS).reportsModelTrap());
   }

   private static VSBindingTrapCommand newTrap() {
      return new VSBindingTrapCommand(mock(BindingModel.class), null);
   }

   private static MessageCommand message(MessageCommand.Type type, String text) {
      MessageCommand command = new MessageCommand();
      command.setType(type);
      command.setMessage(text);
      return command;
   }

   private static List<String> run(Sender sender) throws Exception {
      return CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
         sender.send(dispatcher);
         return dispatcher.getWarnings();
      });
   }

   private static Principal principal() {
      return () -> "admin";
   }

   @FunctionalInterface
   private interface Sender {
      void send(CapturingCommandDispatcher dispatcher) throws Exception;
   }
}
