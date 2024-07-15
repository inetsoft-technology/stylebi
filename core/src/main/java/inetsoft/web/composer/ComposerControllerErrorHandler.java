/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.composer;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.BoundTableNotFoundException;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.uql.asset.InvalidDependencyException;
import inetsoft.uql.viewsheet.ColumnNotFoundException;
import inetsoft.util.*;
import inetsoft.util.log.LogLevel;
import inetsoft.util.log.LogManager;
import inetsoft.util.script.ScriptException;
import inetsoft.web.notifications.NotificationService;
import inetsoft.web.viewsheet.command.ExpiredSheetCommand;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.PlaceholderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice(basePackages = {
   "inetsoft.web.binding", "inetsoft.web.composer", "inetsoft.web.viewsheet",
   "inetsoft.web.vswizard"
})
public class ComposerControllerErrorHandler {
   @ExceptionHandler(ScriptException.class)
   public ResponseEntity<Map<String, String>> handleScriptException(ScriptException e) {
      Map<String, String> payload = new HashMap<>();
      payload.put("error", "scriptException");
      payload.put("message", e.getMessage());

      return new ResponseEntity<>(payload, null, HttpStatus.INTERNAL_SERVER_ERROR);
   }

   @MessageExceptionHandler(ScriptException.class)
   public void handleScriptException(ScriptException e, CommandDispatcher commandDispatcher) {
      sendMessageCommand(e, commandDispatcher, MessageCommand.Type.ERROR);
   }

   @ExceptionHandler(MessageException.class)
   public ResponseEntity<Map<String, String>> handleMessageException(MessageException e) {
      MessageException thrown = e.isDumpStack() ? e : null;
      LogManager.getInstance().logException(LOG, e.getLogLevel(), e.getMessage(), thrown);

      Map<String, String> payload = new HashMap<>();
      payload.put("error", "messageException");
      payload.put("message", e.getMessage());
      return new ResponseEntity<>(payload, null, HttpStatus.INTERNAL_SERVER_ERROR);
   }

   @ExceptionHandler(InvalidUserException.class)
   public ResponseEntity<Map<String, String>> handleInvalidUserException(InvalidUserException e) {
      InvalidUserException thrown = e.isDumpStack() ? e : null;
      LogManager.getInstance().logException(LOG, e.getLogLevel(), e.getMessage(), thrown);

      // inform the user that the page needs to be reloaded
      this.notificationService.sendNotificationToUser(
         Catalog.getCatalog().getString("common.invalidUserReload"), e.getUser());

      Map<String, String> payload = new HashMap<>();
      payload.put("error", "messageException");
      payload.put("message", e.getMessage());

      return new ResponseEntity<>(payload, null, HttpStatus.INTERNAL_SERVER_ERROR);
   }

   @MessageExceptionHandler(ColumnNotFoundException.class)
   public void handleColumnNotFoundException(ColumnNotFoundException e,
                                             CommandDispatcher commandDispatcher)
   {
      ColumnNotFoundException thrown = LOG.isDebugEnabled() ? e : null;
      LogManager.getInstance().logException(LOG, e.getLogLevel(), e.getMessage(), thrown);
      sendMessageCommand(e, commandDispatcher, MessageCommand.Type.ERROR);
   }

   @MessageExceptionHandler(BoundTableNotFoundException.class)
   public void handleBoundTableNotFoundException(BoundTableNotFoundException e,
                                                 CommandDispatcher commandDispatcher)
   {
      BoundTableNotFoundException thrown = LOG.isDebugEnabled() ? e : null;
      LogManager.getInstance().logException(LOG, LogLevel.WARN, e.getMessage(), thrown);
      sendMessageCommand(e, commandDispatcher, MessageCommand.Type.ERROR);
   }

   @MessageExceptionHandler(MessageException.class)
   public void handleMessageException(
      MessageException e, CommandDispatcher commandDispatcher)
   {
      WorksheetEngine.ExceptionKey key =
         new WorksheetEngine.ExceptionKey(e, runtimeViewsheetRef.getRuntimeId());
      WorksheetEngine.ExceptionKey key2 = WorksheetEngine.exceptionMap.get(key);

      if(key2 == null || key2.isTimeout()) {
         WorksheetEngine.exceptionMap.put(key, key);
         sendMessageCommand(e, commandDispatcher, MessageCommand.Type.fromCode(e.getWarningLevel()));

         MessageException thrown = e.isDumpStack() ? e : null;
         LogManager.getInstance().logException(LOG, e.getLogLevel(), e.getMessage(), thrown);
      }
   }

