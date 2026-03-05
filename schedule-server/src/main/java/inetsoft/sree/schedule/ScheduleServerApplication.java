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
package inetsoft.sree.schedule;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.UserEnv;
import inetsoft.util.*;
import inetsoft.util.log.LogManager;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;
import java.util.Properties;

/**
 * Spring Boot entry point for the standalone community schedule server process.
 * <p>
 * This class replaces the legacy {@link ScheduleServer#main(String[])} startup path.
 * Pre-Spring configuration (system properties, logging, headless mode) is performed
 * here before the application context is started. RMI binding and scheduler startup
 * are handled by {@link ScheduleServerContext}.
 */
@SpringBootApplication
@Import(ScheduleServerContext.class)
public class ScheduleServerApplication {
   public static void main(String[] args) {
      ApplicationArguments appArguments = new DefaultApplicationArguments(args);
      System.setProperty("ScheduleServer", "true");

      // During process-aot, disable AOT runtime mode so the context can be analyzed
      if("true".equals(System.getProperty("spring.aot.processing"))) {
         System.setProperty("spring.aot.enabled", "false");
      }
      else {
         String home = null;

         if(appArguments.containsOption("sree.home")) {
            home = appArguments.getOptionValues("sree.home").getFirst();
         }

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
            File homeFile = new File(home);
            homeFile.mkdirs();
            System.setProperty(
               "derby.stream.error.file",
               new File(homeFile, "derby.log").getAbsolutePath());
            ConfigurationContext.getContext().setHome(homeFile.getAbsolutePath());
         }

         System.setProperty("java.rmi.server.hostname", Tool.getRmiIP());
         LogManager.initializeForStartup();

         RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
         List<String> arguments = runtimeBean.getInputArguments();

         for(int i = 0; i < arguments.size() - 1; i++) {
            if("-jar".equals(arguments.get(i))) {
               String jar = arguments.get(i + 1);

               if(jar.matches("^bootstrap.*\\.jar$")) {
                  FileSystemService.getInstance().getFile(jar).deleteOnExit();
               }

               break;
            }
         }

         if(OperatingSystem.isUnix()) {
            String val = System.getProperty("java.awt.headless");

            if(val == null || val.isEmpty()) {
               System.setProperty("java.awt.headless", "true");
            }
         }

         Catalog.setCatalogGetter(UserEnv.getCatalogGetter());
         SreeEnv.setProperty("log.output.stderr", "false");
      }

      SpringApplication app = new SpringApplication(ScheduleServerApplication.class);
      app.setWebApplicationType(WebApplicationType.NONE);
      app.run(args);
   }
}
