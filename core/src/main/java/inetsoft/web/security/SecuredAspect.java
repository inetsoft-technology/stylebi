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
package inetsoft.web.security;

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.Tool;
import inetsoft.web.admin.content.repository.ResourcePermissionService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Objects;

/**
 * Aspect used to authorize access to a method annotated with {@link Secured}.
 *
 * @since 12.3
 */
@Component
@Aspect
public class SecuredAspect {
   /**
    * Creates a new instance of <tt>SecuredAspect</tt>.
    */
   @Autowired
   public SecuredAspect(ResourcePermissionService resourcePermissionService) {
      this.resourcePermissionService = resourcePermissionService;
   }

   /**
    * Authorizes access to the annotated method.
    *
    * @param joinPoint the method join point.
    *
    * @throws Throwable if the method invocation failed.
    */
   @Around("@annotation(inetsoft.web.security.Secured)")
   public Object authorize(ProceedingJoinPoint joinPoint) throws Throwable {
      MethodSignature signature = (MethodSignature) joinPoint.getSignature();
      Method method = signature.getMethod();
      Secured secured = method.getAnnotation(Secured.class);

      if(secured == null) {
         return joinPoint.proceed();
      }

      HttpServletRequest request =
         ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
            .getRequest();

      Principal user = null;
      IdentityID defaultOwner = null;
      String path = null;
      String uri = request.getRequestURI();

      Annotation[][] annotations = method.getParameterAnnotations();

      for(int i = 0; i < annotations.length; i++) {
         for(Annotation annotation : annotations[i]) {
            if(annotation instanceof PermissionUser) {
               String expression = ((PermissionUser) annotation).value();

               if(expression.isEmpty()) {
                  user = (Principal) joinPoint.getArgs()[i];
               }
               else {
                  user = evaluateExpression(expression, joinPoint.getArgs()[i], Principal.class);
               }
               break;
            }
            else if(annotation instanceof PermissionOwner) {
               defaultOwner = IdentityID.getIdentityIDFromKey(evaluateExpression(
                  ((PermissionOwner) annotation).value(), joinPoint.getArgs()[i], String.class));
            }
            else if(annotation instanceof PermissionPath) {
               path = evaluateExpression(
                  ((PermissionPath) annotation).value(), joinPoint.getArgs()[i], String.class);
            }
         }
      }

      if(user == null) {
         user = request.getUserPrincipal();
      }

      if(defaultOwner == null && user != null) {
         defaultOwner = IdentityID.getIdentityIDFromKey(user.getName());
      }

      boolean orOperator = Objects.equals(secured.operator(), "OR");
      boolean check = false;

      for(RequiredPermission permission : secured.value()) {
         if(permission.resource().isEmpty()) {
            ResourceType resourceType = permission.resourceType();

            if(resourceType == ResourceType.REPORT && permission.type() == AssetEntry.Type.REPLET) {
               String resource = path;

               if(permission.scope() == AssetRepository.USER_SCOPE) {
                  resource = Tool.MY_DASHBOARD + "/" + resource;
               }

               check = checkPermission(permission.resourceType(), resource, permission.actions(), user);

               if(orOperator && check || !orOperator && !check) {
                  break;
               }
            }
            else if(resourceType == ResourceType.REPORT ||
               resourceType == ResourceType.VIEWSHEET ||
               resourceType == ResourceType.WORKSHEET)
            {
               AssetEntry entry;

               if(permission.scope() == AssetRepository.USER_SCOPE) {
                  IdentityID owner = IdentityID.getIdentityIDFromKey(permission.owner());

                  if(owner.name.isEmpty()) {
                     owner = defaultOwner;
                  }

                  entry = new AssetEntry(
                     AssetRepository.USER_SCOPE, permission.type(), path, owner);
               }
               else {
                  entry = new AssetEntry(
                     permission.scope(), permission.type(), path, null);
               }

               check = checkAssetPermission(entry, permission.actions(), user);
            }
            else if(path != null) {
               if(resourceType == ResourceType.DATA_SOURCE) {
                  path = resourcePermissionService.getDataSourceResourceName(path);
               }

               check = checkPermission(permission.resourceType(), path, permission.actions(), user);

               if(orOperator && check || !orOperator && !check) {
                  break;
               }
            }
            else {
               throw new IllegalStateException(
                  "No permission path or resource specified in secured annotation");
            }
         }
         else {
            check = checkPermission(permission.resourceType(), permission.resource(), permission.actions(), user);

            if(orOperator && check || !orOperator && !check) {
               break;
            }
         }
      }

      if(!check) {
         throw new SecurityException(
            "Unauthorized access to resource \"" + uri + "\" by user " + user);
      }

      return joinPoint.proceed();
   }

   private <T> T evaluateExpression(String expression, Object argument, Class<T> type) {
      if(expression == null || expression.isEmpty()) {
         return type.cast(argument);
      }
      else {
         StandardEvaluationContext context = new StandardEvaluationContext(argument);
         Expression expr = expressionParser.parseExpression(expression);
         return expr.getValue(context, type);
      }
   }

   private boolean checkPermission(ResourceType type, String resource, ResourceAction[] actions, Principal user) {
      boolean result = true;

      try {
         AnalyticRepository repository = SUtil.getRepletRepository();

         for(ResourceAction action : actions) {
            if(!repository.checkPermission(user, type, resource, action)) {
               result = false;
               break;
            }
         }
      }
      catch(Exception ignore) {
         result = false;
      }

      return result;
   }

   private boolean checkAssetPermission(AssetEntry entry, ResourceAction[] actions,
                                        Principal user)
   {
      AssetRepository repository = AssetUtil.getAssetRepository(false);
      boolean result = true;

      try {
         for(ResourceAction action : actions) {
            repository.checkAssetPermission(user, entry, action);
         }
      }
      catch(Exception ignore) {
         result = false;
      }

      return result;
   }

   private final SpelExpressionParser expressionParser = new SpelExpressionParser();
   private final ResourcePermissionService resourcePermissionService;
}
