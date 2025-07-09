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
package inetsoft.web.viewsheet;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.CheckMissingMVEvent;
import inetsoft.mv.MVSession;
import inetsoft.report.composition.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.uql.asset.internal.WSExecution;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.util.script.ScriptException;
import inetsoft.web.composer.vs.controller.VSLayoutServiceProxy;
import inetsoft.web.composer.ws.event.WSAssemblyEvent;
import inetsoft.web.viewsheet.command.*;
import inetsoft.web.viewsheet.event.VSRefreshEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import jakarta.annotation.PreDestroy;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.security.Principal;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Aspect used to post process event methods {@link Undoable}.
 *
 * @since 12.3
 */
@Component
@Aspect
public class EventAspect {
   /**
    * Creates a new instance of <tt>EventAspect</tt>.
    *
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated with the
    *                            WebSocket session.
    */
   @Autowired
   public EventAspect(
      RuntimeViewsheetRef runtimeViewsheetRef,
      CoreLifecycleService coreLifecycleService,
      ViewsheetService viewsheetService,
      VSLayoutServiceProxy vsLayoutService,
      EventAspectServiceProxy eventAspectServiceProxy)
   {
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.coreLifecycleService = coreLifecycleService;
      this.viewsheetService = viewsheetService;
      this.vsLayoutService = vsLayoutService;
      this.eventAspectServiceProxy = eventAspectServiceProxy;
   }

   @PreDestroy
   public void cancelTimer() {
      timer.cancel();
   }

   /**
    * Post process event methods.
    *
    */
   @AfterReturning("@annotation(Undoable) && within(inetsoft.web..*)")
   @Order(1)
   public void postProcess(JoinPoint joinPoint) {
      Object[] args = joinPoint.getArgs();
      String id = this.runtimeViewsheetRef.getRuntimeId();
      CommandDispatcher commandDispatcher = null;
      Principal principal = null;

      for(Object arg: args) {
         if(arg instanceof CommandDispatcher) {
            commandDispatcher = (CommandDispatcher) arg;
         }
         else if(arg instanceof Principal){
            principal = (Principal) arg;
         }
      }

      this.runtimeViewsheetRef.setLastModified(System.currentTimeMillis());
      this.vsLayoutService.makeUndoable(id, principal, commandDispatcher, null);
   }

   @Before("@annotation(InitWSExecution) && within(inetsoft.web..*)")
   public void setWSExecution(JoinPoint joinPoint) throws Throwable {
      Object[] args = joinPoint.getArgs();
      String id = this.runtimeViewsheetRef.getRuntimeId();
      Principal principal = null;

      for(Object arg : args) {
         if(arg instanceof Principal) {
            principal = (Principal) arg;
         }
      }

      MethodSignature signature = (MethodSignature) joinPoint.getSignature();
      boolean undoable = signature.getMethod().isAnnotationPresent(Undoable.class);

      eventAspectServiceProxy.setWSExecution(id, undoable, principal);
   }

   @AfterReturning("@annotation(InitWSExecution) && within(inetsoft.web..*)")
   public void clearWSExecution(JoinPoint joinPoint) throws Throwable {
      String id = this.runtimeViewsheetRef.getRuntimeId();
      eventAspectServiceProxy.clearWSExecution(id);
   }

