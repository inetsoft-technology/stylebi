/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web;

import inetsoft.test.PenetrationTest;
import inetsoft.test.PenetrationTests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that all web endpoints have a penetration test.
 */
@PenetrationTests
public class PenetrationCoverageTest {
   private Map<String, Map<RequestMethod, RequestCoverage>> requestStatus;
   private Map<String, MessageCoverage> messageStatus;

   @BeforeEach
   void setup() {
      requestStatus = new HashMap<>();
      messageStatus = new HashMap<>();
      collectEndpoints();
      collectCoverage();
   }

   @Test
   void verifyCoverage() {
      List<Executable> tests = new ArrayList<>();

      for(Map<RequestMethod, RequestCoverage> map : requestStatus.values()) {
         for(RequestCoverage coverage : map.values()) {
            tests.add(() -> assertTrue(coverage.tested, coverage::toString));
         }
      }

      for(MessageCoverage coverage : messageStatus.values()) {
         tests.add(() -> assertTrue(coverage.tested, coverage::toString));
      }

      assertAll(tests);
   }

   private void collectEndpoints() {
      ClassPathScanningCandidateComponentProvider provider =
         new ClassPathScanningCandidateComponentProvider(false);
      provider.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
      provider.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

      for(BeanDefinition bean : provider.findCandidateComponents("inetsoft.web")) {
         String controllerClass = bean.getBeanClassName();

         if(bean instanceof AnnotatedBeanDefinition) {
            AnnotationMetadata metadata = ((AnnotatedBeanDefinition) bean).getMetadata();
            addRequestStatus(
               requestStatus, controllerClass, metadata, GetMapping.class.getName(),
               attrs -> new RequestMethod[] { RequestMethod.GET });
            addRequestStatus(
               requestStatus, controllerClass, metadata, PostMapping.class.getName(),
               attrs -> new RequestMethod[] { RequestMethod.POST });
            addRequestStatus(
               requestStatus, controllerClass, metadata, PutMapping.class.getName(),
               attrs -> new RequestMethod[] { RequestMethod.PUT });
            addRequestStatus(
               requestStatus, controllerClass, metadata, DeleteMapping.class.getName(),
               attrs -> new RequestMethod[] { RequestMethod.DELETE });
            addRequestStatus(
               requestStatus, controllerClass, metadata, RequestMapping.class.getName(),
               attrs -> (RequestMethod[]) attrs.get("method"));
            addMessageStatus(messageStatus, controllerClass, metadata);
         }
      }
   }

   private void collectCoverage() {
      ClassPathScanningCandidateComponentProvider provider =
         new ClassPathScanningCandidateComponentProvider(false);
      provider.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile("^.+Tests?$")));

      for(BeanDefinition bean : provider.findCandidateComponents("inetsoft.web")) {
         if(bean instanceof AnnotatedBeanDefinition) {
            AnnotationMetadata metadata = ((AnnotatedBeanDefinition) bean).getMetadata();
            String annotationName = PenetrationTest.class.getName();

            for(MethodMetadata method : metadata.getAnnotatedMethods(annotationName)) {
               Map<String, Object> attributes = method.getAnnotationAttributes(annotationName);

               if(attributes != null) {
                  boolean message = Boolean.TRUE.equals(attributes.get("message"));
                  RequestMethod[] methods = (RequestMethod[]) attributes.get("method");
                  String[] paths = (String[]) attributes.get("path");

                  for(String path: paths) {
                     if(message) {
                        MessageCoverage coverage = messageStatus.get(path);

                        if(coverage != null) {
                           coverage.tested = true;
                        }
                     }
                     else {
                        Map<RequestMethod, RequestCoverage> status = requestStatus.get(path);

                        if(status != null) {
                           for(RequestMethod requestMethod : methods) {
                              RequestCoverage coverage = status.get(requestMethod);

                              if(coverage != null) {
                                 coverage.tested = true;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void addRequestStatus(Map<String, Map<RequestMethod, RequestCoverage>> status,
                                 String controllerClass, AnnotationMetadata metadata,
                                 String annotationName,
                                 Function<Map<String, Object>, RequestMethod[]> fn)
   {
      for(MethodMetadata method : metadata.getAnnotatedMethods(annotationName)) {
         String controllerMethod = method.getMethodName();
         Map<String, Object> attributes = method.getAnnotationAttributes(annotationName);
         addRequestStatus(status, controllerClass, controllerMethod, "value", attributes, fn);
         addRequestStatus(status, controllerClass, controllerMethod, "path", attributes, fn);
      }
   }

   private void addRequestStatus(Map<String, Map<RequestMethod, RequestCoverage>> status,
                                 String controllerClass, String controllerMethod,
                                 String pathAttribute, Map<String, Object> attributes,
                                 Function<Map<String, Object>, RequestMethod[]> fn)
   {
      if(attributes != null) {
         String[] paths = (String[]) attributes.get(pathAttribute);

         if(paths != null) {
            for(String path : paths) {
               for(RequestMethod requestMethod : fn.apply(attributes)) {
                  status.computeIfAbsent(path, key -> new HashMap<>()).put(
                     requestMethod,
                     new RequestCoverage(path, requestMethod, controllerClass, controllerMethod));
               }
            }
         }
      }
   }

   private void addMessageStatus(Map<String, MessageCoverage> status, String controllerClass,
                                 AnnotationMetadata metadata)
   {
      String annotationName = MessageMapping.class.getName();

      for(MethodMetadata method : metadata.getAnnotatedMethods(annotationName)) {
         String controllerMethod = method.getMethodName();
         Map<String, Object> attributes = method.getAnnotationAttributes(annotationName);

         if(attributes != null) {
            String[] paths = (String[]) attributes.get("value");

            if(paths != null) {
               for(String path : paths) {
                  status.computeIfAbsent(
                     path, key -> new MessageCoverage(key, controllerClass, controllerMethod));
               }
            }
         }
      }
   }

   private static final class RequestCoverage {
      private RequestCoverage(String path, RequestMethod method, String controllerClass,
                              String controllerMethod)
      {
         this.path = path;
         this.method = method;
         this.controllerClass = controllerClass;
         this.controllerMethod = controllerMethod;
      }

      @Override
      public String toString() {
         return "RequestCoverage{" +
            "path='" + path + '\'' +
            ", method=" + method +
            ", controllerClass='" + controllerClass + '\'' +
            ", controllerMethod='" + controllerMethod + '\'' +
            '}';
      }

      final String path;
      final RequestMethod method;
      final String controllerClass;
      final String controllerMethod;
      boolean tested;
   }

   private static final class MessageCoverage {
      public MessageCoverage(String path, String controllerClass, String controllerMethod) {
         this.path = path;
         this.controllerClass = controllerClass;
         this.controllerMethod = controllerMethod;
      }

      @Override
      public String toString() {
         return "MessageCoverage{" +
            "path='" + path + '\'' +
            ", controllerClass='" + controllerClass + '\'' +
            ", controllerMethod='" + controllerMethod + '\'' +
            '}';
      }

      final String path;
      final String controllerClass;
      final String controllerMethod;
      boolean tested;
   }
}
