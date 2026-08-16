/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.viewsheet;

import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.AnnotationRectangleVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.AnnotationVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("core")
class AnnotationFamilyTest {
   /**
    * The infos are mocked rather than constructed: {@code VSAssemblyInfo}'s constructor reads
    * {@code SreeEnv}, which needs a Spring context these unit tests deliberately do without.
    */
   private static AnnotationVSAssembly annotation(String line, String rectangle) {
      AnnotationVSAssemblyInfo info = mock(AnnotationVSAssemblyInfo.class);
      when(info.getLine()).thenReturn(line);
      when(info.getRectangle()).thenReturn(rectangle);
      AnnotationVSAssembly assembly = mock(AnnotationVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      return assembly;
   }

   @Test
   void recognizesAllThreeParts() {
      assertTrue(AnnotationFamily.isPart(mock(AnnotationVSAssembly.class)));
      assertTrue(AnnotationFamily.isPart(mock(AnnotationLineVSAssembly.class)));
      assertTrue(AnnotationFamily.isPart(mock(AnnotationRectangleVSAssembly.class)));
   }

   @Test
   void doesNotClaimAnUnrelatedAssembly() {
      assertFalse(AnnotationFamily.isPart(mock(TextVSAssembly.class)));
      assertFalse(AnnotationFamily.isSubordinatePart(mock(TextVSAssembly.class)));
   }

   /** The annotation itself stays in the flat listing; only its line and rectangle fold in. */
   @Test
   void treatsOnlyTheLineAndRectangleAsSubordinate() {
      assertFalse(AnnotationFamily.isSubordinatePart(mock(AnnotationVSAssembly.class)));
      assertTrue(AnnotationFamily.isSubordinatePart(mock(AnnotationLineVSAssembly.class)));
      assertTrue(AnnotationFamily.isSubordinatePart(mock(AnnotationRectangleVSAssembly.class)));
   }

   @Test
   void namesThePartsAnAnnotationOwns() {
      List<String> parts = AnnotationFamily.partsOf(annotation("Line1", "Rect1"));

      assertEquals(List.of("Line1", "Rect1"), parts);
   }

   @Test
   void reportsNoPartsForAnUnrelatedAssembly() {
      assertTrue(AnnotationFamily.partsOf(mock(TextVSAssembly.class)).isEmpty());
   }

   @Test
   void toleratesAnAnnotationMissingAPartRatherThanEmittingNull() {
      assertEquals(List.of("Rect1"), AnnotationFamily.partsOf(annotation(null, "Rect1")));
   }

   @Test
   void readsTheContentFromTheRectangleWhereItActuallyLives() {
      AnnotationRectangleVSAssemblyInfo rectInfo = mock(AnnotationRectangleVSAssemblyInfo.class);
      when(rectInfo.getContent()).thenReturn("<p>Check this spike</p>");
      AnnotationRectangleVSAssembly rectangle = mock(AnnotationRectangleVSAssembly.class);
      when(rectangle.getVSAssemblyInfo()).thenReturn(rectInfo);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("Rect1")).thenReturn(rectangle);

      assertEquals("<p>Check this spike</p>",
                   AnnotationFamily.contentOf(vs, annotation("Line1", "Rect1")));
   }

   @Test
   void reportsNoContentWhenTheRectangleIsMissing() {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(null);

      assertNull(AnnotationFamily.contentOf(vs, annotation("Line1", "Rect1")));
   }

   @Test
   void theRemovalRefusalExplainsTheOrphaning() {
      Exception thrown = AnnotationFamily.removeRefusal("Rect1");

      assertTrue(thrown.getMessage().contains("Rect1"));
      assertTrue(thrown.getMessage().contains("orphan"),
                 "the refusal has to say why, or it reads as an arbitrary restriction");
   }
}