   @Around("@annotation(org.springframework.messaging.handler.annotation.MessageMapping)" +
                   "&& (within(inetsoft.web.composer..*) || within(inetsoft.web.viewsheet..*)" +
                   "|| within(inetsoft.web.binding.controller.ChangeChartTypeController))")
   public Object sendUserMessage(ProceedingJoinPoint pjp) throws Throwable {
      try {
         return pjp.proceed();
      }
      finally {
         Optional<CommandDispatcher> commandDispatcher =
            Arrays.stream(pjp.getArgs())
               .filter(CommandDispatcher.class::isInstance)
               .map(CommandDispatcher.class::cast)
               .findFirst();
         String assemblyName = Arrays
            .stream(pjp.getArgs()).filter(WSAssemblyEvent.class::isInstance)
            .map(e -> ((WSAssemblyEvent) e).getAssemblyName()).findFirst().orElse(null);

         commandDispatcher.ifPresent(c -> sendUserMessages(c, assemblyName));

         Optional<Principal> principal =
            Arrays.stream(pjp.getArgs())
               .filter(Principal.class::isInstance)
               .map(Principal.class::cast)
               .findFirst();

         if(commandDispatcher.isPresent() && principal.isPresent() &&
            runtimeViewsheetRef.getRuntimeId() != null)
         {
            eventAspectServiceProxy.updateViewsheet(runtimeViewsheetRef.getRuntimeId(), commandDispatcher.get(), principal.get());
         }
      }
   }

   private static void sendUserMessages(CommandDispatcher commandDispatcher, String assemblyName) {
      for(MessageCommand.Type type : MessageCommand.Type.values()) {
         final List<UserMessage> messages = Tool.getUserMessages(type);

         if(!messages.isEmpty()) {
            for(UserMessage message : messages) {
               final MessageCommand messageCommand = MessageCommand.fromUserMessage(message);
               messageCommand.setAssemblyName(assemblyName);
               commandDispatcher.sendCommand(messageCommand);
            }
         }
      }

      Tool.clearUserMessage();
   }

   @Around("@annotation(Audited) && within(inetsoft.web..*)")
   public Object handleAudit(ProceedingJoinPoint pjp) throws Throwable {
      ActionRecord record;

      MethodSignature signature = (MethodSignature) pjp.getSignature();
      Audited annotation = signature.getMethod().getAnnotation(Audited.class);
      String objectName = null;
      String actionName = null;
      String actionError = null;

      if(annotation != null && !annotation.objectName().isEmpty()) {
         objectName = annotation.objectName();
      }

      if(annotation != null && !annotation.actionName().isEmpty()) {
         actionName = annotation.actionName();
      }

      if(SUtil.isEmptyString(objectName) || SUtil.isEmptyString(actionName)) {
         Annotation[][] paramAnnotations = signature.getMethod().getParameterAnnotations();
         List<AnnotationParameterTuple<AuditObjectName>> objectParameters = new ArrayList<>();
         Map<Integer, String> errorMsg = new HashMap<>();

         for(int i = 0; i < paramAnnotations.length; i++) {
            for(int j = 0; j < paramAnnotations[i].length; j++) {
               if(paramAnnotations[i][j] instanceof AuditObjectName) {
                  AnnotationParameterTuple<AuditObjectName> objectNameTuple =
                     new AnnotationParameterTuple<>((AuditObjectName) paramAnnotations[i][j],
                     pjp.getArgs()[i]);
                  objectParameters.add(objectNameTuple);
               }
               else if(SUtil.isEmptyString(actionName)
                       && paramAnnotations[i][j] instanceof AuditActionName actionAnnotation)
               {
                  Object arg = pjp.getArgs()[i];

                  if(actionAnnotation.value().isEmpty()) {
                     actionName = String.valueOf(arg);
                  }
                  else {
                     StandardEvaluationContext context = new StandardEvaluationContext(arg);
                     Expression expr = expressionParser.parseExpression(actionAnnotation.value());
                     actionName = expr.getValue(context, String.class);
                  }
               }
               else if(paramAnnotations[i][j] instanceof AuditActionError actionErrorAnnotation) {
                  Object arg = pjp.getArgs()[i];

                  if(arg == null) {
                     actionError = null;
                  }
                  else if(actionErrorAnnotation.value().isEmpty()) {
                     actionError = String.valueOf(arg);
                  }
                  else {
                     StandardEvaluationContext context = new StandardEvaluationContext(arg);
                     context.setVariable("this", arg);
                     Expression expr = expressionParser.parseExpression(actionErrorAnnotation.value());
                     actionError = expr.getValue(context, String.class);
                  }

                  errorMsg.put(actionErrorAnnotation.order(), actionError);
               }
            }
         }

         if(!objectParameters.isEmpty()) {
            objectName = processAnnotationOrder(objectParameters);
         }

         if(errorMsg.size() > 1) {
            actionError = buildOrderedPath(errorMsg);
         }
      }

      String sessionId = null;
      Principal principal = null;

      for(Object arg : pjp.getArgs()) {
         if(arg instanceof XPrincipal) {
            sessionId = ((XPrincipal) arg).getSessionID();
            principal = (Principal) arg;
         }
      }

      if(sessionId == null) {
         principal = ThreadContext.getPrincipal();
      }

      record = SUtil.getActionRecord(
         SUtil.getUserName(principal), actionName, objectName,
         Objects.requireNonNull(annotation).objectType(),
         new Timestamp(System.currentTimeMillis()), actionError, principal, false);

      Object result;

      try {
         result = pjp.proceed();

         if(record != null) {
            record.setActionStatus(ActionRecord.ACTION_STATUS_SUCCESS);
         }
      }
      catch(Throwable thrown) {
         if(record != null) {
            record.setActionStatus(ActionRecord.ACTION_STATUS_FAILURE);
            record.setActionError(thrown.getMessage());
         }

         throw thrown;
      }
      finally {
         if(record != null) {
            Audit.getInstance().auditAction(record, principal);
         }
      }

      return result;
   }

