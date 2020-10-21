package test.alipsa.renjin.client.datautils;

import org.junit.jupiter.api.Test;
import org.renjin.eval.Session;
import org.renjin.eval.SessionBuilder;
import org.renjin.script.RenjinScriptEngine;
import org.renjin.script.RenjinScriptEngineFactory;
import org.renjin.sexp.ListVector;
import se.alipsa.renjin.client.datautils.Table;

import javax.script.ScriptException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DataTransformationTest {

  @Test
  public void testToDataFrame() throws ScriptException {

    String dfCode = "lineItems <- data.frame(\n" +
        "  name = character(),\n" +
        "  jan = numeric(),\n" +
        "  feb = numeric(),\n" +
        "  mar = numeric(),\n" +
        "  apr = numeric(),\n" +
        "  may = numeric(),\n" +
        "  jun = numeric(),\n" +
        "  jul = numeric(),\n" +
        "  aug = numeric(),\n" +
        "  sep = numeric(),\n" +
        "  oct = numeric(),\n" +
        "  nov = numeric(),\n" +
        "  dec = numeric(),\n" +
        "  stringsAsFactors = FALSE\n" +
        ")\n" +
        "lineItems[1, ] <- list('Software', -1200, 0, 0 , 0, -400, -600, 0, -100, -50, -200, -80, 0 )\n" +
        "lineItems[2, ] <- list('Computer hardware', 0, 0, -33000 , 0 , 0, 0, 0, 0, -50000, 0, 0, 0 )\n" +
        "lineItems[3, ] <- list('Admin staff', -7000, -7000, -7000 , -7000, -7000, -7000, -7000, -7000, -7000, -7000, -7000, -7000 )\n" +
        "lineItems[4, ] <- list('Dev staff', -12000, -12000, -12000 , -12000, -12000, -12000, -12000, -18000, -18000, -18000, -18000, -18000 )\n" +
        "lineItems[5, ] <- list('Sales', 22000, 22000, 42000 , 25000, 26000, 27000, 28000, 29000, 60000, 31000, 32000, 33000 )\n" +
        "lineItems";
    RenjinScriptEngineFactory factory = new RenjinScriptEngineFactory();
    Session session = SessionBuilder.buildDefault();
    RenjinScriptEngine engine = factory.getScriptEngine(session);
    ListVector lineItemsDf = (ListVector)engine.eval(dfCode);

    Table table = new Table(lineItemsDf);

    assertEquals(13, table.getHeaderList().size(), "Number of columns in header");
    assertEquals(table.getHeaderList().get(0), "name", "first header");
    assertEquals(table.getHeaderList().get(1), "jan", "second header");
    assertEquals(table.getHeaderList().get(12), "dec", "last header");

    assertEquals(5, table.getRowList().size(), "Should be 5 observations");
    assertEquals("Software", table.getValueAsString(0,0), "row 0 col 0");
    assertEquals(-1200, table.getValueAsInteger(0,1), "row 0 col 1");

    assertEquals("Sales", table.getValueAsString(4,0), "row 4 col 0");
    assertEquals(33_000, table.getValueAsInteger(4,12), "row 4 col 12");

    ListVector df = table.asDataFrame();
    engine.put("extDf", df);

    // TODO compare lineItems with extDf
    engine.eval("print(summary(extDf))");

  }
}
