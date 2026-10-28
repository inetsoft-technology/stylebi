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
package inetsoft.uql.rest.datasource.activecampaign;

import inetsoft.test.*;
import inetsoft.uql.tabular.HttpParameter;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, ActiveCampaignDataSourceTest.CredentialConfig.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
class ActiveCampaignDataSourceTest {
   @Test
   void setApiTokenMarksTheTokenParameterSecretEvenWhenReplaced() {
      ActiveCampaignDataSource ds = new ActiveCampaignDataSource();

      ds.setApiToken("first-token-1234");
      ds.setApiToken("second-token-5678");

      HttpParameter[] params = ds.getQueryHttpParameters();
      assertEquals(1, params.length);
      HttpParameter param = params[0];
      assertEquals("Api-Token", param.getName());
      assertEquals("second-token-5678", param.getValue());
      assertTrue(param.isSecret());
   }

   @Configuration
   static class CredentialConfig {
      @Bean
      public CredentialService credentialService() {
         return mock(CredentialService.class);
      }
   }
}
