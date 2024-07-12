/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
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
package inetsoft.uql.rest.xml.parse;

import com.helger.xml.transform.*;
import inetsoft.uql.rest.xml.xslt.XSLTParamTransformer;
import org.junit.jupiter.api.*;
import org.xml.sax.InputSource;

import javax.xml.transform.*;
import javax.xml.transform.sax.SAXSource;
import java.io.*;
import java.util.Collections;

/**
 * Test class that illustrates how the XSLT transformer and TransformFunctions work to parse an xml
 * template.
 */
@Disabled
public class XSLTTest {
   @Test
   public void test() throws IOException, TransformerException {
      final InputStream input = getClass().getClassLoader().getResourceAsStream(
         "inetsoft/uql/rest/xml/parse/test.xslt");
      final StringStreamSource source =
         new XSLTParamTransformer().transform(input, Collections.emptyMap());

      final TransformerFactory xsltTransformerFactory = TransformerFactory.newInstance();
      final Transformer xsltTransformer = xsltTransformerFactory.newTransformer(source);

      final InputStream xml = getClass().getClassLoader().getResourceAsStream(
         "inetsoft/uql/rest/xml/parse/test.xml");

      final SAXSource sax = new SAXSource(new InputSource(xml));
      final StringStreamResult result = new StringStreamResult();
      xsltTransformer.transform(sax, result);
      System.out.println(result.getAsString());
   }
}
