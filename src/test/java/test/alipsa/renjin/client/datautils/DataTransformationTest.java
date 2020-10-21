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
        "  q1 = numeric(),\n" +
        "  q2 = numeric(),\n" +
        "  q3 = numeric(),\n" +
        "  q4 = numeric(),\n" +
        "  dec = numeric(),\n" +
        "  isValid = logical(),\n" +
        "  stringsAsFactors = FALSE\n" +
        ")\n" +
        "lineItems[1, ] <- list('Software', -1200, 0, 0 , 0, -4.02, TRUE)\n" +
        "lineItems[2, ] <- list('Computer hardware', 0, 0, -33000 , 0 , 3.14, FALSE)\n" +
        "lineItems[3, ] <- list('Admin staff', -7000, -7000, -7000 , -7000, 12.123, TRUE)\n" +
        "lineItems[4, ] <- list('Dev staff', -12000, -12000, -12000 , -12000, 0.01, TRUE )\n" +
        "lineItems[5, ] <- list('Sales', 22000, 22000, 42000 , 25000, 56.1, FALSE )\n" +
        "lineItems";
    RenjinScriptEngineFactory factory = new RenjinScriptEngineFactory();
    Session session = new SessionBuilder().withDefaultPackages().build();
    RenjinScriptEngine engine = factory.getScriptEngine(session);
    ListVector lineItemsDf = (ListVector)engine.eval(dfCode);

    Table table = new Table(lineItemsDf);

    assertEquals(7, table.getHeaderList().size(), "Number of columns in header");
    assertEquals(table.getHeaderList().get(0), "name", "first header");
    assertEquals(table.getHeaderList().get(1), "q1", "second header");
    assertEquals(table.getHeaderList().get(6), "isValid", "last header");

    assertEquals(5, table.getRowList().size(), "Should be 5 observations");
    assertEquals("Software", table.getValueAsString(0,0), "row 0 col 0");
    assertEquals(-1200, table.getValueAsInteger(0,1), "row 0 col 1");

    assertEquals("Sales", table.getValueAsString(4,0), "row 4 col 0");
    assertEquals(false, table.getValueAsBoolean(4,6), "row 4 col 6");

    System.out.println(table.getColumnTypes());
    ListVector df = table.asDataFrame();
    engine.put("extDf", df);

    // TODO compare lineItems with extDf
    engine.eval("str(extDf); print(summary(extDf))");

  }
}
