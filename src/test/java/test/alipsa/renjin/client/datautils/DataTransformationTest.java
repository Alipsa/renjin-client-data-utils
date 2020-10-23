package test.alipsa.renjin.client.datautils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.renjin.eval.Session;
import org.renjin.eval.SessionBuilder;
import org.renjin.script.RenjinScriptEngine;
import org.renjin.script.RenjinScriptEngineFactory;
import org.renjin.sexp.ListVector;
import se.alipsa.renjin.client.datautils.Table;

import javax.script.ScriptException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DataTransformationTest {

  private static ListVector lineItemsDf;
  private static RenjinScriptEngine engine;

  @BeforeAll
  static void init() throws ScriptException {
    System.out.println("init(), creating lineItemsDf");
    String dfCode = "lineItems <- data.frame(\n" +
        "  name = character(),\n" +
        "  q1 = numeric(),\n" +
        "  q2 = integer(),\n" +
        "  q3 = numeric(),\n" +
        "  q4 = numeric(),\n" +
        "  dec = numeric(),\n" +
        "  isValid = logical(),\n" +
        "  stringsAsFactors = FALSE\n" +
        ")\n" +
        "lineItems[1, ] <- list('Software', -1200, 0, 0 , 0, -4.02, TRUE)\n" +
        "lineItems[2, ] <- list('Computer hardware', 0, 0, -33000 , 0 , 3.14, FALSE)\n" +
        "lineItems[3, ] <- list(NA, NA, -7000, -7000 , -7000, 12.123, TRUE)\n" +
        "lineItems[4, ] <- list('Dev staff', -12000, -9000, -12000 , -12000, 0.01, TRUE )\n" +
        "lineItems[5, ] <- list('Sales', 22000, 22000, 42000 , 25000, 56.1, FALSE )\n" +
        "lineItems$q2 <- as.integer(lineItems$q2) \n" +
        "lineItems";
    //System.out.println(dfCode);
    RenjinScriptEngineFactory factory = new RenjinScriptEngineFactory();
    Session session = new SessionBuilder().withDefaultPackages().build();
    engine = factory.getScriptEngine(session);
    lineItemsDf = (ListVector)engine.eval(dfCode);
  }

  @Test
  public void testToDataFrame() throws ScriptException {
    assertNotNull(lineItemsDf, "lineItemsDf is null, something wrong with initialization");
    //engine.eval("str(lineItems); print(summary(lineItems))");
    Table table = new Table(lineItemsDf);
    //System.out.println(table.getColumnTypes());
    //table.getRowList().forEach(System.out::println);

    assertEquals(7, table.getHeaderList().size(), "Number of columns in header");
    assertEquals(table.getHeaderList().get(0), "name", "first header");
    assertEquals(table.getHeaderList().get(1), "q1", "second header");
    assertEquals(table.getHeaderList().get(6), "isValid", "last header");

    assertEquals(5, table.getRowList().size(), "Should be 5 observations");
    assertEquals("Software", table.getValueAsString(0,0), "row 1 col 1");
    assertEquals(-1200, table.getValueAsInteger(0,1), "row 1 col 2");
    assertEquals(true, table.getValueAsBoolean(0,6), "row 1 col 7");

    assertNull(table.getValue(2, 0), "row 3 col 1");
    assertNull(table.getValueAsString(2,0), "row 3 col 1");

    assertNull(table.getValueAsInteger(2, 1), "row 3 col 2");
    assertEquals(Double.NaN, table.getValueAsDouble(2,1), "row 3 col 2");
    assertEquals(Double.NaN, table.getValue(2,1), "row 3 col 2");
    assertEquals(Float.NaN, table.getValueAsFloat(2,1), "row 3 col 2");

    assertEquals(-9000, table.getValue(3,2), "row 4 col 3, " + table.getValue(3,2).getClass());

    assertEquals("Sales", table.getValueAsString(4,0), "row 5 col 1");
    assertEquals(false, table.getValueAsBoolean(4,6), "row 5 col 7");

    ListVector df = table.asDataframe();
    engine.put("extDf", df);

    //engine.eval("print(paste('extDf[4, 2]) =', extDf[4, 2]))");
    //engine.eval("str(extDf); print(summary(extDf))");
    // compare lineItems with extDf
    String compareScript =
        "library('hamcrest') \n"
        + "assertThat(names(extDf), equalTo(names(lineItems))) \n"
        + "assertThat(ncol(extDf), equalTo(ncol(lineItems))) \n"
        + "assertThat(nrow(extDf), equalTo(nrow(lineItems))) \n"
        + "for ( col in 1:ncol(extDf) ) { \n"
        + "  for ( row in 1:nrow(extDf) ) { \n"
        + "    # print(paste0('extDf[', row, ',', col, '] = ', extDf[row, col], ', lineItems[', row, ',', col,'] = ', lineItems[row,col])) \n"
        + "    if (is.na(extDf[row, col])) { \n"
        + "      if(!is.na(lineItems[row,col])) { \n"
        + "        stop(paste0('row ', row, ', col ', col, ' (', names(extDf)[col], '), Expected NA but was ', extDf[row, col])) \n"
        + "      } \n"
        + "    } \n"
        + "    if (is.na(lineItems[row, col])) { \n"
        + "      if(!is.na(extDf[row,col])) { \n"
        + "        stop(paste0('row ', row, ' ,col ', col, ' (', names(extDf)[col], '), Expected ', extDf[row,col], ', but was NA')) \n"
        + "      } \n"
        + "    } \n"
        + "    if (!is.na(extDf[row,col]) && !is.na(lineItems[row,col]) && extDf[row, col] != lineItems[row,col]) { \n"
        + "      stop(paste0('row ', row, ', col ', col, ' (', names(extDf)[col], '), Expected ', lineItems[row,col], ', but was ', extDf[row, col])) \n"
        + "    } \n"
        + "  } \n"
        + "}";

    engine.eval(compareScript);
  }

  @Test
  public void testStringsOnlyTable() {
    assertNotNull(lineItemsDf, "lineItemsDf is null, something wrong with initialization");
    Table table = new Table(lineItemsDf, true);
    int rowIdx = 0;
    for (List<Object> row : table.getRowList()) {
      rowIdx++;
      int colIdx = 0;
      for (Object val : row) {
        colIdx++;
        if (val != null) {
          assertTrue(val instanceof String, "Row " + rowIdx + ", col " + colIdx + " is not a String but a " + val.getClass());
        }
      }
    }
    assertEquals(7, table.getHeaderList().size(), "Number of columns in header");
    assertEquals(table.getHeaderList().get(0), "name", "first header");
    assertEquals(table.getHeaderList().get(2), "q2", "third header");
    assertEquals(table.getHeaderList().get(6), "isValid", "last header");

    assertEquals(5, table.getRowList().size(), "Should be 5 observations");
    assertEquals("Software", table.getValueAsString(0,0), "row 1 col 1");
    assertEquals("-1200", table.getValue(0,1), "row 1 col 2");
    assertEquals(true, table.getValueAsBoolean(0,6), "row 1 col 7");
    assertEquals("TRUE", table.getValue(0,6), "row 1 col 7");

    assertNull(table.getValue(2, 0), "row 3 col 1");
    assertNull(table.getValueAsString(2,0), "row 3 col 1");

    assertNull(table.getValueAsString(2, 1), "row 3 col 2");
    assertNull(table.getValue(2, 1), "row 3 col 2");

    assertEquals("0.01", table.getValue(3,5), "row 4 col 6");
    assertEquals(0.01, table.getValueAsDouble(3,5), "row 4 col 6");

    assertEquals("Sales", table.getValueAsString(4,0), "row 5 col 1");
    assertEquals("22000", table.getValue(4,2), "row 5 col 3");
    assertEquals("FALSE", table.getValue(4,6), "row 5 col 7");
    assertEquals(false, table.getValueAsBoolean(4,6), "row 5 col 7");


  }
}