   @MessageExceptionHandler(ExpiredSheetException.class)
   public void handleExpiredSheetException(ExpiredSheetException e, CommandDispatcher dispatcher) {
      sendExpiredSheetCommand(e, dispatcher);
   }

   @MessageExceptionHandler(ConfirmException.class)
   public void handleConfirmException(ConfirmException e, CommandDispatcher dispatcher,
                                      Principal principal) throws Exception
   {
      RuntimeViewsheet rvs =
         viewsheetService.getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);

      if(!placeholderService.waitForMV(e, rvs, dispatcher)) {
         sendMessageCommand(e, dispatcher, MessageCommand.Type.CONFIRM);
         throw e;
      }
   }

   @ExceptionHandler(ExpiredSheetException.class)
   @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
   public ResponseEntity<Map<String, String>> handleExpiredSheetException(ExpiredSheetException e) {
      Map<String, String> payload = new HashMap<>();
      payload.put("error", "expiredSheet");
      payload.put("id", e.getId());
      payload.put("message", e.getMessage());
      return new ResponseEntity<>(payload, null, HttpStatus.INTERNAL_SERVER_ERROR);
   }

   @MessageExceptionHandler(Exception.class)
   public void handleException(Exception e, CommandDispatcher commandDispatcher) throws Exception {
      MessageCommand command = new MessageCommand();
      String msg = e.getMessage();
      command.setMessage(Catalog.getCatalog().getString("internal.error") +
                         (msg != null ? " " + msg : ""));
      command.setType(MessageCommand.Type.ERROR);
      commandDispatcher.sendCommand(command);
      throw e;
   }

   @ExceptionHandler(InvalidDependencyException.class)
   @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
   public ResponseEntity<Map<String, String>> handleInvalidDependencyException(
      InvalidDependencyException e)
   {
      InvalidDependencyException thrown = LOG.isDebugEnabled() ? e : null;
      LogManager.getInstance().logException(LOG, e.getLogLevel(), e.getMessage(), thrown);

      Map<String, String> payload = new HashMap<>();
      payload.put("error", "invalidDependencyException");
      payload.put("message", e.getMessage());
      return new ResponseEntity<>(payload, null, HttpStatus.INTERNAL_SERVER_ERROR);
   }

   @MessageExceptionHandler(InvalidDependencyException.class)
   public void handleInvalidDependencyException(
      InvalidDependencyException e, Principal principal,
      CommandDispatcher commandDispatcher)
   {
      RuntimeSheet rs = viewsheetService.getSheet(runtimeViewsheetRef.getRuntimeId(), principal);
      rs.rollback();

      InvalidDependencyException thrown = LOG.isDebugEnabled() ? e : null;
      LogManager.getInstance().logException(LOG, e.getLogLevel(), e.getMessage(), thrown);

      sendMessageCommand(e, commandDispatcher, MessageCommand.Type.WARNING);
   }

   private void sendExpiredSheetCommand(Exception e, CommandDispatcher commandDispatcher) {
      ExpiredSheetCommand command = ExpiredSheetCommand.builder()
         .message(e.getMessage())
         .build();
      commandDispatcher.sendCommand(command);
   }

   private void sendMessageCommand(Exception e, CommandDispatcher commandDispatcher,
                                   MessageCommand.Type messageType)
   {
      MessageCommand command = new MessageCommand();
      String msg = e.getMessage();
      command.setMessage(msg == null || msg.isEmpty() ? e.toString() : msg);
      command.setType(messageType);
      commandDispatcher.sendCommand(command);
   }

   @Autowired
   public void setRuntimeViewsheetRef(RuntimeViewsheetRef runtimeViewsheetRef) {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
   }

   @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
   @Autowired
   public void setViewsheetService(ViewsheetService viewsheetService) {
      this.viewsheetService = viewsheetService;
   }

   @Autowired
   public void setPlaceholderService(PlaceholderService placeholderService) {
      this.placeholderService = placeholderService;
   }

   @Autowired
   public void setNotificationService(NotificationService notificationService) {
      this.notificationService = notificationService;
   }

   private RuntimeViewsheetRef runtimeViewsheetRef;
   private ViewsheetService viewsheetService;
   private PlaceholderService placeholderService;
   private NotificationService notificationService;
   private static final Logger LOG = LoggerFactory.getLogger(ComposerControllerErrorHandler.class);
}
