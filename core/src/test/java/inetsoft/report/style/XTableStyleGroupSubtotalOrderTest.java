package inetsoft.report.style;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.XTableUtil;
import inetsoft.uql.viewsheet.internal.VSTableStructureDefaults;
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
class XTableStyleGroupSubtotalOrderTest {
   @Test
   void groupTotalSpecsPrecedeZebraAndCarrySubtotalBackground() {
      XTableStyle style = new XTableStyle(XTableUtil.getDefaultTableLens());

      // shipped Default-Style stand-in: one appended zebra spec
      XTableStyle.Specification zebra = style.new Specification();
      zebra.setType(XTableStyle.Specification.REGULAR);
      zebra.setIndex(1);
      zebra.setRepeat(true);
      zebra.put("background", new Color(0xF5F5F5));
      style.addSpecification(zebra);

      // reproduce the production insertion: levels 0-9, both axes, prepended in order
      Color subtotal = VSTableStructureDefaults.subtotalBackground();
      int pos = 0;

      for(int level = 0; level < 10; level++) {
         XTableStyle.Specification rowSpec = style.new Specification();
         rowSpec.setType(XTableStyle.Specification.ROW_GROUP_TOTAL);
         rowSpec.setIndex(level);
         rowSpec.put("background", subtotal);
         style.addSpecification(pos++, rowSpec);

         XTableStyle.Specification colSpec = style.new Specification();
         colSpec.setType(XTableStyle.Specification.COL_GROUP_TOTAL);
         colSpec.setIndex(level);
         colSpec.put("background", subtotal);
         style.addSpecification(pos++, colSpec);
      }

      // 20 group-total specs prepended, zebra pushed to the end
      assertEquals(21, style.getSpecificationCount());

      for(int i = 0; i < 20; i++) {
         XTableStyle.Specification spec = style.getSpecification(i);
         int type = spec.getType();
         assertTrue(type == XTableStyle.Specification.ROW_GROUP_TOTAL ||
                    type == XTableStyle.Specification.COL_GROUP_TOTAL,
                    "spec " + i + " must be a group-total spec ahead of the zebra");
         assertEquals(0xEEEAE1, ((Color) spec.get("background")).getRGB() & 0xFFFFFF);
      }

      // zebra survives, last, still #F5F5F5 (untouched)
      XTableStyle.Specification last = style.getSpecification(20);
      assertEquals(XTableStyle.Specification.REGULAR, last.getType());
      assertEquals(0xF5F5F5, ((Color) last.get("background")).getRGB() & 0xFFFFFF);
   }
}
