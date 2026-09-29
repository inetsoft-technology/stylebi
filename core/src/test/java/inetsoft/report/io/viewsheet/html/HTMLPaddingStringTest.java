package inetsoft.report.io.viewsheet.html;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every HTML padding declaration reads its own Insets field. Cell padding used to be symmetric,
 * so a padding-top that read Insets.right went unnoticed until slice A made it asymmetric.
 */
@Tag("core")
class HTMLPaddingStringTest {
   @Test
   void eachDeclarationReadsItsOwnField() {
      assertEquals("padding-left:4px;padding-right:3px;padding-top:1px;padding-bottom:2px;",
                   padding(new Insets(1, 4, 2, 3)));
   }

   @Test
   void topFollowsTopNotRight() {
      assertTrue(padding(new Insets(6, 8, 6, 8)).contains("padding-top:6px"));
   }

   @Test
   void symmetricPaddingIsUnchanged() {
      assertEquals("padding-left:16px;padding-right:16px;padding-top:16px;padding-bottom:16px;",
                   padding(new Insets(16, 16, 16, 16)));
   }

   @Test
   void noPaddingWritesNothing() {
      assertEquals("", padding(null));
   }

   private static String padding(Insets insets) {
      return new HTMLCoordinateHelper().getPaddingString(insets);
   }
}
