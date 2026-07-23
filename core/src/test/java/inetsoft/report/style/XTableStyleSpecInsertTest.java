package inetsoft.report.style;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.XTableUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class XTableStyleSpecInsertTest {
   @Test
   void insertPlacesSpecAheadOfExisting() {
      XTableStyle style = new XTableStyle(XTableUtil.getDefaultTableLens());

      // shipped-style stand-in: an appended zebra (REGULAR) spec
      XTableStyle.Specification zebra = style.new Specification();
      zebra.setType(XTableStyle.Specification.REGULAR);
      zebra.setIndex(1);
      zebra.setRepeat(true);
      zebra.put("background", new Color(0xF5F5F5));
      style.addSpecification(zebra);

      assertEquals(1, style.getSpecificationCount());

      // group-total spec inserted at the front must precede the zebra spec
      XTableStyle.Specification groupTotal = style.new Specification();
      groupTotal.setType(XTableStyle.Specification.ROW_GROUP_TOTAL);
      groupTotal.setIndex(0);
      groupTotal.put("background", new Color(0xEEEAE1));
      style.addSpecification(0, groupTotal);

      assertEquals(2, style.getSpecificationCount());
      assertSame(groupTotal, style.getSpecification(0), "group-total must be first (findSpec wins)");
      assertSame(zebra, style.getSpecification(1));
   }
}
