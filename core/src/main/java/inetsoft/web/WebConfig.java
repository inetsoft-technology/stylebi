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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import inetsoft.web.adhoc.DecodeArgumentResolver;
import inetsoft.web.factory.DecodePathVariableResolver;
import inetsoft.web.factory.RemainingPathResolver;
import inetsoft.web.json.ThirdPartySupportModule;
import inetsoft.web.reportviewer.service.HttpServletRequestWrapperArgumentResolver;
import inetsoft.web.security.WebSocketLimitFilter;
import inetsoft.web.viewsheet.service.LinkUriArgumentResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.*;
import org.springframework.context.annotation.*;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.converter.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.socket.server.support.AbstractHandshakeHandler;
import org.springframework.web.util.UrlPathHelper;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;

import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * Class that is responsible for configuring the web MVC layer of the Spring
 * application.
 *
 * @since 12.3
 */
@EnableWebMvc
@EnableAspectJAutoProxy
@EnableMBeanExport
@EnableScheduling
@Configuration
@ComponentScan(basePackages = "inetsoft.web", lazyInit = true)
public class WebConfig implements WebMvcConfigurer, ApplicationContextAware {
   private ApplicationContext applicationContext;

   @Override
   public void setApplicationContext(ApplicationContext applicationContext) {
      this.applicationContext = applicationContext;
   }

   @Override
   public void addResourceHandlers(ResourceHandlerRegistry registry) {
      if(applicationContext instanceof DefaultResourceLoader) {
         DefaultResourceLoader loader = (DefaultResourceLoader) applicationContext;
         loader.addProtocolResolver(new DataSpaceProtocolResolver());
         loader.addProtocolResolver(new ThemeProtocolResolver());
      }

      registry.addResourceHandler("/webjars/**")
         .addResourceLocations("classpath:/META-INF/resources/webjars/")
         .resourceChain(true);

      if(!registry.hasMappingForPattern("/**")) {
         ResourceHandlerRegistration registration =
            registry.addResourceHandler("/**");
         List<String> locations = new ArrayList<>();

         try {
            // Need to add each location of the inetsoft/web/resources directory in the
            // classpath. Spring's default resolver (classpath:/inetsoft/web/resources/)
            // will only use the first location found on the classpath.
            Enumeration<URL> urls =
               getClass().getClassLoader().getResources("inetsoft/web/resources/");

            while(urls.hasMoreElements()) {
               locations.add(urls.nextElement().toExternalForm());
            }
         }
         catch(IOException e) {
            LOG.error("Failed to list resource locations", e);
         }

         if(locations.isEmpty()) {
            locations.add("classpath:/inetsoft/web/resources/");
         }

         locations.add("dataspace:/web-assets/");

         registration.addResourceLocations(locations.toArray(new String[0]))
            .setCacheControl(CacheControl.noCache().cachePrivate().mustRevalidate())
            .resourceChain(true);
      }
   }

   @Override
   public void addViewControllers(ViewControllerRegistry registry) {
      registry.addRedirectViewController("/", "/index.html");
   }

   @Override
   public void addInterceptors(InterceptorRegistry registry) {
      registry.addInterceptor(new CacheInterceptor()).addPathPatterns("/api/**");
      registry.addInterceptor(new LogContextInterceptor()).addPathPatterns("/**");
   }

