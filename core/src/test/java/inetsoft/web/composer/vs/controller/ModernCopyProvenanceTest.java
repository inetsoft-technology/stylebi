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
package inetsoft.web.composer.vs.controller;

import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("core")
class ModernCopyProvenanceTest {
   private static final String ID = "1^128^__NULL__^A/B";

   @Test
   void displayPathResolvesAnExistingOriginal() throws Exception {
      AssetRepository repository = mock(AssetRepository.class);
      when(repository.containsEntry(any(AssetEntry.class))).thenReturn(true);

      assertEquals("A/B", ModernCopyProvenance.displayPath(ID, repository, mock(Principal.class)));
   }

   @Test
   void displayPathIsNullWhenTheOriginalIsGone() throws Exception {
      AssetRepository repository = mock(AssetRepository.class);
      when(repository.containsEntry(any(AssetEntry.class))).thenReturn(false);

      assertNull(ModernCopyProvenance.displayPath(ID, repository, mock(Principal.class)));
   }

   @Test
   void displayPathIsNullForAMalformedIdentifier() throws Exception {
      AssetRepository repository = mock(AssetRepository.class);
      when(repository.containsEntry(any(AssetEntry.class))).thenReturn(true);

      assertNull(ModernCopyProvenance.displayPath("garbage", repository, null));
      assertNull(ModernCopyProvenance.displayPath(null, repository, null));
      assertNull(ModernCopyProvenance.displayPath(ID, null, mock(Principal.class)));
      assertNull(ModernCopyProvenance.displayPath(ID, repository, null));
   }

   @Test
   void displayPathIsNullWhenReadIsDenied() throws Exception {
      AssetRepository repository = mock(AssetRepository.class);
      Principal principal = mock(Principal.class);
      when(repository.containsEntry(any(AssetEntry.class))).thenReturn(true);
      doThrow(new SecurityException("denied")).when(repository)
         .checkAssetPermission(eq(principal), any(AssetEntry.class), eq(ResourceAction.READ));

      assertNull(ModernCopyProvenance.displayPath(ID, repository, principal));
   }

   @Test
   void displayPathChecksReadWhenGranted() throws Exception {
      AssetRepository repository = mock(AssetRepository.class);
      Principal principal = mock(Principal.class);
      when(repository.containsEntry(any(AssetEntry.class))).thenReturn(true);

      assertEquals("A/B", ModernCopyProvenance.displayPath(ID, repository, principal));
      verify(repository).checkAssetPermission(eq(principal), any(AssetEntry.class),
                                              eq(ResourceAction.READ));
   }

   @Test
   void displayPathIsNullWhenTheRepositoryThrows() throws Exception {
      AssetRepository repository = mock(AssetRepository.class);
      when(repository.containsEntry(any(AssetEntry.class))).thenThrow(new RuntimeException("x"));

      assertNull(ModernCopyProvenance.displayPath(ID, repository, mock(Principal.class)));
   }
}