   @Around("@annotation(DatasourceIgnoreGlobalShare) && within(inetsoft.web..*)")
   public Object handleIgnoreGlobalShare(ProceedingJoinPoint pjp) throws Throwable {
      Boolean oldValue = DataSourceRegistry.IGNORE_GLOBAL_SHARE.get();

      try {
         DataSourceRegistry.IGNORE_GLOBAL_SHARE.set(Boolean.TRUE);
         return pjp.proceed();
      }
      finally {
         DataSourceRegistry.IGNORE_GLOBAL_SHARE.set(oldValue);
      }
   }

   private String buildOrderedPath(Map<Integer, String> errorMsg) {
      return errorMsg.entrySet().stream()
         .sorted(Comparator.comparingInt(Map.Entry::getKey))
         .map(Map.Entry::getValue)
         .filter(StringUtils::hasText)
         .collect(Collectors.joining("/"));
   }

   private String processAnnotationOrder(List<AnnotationParameterTuple<AuditObjectName>> objectParameters) {
      objectParameters.sort(Comparator.comparingInt(o -> o.annotation.order()));

      StringBuilder nameBuilder = new StringBuilder();

      for(int i = 0; i < objectParameters.size(); i++) {
         AnnotationParameterTuple<AuditObjectName> nameParameter = objectParameters.get(i);

         AuditObjectName nameAnnotation = nameParameter.annotation;
         Object arg = nameParameter.parameter;
         String objName;

         if(nameAnnotation.value().isEmpty()) {
            objName = arg == null ? null : String.valueOf(arg);
         }
         else {
            StandardEvaluationContext context = new StandardEvaluationContext(arg);
            Expression expr = expressionParser.parseExpression(nameAnnotation.value());
            objName = expr.getValue(context, String.class);
         }

         if(!StringUtils.hasText(objName)) {
            continue;
         }

         int delimiterIndex = objName.indexOf(IdentityID.KEY_DELIMITER);

         if(delimiterIndex != -1) {
            objName = objName.substring(0, delimiterIndex);
         }

         nameBuilder.append(objName);

         if(i < objectParameters.size() - 1) {
            nameBuilder.append("/");
         }
      }

      return nameBuilder.toString();
   }

