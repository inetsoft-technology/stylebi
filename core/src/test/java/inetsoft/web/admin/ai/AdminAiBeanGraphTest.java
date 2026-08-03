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
package inetsoft.web.admin.ai;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards against the defect class this rework exists to fix. {@code AdminBackupService} used to
 * be {@code @Autowired} with a constructor parameter of type {@code inetsoft.setup.StorageService}
 * - a bean that only exists inside {@code inetsoft.setup.DirectStorageConfig}, itself only ever
 * loaded by {@code StorageContext}'s own standalone {@code AnnotationConfigApplicationContext}
 * (the offline setup tool). {@code WebConfig}'s {@code @ComponentScan} - the running web
 * application's actual bean graph - never scans package {@code inetsoft.setup}, so Spring could
 * not construct {@code AdminBackupService} on a live server: {@code NoSuchBeanDefinitionException},
 * which took down {@code AdminAiController} (whose constructor takes {@code AdminBackupService})
 * and every endpoint under it with HTTP 500. All prior unit tests passed regardless, because every
 * one of them constructed the service directly with a mock - none of them asked Spring to resolve
 * the bean graph.
 *
 * <p>This test does not start a Spring context - a context that fails to start could simply be
 * excluded from the run, and standing one up here would reintroduce exactly the "it works if I
 * construct it directly" blind spot this test exists to close. Instead it does a pure
 * classpath/reflection check, modeled directly on {@code WebConfig}: for every
 * {@code @Service}/{@code @Component}/{@code @RestController} class in {@code inetsoft.web.admin.ai},
 * every constructor parameter type must be resolvable as a bean in the web application context -
 * i.e. either annotated {@code @Service}/{@code @Component}/{@code @Configuration}/
 * {@code @RestController} itself (and reachable by {@code WebConfig}'s scan), or returned by a
 * {@code @Bean} method on some {@code @Configuration} class that scan would actually pick up.
 *
 * @see inetsoft.web.WebConfig#WebConfig() WebConfig's {@code @ComponentScan(basePackages = ...)},
 *      whose base packages this test mirrors exactly.
 */
@Tag("core")
class AdminAiBeanGraphTest {
   @Test
   void everyAdminAiBeanConstructorParameterIsResolvableInTheWebContext() {
      Set<Class<?>> beanProviderReturnTypes = collectBeanProviderReturnTypes();
      List<String> problems = new ArrayList<>();

      for(Class<?> beanClass : scanAnnotated(TARGET_PACKAGE, Service.class, Component.class,
                                              RestController.class))
      {
         for(Constructor<?> ctor : beanClass.getDeclaredConstructors()) {
            for(Class<?> paramType : ctor.getParameterTypes()) {
               if(!isResolvableInWebContext(paramType, beanProviderReturnTypes)) {
                  problems.add(beanClass.getName() + " constructor parameter '" +
                     paramType.getName() + "' is not a @Service/@Component/@Configuration/" +
                     "@RestController reachable by WebConfig's @ComponentScan, and is not " +
                     "produced by a @Bean method on any @Configuration class WebConfig scans");
               }
            }
         }
      }

      assertTrue(problems.isEmpty(), "Bean(s) in " + TARGET_PACKAGE + " have constructor " +
         "parameters Spring cannot resolve in the running web application context:\n" +
         String.join("\n", problems));
   }

   /**
    * @param type a constructor parameter type declared on a bean under {@link #TARGET_PACKAGE}.
    * @param beanProviderReturnTypes every type returned by a {@code @Bean} method on a
    *        {@code @Configuration} class under a package {@link #WEB_CONTEXT_PACKAGES} lists.
    */
   private static boolean isResolvableInWebContext(Class<?> type,
                                                     Set<Class<?>> beanProviderReturnTypes)
   {
      if(isUnderWebContextPackage(type) &&
         (type.isAnnotationPresent(Service.class) || type.isAnnotationPresent(Component.class) ||
          type.isAnnotationPresent(Configuration.class) ||
          type.isAnnotationPresent(RestController.class)))
      {
         return true;
      }

      for(Class<?> provided : beanProviderReturnTypes) {
         if(type.isAssignableFrom(provided)) {
            return true;
         }
      }

      return false;
   }

   /** Whether {@code type} lives under a package {@link #WEB_CONTEXT_PACKAGES} lists - i.e. one
    *  {@code WebConfig}'s {@code @ComponentScan} actually scans, so an annotation on {@code type}
    *  would be honoured in the running web application context. */
   private static boolean isUnderWebContextPackage(Class<?> type) {
      String pkg = type.getPackageName();

      for(String basePackage : WEB_CONTEXT_PACKAGES) {
         if(pkg.equals(basePackage) || pkg.startsWith(basePackage + ".")) {
            return true;
         }
      }

      return false;
   }

   private static Set<Class<?>> collectBeanProviderReturnTypes() {
      Set<Class<?>> types = new HashSet<>();

      for(String basePackage : WEB_CONTEXT_PACKAGES) {
         for(Class<?> configClass : scanAnnotated(basePackage, Configuration.class)) {
            for(Method method : configClass.getDeclaredMethods()) {
               if(method.isAnnotationPresent(Bean.class)) {
                  types.add(method.getReturnType());
               }
            }
         }
      }

      return types;
   }

   @SafeVarargs
   private static List<Class<?>> scanAnnotated(String basePackage,
                                                Class<? extends Annotation>... annotations)
   {
      ClassPathScanningCandidateComponentProvider scanner =
         new ClassPathScanningCandidateComponentProvider(false);

      for(Class<? extends Annotation> annotation : annotations) {
         scanner.addIncludeFilter(new AnnotationTypeFilter(annotation));
      }

      List<Class<?>> classes = new ArrayList<>();

      for(BeanDefinition definition : scanner.findCandidateComponents(basePackage)) {
         try {
            classes.add(Class.forName(definition.getBeanClassName()));
         }
         catch(ClassNotFoundException e) {
            throw new IllegalStateException(
               "Failed to load scanned class " + definition.getBeanClassName(), e);
         }
      }

      return classes;
   }

   private static final String TARGET_PACKAGE = "inetsoft.web.admin.ai";

   /** Exactly {@code WebConfig}'s {@code @ComponentScan(basePackages = ...)}. */
   private static final List<String> WEB_CONTEXT_PACKAGES = List.of(
      "inetsoft.web", "inetsoft.storage", "inetsoft.util", "inetsoft.sree",
      "inetsoft.uql.asset.sync");
}
