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

package inetsoft.web;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.*;
import inetsoft.util.*;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.util.log.LogManager;
import inetsoft.web.metrics.*;
import inetsoft.web.security.*;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.DispatcherType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.*;
import org.springframework.boot.actuate.endpoint.web.EndpointMediaTypes;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import java.io.File;
import java.util.EnumSet;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class BaseInetsoftApplication {
   public void start(String[] args) {
      ApplicationArguments appArguments = new DefaultApplicationArguments(args);

      // make sure aot is not enabled during process-aot phase and don't start ignite
      if("true".equals(System.getProperty("spring.aot.processing"))) {
         System.setProperty("spring.aot.enabled", "false");
      }
      else {
         String home = null;

         if(appArguments.containsOption("sree.home")) {
            home = appArguments.getOptionValues("sree.home").getFirst();
         }

         configure(home);
      }

      SpringApplication.run(getClass(), args);
      SreeEnv.reloadLoggingFramework();
      SUtil.initScheduleListener();

      if(isSchedulerServerAutoStart()) {
         ThreadPool.addOnDemand(() -> {
            try {
               SUtil.startScheduler();
            }
            catch(Exception e) {
               LoggerFactory.getLogger(InetsoftApplication.class)
                  .error("Failed to auto-start scheduler", e);
            }
         });
      }
   }

   /**
    * Check if schedule clusters are enabled
    */
   private boolean isScheduleCluster() {
      return "server_cluster".equals(SreeEnv.getProperty("server.type")) ||
         ScheduleClient.getScheduleClient().isCluster();
   }

   /**
    * Checks for cluster server configuration and schedule auto-start property
    */
   private boolean isSchedulerServerAutoStart() {
      return !isScheduleCluster() &&
         "true".equals(SreeEnv.getProperty("schedule.auto.start")) ||
         InetsoftConfig.getInstance().getCloudRunner() != null;
   }

   @Bean
   @Lazy(false)
   public ServletServerContainerFactoryBean createWebSocketContainer() {
      ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
      container.setMaxTextMessageBufferSize(2097152);
      container.setMaxBinaryMessageBufferSize(2097152);
      return container;
   }

   @Bean
   public FilterRegistrationBean<SafeInputStreamFilter> safeInputStreamFilter() {
      FilterRegistrationBean<SafeInputStreamFilter> bean = new FilterRegistrationBean<>();
      bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
      bean.setFilter(new SafeInputStreamFilter());
      bean.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
      return bean;
   }

   @Bean
   public FilterRegistrationBean<GZIPFilter> gzipFilter() {
      FilterRegistrationBean<GZIPFilter> bean = new FilterRegistrationBean<>();
      bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
      bean.setFilter(new GZIPFilter());
      bean.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
      return bean;
   }

   @Bean
   public FilterRegistrationBean<DelegatingFilterProxy> sessionRepositoryFilter() {
      FilterRegistrationBean<DelegatingFilterProxy> bean = new FilterRegistrationBean<>();
      bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
      bean.setFilter(new DelegatingFilterProxy("springSessionRepositoryFilter", null));
      bean.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
      return bean;
   }

   @Bean
   public FilterRegistrationBean<DelegatingFilterProxy> websocketLimitFilter() {
      FilterRegistrationBean<DelegatingFilterProxy> bean = new FilterRegistrationBean<>();
      bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
      bean.setFilter(new DelegatingFilterProxy("webSocketLimitFilter", null));
      bean.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
      return bean;
   }

   @Bean
   public FilterRegistrationBean<SecurityFilterChain> securityChainFilter() {
      FilterRegistrationBean<SecurityFilterChain> bean = new FilterRegistrationBean<>();
      bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 4);
      bean.setFilter(new SecurityFilterChain());
      bean.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
      return bean;
   }

   @Bean
   public FilterRegistrationBean<DelegatingFilterProxy> sessionAccessFilterRegistration() {
      FilterRegistrationBean<DelegatingFilterProxy> bean = new FilterRegistrationBean<>();
      bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 6);
      bean.setFilter(new DelegatingFilterProxy("sessionAccessFilter", null));
      bean.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
      return bean;
   }

   @Bean
   public FilterRegistrationBean<FallbackAuthenticationFilter>
   fallbackAuthenticationFilterFilterRegistration(FallbackAuthenticationFilter filter)
   {
      FilterRegistrationBean<FallbackAuthenticationFilter> bean =
         new FilterRegistrationBean<>(filter);
      bean.setEnabled(false);
      return bean;
   }

   @Bean
   public FilterRegistrationBean<StandardFilterChain>
   standardFilterChainRegistration(StandardFilterChain filter)
   {
      FilterRegistrationBean<StandardFilterChain> bean = new FilterRegistrationBean<>(filter);
      bean.setEnabled(false);
      return bean;
   }

   @Bean
   public EndpointMediaTypes endpointMediaTypes() {
      return new EndpointMediaTypes(
         "application/vnd.spring-boot.actuator.v3+json",
         "application/vnd.spring-boot.actuator.v2+json", "application/json", "text/plain",
         "text/plain;version=0.0.4;charset=utf-8");
   }

   @PreDestroy
   public void shutdownInetsoft() {
      Logger log = LoggerFactory.getLogger(getClass());

      try {
         DataSpace space = DataSpace.getDataSpace();
         space.dispose();
      }
      catch(Exception ex) {
         log.debug("Failed to shut down data space", ex);
      }

      try {
         ScheduleTask.shutdownThreadPool();
      }
      catch(Exception ex) {
         log.debug("Failed to shutdown schedule task thread pool", ex);
      }

      try {
         if("true".equals(SreeEnv.getProperty("schedule.auto.down"))) {
            SUtil.stopScheduler();
         }
      }
      catch(Exception ex) {
         log.debug("Failed to shutdown scheduler", ex);
      }

      try {
         SingletonManager.reset();
      }
      catch(Exception ex) {
         log.debug("Failed to shutdown SingletonManager", ex);
      }

      try {
         GroupedThread.cancelAll();
      }
      catch(Exception ex) {
         log.debug("Failed to cancel threads", ex);
      }
   }

   private void configure(String home) {
      if(configured.compareAndSet(false, true)) {
         if(home == null) {
            home = System.getProperty("sree.home");
         }

         if(home == null) {
            // environment is not available yet, we need to parse the config file directly
            YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
            factory.setResources(new ClassPathResource("application.yaml"));
            factory.afterPropertiesSet();
            Properties properties = factory.getObject();

            if(properties != null) {
               home = properties.getProperty("sree.home");
            }
         }

         if(home != null) {
            System.setProperty(
               "derby.stream.error.file",
               new File(home, "derby.log").getAbsolutePath());
            ConfigurationContext.getContext().setHome(new File(home).getAbsolutePath());
         }

         LogManager.initializeForStartup();
         Tool.setServer(true);
         Cluster.getInstance().setLocalNodeProperty("reportServer", "true");
      }
   }

   private static final AtomicBoolean configured = new AtomicBoolean(false);
}
