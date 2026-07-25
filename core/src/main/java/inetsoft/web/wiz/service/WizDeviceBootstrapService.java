package inetsoft.web.wiz.service;

import inetsoft.uql.viewsheet.vslayout.DeviceInfo;
import inetsoft.uql.viewsheet.vslayout.DeviceRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registers the three shared, width-bound devices the wiz dashboard-generation feature's
 * adaptive layouts match against ({@link WizDashboardService}) -- idempotent, self-healing on
 * every boot, so a rebuilt/reset environment never needs a manual setup step. Device profiles
 * are global/org-wide infrastructure ({@link DeviceRegistry}), not per-dashboard, so registration
 * happens once here rather than at dashboard-generation time.
 */
@Component
public class WizDeviceBootstrapService {
   /** Width < 768px AND the client-reported mobile flag -- see {@link WizDashboardService}'s
    *  Mobile tier. */
   public static final String MOBILE_DEVICE_ID = "wiz-mobile";

   /** Width 2020-2699px, any mobile flag -- see {@link WizDashboardService}'s Wide tier. */
   public static final String WIDE_DEVICE_ID = "wiz-wide";

   /** Width >= 2700px, any mobile flag -- see {@link WizDashboardService}'s Ultrawide tier. */
   public static final String ULTRAWIDE_DEVICE_ID = "wiz-ultrawide";

   public WizDeviceBootstrapService() {
      this(DeviceRegistry.getRegistry());
   }

   /** Package-visible constructor for unit testing with a mocked registry. */
   WizDeviceBootstrapService(DeviceRegistry registry) {
      this.registry = registry;
   }

   @PostConstruct
   public void ensureDevicesRegistered() {
      ensureDevice(MOBILE_DEVICE_ID, "Wiz Mobile", 0, 767);
      ensureDevice(WIDE_DEVICE_ID, "Wiz Wide", 2020, 2699);
      ensureDevice(ULTRAWIDE_DEVICE_ID, "Wiz Ultrawide", 2700, Integer.MAX_VALUE);
   }

   private void ensureDevice(String id, String name, int minWidth, int maxWidth) {
      try {
         if(registry.getDevice(id) != null) {
            return;
         }

         DeviceInfo device = new DeviceInfo();
         device.setId(id);
         device.setName(name);
         device.setMinWidth(minWidth);
         device.setMaxWidth(maxWidth);
         registry.setDevice(device);
      }
      catch(Exception ex) {
         // Fail soft: a permission gap (non-default-org, non-enterprise-admin principal at
         // boot) or a storage error must not block server startup. The affected tier(s) simply
         // never match at view time -- every dashboard falls back to the base 2-column layout,
         // identical to behavior before this feature existed.
         LOG.warn("Failed to register wiz device '{}' -- its adaptive-layout tier will never " +
            "match until this is resolved", id, ex);
      }
   }

   private final DeviceRegistry registry;
   private static final Logger LOG = LoggerFactory.getLogger(WizDeviceBootstrapService.class);
}
