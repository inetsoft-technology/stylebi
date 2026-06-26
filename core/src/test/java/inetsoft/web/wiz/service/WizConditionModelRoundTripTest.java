package inetsoft.web.wiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.web.wiz.model.VisualizationConditionModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the immutable, @JsonCreator-based condition models deserialize the inbound
 * Wiz contract (incl. polymorphic ConditionNode + nested group/subquery) and round-trip.
 */
@Tag("core")
public class WizConditionModelRoundTripTest {
   private static final String JSON =
      "{"
      + "\"baseConditions\":["
      + "  {\"type\":\"condition\",\"condition\":{"
      + "      \"field\":\"state\",\"operation\":\"ONE_OF\",\"negated\":true,"
      + "      \"values\":[{\"type\":\"VALUE\",\"value\":\"NJ\"}]}},"
      + "  {\"type\":\"group\",\"junction\":\"and\",\"items\":["
      + "      {\"type\":\"condition\",\"condition\":{"
      + "          \"field\":\"total\",\"aggregateFormula\":\"Sum\",\"norP\":3,"
      + "          \"operation\":\"GREATER_THAN\",\"equal\":true,"
      + "          \"values\":[{\"type\":\"SUBQUERY\",\"subQuery\":{"
      + "              \"subQueryName\":\"T2\",\"inSubQueryColumn\":\"c\","
      + "              \"where\":{\"subQueryColumn\":\"a\",\"currentTableColumn\":\"b\"}}}]}}"
      + "  ]}"
      + "]}";

   @Test
   public void deserializeAndRoundTrip() throws Exception {
      ObjectMapper m = new ObjectMapper();
      VisualizationConditionModel model = m.readValue(JSON, VisualizationConditionModel.class);

      List<VisualizationConditionModel.ConditionNode> base = model.getBaseConditions();
      assertEquals(2, base.size());

      // Leaf
      VisualizationConditionModel.ConditionLeaf leaf =
         (VisualizationConditionModel.ConditionLeaf) base.get(0);
      assertEquals("state", leaf.getCondition().getField());
      assertEquals("ONE_OF", leaf.getCondition().getOperation());
      assertTrue(leaf.getCondition().isNegated());
      assertEquals("NJ", leaf.getCondition().getValues().get(0).getValue());

      // Group → nested leaf with aggregate, norP, subquery + where
      VisualizationConditionModel.ConditionGroup group =
         (VisualizationConditionModel.ConditionGroup) base.get(1);
      assertEquals("and", group.getJunction());
      VisualizationConditionModel.ConditionLeaf inner =
         (VisualizationConditionModel.ConditionLeaf) group.getItems().get(0);
      VisualizationConditionModel.ConditionSpec spec = inner.getCondition();
      assertEquals("Sum", spec.getAggregateFormula());
      assertEquals(Integer.valueOf(3), spec.getNOrP());          // "norP" wire name
      assertEquals(Boolean.TRUE, spec.getEqual());
      VisualizationConditionModel.SubQuery sub = spec.getValues().get(0).getSubQuery();
      assertEquals("T2", sub.getSubQueryName());
      assertEquals("a", sub.getWhere().getSubQueryColumn());
      assertEquals("b", sub.getWhere().getCurrentTableColumn());

      // Serialize back and re-read: must be stable.
      String out = m.writeValueAsString(model);
      assertTrue(out.contains("\"norP\":3"), "wire name must remain 'norP', got: " + out);
      VisualizationConditionModel again = m.readValue(out, VisualizationConditionModel.class);
      assertEquals(2, again.getBaseConditions().size());
   }
}
