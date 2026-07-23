package inetsoft.web.wiz.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class WizDashboardEventTest {
   private final ObjectMapper mapper = new ObjectMapper();

   @Test
   void deserializesTilesAndLayoutColumns() throws Exception {
      String json = "{\"name\":\"B\",\"identifiers\":[\"v1\",\"v2\"]," +
         "\"layoutColumns\":2,\"tiles\":[{\"identifier\":\"v1\",\"spanCols\":1}," +
         "{\"identifier\":\"v2\",\"spanCols\":2}]}";
      WizDashboardEvent ev = mapper.readValue(json, WizDashboardEvent.class);
      assertEquals(2, ev.getLayoutColumns());
      assertEquals(2, ev.getTiles().size());
      assertEquals("v2", ev.getTiles().get(1).getIdentifier());
      assertEquals(2, ev.getTiles().get(1).getSpanCols());
   }

   @Test
   void tolueratesAbsentTiles() throws Exception {
      WizDashboardEvent ev = mapper.readValue("{\"name\":\"B\",\"identifiers\":[\"v1\"]}", WizDashboardEvent.class);
      assertNull(ev.getTiles());
      assertNull(ev.getLayoutColumns());
   }

   @Test
   void spanColsDefaultsToOneWhenOmitted() throws Exception {
      WizDashboardEvent ev = mapper.readValue(
         "{\"tiles\":[{\"identifier\":\"v1\"}]}", WizDashboardEvent.class);
      assertEquals(1, ev.getTiles().get(0).getSpanCols());
   }
}
