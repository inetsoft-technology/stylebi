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
package inetsoft.web.composer.vs.objects.controller;

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.viewsheet.vslayout.DeviceRegistry;
import inetsoft.web.composer.model.vs.ScreenSizeDialogModel;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/*
 * DeviceController has no test class predating this one -- see permission-matrix-actions.md's
 * "Edit Mobile Devices" writeup (Bug #75603) for the full analysis. The three endpoints write
 * device profiles with no @Secured/@RequiredPermission annotation and no checkPermission() call
 * anywhere in the class; ViewsheetPropertyDialogService computes the DEVICE ACCESS check but only
 * feeds it into a UI-display boolean (editDevicesAllowed), never into an actual authorization gate.
 *
 * This is asserted via reflection on the annotation rather than by invoking the controller and
 * checking a rejection, because there is currently nothing in the method to reject a call with --
 * the fix is expected to add @Secured, matching the RequiredPermission shape already computed (for
 * display only) in ViewsheetPropertyDialogService.
 */
@ExtendWith(MockitoExtension.class)
@Tag("core")
class DeviceControllerTest {

   @Mock
   private DeviceRegistry deviceRegistry;
   @Mock
   private DependencyHandler dependencyHandler;
   @Mock
   private Principal principal;

   private DeviceController controller;

   @BeforeEach
   void setUp() {
      controller = new DeviceController(deviceRegistry, dependencyHandler);
   }

   @Test
   void newEditDeleteDevice_requireDeviceAccessPermission() throws NoSuchMethodException {
      for(String methodName : new String[] {"newDevice", "editDevice", "deleteDevice"}) {
         Method method = DeviceController.class.getMethod(
            methodName, ScreenSizeDialogModel.class, Principal.class);
         Secured secured = method.getAnnotation(Secured.class);
         assertNotNull(secured, methodName + "() must be gated by @Secured");

         boolean requiresDeviceAccess = Arrays.stream(secured.value())
            .anyMatch(rp -> rp.resourceType() == ResourceType.DEVICE &&
               "*".equals(rp.resource()) &&
               Arrays.asList(rp.actions()).contains(ResourceAction.ACCESS));
         assertTrue(requiresDeviceAccess,
            methodName + "() must require DEVICE ACCESS on resource \"*\", matching the " +
            "\"Edit Mobile Devices\" Security Action check already computed (for UI display " +
            "only) in ViewsheetPropertyDialogService: checkPermission(principal, " +
            "ResourceType.DEVICE, \"*\", ResourceAction.ACCESS)");
      }
   }

   /*
    * Device profiles are stored in a single global DeviceRegistry, not per-organization.
    * DEVICE:*:ACCESS alone is therefore not sufficient in multi-org enterprise deployments --
    * an org admin granted that permission within their own org could otherwise modify device
    * profiles used by every other org. DeviceRegistry.isOrgAllowedToEditDevices() mirrors the
    * additional (!enterprise || isSiteAdmin || currentOrg == defaultOrg) gate already applied to
    * the UI control in ViewsheetPropertyDialogService.
    */
   @Test
   void newEditDeleteDevice_orgNotAllowed_rejectsAndDoesNotWrite() {
      try(MockedStatic<DeviceRegistry> registryStatic = mockStatic(DeviceRegistry.class)) {
         registryStatic.when(() -> DeviceRegistry.isOrgAllowedToEditDevices(principal))
            .thenReturn(false);

         ScreenSizeDialogModel device = new ScreenSizeDialogModel();
         device.setId("d1");
         device.setLabel("Device 1");

         assertThrows(SecurityException.class, () -> controller.newDevice(device, principal));
         assertThrows(SecurityException.class, () -> controller.editDevice(device, principal));
         assertThrows(SecurityException.class, () -> controller.deleteDevice(device, principal));

         verify(deviceRegistry, never()).setDevice(any());
         verify(deviceRegistry, never()).deleteDevice(any());
      }
   }
}
