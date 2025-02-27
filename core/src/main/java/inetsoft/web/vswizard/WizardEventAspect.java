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
package inetsoft.web.vswizard;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.CheckMissingMVEvent;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.execution.BoundTableNotFoundException;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.uql.viewsheet.ColumnNotFoundException;
import inetsoft.util.MessageException;
import inetsoft.util.script.ScriptException;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.PlaceholderService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.*;

/**
 * Aspect used to post process event methods {@link HandleWizardExceptions}.
 *
 * @since 12.3
 */
@Component
@Aspect
public class WizardEventAspect {
   /**
    * Creates a new instance of <tt>EventAspect</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    * @param viewsheetService
    */
   @Autowired
   public WizardEventAspect(
      RuntimeViewsheetRef runtimeViewsheetRef,
      PlaceholderService placeholderService,
      ViewsheetService viewsheetService)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.placeholderService = placeholderService;
      this.viewsheetService = viewsheetService;
   }

   @Around("@annotation(HandleWizardExceptions) && within(inetsoft.web.vswizard..*)")
   public Object handleWizardExceptions(ProceedingJoinPoint pjp) throws Throwable {
      List<Exception> oldExceptions = WorksheetService.ASSET_EXCEPTIONS.get();
      List<Exception> exceptions = new ArrayList<>();
      WorksheetService.ASSET_EXCEPTIONS.set(exceptions);

      Optional<CommandDispatcher> commandDispatcher =
         Arrays.stream(pjp.getArgs())
            .filter(CommandDispatcher.class::isInstance)
            .map(CommandDispatcher.class::cast)
            .findFirst();

      Object[] args = pjp.getArgs();
      Principal principal = null;

      for(Object arg : args) {
         if(arg instanceof Principal) {
            principal = (Principal) arg;
            break;
         }
      }

      try {
         try {
            return pjp.proceed();
         }
         catch(Exception ex) {
            // when switch to meta, temp info will be cleared, but some recommend logic may not
            // be complete, ignore this NPE exceptions.
            if(!isMetaData(principal)) {
               exceptions.add(ex);
            }

            return null;
         }
      }
      finally {
         if(oldExceptions == null) {
            WorksheetService.ASSET_EXCEPTIONS.remove();
         }
         else {
            WorksheetService.ASSET_EXCEPTIONS.set(oldExceptions);
         }

         for(Exception ex : exceptions) {
            boolean mvHandled = false;

            if(ex instanceof ConfirmException) {
               ConfirmException e = (ConfirmException) ex;

               if(!(e.getEvent() instanceof CheckMissingMVEvent)) {
                  commandDispatcher.ifPresent(dispatcher -> {
                     sendMessage(e, MessageCommand.Type.CONFIRM, dispatcher);
                  });
               }
               else if(!mvHandled) {
                  commandDispatcher.ifPresent(dispatcher -> {
                     placeholderService.waitForMV(e, null, dispatcher);
                  });
                  mvHandled = true;
               }
            }
            else if(ex instanceof MessageException) {
               MessageException e = (MessageException) ex;
               commandDispatcher.ifPresent(dispatcher -> {
                  sendMessage(e, MessageCommand.Type.fromCode(e.getWarningLevel()), dispatcher);
               });
            }
            else if(ex instanceof ScriptException ||
               ex != null && ex.getCause() instanceof ScriptException ||
               ex instanceof ColumnNotFoundException)
            {
               commandDispatcher.ifPresent(dispatcher -> {
                  sendMessage(ex, MessageCommand.Type.WARNING, dispatcher);
               });
            }
            else if(ex instanceof BoundTableNotFoundException) {
               commandDispatcher.ifPresent(dispatcher -> {
                  sendMessage(ex, MessageCommand.Type.ERROR, dispatcher);
               });
            }
            else if(ex != null) {
               LOG.error("Failed to process event: " + ex, ex);

               commandDispatcher.ifPresent(dispatcher -> {
                  sendMessage(ex, MessageCommand.Type.ERROR, dispatcher);
               });
            }
         }
      }
   }

   private boolean isMetaData(Principal principal) throws Throwable {
      RuntimeViewsheet rvs =
         viewsheetService.getViewsheet(runtimeViewsheetRef.getRuntimeId(), principal);

      if(rvs == null) {
         return false;
      }

      return rvs.getViewsheet().getViewsheetInfo().isMetadata();
   }

   private void sendMessage(Exception e, MessageCommand.Type type, CommandDispatcher dispatcher) {
      MessageCommand command = new MessageCommand();
      String msg = e.getMessage();
      command.setMessage(msg == null || msg.isEmpty() ? e.toString() : msg);
      command.setType(type);
      dispatcher.sendCommand(command);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final PlaceholderService placeholderService;
   private final ViewsheetService viewsheetService;
   private static final Logger LOG = LoggerFactory.getLogger(WizardEventAspect.class);
}
