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

import inetsoft.uql.viewsheet.ColumnNotFoundException;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.web.binding.command.VSBindingTrapCommand;
import inetsoft.web.messaging.MessageAttributes;
import inetsoft.web.messaging.MessageContextHolder;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.command.ViewsheetCommand;
import inetsoft.web.viewsheet.controller.table.BaseTableService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CommandDispatcherService;
import inetsoft.web.wiz.viewsheet.AgentTrapWarning;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.GenericMessage;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A {@link CommandDispatcher} for agent-driven, browser-less calls into composer services that
 * <em>captures</em> dispatched commands instead of discarding them.
 *
 * <p>Composer services do not signal failure by throwing. They send a
 * {@code MessageCommand} of {@code Type.ERROR} through the dispatcher and then return
 * normally — see {@code VSObjectPropertyService.editObjectProperty}, which does this for a
 * rename dependency cycle, a missing assembly, and an invalid input list. Because
 * {@link CommandDispatcher#withDummyDispatcher} overrides {@code sendCommand} to a no-op,
 * those failures vanish and the caller observes a clean return.
 *
 * <p>This dispatcher keeps the commands so a caller can inspect what actually happened. It
 * transmits nothing: like the dummy dispatcher it is meant for callers with no browser
 * session, and the wiz pairing layer broadcasts to the browser separately.
 */
public final class CapturingCommandDispatcher extends CommandDispatcher {
   private CapturingCommandDispatcher(StompHeaderAccessor headerAccessor,
                                      CommandDispatcherService dispatcherService)
   {
      super(headerAccessor, dispatcherService, null);
   }

   /**
    * Run {@code fn} with a capturing dispatcher, mirroring
    * {@link CommandDispatcher#withDummyDispatcher}'s message-context handling so composer
    * services see the thread state they expect.
    */
   public static <T> T withCapturingDispatcher(Principal principal, CapturingTask<T> fn)
      throws Exception
   {
      GenericMessage<String> message = new GenericMessage<>("captured");
      MessageAttributes messageAttributes = new MessageAttributes(message);
      StompHeaderAccessor headerAccessor = messageAttributes.getHeaderAccessor();
      headerAccessor.setUser(principal);

      SimpMessagingTemplate messagingTemplate = new SimpMessagingTemplate(new MessageChannel() {
         @Override
         public boolean send(Message<?> message) {
            return true;
         }

         @Override
         public boolean send(Message<?> message, long timeout) {
            return true;
         }
      });

      // Save the previous message attributes so we can restore them, as withDummyDispatcher does.
      MessageAttributes previousAttributes = MessageContextHolder.getMessageAttributes();
      MessageContextHolder.setMessageAttributes(messageAttributes);

      // Null cluster is deliberate. This service is constructed by hand rather than managed by
      // Spring, so its @PostConstruct never runs. The cluster is used elsewhere in that service
      // (convertAndSendToUser and the session-state paths), so the null is safe for a different
      // reason than "nothing reads it": every method that would touch it is overridden below to a
      // no-op, because this dispatcher never transmits. A later change that leans on the wrong
      // reason would NPE, which is why the right one is written down. The
      // class already guards for a null cluster. Nothing is transmitted through it in any case.
      // Taking Cluster.getInstance() here would make this dispatcher unusable outside a live
      // Spring context for no benefit.
      CommandDispatcherService service =
         new CommandDispatcherService(messagingTemplate, null)
      {
         @Override
         public void convertAndSendToUser(String user, String destination, Object payload,
                                          Map<String, Object> headers) throws MessagingException
         {
            // NO-OP — nothing is transmitted.
         }
      };

      CapturingCommandDispatcher dispatcher =
         new CapturingCommandDispatcher(headerAccessor, service);

      try {
         T result = fn.apply(dispatcher);
         List<String> errors = dispatcher.getErrors();

         if(!errors.isEmpty()) {
            throw new CommandErrorException(errors);
         }

         return result;
      }
      catch(Exception e) {
         CommandErrorException replacement = toCommandError(dispatcher, e);

         if(replacement != null) {
            throw replacement;
         }

         throw e;
      }
      finally {
         MessageContextHolder.setMessageAttributes(previousAttributes);
      }
   }

   /**
    * Decides whether a thrown exception should be reported as the ERROR(s) the service dispatched
    * (or its own user-facing message) rather than escape as an opaque 500.
    *
    * <p>A composer service reports a failed execute by dispatching an ERROR and carrying on, so
    * a follow-on exception thrown afterwards ({@code NullPointerException},
    * {@code IllegalStateException}) is a symptom and would hide the real message. Likewise
    * {@code ColumnNotFoundException} and a plain {@code MessageException} are rethrown by the
    * execute path with no ERROR dispatched, and carry text meant for the user.
    *
    * <p>Anything with its own handler or control-flow meaning (security, bad request, stale
    * session, confirm/cancel/expired, render-not-ready, ...) is left alone, so it keeps its status.
    * The original exception is kept as the cause.
    *
    * @return the exception to throw instead, or null to rethrow {@code e} unchanged.
    */
   private static CommandErrorException toCommandError(CapturingCommandDispatcher dispatcher,
                                                       Exception e)
   {
      if(e instanceof CommandErrorException) {
         return null;
      }

      List<String> errors = dispatcher.getErrors();
      boolean followOn = e.getClass() == NullPointerException.class ||
         e.getClass() == IllegalStateException.class ||
         e.getClass() == ClassCastException.class ||
         e.getClass() == IndexOutOfBoundsException.class;
      boolean userFacing = e instanceof ColumnNotFoundException ||
         e.getClass() == MessageException.class;

      if(!followOn && !userFacing) {
         return null;
      }

      if(errors.isEmpty()) {
         if(!userFacing) {
            return null;
         }

         errors = List.of(CommandErrorException.messageOf(e.getMessage()));
      }

      CommandErrorException replacement = new CommandErrorException(errors);
      replacement.initCause(e);
      return replacement;
   }

   /**
    * Messages from captured {@code MessageCommand}s of {@code Type.ERROR}, in dispatch order.
    */
   public List<String> getErrors() {
      return messagesOfType(MessageCommand.Type.ERROR);
   }

   /**
    * Messages from captured {@code MessageCommand}s of {@code Type.CONFIRM}, in dispatch order. A
    * script's {@code confirm()} dispatches one and waits for an answer nobody gives here, so the
    * script's confirmed branch has not run; callers report these rather than answer them.
    */
   public List<String> getConfirmMessages() {
      return messagesOfType(MessageCommand.Type.CONFIRM);
   }

   /**
    * Messages from captured {@code MessageCommand}s of {@code Type.WARNING}, in dispatch order,
    * followed by any script-execution failure (see {@link #getScriptFailures}). Warnings do not
    * fail the call -- the caller decides whether to relay them.
    */
   public List<String> getWarnings() {
      List<String> warnings = collapseTrapWarnings(messagesOfType(MessageCommand.Type.WARNING));
      warnings.addAll(getScriptFailures());
      return warnings;
   }

   /**
    * Bug #80476: at most one {@code Data-model trap:} warning per call. A binding write sends a
    * {@code VSBindingTrapCommand} (post-apply) whose model is non-null only when the write
    * introduced a NEW trap; Composer shows its alert on exactly that, and an unchanged or
    * pre-existing trap is silent. The command carries only the grayed-out fields, not the tables
    * forming the trap, so its warning is generic and yields to a table-naming one already present
    * (from the {@code VSObjectPropertyService} hook). One call can send several such commands
    * (multi-step chart writes); each step is compared with its predecessor, so a trap introduced
    * by step 1 and undone by step 2 still warns, and one introduced by step 2 after step 1
    * trapped is silent. {@code ChangeChartRefService} checks only for a non-zero change hint, so
    * a hint-0 chart binding write is never reported.
    */
   private List<String> collapseTrapWarnings(List<String> warnings) {
      boolean seen = false;

      for(int i = 0; i < warnings.size(); ) {
         if(AgentTrapWarning.isTrapWarning(warnings.get(i))) {
            if(seen) {
               warnings.remove(i);
               continue;
            }

            seen = true;
         }

         i++;
      }

      if(!seen && newBindingTrap()) {
         warnings.add(AgentTrapWarning.genericTrap());
      }

      return warnings;
   }

   private boolean newBindingTrap() {
      for(Command command : captured) {
         if(command.getCommand() instanceof VSBindingTrapCommand trap && trap.getModel() != null) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean reportsModelTrap() {
      return true;
   }

   /**
    * Messages from captured {@code Type.INFO} {@code MessageCommand}s that report a script
    * throwing while a table was built. The table path swallows that failure into an INFO toast
    * and leaves the table empty, so without this a write that ran the failing script reads as a
    * plain success. Every other INFO (toasts, binding confirms, {@code alert()}) is ignored.
    */
   public List<String> getScriptFailures() {
      List<String> messages = new ArrayList<>();
      java.util.Set<String> seen = new java.util.HashSet<>();

      for(Command command : captured) {
         if(command.getCommand() instanceof MessageCommand message &&
            message.getType() == MessageCommand.Type.INFO && message.getMessage() != null &&
            message.getMessage().startsWith(BaseTableService.SCRIPT_FAILURE_PREFIX))
         {
            String text = CommandErrorException.messageOf(message.getMessage());

            if(seen.add(failureKey(text))) {
               messages.add(text + SCRIPT_FAILURE_NOTE);
            }
         }
      }

      return messages;
   }

   /**
    * The same failure reaches the dispatcher from the table path (with a "To fix" hint) and from
    * the property path (without one), so two reports are one failure when they name the same
    * assembly and carry the same engine error. The key is the first line plus the text after the
    * last "Script failed:" line, which ignores only the hint.
    */
   private static String failureKey(String text) {
      int nl = text.indexOf('\n');
      String head = nl < 0 ? text : text.substring(0, nl);
      int body = text.lastIndexOf(SCRIPT_FAILED_MARKER);
      return head + '\n' + (body < 0 ? "" : text.substring(body + SCRIPT_FAILED_MARKER.length()));
   }

   /**
    * Length of the localized "Script failed" label that {@code VSObjectPropertyService} puts in
    * front of an assembly-script failure, or -1 if the message is not one.
    */
   private static int scriptFailedLabelLength(String message) {
      String label = Catalog.getCatalog().getString("Script failed");

      if(message.startsWith(label + BaseTableService.SCRIPT_FAILURE_PREFIX)) {
         return label.length();
      }

      // The catalog is read on this thread; a message produced under another locale carries the
      // English label.
      if(message.startsWith(ENGLISH_LABEL + BaseTableService.SCRIPT_FAILURE_PREFIX)) {
         return ENGLISH_LABEL.length();
      }

      return -1;
   }

   private static final String ENGLISH_LABEL = "Script failed:";
   private static final String SCRIPT_FAILED_MARKER = "Script failed:\n";

   private static final String SCRIPT_FAILURE_NOTE =
      "\nThe table may show no data; the failure can predate this call.";

   private List<String> messagesOfType(MessageCommand.Type type) {
      List<String> messages = new ArrayList<>();

      for(Command command : captured) {
         if(command.getCommand() instanceof MessageCommand message && message.getType() == type) {
            messages.add(CommandErrorException.messageOf(message.getMessage()));
         }
      }

      return messages;
   }

   /**
    * Bug #80820: reclassifies the ERROR {@code VSObjectPropertyService.editObjectProperty} sends
    * for a failing assembly script ({@code "Script failed:" + "Script execution error in
    * assembly: ..."}) as the INFO the table path already uses, so {@link #getScriptFailures}
    * reports it as a warning instead of {@link #getErrors} failing the write. This is the #78378
    * contract: the failure comes from a script the change merely re-ran and may predate it.
    *
    * <p>The seam is here, not in {@code VSObjectPropertyService}, because the Composer UI shares
    * that service and must keep its error dialog. The effect reaches every wiz write that goes
    * through it: set_calendar_display, set_assembly_properties, /edit (rename, paste, cut),
    * set_selection_layout and set_chart_element_visibility now return ok plus a warning where
    * they used to throw. Only messages that start with the exact label and assembly-script prefix
    * are touched; any other ERROR still fails the call.
    *
    * <p>Caveats: the label is localized through the Catalog of the sending thread, so an ERROR
    * produced under another locale is matched by its English label only. The match is on text, so a
    * script failure whose cause is a {@code TableLoadException} (Bug #78071) is downgraded too,
    * although {@code BaseTableService} still reports that one as an error.
    */
   private static ViewsheetCommand downgradeScriptFailure(ViewsheetCommand command) {
      if(command instanceof MessageCommand m && m.getType() == MessageCommand.Type.ERROR &&
         m.getMessage() != null)
      {
         int label = scriptFailedLabelLength(m.getMessage());

         if(label >= 0) {
            MessageCommand info = new MessageCommand();
            info.setMessage(m.getMessage().substring(label));
            info.setType(MessageCommand.Type.INFO);
            return info;
         }
      }

      return command;
   }

   /**
    * Captures the command rather than transmitting it. Deliberately does not call
    * {@code super}, which would engage the debounce/flush machinery that needs a real STOMP
    * session.
    */
   @Override
   public void sendCommand(String assemblyName, ViewsheetCommand command) {
      command = downgradeScriptFailure(command);
      captured.add(new Command(assemblyName, commandTypeOf(command), command));
   }

   /**
    * Stays capturing when detached.
    *
    * <p>{@code CommandDispatcher.detach()} returns a copy of the base class, so anything sent
    * through it was neither captured nor inspected — the hole this class exists to close.
    * {@code CoreLifecycleService.createList} calls {@code detach()}, which puts it on the add,
    * remove, group and rename paths; an ERROR dispatched during that refresh was dropped and the
    * op reported success.
    *
    * <p>Returning {@code this} rather than a copy is safe precisely because this dispatcher never
    * transmits: {@code send} and {@code convertAndSendToUser} are both no-ops, so there is no
    * message-thread state a detached copy would need to shed — and one captured list is what the
    * caller actually wants to inspect.
    */
   @Override
   public CommandDispatcher detach() {
      return this;
   }

   /**
    * Iterates what was captured.
    *
    * <p>{@code sendCommand} deliberately skips {@code super}, so the base {@code commands} list
    * stays empty and the inherited {@code iterator()}/{@code stream()} returned nothing. Framework
    * code reads exactly those — {@code CoreLifecycleService} decides {@code checkMVHandled} from
    * {@code stream()}, which being always false re-sent {@code CheckMVEvent} and added a SECOND
    * checkpoint, breaking the one-checkpoint-per-call promise {@code mutate} makes.
    */
   @Override
   public java.util.Iterator<Command> iterator() {
      return Collections.unmodifiableList(captured).iterator();
   }

   @Override
   public java.util.stream.Stream<Command> stream() {
      return List.copyOf(captured).stream();
   }

   @Override
   public void flush() {
      // NO-OP — nothing is transmitted, so there is nothing to flush.
   }

   /** The number of commands captured so far, as a mark for {@link #consumeErrorsSince}. */
   public int capturedCount() {
      return captured.size();
   }

   /**
    * Removes the captured {@code Type.ERROR} messages dispatched after {@code mark} (a value from
    * {@link #capturedCount}) and returns their text. The post-check in
    * {@link #withCapturingDispatcher} fails the whole call on any remaining ERROR, so a caller
    * that has already made an irreversible write and wants to report an error instead of failing
    * takes it here. Errors dispatched before the mark are left for that post-check.
    */
   public List<String> consumeErrorsSince(int mark) {
      List<String> messages = new ArrayList<>();

      for(int i = Math.max(mark, 0); i < captured.size(); ) {
         if(captured.get(i).getCommand() instanceof MessageCommand message &&
            message.getType() == MessageCommand.Type.ERROR)
         {
            messages.add(CommandErrorException.messageOf(message.getMessage()));
            captured.remove(i);
         }
         else {
            i++;
         }
      }

      return messages;
   }

   /** Every command the wrapped call dispatched, in order. */
   public List<Command> getCapturedCommands() {
      return Collections.unmodifiableList(captured);
   }

   /**
    * Mirrors {@code CommandDispatcher}'s Immutable-stripping so a captured command's type
    * reads the way the browser would have seen it.
    */
   private static String commandTypeOf(ViewsheetCommand command) {
      Class<?> commandClass = command.getClass();
      String commandType = commandClass.getSimpleName();

      if(commandType.startsWith("Immutable")) {
         Class<?> baseClass = commandClass.getSuperclass();

         if(baseClass != null) {
            com.fasterxml.jackson.databind.annotation.JsonSerialize annotation =
               baseClass.getAnnotation(com.fasterxml.jackson.databind.annotation.JsonSerialize.class);

            if(annotation != null && commandClass.equals(annotation.as())) {
               commandType = baseClass.getSimpleName();
            }
         }
      }

      return commandType;
   }

   @FunctionalInterface
   public interface CapturingTask<T> {
      T apply(CapturingCommandDispatcher dispatcher) throws Exception;
   }

   private final List<Command> captured = new ArrayList<>();
}
