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
package inetsoft.uql.rest.xml;

import com.helger.xml.transform.StringStreamSource;
import inetsoft.uql.rest.*;
import inetsoft.uql.rest.xml.parse.*;
import inetsoft.uql.rest.xml.xslt.XSLTParamTransformer;
import inetsoft.uql.util.BaseJsonTable;
import org.apache.commons.io.output.NullOutputStream;
import org.slf4j.*;
import org.xml.sax.InputSource;

import javax.xml.transform.*;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * Handles transforming an XML stream into a java object.
 */
public abstract class AbstractXMLStreamTransformer implements InputTransformer {
   AbstractXMLStreamTransformer(RestXMLQuery query) throws Exception {
      this.query = query;
      parser = DocumentParserFactory.createDocumentParser(query);
   }

   @Override
   public Object transform(InputStream input, String path) {
      throw new UnsupportedOperationException();
   }

   @Override
   public Object transform(Object obj, String path) {
      throw new UnsupportedOperationException();
   }

   @Override
   public ParsedNode transform(InputStream input) throws Exception {
      if(transformer == null || query instanceof EndpointQuery &&
                                ((EndpointQuery) query).getLookupEndpoint0() != null)
      {
         transformer = createXSLTTransformer();
      }

      parser.resetRoot();
      runXSLTTransformer(input);
      return parser.getRoot();
   }

   @Override
   public String updateOutputString(String output, String path, Object value) {
      throw new UnsupportedOperationException();
   }

   private void runXSLTTransformer(InputStream input) throws Exception {
      final SAXSource source = new SAXSource(new InputSource(input));
      final StreamResult result = new StreamResult(NullOutputStream.NULL_OUTPUT_STREAM);

      runWithPluginContextClassLoader(() -> {
         transformer.transform(source, result);
         return null; // no return type
      });
   }

   private static <T> T runWithPluginContextClassLoader(Callable<T> fn) throws Exception {
      final Thread thread = Thread.currentThread();
      final ClassLoader old = thread.getContextClassLoader();
      thread.setContextClassLoader(AbstractXMLStreamTransformer.class.getClassLoader());

      try {
         return fn.call();
      }
      finally {
         thread.setContextClassLoader(old);
      }
   }

   private Transformer createXSLTTransformer() throws Exception {
      final StringStreamSource source = createXSLTSource();

      return runWithPluginContextClassLoader(() -> {
         final TransformerFactory xsltTransformerFactory = TransformerFactory.newInstance();
         final Transformer xsltTransformer = xsltTransformerFactory.newTransformer(source);
         xsltTransformer.setParameter("parser", parser);
         setXSLTObjectParameters(xsltTransformer);
         return xsltTransformer;
      });
   }

   protected void setXSLTObjectParameters(Transformer transformer) {
      // no-op, to be optionally overwritten by implementing class
   }

   private StringStreamSource createXSLTSource() throws IOException {
      final Map<String, String> xsltParams = new HashMap<>();
      xsltParams.put("$xpath", getXpath(query));
      addXSLTStringParams(xsltParams);

      final InputStream xsltInput = getXSLTInputStream();
      final XSLTParamTransformer inputTransformer = new XSLTParamTransformer();
      return inputTransformer.transform(xsltInput, xsltParams);
   }

   protected void addXSLTStringParams(Map<String, String> params) {
      // no-op, to be optionally overwritten by implementing class
   }

   protected abstract InputStream getXSLTInputStream();

   private String getXpath(RestXMLQuery query) {
      String xpath = query.getXpath();

      if(xpath == null || xpath.isEmpty()) {
         xpath = "*";
      }

      return xpath;
   }

   void initializeColumnTypes(BaseJsonTable table) {
      final TypeMap typeMap = parser.getTypeMap();

      for(final Map.Entry<List<String>, String> entry : typeMap.getTypes().entrySet()) {
         final List<String> path = entry.getKey();
         final String type = entry.getValue();
         final String header = String.join(".", path);
         table.setColumnType(header, type);
      }
   }

   public List<MapNode> getLookupEntities(String entityPath) {
      final ParsedNode root = parser.getRoot();

      if(root instanceof MapNode) {
         return ((MapNode) root).getLookupEntities(entityPath);
      }

      return Collections.emptyList();
   }

   private Transformer transformer;

   private final DocumentParser parser;
   protected final RestXMLQuery query;

   public static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
