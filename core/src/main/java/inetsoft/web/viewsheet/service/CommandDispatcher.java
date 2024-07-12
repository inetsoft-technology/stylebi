/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.viewsheet.service;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.sree.RepletRepository;
import inetsoft.util.*;
import inetsoft.web.messaging.MessageAttributes;
import inetsoft.web.messaging.MessageContextHolder;
import inetsoft.web.viewsheet.command.ViewsheetCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.DestinationUserNameProvider;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.lang.invoke.MethodHandles;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Class that encapsulates the sending of commands to the viewer.
 *
 * @since 12.3
 */
public class CommandDispatcher implements Iterable<CommandDispatcher.Command> {
   /**
    * Creates a new instance of <tt>CommandDispatcher</tt>.
    *  @param headerAccessor    the message header accessor for the current
    *                          WebSocketSession.
    * @param messagingTemplate the messaging template used to send STOMP messages.
    * @param sessionRepository
    */
   public CommandDispatcher(StompHeaderAccessor headerAccessor,
                            SimpMessagingTemplate messagingTemplate,
                            FindByIndexNameSessionRepository<? extends Session> sessionRepository)
   {
      this.headerAccessor = headerAccessor;
      this.messagingTemplate = messagingTemplate;
      this.clientId = null;
      this.sessionRepository = sessionRepository;
      this.pending = new ArrayList<>();
   }

   /**
    * Create a new Command Dispatcher based on an existing that dispatches its commands
    * to a given client ID
    */
   public CommandDispatcher(CommandDispatcher source, String clientId) {
      this.headerAccessor = source.headerAccessor;
      this.messagingTemplate = source.messagingTemplate;
      this.clientId = clientId;
      this.commands.addAll(source.commands);
      this.sessionRepository = source.sessionRepository;
      this.pending = source.pending;
   }

   /**
    * Sends a command to the client. This should only be used to send commands not
    * associated with a specific assembly, those should be sent using
    * {@link #sendCommand(String, ViewsheetCommand)}. The result of
    * {@link Class#getSimpleName()} will be used for the command type.
    *
    * @param command the command to send.
    */
   public void sendCommand(ViewsheetCommand command) {
      sendCommand("", command);
   }

   /**
    * Sends a command to the client. The result of {@link Class#getSimpleName()} will be
    * used for the command type.
    *
    * @param assemblyName the name of the assembly targeted by the command.
    * @param command      the command to send.
    */
   public void sendCommand(String assemblyName, ViewsheetCommand command) {
      Class<?> commandClass = command.getClass();
      String commandType = commandClass.getSimpleName();

      if(commandType.startsWith("Immutable")) {
         Class<?> baseClass = commandClass.getSuperclass();
         JsonSerialize annotation = baseClass.getAnnotation(JsonSerialize.class);

         if(annotation != null && commandClass.equals(annotation.as())) {
            commandType = baseClass.getSimpleName();
         }
      }

      sendCommand(assemblyName, commandType, command);
   }

   /**
    * Sends a command to the client.
    *
    * @param assemblyName the name of the assembly targeted by the command.
    * @param commandType  the type of command.
    * @param command      the command to send.
    */
   private void sendCommand(String assemblyName, String commandType, ViewsheetCommand command) {
      if(!command.isValid()) {
         return;
      }

      synchronized(pending) {
         if(timerTask != null) {
            timerTask.cancel();
            timerTask = null;
         }

         SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.create();
         headerAccessor.setSessionId(CommandDispatcher.this.headerAccessor.getSessionId());
         headerAccessor.setLeaveMutable(true);
         headerAccessor.setNativeHeader(ASSEMBLY_NAME_HEADER, assemblyName);
         headerAccessor.setNativeHeader(COMMAND_TYPE_HEADER, commandType);
         copyApplicationHeaders(headerAccessor);

         PendingCommand pcmd = new PendingCommand(assemblyName, command, headerAccessor);

         if(debouncer.debounce(pending, pcmd)) {
            getTimer().schedule(timerTask = new FlushPendingTask(), 300);
         }
         else {
            new FlushPendingTask().run();
         }
      }

      commands.add(new Command(assemblyName, commandType, command));
   }

   private Timer getTimer() {
      timerLock.lock();

      try {
         return ConfigurationContext.getContext()
            .computeIfAbsent(TIMER, key -> new CloseableTimer());
      }
      finally {
         timerLock.unlock();
      }
   }

