package inetsoft.web.wiz.service;

import inetsoft.uql.viewsheet.vslayout.DeviceInfo;
import inetsoft.uql.viewsheet.vslayout.DeviceRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@Tag("core")
class WizDeviceBootstrapServiceTest {
   @Test
   void registersAllThreeDevicesWhenNoneExist() {
      DeviceRegistry registry = mock(DeviceRegistry.class);
      when(registry.getDevice(anyString())).thenReturn(null);

      new WizDeviceBootstrapService(registry).ensureDevicesRegistered();

      verify(registry).setDevice(argThat(d ->
         d.getId().equals(WizDeviceBootstrapService.MOBILE_DEVICE_ID) &&
         d.getMinWidth() == 0 && d.getMaxWidth() == 767));
      verify(registry).setDevice(argThat(d ->
         d.getId().equals(WizDeviceBootstrapService.WIDE_DEVICE_ID) &&
         d.getMinWidth() == 2020 && d.getMaxWidth() == 2699));
      verify(registry).setDevice(argThat(d ->
         d.getId().equals(WizDeviceBootstrapService.ULTRAWIDE_DEVICE_ID) &&
         d.getMinWidth() == 2700 && d.getMaxWidth() == Integer.MAX_VALUE));
   }

   @Test
   void skipsDevicesThatAlreadyExist() {
      DeviceRegistry registry = mock(DeviceRegistry.class);
      DeviceInfo existing = new DeviceInfo();
      existing.setId(WizDeviceBootstrapService.MOBILE_DEVICE_ID);
      when(registry.getDevice(WizDeviceBootstrapService.MOBILE_DEVICE_ID)).thenReturn(existing);
      when(registry.getDevice(WizDeviceBootstrapService.WIDE_DEVICE_ID)).thenReturn(null);
      when(registry.getDevice(WizDeviceBootstrapService.ULTRAWIDE_DEVICE_ID)).thenReturn(null);

      new WizDeviceBootstrapService(registry).ensureDevicesRegistered();

      verify(registry, never()).setDevice(argThat(d -> d.getId().equals(WizDeviceBootstrapService.MOBILE_DEVICE_ID)));
      verify(registry).setDevice(argThat(d -> d.getId().equals(WizDeviceBootstrapService.WIDE_DEVICE_ID)));
      verify(registry).setDevice(argThat(d -> d.getId().equals(WizDeviceBootstrapService.ULTRAWIDE_DEVICE_ID)));
   }

   @Test
   void doesNotThrowWhenRegistryWriteFails() {
      DeviceRegistry registry = mock(DeviceRegistry.class);
      when(registry.getDevice(anyString())).thenReturn(null);
      doThrow(new RuntimeException("boom")).when(registry).setDevice(any());

      assertDoesNotThrow(() -> new WizDeviceBootstrapService(registry).ensureDevicesRegistered());
   }
}
