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
package inetsoft.web.admin.ai.general;

import inetsoft.web.admin.general.*;
import inetsoft.web.admin.general.model.*;
import inetsoft.web.admin.general.model.model.SMTPAuthType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pins that each sub-model is wired to the right service method, and that dataSpace has no writer. */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class GeneralSettingsAccessTest {
   @Mock private LocalizationSettingsService localization;
   @Mock private MVSettingsService mv;
   @Mock private CacheSettingsService cache;
   @Mock private EmailSettingsService email;
   @Mock private PerformanceSettingsService performance;
   @Mock private DataSpaceSettingsService dataSpace;

   private static final Principal PRINCIPAL = () -> "admin";

   private GeneralSettingsAccess access() {
      return new GeneralSettingsAccess(localization, mv, cache, email, performance, dataSpace);
   }

   @Test
   void eachReaderCallsItsOwnService() throws Exception {
      LocalizationSettingsModel localizationModel = LocalizationSettingsModel.builder().build();
      MVSettingsModel mvModel = MVSettingsModel.builder()
         .onDemand(false).onDemandDefault(false).metadata(false).required(false).build();
      CacheSettingsModel cacheModel =
         CacheSettingsModel.builder().directory("/c").cleanUpStartup(false).build();
      EmailSettingsModel emailModel = EmailSettingsModel.builder()
         .fromAddress("a@b.c").smtpAuthentication(SMTPAuthType.NONE).ssl(false).tls(false).build();
      PerformanceSettingsModel performanceModel = PerformanceSettingsModel.builder()
         .queryTimeout(1).queryPreviewTimeout(1).maxQueryRowCount(1).maxQueryPreviewRowCount(1)
         .maxTableRowCount(1).dataSetCaching(false).dataCacheSize(1).dataCacheTimeout(1).build();
      DataSpaceSettingsModel dataSpaceModel = DataSpaceSettingsModel.builder()
         .keyValueType("mapdb").blobType("local").assetBackupTaskName("").build();

      when(localization.getModel()).thenReturn(localizationModel);
      when(mv.getModel(PRINCIPAL)).thenReturn(mvModel);
      when(cache.getModel()).thenReturn(cacheModel);
      when(email.getModel()).thenReturn(emailModel);
      when(performance.getModel()).thenReturn(performanceModel);
      when(dataSpace.getModel(PRINCIPAL)).thenReturn(dataSpaceModel);

      GeneralSettingsAccess access = access();
      assertSame(localizationModel, access.read(GeneralSubModel.LOCALIZATION, PRINCIPAL));
      assertSame(mvModel, access.read(GeneralSubModel.MV, PRINCIPAL));
      assertSame(cacheModel, access.read(GeneralSubModel.CACHE, PRINCIPAL));
      assertSame(emailModel, access.read(GeneralSubModel.EMAIL, PRINCIPAL));
      assertSame(performanceModel, access.read(GeneralSubModel.PERFORMANCE, PRINCIPAL));
      assertSame(dataSpaceModel, access.read(GeneralSubModel.DATA_SPACE, PRINCIPAL));
   }

   @Test
   void eachWriterCallsItsOwnServiceSetModel() throws Exception {
      CacheSettingsModel cacheModel =
         CacheSettingsModel.builder().directory("/c").cleanUpStartup(true).build();
      access().write(GeneralSubModel.CACHE, cacheModel, PRINCIPAL);
      verify(cache).setModel(cacheModel, PRINCIPAL);

      EmailSettingsModel emailModel = EmailSettingsModel.builder()
         .fromAddress("a@b.c").smtpAuthentication(SMTPAuthType.NONE).ssl(false).tls(false).build();
      access().write(GeneralSubModel.EMAIL, emailModel, PRINCIPAL);
      verify(email).setModel(emailModel, PRINCIPAL);
   }

   /**
    * The backstop behind {@code GeneralSubModel.writable()}. The plan service refuses dataSpace long
    * before a write could be attempted, but a future caller reaching write() directly must still
    * fail loudly rather than silently do nothing.
    */
   @Test
   void dataSpaceWriteThrows() {
      DataSpaceSettingsModel model = DataSpaceSettingsModel.builder()
         .keyValueType("mapdb").blobType("local").assetBackupTaskName("").build();

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> access().write(GeneralSubModel.DATA_SPACE, model, PRINCIPAL));

      assertTrue(e.getMessage().contains("read-only"), e.getMessage());
      assertTrue(e.getMessage().contains("inetsoft.yaml"), e.getMessage());
   }

   @Test
   void everySubModelHasAReader() throws Exception {
      // Each read must reach an adapter rather than NPE on a missing map entry. A null service
      // return is fine here; a missing adapter is not.
      for(GeneralSubModel subModel : GeneralSubModel.values()) {
         assertDoesNotThrow(() -> access().read(subModel, PRINCIPAL),
                            subModel.key() + " has no reader");
      }
   }
}