   /**
    * Force pending command to be sent.
    */
   public void flush() {
      new FlushPendingTask().run();
   }

   @Override
   public Iterator<Command> iterator() {
      return Collections.unmodifiableList(commands).iterator();
   }

   public Stream<Command> stream() {
      return commands.stream();
   }

   /**
    * Gets the shared assembly hint for the current thread.
    *
    * @return the shared assembly hint.
    */
   public String getSharedHint() {
      return sharedHint;
   }

   /**
    * Sets the shared assembly hint for the current thread.
    *
    * @param sharedHint the shared assembly hint.
    */
   public void setSharedHint(String sharedHint) {
      this.sharedHint = sharedHint;
   }

   /**
    * Creates a copy of this dispatcher instance that is detached from the message thread.
    *
    * @return a detached copy.
    */
   public CommandDispatcher detach() {
      CommandDispatcher copy = new CommandDispatcher(this, clientId);
      copy.detached = true;
      copy.detachedAttributes = new HashMap<>();
      final MessageAttributes attributes = MessageContextHolder.currentMessageAttributes();
      copy.detachedAttributes.put(RUNTIME_ID_ATTR, attributes.getAttribute(RUNTIME_ID_ATTR));
      copy.detachedAttributes.put(LAST_MODIFIED_ATTR, attributes.getAttribute(LAST_MODIFIED_ATTR));
      return copy;
   }

   /**
    * Gets the socket session identifier.
    *
    * @return the session ID.
    */
   public String getSessionId() {
      return this.headerAccessor.getSessionId();
   }

   /**
    * Gets the name of the WebSocket session user.
    *
    * @return the user name.
    */
   public String getUserName() {
      Principal user = headerAccessor.getUser();
      String userName = null;

      if(user instanceof DestinationUserNameProvider) {
         userName = ((DestinationUserNameProvider) user).getDestinationUserName();
      }
      else if(user != null) {
         userName = user.getName();
      }

      return userName;
   }

   public static <T> T withDummyDispatcher(Principal principal, DummyDispatcherTask<T> fn)
      throws Exception
   {
      GenericMessage<String> message = new GenericMessage<>("simulated");
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
      CommandDispatcher dispatcher = new CommandDispatcher(headerAccessor, messagingTemplate, null)
      {
         @Override
         public void sendCommand(String assemblyName, ViewsheetCommand command) {
            // NO-OP
         }

         @Override
         public void flush() {
            // NO-OP
         }
      };
      MessageContextHolder.setMessageAttributes(messageAttributes);

      try {
         return fn.apply(dispatcher);
      }
      finally {
         MessageContextHolder.setMessageAttributes(null);
      }
   }

   /**
    * Copies the application routing headers from the incoming message headers to the
    * outgoing message headers.
    *
    * @param headerAccessor the accessor for the outgoing message headers.
    */
   private void copyApplicationHeaders(SimpMessageHeaderAccessor headerAccessor) {
      // if we defined a clientId to send to already then use that, otherwise check the
      // headers for the clientId and use that one if available
      if(clientId != null) {
         headerAccessor.setNativeHeader(CLIENT_ID_HEADER, clientId);
      }
      else {
         final String inetsoftClientId = getNativeHeader(CLIENT_ID_HEADER);

         if(inetsoftClientId != null) {
            headerAccessor.setNativeHeader(CLIENT_ID_HEADER, inetsoftClientId);
         }
      }

      String previewId = getNativeHeader(PREVIEW_ID_HEADER);

      if(previewId != null) {
         headerAccessor.setNativeHeader(PREVIEW_ID_HEADER, previewId);
      }

      String runtimeId;
      Long lastModified;

      if(detached) {
         runtimeId = (String) detachedAttributes.get(RUNTIME_ID_ATTR);
         lastModified = (Long) detachedAttributes.get(LAST_MODIFIED_ATTR);
      }
      else {
         final MessageAttributes attributes = MessageContextHolder.currentMessageAttributes();
         runtimeId = (String) attributes.getAttribute(RUNTIME_ID_ATTR);
         lastModified = (Long) attributes.getAttribute(LAST_MODIFIED_ATTR);
      }

      if(runtimeId != null) {
         headerAccessor.setNativeHeader(RUNTIME_ID_ATTR, runtimeId);
      }

      if(lastModified != null) {
         headerAccessor.setNativeHeader(LAST_MODIFIED_ATTR, lastModified.toString());
      }
   }

