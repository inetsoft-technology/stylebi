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
package inetsoft.graph.data;

import inetsoft.report.Hyperlink;
import inetsoft.test.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link HRef} against its subclass {@link Hyperlink.Ref}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class HRefHyperlinkRefTest {
   @Test
   @Disabled("Not filed (not reachable: nothing creates `new HRef(`, only Hyperlink.Ref instances exist): HRef.equals(Hyperlink.Ref) is true but Hyperlink.Ref.equals(HRef) is false: "
      + "expected the same answer both ways")
   void equalsIsSymmetricBetweenARefAndItsSubclass() {
      HRef h = new HRef("u");
      Hyperlink.Ref r = new Hyperlink.Ref();
      r.setLink("u");
      assertEquals(h.equals(r), r.equals(h));
   }

   @Test
   @Disabled("Not filed (not safe to fix alone: PDF3Generator.java:629 keys a LinkedHashMap by Hyperlink.Ref, a content hashCode would merge link areas): Hyperlink.Ref overrides equals without hashCode: expected equal hash codes for equal refs, "
      + "actual different identity hash codes")
   void equalHyperlinkRefsHaveEqualHashCodes() {
      Hyperlink.Ref a = new Hyperlink.Ref();
      a.setLink("u");
      a.setName("n");
      a.setParameter("p", 1);
      Hyperlink.Ref b = new Hyperlink.Ref();
      b.setLink("u");
      b.setName("n");
      b.setParameter("p", 1);
      assertEquals(a, b);
      assertEquals(a.hashCode(), b.hashCode());
   }
}
