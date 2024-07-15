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
package com.inetsoft.build.shade;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.resource.ReproducibleResourceTransformer;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class AwsTransformer implements ReproducibleResourceTransformer {
   private final Map<String, Set<String>> serviceEntries = new LinkedHashMap<>();

   @Override
   public boolean canTransformResource(String resource) {
      return "inetsoft/storage/aws/software/amazon/awssdk/services/s3/execution.interceptors"
         .equalsIgnoreCase(resource);
   }

   @Override
   public void processResource(String resource, InputStream is, List<Relocator> relocators,
                               long time) throws IOException
   {
      Set<String> serviceLines = getServiceLines(resource);

      for(String line : readAllLines(is)) {
         if(!line.isEmpty()) {
            serviceLines.add(relocateIfPossible(relocators, line));
         }
      }

      is.close();
   }

   public void processResource(String resource, InputStream is, List<Relocator> relocators)
      throws IOException
   {
      Set<String> serviceLines = getServiceLines(resource);

      for(String line : readAllLines(is)) {
         if(!line.isEmpty()) {
            serviceLines.add(relocateIfPossible(relocators, line));
         }
      }

      is.close();
   }

   private Set<String> getServiceLines(String resource) {
      Set<String> lines = serviceEntries.get(resource);

      if(lines == null) {
         lines = new LinkedHashSet<>();
         serviceEntries.put(resource, lines);
      }

      return lines;
   }

   private List<String> readAllLines(InputStream is) throws IOException {
      return IOUtils.readLines(is, "utf-8");
   }

   private String relocateIfPossible(List<Relocator> relocators, String line) {
      for(Relocator relocator : relocators) {
         if(relocator.canRelocateClass(line)) {
            return relocator.relocateClass(line);
         }
      }

      return line;
   }

   public boolean hasTransformedResource() {
      return !serviceEntries.isEmpty();
   }

   public void modifyOutputStream(JarOutputStream jos) throws IOException {
      for(Map.Entry<String, Set<String>> entry : serviceEntries.entrySet()) {
         jos.putNextEntry(new JarEntry(entry.getKey()));
         jos.write(toResourceBytes(entry.getValue()));
      }
   }

   private byte[] toResourceBytes(Set<String> value) throws IOException {
      StringBuilder builder = new StringBuilder();

      for(String line : value) {
         builder.append(line).append('\n');
      }

      return builder.toString().getBytes("utf-8");
   }
}
