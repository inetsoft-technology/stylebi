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
package inetsoft.report.lib;

import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.invoke.MethodHandles;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Value.Immutable
public interface ScriptEntry {
   String function();

   @Nullable
   String signature();

   static Builder builder() {
      return new Builder();
   }

   class Builder extends ImmutableScriptEntry.Builder {
      /**
       * Set a new signature or update an existing signature for script functions.
       *
       * @param name the specified script function name.
       * @param func the specified script function.
       */
      public Builder from(String name, String func) {
         BufferedReader reader = new BufferedReader(new StringReader(func));
         StringBuilder bufInSingleLine = new StringBuilder();
         boolean isMultiLineComment = false;
         String line;

         try {
            // trim the spaces and the comments
            while((line = reader.readLine()) != null) {
               int idx = line.indexOf("//");

               if(idx != -1) {
                  line = line.substring(0, idx);
               }

               if(!isMultiLineComment) {
                  idx = line.indexOf("/*");

                  if(idx != -1) {
                     isMultiLineComment = true;
                     line = line.substring(0, idx);
                  }
               }
               else {
                  idx = line.indexOf("*/");

                  if(idx != -1) {
                     isMultiLineComment = false;
                     line = line.substring(idx + 2);
                  }
                  else {
                     continue;
                  }
               }

               idx = line.indexOf("//");

               if(idx != -1) {
                  line = line.substring(0, idx);
               }

               String content = line.trim();
               bufInSingleLine.append(content);
               bufInSingleLine.append(
                  content.length() == 0 || content.endsWith("(") ? "" : " ");
            }
         }
         catch(IOException ex) {
            LOG.warn("Failed to get user signature, I/O error reading script function", ex);
         }

         Pattern p = Pattern.compile("function[ ]+" + name + "[(].*[)]");
         Matcher matcher = p.matcher(bufInSingleLine);
         final String signature;

         if(matcher.find()) {
            String sig = matcher.group();
            int beginIndex = sig.indexOf(name);
            int endIndex = sig.indexOf(')', beginIndex);
            sig = sig.substring(beginIndex, endIndex + 1);
            signature = sig;
         }
         // if real signore not found, just put an empty one otherwise it shows up as null
         else {
            signature =  name + "()";
         }

         return builder().function(func).signature(signature);
      }

      private static final Logger LOG =
         LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   }
}