   @Around("@annotation(HandleAssetExceptions) && within(inetsoft.web..*)")
   public Object handleAssetExceptions(ProceedingJoinPoint pjp) throws Throwable {
      List<Exception> oldExceptions = WorksheetService.ASSET_EXCEPTIONS.get();
      List<Exception> exceptions = new ArrayList<>();
      WorksheetService.ASSET_EXCEPTIONS.set(exceptions);

      Optional<CommandDispatcher> commandDispatcher =
         Arrays.stream(pjp.getArgs())
            .filter(CommandDispatcher.class::isInstance)
            .map(CommandDispatcher.class::cast)
            .findFirst();

      try {
         return pjp.proceed();
      }
      finally {
         if(oldExceptions == null) {
            WorksheetService.ASSET_EXCEPTIONS.remove();
         }
         else {
            WorksheetService.ASSET_EXCEPTIONS.set(oldExceptions);
         }

         commandDispatcher.ifPresent(dispatcher -> {
            boolean mvHandled = false;
            for(Exception ex : exceptions) {
               if(ex instanceof ConfirmException e) {
                  if(!(e.getEvent() instanceof CheckMissingMVEvent)) {
                     sendMessage(e, MessageCommand.Type.CONFIRM, dispatcher);
                  }
                  else if(!mvHandled) {
                     coreLifecycleService.waitForMV(e, null, dispatcher);
                     mvHandled = true;
                  }
               }
               else if(ex instanceof MessageException e) {
                  sendMessage(e, MessageCommand.Type.fromCode(e.getWarningLevel()), dispatcher);
               }
               else if(ex instanceof ScriptException ||
                  ex != null && ex.getCause() instanceof ScriptException)
               {
                  sendMessage(ex, MessageCommand.Type.INFO, dispatcher);
               }
               else if(ex instanceof ExpiredSheetException) {
                  ExpiredSheetCommand command = ExpiredSheetCommand.builder()
                     .message(ex.getMessage())
                     .build();
                  dispatcher.sendCommand(command);
               }
            }
         });
      }
   }

   private void sendMessage(Exception e, MessageCommand.Type type, CommandDispatcher dispatcher) {
      MessageCommand command = new MessageCommand();
      String msg = e.getMessage();
      command.setMessage(msg == null || msg.isEmpty() ? e.toString() : msg);
      command.setType(type);
      dispatcher.sendCommand(command);
   }

   @Around("@annotation(LoadingMask) && within(inetsoft.web..*)")
   public Object clearLoadingMask(ProceedingJoinPoint pjp) throws Throwable {
      LoadingMask mask = ((MethodSignature) pjp.getSignature()).getMethod()
         .getAnnotation(LoadingMask.class);
      boolean force = mask != null && mask.value();

      Optional<VSRefreshEvent> refreshEvent =
         Arrays.stream(pjp.getArgs())
            .filter(VSRefreshEvent.class::isInstance)
            .map(VSRefreshEvent.class::cast)
            .findFirst();

      if(refreshEvent.isPresent() && refreshEvent.get().autoRefresh()) {
         return pjp.proceed();
      }

      Optional<CommandDispatcher> commandDispatcher =
         Arrays.stream(pjp.getArgs())
            .filter(CommandDispatcher.class::isInstance)
            .map(CommandDispatcher.class::cast)
            .findFirst();

      Lock lock = new ReentrantLock();
      AtomicBoolean complete = new AtomicBoolean(false);
      AtomicBoolean loading = new AtomicBoolean(false);
      final Thread pthread = Thread.currentThread();

      commandDispatcher.ifPresent(dispatcher -> {
         boolean preparing = "true".equals(ThreadContext.getSessionInfo("preparing.data", pthread));

         if(force) {
            dispatcher.sendCommand(new ShowLoadingMaskCommand());
            loading.set(true);
         }
         else {
            CommandDispatcher detached = dispatcher.detach();
            timer.schedule(new TimerTask() {
               @Override
               public void run() {
                  lock.lock();

                  try {
                     if(!complete.get()) {
                        detached.sendCommand(new ShowLoadingMaskCommand());
                        loading.set(true);
                     }
                  }
                  finally {
                     lock.unlock();
                  }
               }
            }, 1000L);
         }

         if(!preparing) {
            // check if building cache, and prompt user
            CommandDispatcher detached = dispatcher.detach();
            timer.schedule(new TimerTask() {
               @Override
               public void run() {
                  lock.lock();

                  if(complete.get()) {
                     cancel();
                  }

                  try {
                     if("true".equals(ThreadContext.getSessionInfo("preparing.data", pthread))) {
                        detached.sendCommand(new ShowLoadingMaskCommand(true));
                        cancel();
                     }
                  }
                  finally {
                     lock.unlock();
                  }
               }
            }, 1000, 1000);
         }
      });

      try {
         return pjp.proceed();
      }
      finally {
         commandDispatcher.ifPresent(dispatcher -> {
            lock.lock();

            try {
               if(loading.get()) {
                  dispatcher.sendCommand(new ClearLoadingCommand());
               }

               complete.set(true);
            }
            finally {
               lock.unlock();
            }
         });
      }
   }

