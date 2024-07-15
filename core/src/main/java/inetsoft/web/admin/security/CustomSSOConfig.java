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
package inetsoft.web.admin.security;

import inetsoft.sree.SreeEnv;
import inetsoft.util.DataSpace;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class CustomSSOConfig {
   public String getClassName() {
      return SreeEnv.getProperty("sso.custom.class");
   }

   public void setClassName(String className) {
      SreeEnv.setProperty("sso.custom.class", className);

      if(className != null) {
         setInlineGroovyClass(null);
      }
   }

   public String getInlineGroovyClass() {
      DataSpace dataSpace = DataSpace.getDataSpace();

      if(dataSpace.exists(null, FILE_NAME)) {

         try(InputStream input = dataSpace.getInputStream(null, FILE_NAME)) {
            return IOUtils.toString(input, StandardCharsets.UTF_8);
         }
         catch(Exception e) {
            LOG.warn("Failed to read inline Groovy SSO class", e);
         }
      }

      return null;
   }

   public void setInlineGroovyClass(String content) {
      DataSpace dataSpace = DataSpace.getDataSpace();

      if(content == null) {
         if(dataSpace.exists(null, FILE_NAME)) {
            dataSpace.delete(null, FILE_NAME);
         }
      }
      else {
         setClassName(null);

         try {
            dataSpace.withOutputStream(
               null, FILE_NAME, output -> IOUtils.write(content, output, StandardCharsets.UTF_8));
         }
         catch(Exception e) {
            LOG.warn("Failed to write inline Groovy SSO class", e);
         }
      }
   }

   private static final String FILE_NAME = "GroovySSOFilter.groovy";
   private static final Logger LOG = LoggerFactory.getLogger(CustomSSOConfig.class);
}
