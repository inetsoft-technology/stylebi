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
package inetsoft.web.security;


import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.security.Principal;

import inetsoft.sree.security.SecurityException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Annotation that is used to limit multi tenancy user to access method with {@link DeniedMultiTenancyOrgUser}.
 *
 * @since 14.0
 */

@Component
@Aspect
public class DeniedMultiTenancyOrgUserAspect {
   /**
    * Authorizes access to the annotated method.
    *
    * @param joinPoint the method join point.
    *
    * @throws Throwable if the method invocation failed.
    */
   @Around("(@within(inetsoft.web.security.DeniedMultiTenancyOrgUser) || " +
      "@annotation(inetsoft.web.security.DeniedMultiTenancyOrgUser)) && execution(* *(..))")
   public Object authorize(ProceedingJoinPoint joinPoint) throws Throwable {
      if(!SecurityEngine.getSecurity().isSecurityEnabled() || !SUtil.isMultiTenant()) {
         return joinPoint.proceed();
      }

      MethodSignature signature = (MethodSignature) joinPoint.getSignature();
      Method method = signature.getMethod();
      HttpServletRequest request =
         ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
            .getRequest();

      Principal user = null;
      Annotation[][] annotations = method.getParameterAnnotations();
      String uri = request.getRequestURI();

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
         }
      }

      if(user == null) {
         user = request.getUserPrincipal();
      }

      if(user == null || !OrganizationManager.getInstance().isSiteAdmin(user)) {
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

   private final SpelExpressionParser expressionParser = new SpelExpressionParser();
}
