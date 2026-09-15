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

import javax.xml.XMLConstants;
import javax.xml.stream.*;
import javax.xml.transform.*;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

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
      final byte[] responseBytes = input.readAllBytes();
      final Map<String, String> namespaces = peekRootNamespaces(responseBytes);

      if(transformer == null || !namespaces.equals(transformerNamespaces) ||
                                query instanceof EndpointQuery &&
                                ((EndpointQuery) query).getLookupEndpoint0() != null)
      {
         transformerNamespaces = namespaces;
         transformer = createXSLTTransformer(namespaces);
      }

      parser.resetRoot();
      runXSLTTransformer(new ByteArrayInputStream(responseBytes));
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

   private Transformer createXSLTTransformer(Map<String, String> namespaces) throws Exception {
      final StringStreamSource source = createXSLTSource(namespaces);

      return runWithPluginContextClassLoader(() -> {
         final TransformerFactory xsltTransformerFactory = TransformerFactory.newInstance();

         try {
            xsltTransformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
         }
         catch(IllegalArgumentException ignored) {}

         try {
            xsltTransformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
         }
         catch(IllegalArgumentException ignored) {}

         final Transformer xsltTransformer = xsltTransformerFactory.newTransformer(source);
         xsltTransformer.setParameter("parser", parser);
         setXSLTObjectParameters(xsltTransformer);
         return xsltTransformer;
      });
   }

   protected void setXSLTObjectParameters(Transformer transformer) {
      // no-op, to be optionally overwritten by implementing class
   }

   private StringStreamSource createXSLTSource(Map<String, String> namespaces) throws IOException {
      final Map<String, String> xsltParams = new HashMap<>();
      xsltParams.put("$xpath", getXpath(query));
      addXSLTStringParams(xsltParams);

      final InputStream xsltInput = injectNamespaceDeclarations(getXSLTInputStream(), namespaces);
      final XSLTParamTransformer inputTransformer = new XSLTParamTransformer();
      return inputTransformer.transform(xsltInput, xsltParams);
   }

   /**
    * Declares the response document's own namespace prefixes on the generated stylesheet's
    * root (its {@code $namespaces} placeholder - see basic-xpath.xslt/iteration-xpath.xslt/
    * paginated-xpath.xslt), so a caller xpath using the document's real prefixes (e.g.
    * "/wb:countries/wb:country") resolves against the document instead of the stylesheet's
    * previously-empty namespace scope (which only ever bound xsl/is). Done as a raw text
    * substitution, not via XSLTParamTransformer's own param map, because that map HTML-attribute-
    * encodes values (correct for a match-pattern string, wrong here since these need to remain
    * literal xmlns:prefix="uri" XML syntax).
    */
   private static InputStream injectNamespaceDeclarations(InputStream xsltInput,
                                                           Map<String, String> namespaces)
      throws IOException
   {
      final String declarations = namespaces.entrySet().stream()
         .map(e -> "xmlns:" + e.getKey() + "=\"" + escapeAttributeValue(e.getValue()) + "\"")
         .collect(Collectors.joining(" "));
      final String xslt = new String(xsltInput.readAllBytes(), StandardCharsets.UTF_8)
         .replace("$namespaces", declarations);
      return new ByteArrayInputStream(xslt.getBytes(StandardCharsets.UTF_8));
   }

   /**
    * XML attribute-value escaping, in the order that matters: {@code &} first (otherwise the
    * entities produced by the other two replacements would themselves get escaped), then
    * {@code "} (the value is always embedded in a double-quoted attribute here), then
    * {@code <} (illegal unescaped inside any attribute value, and would otherwise let a
    * response-controlled namespace URI splice markup into the generated stylesheet).
    */
   private static String escapeAttributeValue(String value) {
      return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
   }

   /**
    * Peeks only the response document's root start-element for its declared xmlns:prefix
    * bindings (a cheap StAX scan that stops at the first START_ELEMENT), rather than parsing
    * the whole document twice. A document with no declared prefixes, or one that fails to parse
    * here, yields an empty map - xpath resolution then behaves exactly as it did before this fix
    * (only xsl/is bound). A bare default ("xmlns=...", no prefix) namespace is deliberately not
    * collected: an unprefixed xpath step still resolves to no-namespace-URI regardless (XPath/
    * XSLT 1.0 node-test rule), so injecting it would not change matching behavior.
    *
    * <p>A namespace URI that isn't {@code http(s):} is also skipped - defense in depth, since
    * these bindings get spliced onto the same stylesheet root that declares {@code xmlns:is=
    * "xalan://..."} (a Java-extension binding). There's no legitimate reason a response
    * document's own namespace URI needs to resolve as an extension scheme like {@code xalan:},
    * and Xalan only consults such a binding when a name is used in a function-call position, not
    * as a plain node-test namespace - but excluding non-http(s) schemes here costs nothing and
    * removes the question entirely.
    */
   private static Map<String, String> peekRootNamespaces(byte[] responseBytes) {
      final Map<String, String> namespaces = new LinkedHashMap<>();

      try {
         final XMLInputFactory factory = XMLInputFactory.newInstance();
         factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
         factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
         final XMLStreamReader reader =
            factory.createXMLStreamReader(new ByteArrayInputStream(responseBytes));

         while(reader.hasNext()) {
            if(reader.next() == XMLStreamConstants.START_ELEMENT) {
               for(int i = 0; i < reader.getNamespaceCount(); i++) {
                  final String prefix = reader.getNamespacePrefix(i);
                  final String uri = reader.getNamespaceURI(i);

                  if(prefix != null && !prefix.isEmpty() && isHttpUri(uri)) {
                     namespaces.put(prefix, uri);
                  }
               }

               break;
            }
         }

         reader.close();
      }
      catch(Exception ignored) {
         // response isn't parseable as XML (or has no root element) - fall back to no injected
         // namespaces, same behavior as before this fix.
      }

      return namespaces;
   }

   private static boolean isHttpUri(String uri) {
      if(uri == null) {
         return false;
      }

      final String lower = uri.toLowerCase(Locale.ROOT);
      return lower.startsWith("http://") || lower.startsWith("https://");
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
   private Map<String, String> transformerNamespaces;

   private final DocumentParser parser;
   protected final RestXMLQuery query;

   public static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
