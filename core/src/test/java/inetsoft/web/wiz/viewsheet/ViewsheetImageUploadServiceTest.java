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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.service.VSObjectService;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug 81224: {@link ViewsheetImageUploadService}, the wiz agent's Static Image upload. The session
 * service and the image check are mocked; the dashboard is a real {@link Viewsheet}, since the
 * service writes its uploaded-image map.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetImageUploadServiceTest {
   private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3};

   private final Principal user = () -> "agent";
   private ViewsheetSessionService sessions;
   private VSObjectService objects;
   private Viewsheet vs;
   private ViewsheetImageUploadService service;

   @BeforeEach
   void setUp() throws Exception {
      sessions = mock(ViewsheetSessionService.class);
      objects = mock(VSObjectService.class);
      when(objects.isImage(any())).thenReturn(true);
      vs = new Viewsheet();
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);

      // like ViewsheetSessionService.mutate: run the mutation, hand back the warnings
      when(sessions.mutate(anyString(), any(Principal.class),
                           any(ViewsheetSessionService.Mutation.class)))
         .thenAnswer(inv -> {
            inv.<ViewsheetSessionService.Mutation>getArgument(2)
               .run(rvs, "rid", mock(CapturingCommandDispatcher.class));
            return List.of();
         });

      service = new ViewsheetImageUploadService(sessions, objects);
   }

   @Test
   void storesTheImageAndReturnsTheValueAnImageAssemblyTakes() throws Exception {
      Map<String, Object> result = service.upload("tok", user, PNG, "logo.png");

      assertArrayEquals(PNG, vs.getUploadedImageBytes("logo.png"));
      assertEquals(true, result.get("ok"));
      assertEquals("logo.png", result.get("name"));
      assertEquals("^UPLOADED^logo.png", result.get("selectedImage"));
      assertEquals(false, result.get("replaced"));
      assertEquals(PNG.length, result.get("size"));
   }

   @Test
   void aSecondUploadOfTheSameNameReplacesItAndSaysSo() throws Exception {
      service.upload("tok", user, PNG, "logo.png");
      byte[] other = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 9, 9};
      Map<String, Object> result = service.upload("tok", user, other, "logo.png");

      assertEquals(true, result.get("replaced"));
      assertArrayEquals(other, vs.getUploadedImageBytes("logo.png"));
      assertEquals(1, vs.getUploadedImageNames().length);
   }

   @Test
   void aNameWithSpacesIsKeptAsIs() throws Exception {
      Map<String, Object> result = service.upload("tok", user, PNG, "company logo 2.png");

      assertEquals("^UPLOADED^company logo 2.png", result.get("selectedImage"));
      assertNotNull(vs.getUploadedImageBytes("company logo 2.png"));
   }

   @Test
   void aFileThatIsNotAnImageIsRefusedByNameAndNothingIsStored() throws Exception {
      when(objects.isImage(any())).thenReturn(false);

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> service.upload("tok", user, new byte[] {'h', 'i'}, "notes.png"));

      assertTrue(e.getMessage().contains("'notes.png'"), e.getMessage());
      assertTrue(e.getMessage().contains("not an image"), e.getMessage());
      assertRefusedWithoutSideEffects();
   }

   @Test
   void anImageCheckThatThrowsIsAlsoARefusalNotAServerError() throws Exception {
      when(objects.isImage(any())).thenThrow(new IllegalStateException("bad svg"));

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> service.upload("tok", user, new byte[] {'<', 's'}, "broken.svg"));

      assertTrue(e.getMessage().contains("'broken.svg'"), e.getMessage());
      assertRefusedWithoutSideEffects();
   }

   @Test
   void anEmptyFileIsRefusedBeforeTheImageCheck() throws Exception {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> service.upload("tok", user, new byte[0], "a.png"));

      assertTrue(e.getMessage().contains("'a.png'") && e.getMessage().contains("empty"),
                 e.getMessage());
      verify(objects, never()).isImage(any());
      assertRefusedWithoutSideEffects();
   }

   @Test
   void aTooLargeFileIsRefusedWithItsSizeAndTheLimit() throws Exception {
      byte[] big = new byte[ViewsheetImageUploadService.MAX_IMAGE_BYTES + 1];

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> service.upload("tok", user, big, "huge.png"));

      assertTrue(e.getMessage().contains("'huge.png'"), e.getMessage());
      assertTrue(e.getMessage().contains(String.valueOf(big.length)), e.getMessage());
      assertTrue(e.getMessage().contains(
         String.valueOf(ViewsheetImageUploadService.MAX_IMAGE_BYTES)), e.getMessage());
      assertRefusedWithoutSideEffects();
   }

   @Test
   void aFileAtTheLimitIsAccepted() throws Exception {
      byte[] atLimit = new byte[ViewsheetImageUploadService.MAX_IMAGE_BYTES];

      service.upload("tok", user, atLimit, "max.png");

      assertEquals(1, vs.getUploadedImageNames().length);
   }

   @Test
   void namesThatWouldBreakTheSavedDashboardOrThePreviewAreRefused() throws Exception {
      String[] bad = {
         null, "", "   ", "^SKIN^x.png", "^x.png", "a]]>b.png", "a/b.png", "a\\b.png",
         "a?b.png", "a#b.png", "a%b.png", "a\nb.png", "a\u0000b.png", "x".repeat(129) + ".png"
      };

      for(String name : bad) {
         assertThrows(IllegalArgumentException.class,
                      () -> service.upload("tok", user, PNG, name), String.valueOf(name));
      }

      assertRefusedWithoutSideEffects();
   }

   @Test
   void aNameAtTheLengthLimitIsAccepted() throws Exception {
      String name = "x".repeat(ViewsheetImageUploadService.MAX_NAME_LENGTH - 4) + ".png";

      service.upload("tok", user, PNG, name);

      assertNotNull(vs.getUploadedImageBytes(name));
   }

   @Test
   void aRefusedUploadCostsNoUndoStep() throws Exception {
      assertThrows(IllegalArgumentException.class,
                   () -> service.upload("tok", user, PNG, "a/b.png"));
      assertThrows(IllegalArgumentException.class,
                   () -> service.upload("tok", user, new byte[0], "a.png"));

      verify(sessions, never()).mutate(anyString(), any(Principal.class),
                                       any(ViewsheetSessionService.Mutation.class));
   }

   private void assertRefusedWithoutSideEffects() throws Exception {
      assertEquals(0, vs.getUploadedImageNames().length);
      verify(sessions, never()).mutate(anyString(), any(Principal.class),
                                       any(ViewsheetSessionService.Mutation.class));
   }
}
