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
import inetsoft.util.MessageException;
import inetsoft.web.messaging.MessageAttributes;
import inetsoft.web.messaging.MessageContextHolder;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.command.ViewsheetCommand;
import inetsoft.web.viewsheet.controller.table.BaseTableService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CommandDispatcherService;
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
    * Messages from captured {@code MessageCommand}s of {@code Type.WARNING}, in dispatch order,
    * followed by any script-execution failure (see {@link #getScriptFailures}). Warnings do not
    * fail the call -- the caller decides whether to relay them.
    */
   public List<String> getWarnings() {
      List<String> warnings = messagesOfType(MessageCommand.Type.WARNING);
      warnings.addAll(getScriptFailures());
      return warnings;
   }

   /**
    * Messages from captured {@code Type.INFO} {@code MessageCommand}s that report a script
    * throwing while a table was built. The table path swallows that failure into an INFO toast
    * and leaves the table empty, so without this a write that ran the failing script reads as a
    * plain success. Every other INFO (toasts, binding confirms, {@code alert()}) is ignored.
    */
   public List<String> getScriptFailures() {
      List<String> messages = new ArrayList<>();

      for(Command command : captured) {
         if(command.getCommand() instanceof MessageCommand message &&
            message.getType() == MessageCommand.Type.INFO && message.getMessage() != null &&
            message.getMessage().startsWith(BaseTableService.SCRIPT_FAILURE_PREFIX))
         {
            String text = CommandErrorException.messageOf(message.getMessage()) + SCRIPT_FAILURE_NOTE;

            if(!messages.contains(text)) {
               messages.add(text);
            }
         }
      }

      return messages;
   }

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
    * Captures the command rather than transmitting it. Deliberately does not call
    * {@code super}, which would engage the debounce/flush machinery that needs a real STOMP
    * session.
    */
   @Override
   public void sendCommand(String assemblyName, ViewsheetCommand command) {
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