   private String getNativeHeader(String name) {
      String value = null;
      List<String> headers = this.headerAccessor.getNativeHeader(name);

      if(headers != null && !headers.isEmpty()) {
         value = headers.get(0);
      }

      return value;
   }

   private class FlushPendingTask extends TimerTask {
      public void run() {
         try {
            synchronized(pending) {
               dispatchPending();
               pending.clear();
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to flush pending tasks.", ex);
         }
      }

      private void dispatchPending() {
         // login name
         final Principal user = headerAccessor.getUser();
         // destination user name
         final String userName = getUserName();
         final String runtimeId = headerAccessor.getFirstNativeHeader(RUNTIME_ID_ATTR);
         final String destination = "/topic/refresh-host/" + runtimeId;
         final List<String> runtimeSessions;

         runtimeSessions = Collections.emptyList();

         pending.forEach(c -> {
            final ViewsheetCommand command = c.getCommand();
            final MessageHeaders headers = c.getHeaderAccessor().getMessageHeaders();
            messagingTemplate.convertAndSendToUser(userName, COMMANDS_TOPIC, command, headers);

            runtimeSessions.forEach((session) -> {
               // copy the headers from the command and rewrite the user and sessionId so we can
               // dispatch it to other users
               final Message<ViewsheetCommand> message = MessageBuilder.withPayload(command)
                  .copyHeaders(headers)
                  .setHeader(StompHeaderAccessor.USER_HEADER, session)
                  .removeHeader(StompHeaderAccessor.SESSION_ID_HEADER).build();

               messagingTemplate.convertAndSendToUser(session, destination,
                                                      command, message.getHeaders());
            });
         });
      }
   }

   public static class PendingCommand {
      public PendingCommand(String assembly, ViewsheetCommand command,
                            SimpMessageHeaderAccessor headerAccessor)
      {
         this.assembly = assembly;
         this.command = command;
         this.headerAccessor = headerAccessor;
      }

      public String getAssembly() {
         return assembly;
      }

      public ViewsheetCommand getCommand() {
         return command;
      }

      public SimpMessageHeaderAccessor getHeaderAccessor() {
         return headerAccessor;
      }

      private SimpMessageHeaderAccessor headerAccessor;
      private ViewsheetCommand command;
      private String assembly;
   }

   private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
   private final StompHeaderAccessor headerAccessor;
   private final SimpMessagingTemplate messagingTemplate;
   private final List<Command> commands = new ArrayList<>();
   private final List<PendingCommand> pending; // R-W access should be thread-safe.
   private final String clientId;
   private String sharedHint;
   private Map<String, Object> detachedAttributes = null;
   private boolean detached = false;
   private TimerTask timerTask = null;
   private CommandDebouncer debouncer = new CommandDebouncer();

   public static final String RUNTIME_ID_ATTR = "sheetRuntimeId";
   private static final String LAST_MODIFIED_ATTR = "sheetLastModified";
   private static final String PREVIEW_ID_HEADER = "sheetPreviewId";
   private static final String CLIENT_ID_HEADER = "inetsoftClientId";
   public static final String COMMAND_TYPE_HEADER = "commandType";
   private static final String ASSEMBLY_NAME_HEADER = "assemblyName";
   public static final String COMMANDS_TOPIC = "/commands";
   private static final String TIMER = CommandDispatcher.class.getName() + ".timer";
   private static final Lock timerLock = new ReentrantLock();

   public static final class Command {
      public Command() {
      }

      public Command(String assembly, String type, ViewsheetCommand command) {
         this.assembly = assembly;
         this.type = type;
         this.command = command;
      }

      public String getAssembly() {
         return assembly;
      }

      public void setAssembly(String assembly) {
         this.assembly = assembly;
      }

      public String getType() {
         return type;
      }

      public void setType(String type) {
         this.type = type;
      }

      public ViewsheetCommand getCommand() {
         return command;
      }

      public void setCommand(ViewsheetCommand command) {
         this.command = command;
      }

      private String assembly;
      private String type;
      private ViewsheetCommand command;
   }

   @FunctionalInterface
   public interface DummyDispatcherTask<T> {
      T apply(CommandDispatcher dispatcher) throws Exception;
   }

   private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
