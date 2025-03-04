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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.SreeEnv;
import inetsoft.util.GroupedThread;
import inetsoft.web.messaging.*;
import inetsoft.web.session.IgniteSessionRepository;
import inetsoft.web.viewsheet.service.CommandDispatcherArgumentResolver;
import inetsoft.web.viewsheet.service.LinkUriArgumentResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.*;
import org.springframework.messaging.handler.invocation.HandlerMethodArgumentResolver;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.session.Session;
import org.springframework.session.web.socket.config.annotation.AbstractSessionWebSocketMessageBrokerConfigurer;
import org.springframework.session.web.socket.server.SessionRepositoryMessageInterceptor;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class that is responsible for configuring the web socket support of the application.
 *
 * @since 12.3
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig<S extends Session> extends
   AbstractSessionWebSocketMessageBrokerConfigurer<S>
{
   @Autowired
   public WebSocketConfig(ObjectMapper objectMapper,
                          IgniteSessionRepository igniteSessionRepository,
                          SessionConnectionService connectionService)
   {
      this.objectMapper = objectMapper;
      this.igniteSessionRepository = igniteSessionRepository;
      this.connectionService = connectionService;
   }

   @Override
   public void configureStompEndpoints(StompEndpointRegistry registry) {
      registry.addEndpoint("/vs-events")
         .addInterceptors(
            new ClipboardHandshakeInterceptor(),
            new RequestAttributeHandshakeInterceptor())
         .setAllowedOriginPatterns("*")
         .withSockJS()
         .setClientLibraryUrl("../js/sockjs.min.js")
         .setTaskScheduler(taskScheduler());
   }

   @Override
   public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
      registration.setSendBufferSizeLimit(Integer.MAX_VALUE)
         .setMessageSizeLimit(1024 * 1024)
         .setSendTimeLimit(60000)
         .addDecoratorFactory(new SessionConnectionDecoratorFactory(connectionService));
   }

   @Override
   public void configureMessageBroker(MessageBrokerRegistry registry) {
      registry.setApplicationDestinationPrefixes("/events", "/user");
      registry.enableSimpleBroker(
         "/commands", "/schedule", "/dependency-changed",
         "/asset-changed", "/repository-changed", "/schedule-changed", "/em-schedule-changed",
         "/composer-client", "/monitoring", "/topic", "/team", "/notifications",
         "/em-content-changed", "/data-changed", "/report-export-changed",
         "/schedule-folder-changed", "/em-mv-changed", "/em-plugin-changed",
         "/session-expiration", "/license-changed")
              .setTaskScheduler(taskScheduler())
              .setHeartbeatValue(new long[] {25000L, 25000L});
      registry.setUserDestinationPrefix("/user");
      registry.setPreservePublishOrder(true);
   }

   @Override
   public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
      argumentResolvers.add(commandDispatcherArgumentResolver());
      argumentResolvers.add(new LinkUriArgumentResolver());
   }

   @Override
   public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
      DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
      resolver.setDefaultMimeType(MediaType.APPLICATION_JSON);
      MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
      converter.setObjectMapper(objectMapper);
      converter.setContentTypeResolver(resolver);
      messageConverters.add(converter);
      final StringMessageConverter stringMessageConverter = new StringMessageConverter();
      stringMessageConverter.setContentTypeResolver(resolver);
      messageConverters.add(stringMessageConverter);
      return false;
   }

   @Override
   public void configureClientInboundChannel(ChannelRegistration registration) {
      // Augment default message types to have websocket heartbeats refresh the spring session.
      final SessionRepositoryMessageInterceptor<S> messageInterceptor = sessionRepositoryInterceptor();
      messageInterceptor.setMatchingMessageTypes(EnumSet.of(SimpMessageType.CONNECT,
                                                            SimpMessageType.SUBSCRIBE,
                                                            SimpMessageType.UNSUBSCRIBE));

      ThreadPoolTaskExecutor executor = eventTaskExecutor();

      registration
         .interceptors(new MessageScopeInterceptor(),
                       messageInterceptor,
                       new SessionAccessInterceptor(igniteSessionRepository, objectMapper))
         // This is the thread pool used to process asset events. In 12.2 we just spawned a new
         // thread every time that an event was received, so it was basically an unbounded pool.
         .taskExecutor(executor).corePoolSize(executor.getCorePoolSize());
   }

   @Override
   public void configureClientOutboundChannel(ChannelRegistration registration) {
      registration
         // This is the thread pool used to write responses to the client. Just use an unbounded
         // pool with a base size of 3.
         .taskExecutor();
   }

   @Bean
   public CommandDispatcherArgumentResolver commandDispatcherArgumentResolver() {
      return new CommandDispatcherArgumentResolver();
   }

   @Bean
   public ThreadPoolTaskExecutor eventTaskExecutor() {
      ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
      executor.setCorePoolSize(getEventThreadPoolSize());
      executor.setThreadFactory(eventThreadFactory());
      return executor;
   }

   @Bean
   public ThreadFactory eventThreadFactory() {
      AtomicInteger counter = new AtomicInteger(0);

      return r -> {
         GroupedThread thread = new GroupedThread(r);
         thread.setName("clientInboundChannel-" + counter.incrementAndGet());
         thread.setDaemon(true);
         return thread;
      };
   }

   @Bean
   public static BeanFactoryPostProcessor messageScopePostProcessor() {
      return new MessageScopePostProcessor();
   }

   @Bean
   public ThreadPoolTaskScheduler taskScheduler(){
      ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
      threadPoolTaskScheduler.setErrorHandler(t -> {
         Throwable cause = t.getCause();
         boolean isClientAbortException =
            GlobalExceptionHandler.isClientAbortException(t.getClass().getName());
         boolean causedByClientAbortException = cause != null &&
            GlobalExceptionHandler.isClientAbortException(cause.getClass().getName());

         if(isClientAbortException || causedByClientAbortException) {
            LOG.debug("Client closed connection", t);
         }
         else {
            LOG.error("Unable to process scheduled task", t);
         }
      });

      return threadPoolTaskScheduler;
   }

   @Bean
   public ServletServerContainerFactoryBean servletServerContainerFactoryBean() {
      ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
      container.setMaxBinaryMessageBufferSize(2097152);
      container.setMaxTextMessageBufferSize(2097152);
      return container;
   }

   private int getEventThreadPoolSize() {
      String property = SreeEnv.getProperty("inbound.channel.pool.size");

      if(property != null && !property.isEmpty()) {
         try {
            int value = Integer.parseInt(property);

            if(value > 0) {
               return value;
            }

            LOG.warn(
               "The inbound.channel.pool.size property requires a positive integer: {}", value);
         }
         catch(NumberFormatException e) {
            LOG.warn(
               "The inbound.channel.pool.size property requires a positive integer: {}",
               property, e);
         }
      }

      return Runtime.getRuntime().availableProcessors() * 8;
   }

   private ObjectMapper objectMapper;
   private IgniteSessionRepository igniteSessionRepository;
   private SessionConnectionService connectionService;
   private static final Logger LOG = LoggerFactory.getLogger(WebSocketConfig.class);
}
