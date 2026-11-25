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

import inetsoft.report.composition.WorksheetService;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.util.MessageException;
import inetsoft.util.log.LogLevel;
import inetsoft.util.script.ScriptException;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.controller.table.BaseTableService;
import inetsoft.web.wiz.service.RenderWaitSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82316: the server-side Console buffer is fed from the ASSET_EXCEPTIONS thread-local, from
 * the capturing dispatcher and from {@code AssetScriptFailureWarnings.collect}.
 */
@Tag("core")
class ConsoleMessageCaptureTest {
   private static final String FAILURE = BaseTableService.SCRIPT_FAILURE_PREFIX + "B5Chart" +
      "\nScript failed:\nReferenceError: noSuchFunctionB7 is not defined (line 1)";

   private final String runtimeId = "rt-" + UUID.randomUUID();

   private ConsoleMessageBuffer.Snapshot read() {
      return ConsoleMessageCapture.buffer(runtimeId).read(null, 200, 0);
   }

   @Test
   void aScriptFailureInTheListIsRecordedAsInfoNamingTheAssembly() throws Exception {
      ConsoleMessageCapture.scope(runtimeId, () -> {
         WorksheetService.ASSET_EXCEPTIONS.get().add(new ScriptException(FAILURE));
         return null;
      });

      ConsoleMessageBuffer.Snapshot snapshot = read();
      assertEquals(1, snapshot.entries().size());
      ConsoleMessageBuffer.Entry entry = snapshot.entries().get(0);
      assertEquals("INFO", entry.type());
      assertEquals("B5Chart", entry.assembly());
      assertTrue(entry.message().contains("noSuchFunctionB7 is not defined"), entry.message());
      assertEquals(1, snapshot.info());
      assertEquals(0, snapshot.error());
   }

   /** The list is null outside a scope, which is why the failure used to vanish. */
   @Test
   void theListIsInstalledOnlyInsideTheScopeAndRestoredAfter() throws Exception {
      assertNull(WorksheetService.ASSET_EXCEPTIONS.get());

      ConsoleMessageCapture.scope(runtimeId, () -> {
         assertNotNull(WorksheetService.ASSET_EXCEPTIONS.get());
         return null;
      });

      assertNull(WorksheetService.ASSET_EXCEPTIONS.get());
   }

   @Test
   void aScopeInsideAnOuterListKeepsTheOuterListAndPassesItsExceptionsOn() throws Exception {
      List<Exception> outer = new ArrayList<>();
      WorksheetService.ASSET_EXCEPTIONS.set(outer);

      try {
         ConsoleMessageCapture.scope(runtimeId, () -> {
            WorksheetService.ASSET_EXCEPTIONS.get().add(new ScriptException(FAILURE));
            return null;
         });

         assertSame(outer, WorksheetService.ASSET_EXCEPTIONS.get());
         assertEquals(1, outer.size());
         assertEquals(1, read().entries().size());
      }
      finally {
         WorksheetService.ASSET_EXCEPTIONS.remove();
      }
   }

   /** Amendment 5: only a script failure is a row; confirm and lock-owner entries are not. */
   @Test
   void confirmAndPlainExceptionsAreNotRecorded() throws Exception {
      ConsoleMessageCapture.scope(runtimeId, () -> {
         List<Exception> list = WorksheetService.ASSET_EXCEPTIONS.get();
         list.add(new ConfirmException("locked by another user", ConfirmException.INFO));
         list.add(new RuntimeException("query failed"));
         return null;
      });

      assertEquals(0, read().entries().size());
   }

   /** Amendment 3: the render retry loop runs the failing script several times in one call. */
   @Test
   void theSameFailureRepeatedInOneScopeIsOneRowButALaterScopeAddsAnother() throws Exception {
      ConsoleMessageCapture.scope(runtimeId, () -> {
         for(int i = 0; i < 4; i++) {
            WorksheetService.ASSET_EXCEPTIONS.get().add(new ScriptException(FAILURE));
         }

         return null;
      });

      assertEquals(1, read().entries().size());

      ConsoleMessageCapture.scope(runtimeId, () -> {
         WorksheetService.ASSET_EXCEPTIONS.get().add(new ScriptException(FAILURE));
         return null;
      });

      assertEquals(2, read().entries().size());
      assertEquals(2, read().info());
   }

   @Test
   void twoDifferentFailuresOfOneAssemblyAreTwoRows() throws Exception {
      ConsoleMessageCapture.scope(runtimeId, () -> {
         List<Exception> list = WorksheetService.ASSET_EXCEPTIONS.get();
         list.add(new ScriptException(FAILURE));
         list.add(new ScriptException(FAILURE.replace("noSuchFunctionB7", "other")));
         return null;
      });

      assertEquals(2, read().entries().size());
   }

