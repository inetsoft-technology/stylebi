/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.report;

import inetsoft.test.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class HyperlinkRefTest {
   /**
    * Bug #76640, a column value substituted into a web link must be percent-encoded so
    * that characters that are not valid in a URL query do not corrupt the link.
    */
   @Test
   public void testWebLinkColumnValueIsEncoded() {
      Hyperlink link = new Hyperlink();
      link.setLinkType(Hyperlink.WEB_LINK);
      link.setLink("http://host/page?x=field['Range']");

      Map<String, Object> map = new HashMap<>();
      map.put("Range", "> 12");

      Hyperlink.Ref ref = new Hyperlink.Ref(link, map);
      Assertions.assertEquals("http://host/page?x=%3E%2012", ref.getLink());
   }

   /**
    * A value containing a query separator must not be able to inject extra parameters
    * into the target URL.
    */
   @Test
   public void testWebLinkColumnValueSeparatorIsEncoded() {
      Hyperlink link = new Hyperlink();
      link.setLinkType(Hyperlink.WEB_LINK);
      link.setLink("http://host/page?x=field['Range']");

      Map<String, Object> map = new HashMap<>();
      map.put("Range", "a&y=b");

      Hyperlink.Ref ref = new Hyperlink.Ref(link, map);
      Assertions.assertEquals("http://host/page?x=a%26y%3Db", ref.getLink());
   }

   /**
    * Only web links are URL-encoded. A viewsheet link uses the value as part of an
    * asset identifier, so it must be left alone.
    */
   @Test
   public void testViewsheetLinkColumnValueIsNotEncoded() {
      Hyperlink link = new Hyperlink();
      link.setLinkType(Hyperlink.VIEWSHEET_LINK);
      link.setLink("1^128^__NULL__^field['Range']^host-org");

      Map<String, Object> map = new HashMap<>();
      map.put("Range", "my vs");

      Hyperlink.Ref ref = new Hyperlink.Ref(link, map);
      Assertions.assertEquals("1^128^__NULL__^my vs^host-org", ref.getLink());
   }

   /**
    * A link whose entire URL comes from a column is used as-is and must not be encoded.
    */
   @Test
   public void testColumnSuppliedUrlIsNotEncoded() {
      Hyperlink link = new Hyperlink();
      link.setLinkType(Hyperlink.WEB_LINK);
      link.setLink("hyperlink:Url");

      Map<String, Object> map = new HashMap<>();
      map.put("Url", "http://host/page?x=1&y=2");

      Hyperlink.Ref ref = new Hyperlink.Ref(link, map);
      Assertions.assertEquals("http://host/page?x=1&y=2", ref.getLink());
   }
}
