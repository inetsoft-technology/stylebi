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

import inetsoft.web.messaging.MessageAttributes;
import inetsoft.web.messaging.MessageContextHolder;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.command.ViewsheetCommand;
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
      finally {
         MessageContextHolder.setMessageAttributes(previousAttributes);
      }
   }

   /**
    * Messages from captured {@code MessageCommand}s of {@code Type.ERROR}, in dispatch order.
    */
   public List<String> getErrors() {
      return messagesOfType(MessageCommand.Type.ERROR);
   }

   /**
    * Messages from captured {@code MessageCommand}s of {@code Type.WARNING}, in dispatch order.
    * Warnings do not fail the call — the caller decides whether to relay them.
    */
   public List<String> getWarnings() {
      return messagesOfType(MessageCommand.Type.WARNING);
   }

   private List<String> messagesOfType(MessageCommand.Type type) {
      List<String> messages = new ArrayList<>();

      for(Command command : captured) {
         if(command.getCommand() instanceof MessageCommand message && message.getType() == type) {
            messages.add(message.getMessage());
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