   /** Amendment 2: collect consumes its own list, so an outer scope would see nothing from it. */
   @Test
   void aNestedCollectFeedsTheBufferThroughTheOuterScope() throws Exception {
      List<String> warnings = ConsoleMessageCapture.scope(runtimeId, () ->
         AssetScriptFailureWarnings.collect(() -> {
            WorksheetService.ASSET_EXCEPTIONS.get().add(new ScriptException(FAILURE));
            return new ArrayList<>();
         }));

      assertEquals(1, warnings.size(), "collect's own result is unchanged");
      assertEquals(1, read().entries().size());
      assertEquals("INFO", read().entries().get(0).type());
   }

   @Test
   void collectOutsideAScopeRecordsNothingAndStillReturnsItsWarnings() throws Exception {
      List<String> warnings = AssetScriptFailureWarnings.collect(() -> {
         WorksheetService.ASSET_EXCEPTIONS.get().add(new ScriptException(FAILURE));
         return new ArrayList<>();
      });

      assertEquals(1, warnings.size());
      assertEquals(0, read().entries().size());
   }

   /** Amendment 7: the capturing dispatcher's INFO/WARNING/ERROR commands, not CONFIRM. */
   @Test
   void dispatcherMessagesAreRecordedByType() throws Exception {
      try {
         ConsoleMessageCapture.scope(runtimeId, () ->
            CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
               dispatcher.sendCommand(message(MessageCommand.Type.INFO, "an alert"));
               dispatcher.sendCommand(message(MessageCommand.Type.WARNING, "a warning"));
               dispatcher.sendCommand(message(MessageCommand.Type.ERROR, "an error"));
               dispatcher.sendCommand(message(MessageCommand.Type.CONFIRM, "really?"));
               return null;
            }));
         fail("the ERROR must still fail the call");
      }
      catch(CommandErrorException expected) {
         // the call's behaviour is unchanged; the message is recorded all the same
      }

      ConsoleMessageBuffer.Snapshot snapshot = read();
      assertEquals(3, snapshot.entries().size(), snapshot.entries().toString());
      assertEquals(1, snapshot.info());
      assertEquals(1, snapshot.warning());
      assertEquals(1, snapshot.error());
   }

   /** The table path's INFO and the exception list carry one failure; it is one row. */
   @Test
   void theSameFailureFromTheDispatcherAndTheListIsOneRow() throws Exception {
      ConsoleMessageCapture.scope(runtimeId, () ->
         CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
            dispatcher.sendCommand(message(MessageCommand.Type.INFO,
                                           FAILURE.replace("\nScript failed:",
                                                           "\nTo fix: x\nScript failed:")));
            WorksheetService.ASSET_EXCEPTIONS.get().add(new ScriptException(FAILURE));
            return null;
         }));

      assertEquals(1, read().entries().size(), read().entries().toString());
   }

   @Test
   void withoutAScopeTheDispatcherRecordsNothing() throws Exception {
      CapturingCommandDispatcher.withCapturingDispatcher(principal(), dispatcher -> {
         dispatcher.sendCommand(message(MessageCommand.Type.INFO, "an alert"));
         return null;
      });

      assertEquals(0, read().entries().size());
   }

   /**
    * Amendment 6: the whole-sheet image renders on a virtual thread, where the caller's list does
    * not exist. The control shows the failure is lost when the scope is opened on the caller's
    * thread; the scope inside the task records it.
    */
   @Test
   void aScopeOnTheCallersThreadDoesNotReachATaskOnAnotherThread() throws Exception {
      ConsoleMessageCapture.scope(runtimeId, () ->
         RenderWaitSupport.awaitOrRetry(() -> {
            List<Exception> list = WorksheetService.ASSET_EXCEPTIONS.get();

            if(list != null) {
               list.add(new ScriptException(FAILURE));
            }

            return null;
         }, 5000, 1));

      assertEquals(0, read().entries().size(), "control: the other thread has no list");

      RenderWaitSupport.awaitOrRetry(() ->
         ConsoleMessageCapture.scope(runtimeId, () -> {
            WorksheetService.ASSET_EXCEPTIONS.get().add(new ScriptException(FAILURE));
            return null;
         }), 5000, 1);

      assertEquals(1, read().entries().size());
   }

   @Test
   void theBufferKeepsTheLatestEntriesAndCountsWhatItKeeps() {
      ConsoleMessageBuffer buffer = new ConsoleMessageBuffer();

      for(int i = 1; i <= ConsoleMessageBuffer.CAPACITY + 5; i++) {
         buffer.add(i % 2 == 0 ? "ERROR" : "INFO", "m" + i, null);
      }

      ConsoleMessageBuffer.Snapshot all = buffer.read(null, ConsoleMessageBuffer.CAPACITY, 0);
      assertEquals(ConsoleMessageBuffer.CAPACITY, all.entries().size());
      assertEquals(5, all.dropped());
      assertEquals(ConsoleMessageBuffer.CAPACITY, all.total());
      assertEquals("m6", all.entries().get(0).message());
      assertEquals(all.entries().get(1).seq() - 1, all.entries().get(0).seq());
   }

   @Test
   void readFiltersByTypeLimitsToTheLatestAndPollsBySequence() {
      ConsoleMessageBuffer buffer = new ConsoleMessageBuffer();
      buffer.add("INFO", "a", null);
      buffer.add("ERROR", "b", null);
      buffer.add("INFO", "c", null);
      buffer.add("WARNING", "d", null);
      buffer.add("INFO", "e", null);

      ConsoleMessageBuffer.Snapshot info = buffer.read(Set.of("INFO"), 2, 0);
      assertEquals(List.of("c", "e"), info.entries().stream().map(e -> e.message()).toList());
      assertEquals(3, info.matching());
      assertEquals(3, info.info());
      assertEquals(1, info.error());

      long third = buffer.read(null, 50, 0).entries().get(2).seq();
      assertEquals(2, buffer.read(null, 50, third).entries().size());
      assertEquals(0, buffer.read(Set.of(), 50, 0).entries().size());
   }

   @Test
   void clearEmptiesTheBufferResetsDroppedAndKeepsTheSequence() {
      ConsoleMessageBuffer buffer = new ConsoleMessageBuffer();
      buffer.add("INFO", "a", null);
      buffer.add("INFO", "b", null);

      long last = buffer.read(null, 50, 0).entries().get(1).seq();

      assertEquals(2, buffer.clear());
      assertEquals(0, buffer.read(null, 50, 0).total());
      assertTrue(buffer.add("INFO", "c", null) > last);
   }

   /** Sequence numbers never repeat across buffers, so a recreated buffer cannot confuse a poller. */
   @Test
   void sequenceNumbersAreNotReusedByANewBuffer() {
      long first = new ConsoleMessageBuffer().add("INFO", "a", null);
      long second = new ConsoleMessageBuffer().add("INFO", "b", null);

      assertTrue(second > first);
   }

   /** Finding 1: FormatPainterService and the sandbox swap the list in the middle of the work. */
   @Test
   void aFailureRaisedAfterTheListWasReplacedIsStillRecorded() throws Exception {
      ConsoleMessageCapture.scope(runtimeId, () -> {
         WorksheetService.ASSET_EXCEPTIONS.set(new ArrayList<>());
         WorksheetService.ASSET_EXCEPTIONS.get().add(new ScriptException(FAILURE));
         return null;
      });

      assertEquals(1, read().entries().size());
      assertNull(WorksheetService.ASSET_EXCEPTIONS.get(), "the caller's null is restored");
   }

   /** VSObjectPropertyService removes the list after reading it. */
   @Test
   void aFailureRecordedBeforeTheListWasRemovedIsKept() throws Exception {
      ConsoleMessageCapture.scope(runtimeId, () -> {
         WorksheetService.ASSET_EXCEPTIONS.get().add(new ScriptException(FAILURE));
         WorksheetService.ASSET_EXCEPTIONS.remove();
         return null;
      });

      assertEquals(1, read().entries().size());
   }

   /** Finding 4: the Composer Console shows a MessageException at its own level. */
   @Test
   void aMessageExceptionIsRecordedAtItsOwnLevelAndAConfirmExceptionIsNot() throws Exception {
      ConsoleMessageCapture.scope(runtimeId, () -> {
         List<Exception> list = WorksheetService.ASSET_EXCEPTIONS.get();
         list.add(new MessageException("ignoring the filter", LogLevel.WARN, false,
                                       ConfirmException.WARNING));
         list.add(new MessageException("a hard one", LogLevel.ERROR, false, ConfirmException.ERROR));
         list.add(new MessageException("fyi", LogLevel.INFO, false, ConfirmException.INFO));
         list.add(new ConfirmException("locked", ConfirmException.INFO));
         return null;
      });

      ConsoleMessageBuffer.Snapshot snapshot = read();
      assertEquals(3, snapshot.entries().size(), snapshot.entries().toString());
      assertEquals(1, snapshot.warning());
      assertEquals(1, snapshot.error());
      assertEquals(1, snapshot.info());
   }

   /**
    * Finding 2: VSObjectPropertyService turns every non-script entry it finds in the list into an
    * ERROR command, which fails the write after it was applied. The list used to be null there, so
    * what a reader iterates must not contain a query failure; a script failure stays visible (the
    * hook downgrades it to the warning the write relays), and the Console still gets the script
    * failure.
    */
   @Test
   void readersOfTheListDoNotSeeAnEntryThatWouldFailTheirWrite() throws Exception {
      ConsoleMessageCapture.scope(runtimeId, () -> {
         List<Exception> list = WorksheetService.ASSET_EXCEPTIONS.get();
         list.add(new RuntimeException("query failed"));
         list.add(new ScriptException("Script execution error: not the assembly kind"));
         list.add(new ScriptException(FAILURE));

         assertEquals(1, list.size(), list.toString());
         assertTrue(list.get(0).getMessage().startsWith(BaseTableService.SCRIPT_FAILURE_PREFIX));
         return null;
      });

      assertEquals(2, read().entries().size(), "both script failures reach the Console");
   }

   private static MessageCommand message(MessageCommand.Type type, String text) {
      MessageCommand command = new MessageCommand();
      command.setType(type);
      command.setMessage(text);
      return command;
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
