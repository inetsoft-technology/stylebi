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
package inetsoft.web.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.AuthenticationChain;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class AuthenticationProviderServiceTest {

   @Mock private SecurityEngine securityEngine;
   @Mock private SimpMessagingTemplate messageTemplate;
   @Mock private Cluster cluster;
   @Mock private AuthenticationChain authenticationChain;

   private AuthenticationProviderService service;

   @BeforeEach
   void setUp() {
      service = new AuthenticationProviderService(securityEngine, new ObjectMapper(),
                                                   messageTemplate, cluster);
   }

   // getAuthenticationProvider(name) with a name that matches no configured provider must throw
   // a MessageException, not an NPE (bug #76335).
   @Test
   void getAuthenticationProvider_unknownName_throwsMessageExceptionNotNPE() {
      when(securityEngine.getAuthenticationChain()).thenReturn(Optional.of(authenticationChain));
      when(authenticationChain.stream()).thenReturn(Stream.empty());

      assertThrows(MessageException.class,
                   () -> service.getAuthenticationProvider("does-not-exist"));
   }
}
