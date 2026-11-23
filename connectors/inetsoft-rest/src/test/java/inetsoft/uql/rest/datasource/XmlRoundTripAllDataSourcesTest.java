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
package inetsoft.uql.rest.datasource;

import inetsoft.test.EndpointsSpringContext;
import inetsoft.test.SreeHome;
import inetsoft.uql.rest.AbstractRestDataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Saves and loads every REST data source: a data source in its default state must come back with
 * the same text properties (a missing value must not turn into the text "null"), and text with
 * XML special characters must still produce well-formed XML and come back unchanged.
 */
@EndpointsSpringContext
@SreeHome
@Tag("core")
class XmlRoundTripAllDataSourcesTest {
   private static final String SPECIAL = "a&b<c>\"d'e";

   /** Known problem: QuickBooks #81964 (a missing value is saved as the text "null"). */
   private static final Set<String> KNOWN = Set.of("QuickbooksReportsDataSource.null");

   /**
    * Properties that are not kept as they are by design: the name belongs to the repository, the
    * content type is only written for requests with a body, and a few data sources turn an empty
    * value into null or a default.
    */
   private static final Set<String> NOT_KEPT = Set.of(
      "getName", "getContentType", "getDomainSuffix");

   private static final Set<String> NOT_TEXT = Set.of(
      "setName", "setDescription", "setURL", "setUrl", "setFolder", "setCreatedBy",
      "setLastModifiedBy", "setCredentialId");

   private static Object roundTrip(Object source, Class<?> type) throws Exception {
      StringWriter text = new StringWriter();
      PrintWriter writer = new PrintWriter(text);
      ((inetsoft.util.XMLSerializable) source).writeXML(writer);
      writer.flush();
      Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(text.toString().getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
      Object target = type.getConstructor().newInstance();
      ((inetsoft.util.XMLSerializable) target).parseXML(root);
      return target;
   }

   private static Map<String, Object> textProperties(Object ds) {
      Map<String, Object> values = new TreeMap<>();

      for(Method m : ds.getClass().getMethods()) {
         String n = m.getName();

         if(m.getParameterCount() == 0 && m.getReturnType() == String.class &&
            n.startsWith("get") && !n.equals("getClass") && !n.startsWith("getFull") &&
            !n.equals("getType") && !n.equals("getResponseCacheIdentity") && !n.equals("getURL") && !n.equals("getAuthorizationUri") &&
            !n.equals("getTokenUri") && !n.equals("getCreatedBy") &&
            !n.equals("getLastModifiedBy"))
         {
            try {
               values.put(n, m.invoke(ds));
            }
            catch(Throwable e) {
               // not readable in this state
            }
         }
      }

      return values;
   }

   private static boolean isSavedText(Class<?> type, String setter) {
      try {
         type.getMethod("get" + setter.substring(3));
         return !NOT_TEXT.contains(setter);
      }
      catch(NoSuchMethodException e) {
         return false;
      }
   }

   @Test
   void everyDataSourceKeepsItsTextPropertiesThroughSaveAndLoad() throws Exception {
      ClassPathScanningCandidateComponentProvider scanner =
         new ClassPathScanningCandidateComponentProvider(false);
      scanner.addIncludeFilter(new AssignableTypeFilter(AbstractRestDataSource.class));
      List<String> problems = new ArrayList<>();
      int checked = 0;

      for(BeanDefinition definition : scanner.findCandidateComponents("inetsoft.uql.rest")) {
         Class<?> type = Class.forName(definition.getBeanClassName());

         if(Modifier.isAbstract(type.getModifiers())) {
            continue;
         }

         String simple = type.getSimpleName();
         Object source;

         try {
            source = type.getConstructor().newInstance();
            ((AbstractRestDataSource<?>) source).setName("rt-" + simple);
         }
         catch(Throwable e) {
            continue;
         }

         checked++;

         if(!KNOWN.contains(simple + ".null")) {
            try {
               Map<String, Object> before = textProperties(source);
               Map<String, Object> after = textProperties(roundTrip(source, type));

               for(Map.Entry<String, Object> e : before.entrySet()) {
                  Object a = e.getValue() == null ? "" : e.getValue();
                  Object b = after.get(e.getKey()) == null ? "" : after.get(e.getKey());

                  if(!NOT_KEPT.contains(e.getKey()) && !Objects.equals(a, b)) {
                     problems.add(simple + " default " + e.getKey() + ": '" + e.getValue() +
                                  "' came back as '" + after.get(e.getKey()) + "'");
                  }
               }
            }
            catch(Throwable e) {
               problems.add(simple + " default: " + e);
            }
         }

         for(Method m : type.getMethods()) {
            String name = m.getName();

            if(!name.startsWith("set") || m.getParameterCount() != 1 ||
               m.getParameterTypes()[0] != String.class || !isSavedText(type, name) ||
               NOT_KEPT.contains("get" + name.substring(3)))
            {
               continue;
            }

            try {
               Object ds = type.getConstructor().newInstance();
               ((AbstractRestDataSource<?>) ds).setName("rt-" + simple);
               m.invoke(ds, SPECIAL);
               String getter = "get" + name.substring(3);
               Object expected = type.getMethod(getter).invoke(ds);

               if(!SPECIAL.equals(expected)) {
                  continue; // the setter does not keep the value as is
               }

               Object back = roundTrip(ds, type);
               Object actual = type.getMethod(getter).invoke(back);

               if(!Objects.equals(expected, actual)) {
                  problems.add(simple + "." + name + ": '" + expected + "' came back as '" +
                               actual + "'");
               }
            }
            catch(Throwable e) {
               Throwable c = e.getCause() != null ? e.getCause() : e;

               if(!(e instanceof InvocationTargetException) || c instanceof org.xml.sax.SAXException) {
                  problems.add(simple + "." + name + ": " + c);
               }
            }
         }
      }

      assertTrue(checked > 40, "only " + checked + " data sources were checked");
      assertTrue(problems.isEmpty(), problems.size() + " problem(s):\n" +
                 String.join("\n", problems));
   }
}
