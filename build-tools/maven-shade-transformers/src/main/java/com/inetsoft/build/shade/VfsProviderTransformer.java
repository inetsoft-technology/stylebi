/*
 * maven-shade-transformers - StyleBI is a business intelligence web application.
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
package com.inetsoft.build.shade;

import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.resource.ReproducibleResourceTransformer;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class VfsProviderTransformer implements ReproducibleResourceTransformer {
   private long time = 0L;
   private Document document;
   private String resource;

   @Override
   public void processResource(String resource, InputStream is, List<Relocator> relocators,
                               long time) throws IOException
   {
      this.time = time;
      this.resource = resource;

      try {
         document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);
         Element root = document.getDocumentElement();
         relocateClasses(root.getElementsByTagName("default-provider"), relocators);
         relocateClasses(root.getElementsByTagName("provider"), relocators);
      }
      catch(SAXException | ParserConfigurationException e) {
         throw new IOException("Failed to parse providers.xml", e);
      }
   }

   @Override
   public boolean canTransformResource(String resource) {
      return "inetsoft/staging/org/apache/commons/vfs2/impl/providers.xml".equals(resource);
   }

   @Override
   public void processResource(String resource, InputStream is, List<Relocator> relocators)
      throws IOException
   {
      processResource(resource, is, relocators, 0L);
   }

   @Override
   public boolean hasTransformedResource() {
      return document != null;
   }

   @Override
   public void modifyOutputStream(JarOutputStream os) throws IOException {
      JarEntry entry = new JarEntry(resource);
      entry.setTime(time);
      os.putNextEntry(entry);

      try {
         Transformer transformer = TransformerFactory.newInstance().newTransformer();
         DOMSource source = new DOMSource(document);
         StreamResult result = new StreamResult(os);
         transformer.transform(source, result);
      }
      catch(TransformerException e) {
         throw new IOException("Failed to write transformed providers.xml", e);
      }

      os.closeEntry();
   }

   private void relocateClasses(NodeList nodes, List<Relocator> relocators) {
      for(int i = 0; i < nodes.getLength(); i++) {
         Element element = (Element) nodes.item(i);
         String className = element.getAttribute("class-name");

         if(!className.isEmpty()) {
            for(Relocator relocator : relocators) {
               if(relocator.canRelocateClass(className)) {
                  className = relocator.relocateClass(className);
                  element.setAttribute("class-name", className);
                  break;
               }
            }
         }
      }
   }
}
