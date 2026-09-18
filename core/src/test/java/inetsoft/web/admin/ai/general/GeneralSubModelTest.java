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

import inetsoft.util.audit.AdminChangeRecord;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Pins the sub-model catalog: its membership, its secret set, and its field-name derivation. */
@Tag("core")
class GeneralSubModelTest {
   @Test
   void requireMatchesEachKeyExactly() {
      for(GeneralSubModel subModel : GeneralSubModel.values()) {
         assertSame(subModel, GeneralSubModel.require(subModel.key()));
      }
   }

   @Test
   void requireIsCaseSensitive() {
      assertThrows(IllegalArgumentException.class, () -> GeneralSubModel.require("Email"));
      assertThrows(IllegalArgumentException.class, () -> GeneralSubModel.require("EMAIL"));
   }

   @Test
   void requireNamesEveryValidKeyWhenUnknown() {
      IllegalArgumentException e =
         assertThrows(IllegalArgumentException.class, () -> GeneralSubModel.require("nope"));

      for(GeneralSubModel subModel : GeneralSubModel.values()) {
         assertTrue(e.getMessage().contains(subModel.key()),
                    "unknown-key message should name " + subModel.key() + ": " + e.getMessage());
      }
   }

   /**
    * licenseKey is a real sub-model of the Enterprise Manager page, just not of this area. A caller
    * that asks for it has made a reasonable mistake and needs to be sent somewhere specific, not
    * told the name does not exist.
    */
   @Test
   void requireRedirectsLicenseKeyToTheLicensingArea() {
      IllegalArgumentException e =
         assertThrows(IllegalArgumentException.class, () -> GeneralSubModel.require("licenseKey"));

      assertTrue(e.getMessage().contains("list_license_keys"), e.getMessage());
      assertTrue(e.getMessage().contains("apply_license_changes"), e.getMessage());
   }

   /**
    * The four names here must stay exactly the four {@code mail.*} values
    * {@code AdminPropertyCatalog.ENCRYPTED_CREDENTIALS} covers. If the two lists drift, the
    * catalog's "admin-chat will not set it" guidance becomes false for whichever name this area
    * still accepts.
    */
   @Test
   void onlyEmailHasSecretFields() {
      assertEquals(Set.of("smtpPassword", "smtpClientSecret", "smtpAccessToken", "smtpRefreshToken"),
                   GeneralSubModel.EMAIL.secretFields());

      for(GeneralSubModel subModel : GeneralSubModel.values()) {
         if(subModel != GeneralSubModel.EMAIL) {
            assertEquals(Set.of(), subModel.secretFields(),
                         subModel.key() + " should declare no secret fields");
         }
      }
   }

   /** smtpTokenUri is read with getPassword but written with setProperty -- classified by its
    * writer, so it is an ordinary field. Guarding against a well-meaning "fix". */
   @Test
   void smtpTokenUriIsNotSecret() {
      assertFalse(GeneralSubModel.EMAIL.secretFields().contains("smtpTokenUri"));
      assertTrue(GeneralSubModel.EMAIL.fieldNames().contains("smtpTokenUri"));
   }

   /** Every secret must actually be a field of the model, or masking silently does nothing. */
   @Test
   void everySecretFieldIsAFieldOfItsModel() {
      for(GeneralSubModel subModel : GeneralSubModel.values()) {
         for(String secret : subModel.secretFields()) {
            assertTrue(subModel.fieldNames().contains(secret),
                       subModel.key() + " declares secret \"" + secret + "\" that is not one of " +
                       "its fields " + subModel.fieldNames());
         }
      }
   }

   /**
    * The drift test. Field names are derived reflectively, so this asserts the derivation actually
    * sees the interface's accessors -- if an Immutables model gains or loses a field, this is what
    * notices.
    */
   @Test
   void fieldNamesMatchTheModelInterfaces() {
      assertEquals(Set.of("locales"), GeneralSubModel.LOCALIZATION.fieldNames());
      assertEquals(Set.of("onDemand", "onDemandDefault", "defaultCycle", "metadata", "required",
                          "cycles"),
                   GeneralSubModel.MV.fieldNames());
      assertEquals(Set.of("directory", "cleanUpStartup"), GeneralSubModel.CACHE.fieldNames());
      assertEquals(Set.of("queryTimeout", "queryPreviewTimeout", "maxQueryRowCount",
                          "maxQueryPreviewRowCount", "maxTableRowCount", "dataSetCaching",
                          "dataCacheSize", "dataCacheTimeout"),
                   GeneralSubModel.PERFORMANCE.fieldNames());
      assertEquals(Set.of("keyValueType", "blobType", "assetBackupTaskName"),
                   GeneralSubModel.DATA_SPACE.fieldNames());
   }

   @Test
   void dataSpaceIsTheOnlyReadOnlySubModel() {
      for(GeneralSubModel subModel : GeneralSubModel.values()) {
         assertEquals(subModel != GeneralSubModel.DATA_SPACE, subModel.writable(),
                      subModel.key() + " writability");
      }
   }

   /**
    * The deviation from PresentationSubModel, pinned: storage scope must NOT imply
    * non-compensable here. If someone re-couples them, the apply service starts reporting
    * localization and mv rollbacks as unconditional failures when they in fact succeed.
    */
   @Test
   void storageScopeSubModelsAreStillCompensable() {
      assertTrue(GeneralSubModel.LOCALIZATION.isStorageScope());
      assertTrue(GeneralSubModel.MV.isStorageScope());
      assertTrue(GeneralSubModel.LOCALIZATION.compensable());
      assertTrue(GeneralSubModel.MV.compensable());
   }

   @Test
   void everyWritableSubModelIsCompensable() {
      for(GeneralSubModel subModel : GeneralSubModel.values()) {
         if(subModel.writable()) {
            assertTrue(subModel.compensable(), subModel.key() + " should be compensable");
         }
      }
   }

   @Test
   void scopeAndRiskUseTheSharedAuditConstants() {
      for(GeneralSubModel subModel : GeneralSubModel.values()) {
         assertTrue(AdminChangeRecord.SCOPE_VALUE.equals(subModel.scope()) ||
                    AdminChangeRecord.SCOPE_STORAGE.equals(subModel.scope()),
                    subModel.key() + " scope");
         assertTrue(AdminChangeRecord.RISK_LOW.equals(subModel.risk()) ||
                    AdminChangeRecord.RISK_HIGH.equals(subModel.risk()),
                    subModel.key() + " risk");
      }
   }

   @Test
   void allKeysListsEveryValue() {
      String keys = GeneralSubModel.allKeys();

      for(GeneralSubModel subModel : GeneralSubModel.values()) {
         assertTrue(keys.contains(subModel.key()), keys);
      }
   }
}