   @Around("@annotation(ExecutionMonitoring) && within(inetsoft.web..*)")
   public Object addExecutionMonitoring(ProceedingJoinPoint pjp) throws Throwable {
      eventAspectServiceProxy.addExecutionMonitoring(this.runtimeViewsheetRef.getRuntimeId());

      try {
         return pjp.proceed();
      }
      finally {
         eventAspectServiceProxy.removeExecutionMonitoring(this.runtimeViewsheetRef.getRuntimeId());
      }
   }

   @Before("@annotation(SwitchOrg) && within(inetsoft.web..*)")
   public void beforeController(JoinPoint joinPoint) throws Exception {
      Object[] args = joinPoint.getArgs();

      for(Object arg : args) {
         if(arg instanceof SRPrincipal) {
            principal = (SRPrincipal) arg;
         }
      }

      if(Organization.getDefaultOrganizationID().equals(principal.getOrgId())) {
         return;
      }

      Annotation[][] parameterAnnotations =
         ((MethodSignature) joinPoint.getSignature()).getMethod().getParameterAnnotations();

      for(int i = 0; i < parameterAnnotations.length; i++) {
         for(int j = 0; j < parameterAnnotations[i].length; j++) {
            if(parameterAnnotations[i][j] instanceof OrganizationID) {
               Object arg = args[i];
               String annoValue = ((OrganizationID) parameterAnnotations[i][j]).value();

               if(annoValue.isEmpty()) {
                  orgId = arg == null ? null : String.valueOf(arg);
               }
               else {
                  StandardEvaluationContext context = new StandardEvaluationContext(arg);
                  Expression expr = expressionParser.parseExpression(annoValue);
                  orgId = expr.getValue(context, String.class);
               }
            }
         }
      }

      if(orgId == null) {
         return;
      }

      switchOrganization(orgId, principal);
   }

   @After("@annotation(SwitchOrg) && within(inetsoft.web..*)")
   public void afterController() {
      OrganizationContextHolder.clear();
   }

   private static class AnnotationParameterTuple<T> {
      public AnnotationParameterTuple(T annotation, Object parameter) {
         this.annotation = annotation;
         this.parameter = parameter;
      }

      public T getAnnotation() {
         return annotation;
      }

      public void setAnnotation(T annotation) {
         this.annotation = annotation;
      }

      public Object getParameter() {
         return parameter;
      }

      public void setParameter(Object parameter) {
         this.parameter = parameter;
      }

      private T annotation;
      private Object parameter;
   }

   public static void switchOrganization(String orgID, Principal principal) throws Exception {
      OrganizationContextHolder.setCurrentOrgId(orgID);
      ThreadContext.setContextPrincipal(principal);
   }

   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final CoreLifecycleService coreLifecycleService;
   private final ViewsheetService viewsheetService;
   private final VSLayoutServiceProxy vsLayoutService;
   private final EventAspectServiceProxy eventAspectServiceProxy;
   private final Timer timer = new Timer();
   private final SpelExpressionParser expressionParser = new SpelExpressionParser();
   private String orgId;
   private SRPrincipal principal;
   private static final Logger LOG = LoggerFactory.getLogger(EventAspect.class);
}
