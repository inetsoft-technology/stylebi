/*
 * Path Utilities Maven Plugin - StyleBI is a business intelligence web application.
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
package com.inetsoft.build.pathutils;

import org.apache.maven.plugin.*;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.Properties;

@Mojo(name = "dockerize-paths", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public class DockerizePathsMojo extends AbstractMojo {
   /**
    * The current maven project.
    */
   @Parameter(property = "project", required = true, readonly = true)
   private MavenProject project;

   /**
    * Map of property names containing a path to convert to the name of the property containing the
    * converted path.
    */
   @Parameter(property = "properties")
   private Properties properties;

   @Override
   public void execute() throws MojoExecutionException, MojoFailureException {
      if(properties != null && !properties.isEmpty()) {
         for(String source : properties.stringPropertyNames()) {
            String target = properties.getProperty(source);
            String sourceValue;

            if("project.build.directory".equals(source)) {
               sourceValue = new File(project.getBasedir(), "target").getAbsolutePath();
            }
            else {
               sourceValue = project.getProperties().getProperty(target);
            }

            if(sourceValue != null) {
               if(sourceValue.matches("^[A-Za-z]:.+$")) {
                  String targetValue = "/" + sourceValue.substring(0, 1).toLowerCase() +
                     "/" + sourceValue.substring(3).replace('\\', '/');
                  project.getProperties().put(target, targetValue);
               }
            }
         }
      }
   }
}
