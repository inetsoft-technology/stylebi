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
import inetsoft.util.script.ScriptException;
import inetsoft.web.viewsheet.controller.table.BaseTableService;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.service.CommandDispatcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bug #82316: feeds the per-runtime {@link ConsoleMessageBuffer} from the places a wiz call can
 * learn what the Composer would have put in its Console.
 *
 * <p>A {@link #scope} is opened around a server-side operation on one runtime viewsheet (a
 * mutate, a read, an image render). Inside it:
 * <ul>
 *   <li>a fresh {@code ASSET_EXCEPTIONS} list is installed. That thread-local is null unless
 *       something scopes it, and {@code ViewsheetSandbox} records an assembly-script failure only
 *       into a non-null list, so without this the failure is dropped. When the scope ends, its
 *       script failures are recorded as {@code INFO}, the type the Composer Console gives them;</li>
 *   <li>{@link CapturingCommandDispatcher#withCapturingDispatcher} records the INFO, WARNING and
 *       ERROR {@code MessageCommand}s it captured;</li>
 *   <li>{@link AssetScriptFailureWarnings#collect}, which consumes its own list and so would
 *       starve the scope's, records what it found.</li>
 * </ul>
 *
 * <p>The scope lives on the thread that opened it. Work handed to another thread (the
 * whole-sheet image render runs on a virtual thread) must open its own scope inside the task.
 *
 * <p>Within one scope the same message is recorded once: the render retry loop and repeated
 * refreshes run the failing script several times in one call. A later scope records it again, one
 * row per occurrence like the Composer's one row per event.
 */
public final class ConsoleMessageCapture {
   private ConsoleMessageCapture() {
   }

   /** Runtimes whose buffers are kept; the least recently used is dropped past this. */
   static final int MAX_RUNTIMES = 256;

   /** The buffer of a runtime, created on first use. */
   public static ConsoleMessageBuffer buffer(String runtimeId) {
      synchronized(BUFFERS) {
         return BUFFERS.computeIfAbsent(runtimeId, id -> new ConsoleMessageBuffer());
      }
   }

   /**
    * Runs {@code work} with the console scope described on the class. A scope already open on
    * this thread for the same runtime is reused, so nested calls share one set of seen messages.
    */
   public static <T> T scope(String runtimeId, Callable<T> work) throws Exception {
      Scope outer = CURRENT.get();

      if(runtimeId == null || outer != null && runtimeId.equals(outer.runtimeId)) {
         return work.call();
      }

      Scope scope = new Scope(runtimeId, buffer(runtimeId));
      List<Exception> previous = WorksheetService.ASSET_EXCEPTIONS.get();
      ScopedList mine = new ScopedList();
      CURRENT.set(scope);
      WorksheetService.ASSET_EXCEPTIONS.set(mine);

      try {
         return work.call();
      }
      finally {
         // some services replace the list mid-operation (FormatPainterService, the sandbox's
         // MV-on-demand branch) or remove it (VSObjectPropertyService); a failure raised after
         // that lands in the replacement, so the live list is read again here, as
         // EventAspect.handleAssetExceptions and AssetScriptFailureWarnings.collect do
         List<Exception> live = WorksheetService.ASSET_EXCEPTIONS.get();

         if(previous == null) {
            WorksheetService.ASSET_EXCEPTIONS.remove();
         }
         else {
            WorksheetService.ASSET_EXCEPTIONS.set(previous);
            previous.addAll(mine.all());
         }

         if(outer == null) {
            CURRENT.remove();
         }
         else {
            CURRENT.set(outer);
         }

         recordExceptions(scope, mine.all());

         if(live != null && live != mine) {
            recordExceptions(scope, live);
         }
      }
   }

   /** Records the exceptions among {@code exceptions} that the Console shows, in the open scope. */
   static void recordScriptFailures(Collection<? extends Exception> exceptions) {
      Scope scope = CURRENT.get();

      if(scope != null && exceptions != null) {
         recordExceptions(scope, exceptions);
      }
   }

   /**
    * A script failure is an INFO row, a {@code MessageException} one at its own warning level, as
    * {@code EventAspect.handleAssetExceptions} sends them. Anything else in the list (a
    * {@code ConfirmException} such as the lock-owner warning, a query failure) is not a row.
    */
   private static void recordExceptions(Scope scope, Collection<? extends Exception> list) {
      List<Exception> copy;

      synchronized(list) {
         copy = new ArrayList<>(list);
      }

      for(Exception ex : copy) {
         String message = AssetScriptFailureWarnings.scriptFailureMessage(ex);

         if(message != null) {
            scope.record(ConsoleMessageBuffer.INFO, message, null);
         }
         else if(ex instanceof MessageException e && e.getMessage() != null) {
            String type = switch(e.getWarningLevel()) {
               case ConfirmException.ERROR -> ConsoleMessageBuffer.ERROR;
               case ConfirmException.WARNING -> ConsoleMessageBuffer.WARNING;
               case ConfirmException.INFO -> ConsoleMessageBuffer.INFO;
               default -> null;
            };

            if(type != null) {
               scope.record(type, e.getMessage(), null);
            }
         }
      }
   }

   /** Records the INFO, WARNING and ERROR messages among {@code commands} in the open scope. */
   static void recordCommands(Collection<CommandDispatcher.Command> commands) {
      Scope scope = CURRENT.get();

      if(scope == null) {
         return;
      }

      for(CommandDispatcher.Command command : commands) {
         if(command != null && command.getCommand() instanceof MessageCommand message &&
            message.getMessage() != null)
         {
            String type = switch(message.getType()) {
               case ERROR -> ConsoleMessageBuffer.ERROR;
               case WARNING -> ConsoleMessageBuffer.WARNING;
               case INFO -> ConsoleMessageBuffer.INFO;
               default -> null; // CONFIRM and anything else asks a question, it is not a message
            };

            if(type != null) {
               scope.record(type, message.getMessage(), command.getAssembly());
            }
         }
      }
   }

   private static String assemblyOf(String message) {
      Matcher matcher = ASSEMBLY.matcher(message);
      return matcher.find() ? matcher.group(1) : null;
   }

   /**
    * The same failure arrives from the table path with a "To fix" hint and from the exception
    * list without one, so the key is the first line plus the engine error after the last
    * "Script failed:" line, which ignores only the hint.
    */
   static String key(String type, String message) {
      int nl = message.indexOf('\n');
      String head = (nl < 0 ? message : message.substring(0, nl)).trim();
      int body = message.lastIndexOf(SCRIPT_FAILED_MARKER);
      return type + '\n' + head + '\n' +
         (body < 0 ? "" : message.substring(body + SCRIPT_FAILED_MARKER.length()).trim());
   }

   /**
    * The list a scope installs. Services read {@code ASSET_EXCEPTIONS} and act on what is in it:
    * {@code VSObjectPropertyService.editObjectProperty} turns every entry that is not a
    * {@code MessageException} or {@code ConfirmException} into an ERROR command, and the capturing
    * dispatcher then fails the write after it was applied. That list used to be null unless a
    * script failure had created it, so a scope must not make such a write fail. Only what a
    * reader already handled as a script failure ({@code BaseTableService.SCRIPT_FAILURE_PREFIX},
    * which {@code CapturingCommandDispatcher} reports as a warning) stays visible to readers;
    * every entry is kept in {@link #all()} for the Console.
    */
   private static final class ScopedList extends ArrayList<Exception> {
      @Override
      public boolean add(Exception ex) {
         held.add(ex);
         return !visible(ex) || super.add(ex);
      }

      @Override
      public boolean addAll(Collection<? extends Exception> exceptions) {
         boolean changed = false;

         for(Exception ex : exceptions) {
            changed |= add(ex);
         }

         return changed;
      }

      List<Exception> all() {
         synchronized(held) {
            return new ArrayList<>(held);
         }
      }

      private static boolean visible(Exception ex) {
         return ex instanceof MessageException || ex instanceof ConfirmException ||
            ex instanceof ScriptException && ex.getMessage() != null &&
            ex.getMessage().startsWith(BaseTableService.SCRIPT_FAILURE_PREFIX);
      }

      private final List<Exception> held = java.util.Collections.synchronizedList(new ArrayList<>());
   }

   private static final class Scope {
      Scope(String runtimeId, ConsoleMessageBuffer buffer) {
         this.runtimeId = runtimeId;
         this.buffer = buffer;
      }

      void record(String type, String message, String assembly) {
         if(message.isBlank()) {
            return;
         }

         if(seen.add(key(type, message))) {
            buffer.add(type, message, assembly != null && !assembly.isEmpty() ? assembly :
               assemblyOf(message));
         }
      }

      private final String runtimeId;
      private final ConsoleMessageBuffer buffer;
      private final Set<String> seen = new HashSet<>();
   }

   private static final Pattern ASSEMBLY =
      Pattern.compile("Script execution error in assembly:\\s*(\\S+)");
   private static final String SCRIPT_FAILED_MARKER = "Script failed:\n";
   private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
   private static final Map<String, ConsoleMessageBuffer> BUFFERS =
      new LinkedHashMap<>(16, 0.75f, true) {
         @Override
         protected boolean removeEldestEntry(Map.Entry<String, ConsoleMessageBuffer> eldest) {
            return size() > MAX_RUNTIMES;
         }
      };
}
