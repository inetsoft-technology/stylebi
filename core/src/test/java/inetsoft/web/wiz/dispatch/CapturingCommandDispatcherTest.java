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

import inetsoft.report.composition.ExpiredSheetException;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.uql.viewsheet.ColumnNotFoundException;
import inetsoft.util.InvalidUserException;
import inetsoft.util.MessageException;
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

   /**
    * {@code CommandDispatcher.detach()} returns a copy of the BASE class, so anything sent
    * through it is neither captured nor inspected — the exact hole this class exists to close.
    * It is reached in practice, not hypothetically: {@code CoreLifecycleService.createList} calls
    * {@code detach()}, and that is on the add, remove, group and rename paths — rename being the
    * op this feature's own description calls out as the one where capture matters most. An ERROR
    * dispatched during that refresh was dropped and the op reported success.
    *
    * <p>This dispatcher never transmits — {@code send} and {@code convertAndSendToUser} are both
    * no-ops — so returning itself is safe and keeps one captured list rather than a copy nobody
    * reads.
    */
   @Test
   void detachStaysCapturingSoRefreshErrorsAreNotDropped() {
      CommandErrorException thrown = assertThrows(CommandErrorException.class, () ->
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            dispatcher.detach().sendCommand(message(MessageCommand.Type.ERROR, "from refresh"));
            return null;
         }));

      assertTrue(thrown.getErrors().contains("from refresh"),
                 "an error sent through the detached copy must still be captured, got: " +
                 thrown.getErrors());
   }

   /**
    * The base {@code commands} list stays empty because {@code sendCommand} deliberately skips
    * {@code super}, so the inherited {@code iterator()}/{@code stream()} saw nothing. Framework
    * code reads exactly those: {@code CoreLifecycleService} uses {@code stream()} to decide
    * {@code checkMVHandled} — always false here, which re-sends {@code CheckMVEvent} and adds a
    * SECOND checkpoint, breaking the one-checkpoint-per-call promise {@code mutate} makes — and
    * iterates the dispatcher to detect {@code InitGridCommand}.
    */
   @Test
   void theDispatcherIteratesItsOwnCapturedCommands() throws Exception {
      long[] counts = CapturingCommandDispatcher.withCapturingDispatcher(principal(), d -> {
         d.sendCommand(message(MessageCommand.Type.INFO, "one"));
         d.sendCommand(message(MessageCommand.Type.INFO, "two"));

         long iterated = 0;

         for(CommandDispatcher.Command ignored : d) {
            iterated++;
         }

         return new long[]{ iterated, d.stream().count() };
      });

      assertEquals(2, counts[0], "iterator() must see the captured commands");
      assertEquals(2, counts[1], "stream() must see the captured commands");
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

   /** Bug #78306: a follow-on NPE must not hide the ERROR the service already dispatched. */
   @Test
   void capturedErrorWinsOverAFollowOnNullPointerException() {
      CommandErrorException thrown = assertThrows(CommandErrorException.class, () ->
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            dispatcher.sendCommand(message(MessageCommand.Type.ERROR, "real execute failure"));
            throw new NullPointerException("follow-on");
         }));

      assertTrue(thrown.getMessage().contains("real execute failure"), thrown.getMessage());
      assertInstanceOf(NullPointerException.class, thrown.getCause());
   }

   @Test
   void capturedErrorWinsOverAFollowOnIllegalState() {
      assertThrows(CommandErrorException.class, () ->
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            dispatcher.sendCommand(message(MessageCommand.Type.ERROR, "real execute failure"));
            throw new IllegalStateException("follow-on");
         }));
   }

   /** Exceptions with their own handler (403, 400, ...) keep their type after an ERROR. */
   @Test
   void typedExceptionsStillPropagateAfterAnError() {
      assertThrows(SecurityException.class, () ->
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            dispatcher.sendCommand(message(MessageCommand.Type.ERROR, "e"));
            throw new SecurityException("denied");
         }));
      assertThrows(IllegalArgumentException.class, () ->
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            dispatcher.sendCommand(message(MessageCommand.Type.ERROR, "e"));
            throw new IllegalArgumentException("bad input");
         }));
      assertThrows(InvalidUserException.class, () ->
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            dispatcher.sendCommand(message(MessageCommand.Type.ERROR, "e"));
            throw new InvalidUserException("stale", (java.security.Principal) null);
         }));
   }

   @Test
   void controlFlowExceptionsPropagateUnchanged() {
      assertThrows(ExpiredSheetException.class, () ->
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            dispatcher.sendCommand(message(MessageCommand.Type.ERROR, "e"));
            throw new ExpiredSheetException("id", null);
         }));
      assertThrows(ConfirmException.class, () ->
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            throw new ConfirmException("confirm");
         }));
   }

   @Test
   void columnNotFoundBecomesACommandErrorWhenNoErrorWasCaptured() {
      CommandErrorException thrown = assertThrows(CommandErrorException.class, () ->
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            throw new ColumnNotFoundException("col X");
         }));

      assertTrue(thrown.getMessage().contains("col X"), thrown.getMessage());
      assertInstanceOf(ColumnNotFoundException.class, thrown.getCause());
   }

   @Test
   void messageExceptionBecomesACommandErrorWhenNoErrorWasCaptured() {
      CommandErrorException thrown = assertThrows(CommandErrorException.class, () ->
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            throw new MessageException("user facing");
         }));

      assertTrue(thrown.getMessage().contains("user facing"), thrown.getMessage());
   }

   @Test
   void unrelatedExceptionWithoutCapturedErrorsIsUnchanged() {
      assertThrows(IndexOutOfBoundsException.class, () ->
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            throw new IndexOutOfBoundsException("plain");
         }));
   }

   /** An ERROR whose message is null (e.g. from an NPE's getMessage()) must not NPE the wrapper. */
   @Test
   void nullErrorMessageStillYieldsACommandError() {
      CommandErrorException thrown = assertThrows(CommandErrorException.class, () ->
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            dispatcher.sendCommand(message(MessageCommand.Type.ERROR, null));
            return null;
         }));

      assertFalse(thrown.getErrors().isEmpty());
      assertTrue(thrown.getErrors().stream().allMatch(m -> m != null && !m.isBlank()));
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