   @Override
   public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
      argumentResolvers.add(new DecodeArgumentResolver());
      argumentResolvers.add(new DecodePathVariableResolver());
      argumentResolvers.add(new RemainingPathResolver());
      argumentResolvers.add(new LinkUriArgumentResolver());
      argumentResolvers.add(new HttpServletRequestWrapperArgumentResolver());
   }

   @Override
   public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
      configurer.enable();
   }

   @Override
   public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
      converters.add(jsonMessageConverter());
      converters.add(resourceMessageConvertor());
      converters.add(textMessageConverter());
      converters.add(byteArrayHttpMessageConverter());
   }

   @Bean
   public MappingJackson2HttpMessageConverter jsonMessageConverter() {
      MappingJackson2HttpMessageConverter converter =
         new MappingJackson2HttpMessageConverter();
      converter.setObjectMapper(objectMapper());
      return converter;
   }

   @Bean
   public ResourceHttpMessageConverter resourceMessageConvertor() {
      ResourceHttpMessageConverter converter = new ResourceHttpMessageConverter();
      converter.setSupportedMediaTypes(Arrays.asList(
         MediaType.IMAGE_GIF, MediaType.IMAGE_JPEG, MediaType.IMAGE_PNG, MediaType.APPLICATION_PDF,
         MediaType.parseMediaType("image/svg+xml")
      ));
      return converter;
   }

   @Bean
   public StringHttpMessageConverter textMessageConverter() {
      StringHttpMessageConverter converter = new StringHttpMessageConverter();
      converter.setSupportedMediaTypes(List.of(
         MediaType.TEXT_PLAIN,
         MediaType.parseMediaType("application/openmetrics-text")));
      return converter;
   }

   @Bean
   public ByteArrayHttpMessageConverter byteArrayHttpMessageConverter() {
      ByteArrayHttpMessageConverter converter = new ByteArrayHttpMessageConverter();
      converter.setSupportedMediaTypes(List.of(
         MediaType.APPLICATION_OCTET_STREAM,
         MediaType.TEXT_PLAIN,
         MediaType.parseMediaType("application/openmetrics-text")));
      return converter;
   }

   @Bean
   public ObjectMapper objectMapper() {
      StreamReadConstraints defaults = StreamReadConstraints.defaults();
      JsonFactory jsonFactory = new MappingJsonFactory();
      jsonFactory.setStreamReadConstraints(StreamReadConstraints.builder()
         .maxDocumentLength(defaults.getMaxDocumentLength())
         .maxNameLength(defaults.getMaxNameLength())
         .maxNumberLength(defaults.getMaxNumberLength())
         .maxNestingDepth(defaults.getMaxNestingDepth())
         .maxStringLength(1073741824)
         .build());
      ObjectMapper mapper = new ObjectMapper(jsonFactory);
      mapper.registerModule(new Jdk8Module());
      mapper.registerModule(new JavaTimeModule());
      mapper.registerModule(new ThirdPartySupportModule());
      mapper.registerModule(new GuavaModule());
      mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
      return mapper;
   }

   @Bean
   public StandardServletMultipartResolver multipartResolver() {
      StandardServletMultipartResolver resolver = new StandardServletMultipartResolver();
      resolver.setResolveLazily(true);
      return resolver;
   }

   @Bean
   public ViewResolver viewResolver() {
      ThymeleafViewResolver resolver = new ThymeleafViewResolver();
      resolver.setTemplateEngine(templateEngine());
      resolver.setCharacterEncoding("UTF-8");
      return resolver;
   }

   @Bean
   public SpringTemplateEngine templateEngine() {
      SpringTemplateEngine engine = new SpringTemplateEngine();
      engine.setTemplateResolver(templateResolver());
      return engine;
   }

   @Bean
   public SpringResourceTemplateResolver templateResolver() {
      SpringResourceTemplateResolver resolver =
         new SpringResourceTemplateResolver();
      resolver.setApplicationContext(applicationContext);
      resolver.setPrefix("classpath:/inetsoft/web/resources/");
      resolver.setSuffix(".html");
      resolver.setTemplateMode("LEGACYHTML5");
      resolver.setCacheable(false);
      return resolver;
   }

   @Bean
   public MessageSource messageSource() {
      return new CatalogMessageSource();
   }

   @Bean
   public WebSocketLimitFilter webSocketLimitFilter() {
      return new WebSocketLimitFilter();
   }

   @Override
   public void configurePathMatch(PathMatchConfigurer configurer) {
      // .com in name causing json return type to be rejected
      configurer.setUseSuffixPatternMatch(false);

      if(isWebSphere()) {
         // WebSphere strips the trailing slash
         UrlPathHelper helper = new UrlPathHelper();
         helper.setAlwaysUseFullPath(true);
         helper.setRemoveSemicolonContent(false);
         configurer.setUrlPathHelper(helper);
      }
      else if(configurer.getUrlPathHelper() == null) {
         UrlPathHelper helper = new UrlPathHelper();
         helper.setRemoveSemicolonContent(false);
         configurer.setUrlPathHelper(helper);
      }

      // need for cjk chars on uri path. (58590)
      configurer.getUrlPathHelper().setDefaultEncoding("UTF-8");
   }

   @Override
   public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
      // .com in name causing json return type to be rejected
      configurer.favorPathExtension(false);
   }

   private boolean isWebSphere() {
      return ClassUtils.isPresent(
         "com.ibm.websphere.wsoc.WsWsocServerContainer",
         AbstractHandshakeHandler.class.getClassLoader());
   }

   private static final Logger LOG = LoggerFactory.getLogger(WebConfig.class);
}
