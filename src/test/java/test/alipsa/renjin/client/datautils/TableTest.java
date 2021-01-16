package test.alipsa.renjin.client.datautils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.renjin.eval.Session;
import org.renjin.eval.SessionBuilder;
import org.renjin.script.RenjinScriptEngine;
import org.renjin.script.RenjinScriptEngineFactory;
import org.renjin.sexp.ListVector;
import org.renjin.sexp.SEXP;
import se.alipsa.renjin.client.datautils.DataType;
import se.alipsa.renjin.client.datautils.Table;

import javax.script.ScriptException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TableTest {

  private static RenjinScriptEngine engine;
  private final Map<String,Object> cache = new HashMap<>();

  @BeforeAll
  public static void init() {
    RenjinScriptEngineFactory factory = new RenjinScriptEngineFactory();
    Session session = new SessionBuilder().withDefaultPackages().build();
    engine = factory.getScriptEngine(session);
  }

  @Test
  public void testTableCreationFromSexpMatrix() throws ScriptException {

    SEXP sexp = (SEXP) engine.eval("m <- matrix(nrow = 3, ncol = 3)\n" +
        "m[1,] <- c(1.0, 2.0, 3.0)\n" +
        "m[2,] <- c(4,5,6)\n" +
        "m[3,] <- c(7,8,9) \n" +
        "m");
    Table table = Table.createTable(sexp);
    table.getColumnTypes().forEach(type -> assertThat(type, equalTo(DataType.DOUBLE)));
    assertThat(table.getValueAsInteger(0, 0), equalTo(1));
    assertThat(table.getValueAsInteger(1, 0), equalTo(4));
    assertThat(table.getValueAsInteger(2, 2), equalTo(9));

    sexp = (SEXP) engine.eval("matrix(1:9, nrow = 3, ncol = 3)");
    table = Table.createTable(sexp);
    table.getColumnTypes().forEach(type -> assertThat(type, equalTo(DataType.INTEGER)));
    assertThat(table.getValueAsInteger(0, 0), equalTo(1));
    assertThat(table.getValueAsInteger(1, 0), equalTo(2));
    assertThat(table.getValueAsInteger(2, 2), equalTo(9));
  }

  @Test
  public void testTableCreationFromSexpVector() throws ScriptException {
    SEXP sexp = (SEXP) engine.eval("c(TRUE, FALSE, TRUE, NA)");
    Table table = Table.createTable(sexp);
    table.getColumnTypes().forEach(type -> assertThat(type, equalTo(DataType.BOOLEAN)));
    assertThat(table.getValueAsBoolean(0, 0), equalTo(true));
    assertThat(table.getValue(1, 0), equalTo(false));
    assertThat(table.getValueAsBoolean(3, 0), is(nullValue()));
  }

  @Test
  public void testTableCreationFromSexpListVector() throws ScriptException {
    String code =
    "employee <- c('John Doe','Peter Smith','Jane Doe') \n " +
        "salary <- c(21000, 23400, 26800) \n" +
        "startdate <- as.Date(c('2013-11-1','2018-3-25','2017-3-14')) \n" +
        "endDate <- as.POSIXct(c('2020-01-10 00:00:00', '2020-04-12 12:10:13', '2020-10-06 10:00:05'), tz='UTC' ) \n" +
        "data.frame(employee, salary, startdate, endDate)";
    SEXP sexp = (SEXP) engine.eval(code);

    Table table = Table.createTable(sexp);
    assertThat(table.getValue(0, 0), equalTo("John Doe"));
    assertThat(table.getValue(1, 1), equalTo(23400.0));
    assertThat(table.getValueAsLocalDate(2, 2), equalTo(LocalDate.of(2017, 3, 14)));
    LocalDateTime expected = LocalDateTime.of(2020, 10, 6, 10, 0, 5);
    assertThat(table.getValueAsLocalDateTime(2, 3), equalTo(expected));
    assertThat(table.getValueAsLong(2, 3), equalTo(expected.toEpochSecond(ZoneOffset.UTC)));
  }

  /**
   *
   * Full Funding	4563.153	380.263	4.938
   * Baseline Funding	3385.593	282.133	3.664
   * Current Funding	2700	225	2.922
   *
   * Transposed
   * Full Funding	Baseline Funding	Current Funding
   * 4563.153	3385.593	2700
   * 380.263	282.133	225
   * 4.938	3.664	2.922
   */
  @Test
  public void testTableTranspose() {
    List<String> header = new ArrayList<String>() {{
      add("Alternative");
      add("Annual");
      add("Monthly");
      add("Per unit and month");
    }};

    List<List<Object>> rowList = new ArrayList<List<Object>>() {{
      add(new ArrayList<Object>(){{
        add("Full Funding");
        add(4563.153);
        add(380.263);
        add(4.938);
      }});
      add(new ArrayList<Object>(){{
        add("Baseline Funding");
        add(3385.593);
        add(282.133);
        add(3.664);
      }});
      add(new ArrayList<Object>(){{
        add("Current Funding");
        add(2700);
        add(225);
        add(2.922);
      }});
    }};

    Table table = new Table(header, rowList);
    Table transposed = table.asTransposed();

    // Transpose again so that it becomes the same as the original so we can compare
    List<List<Object>> transposedColumnList = transposed.getColumnList();
    for (int i = 0; i < table.getRowSize(); i++) {
      List<Object> tableRow = table.getRowList().get(i);
      List<Object> transposedColRow = transposedColumnList.get(i);
      for (int j = 0; j < tableRow.size(); j++) {
        assertEquals(tableRow.get(j), transposedColRow.get(j), "Error in row " + i + ", col " + j);
      }
    }

    List<List<Object>> tableColumnList = table.getColumnList();
    List<List<Object>> transposedRowList = transposed.getRowList();
    for (int i = 0; i < table.getHeaderSize(); i++) {
      List<Object> tableRow = tableColumnList.get(i);
      List<Object> transposedRow = transposedRowList.get(i);
      for (int j = 0; j < tableRow.size(); j++) {
        assertEquals(tableRow.get(j), transposedRow.get(j), "Error in row " + i + ", col " + j);
      }
    }
  }

  private ListVector lineItemsDf() throws ScriptException {
    if (!cache.containsKey("lineItemsDf")) {

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
      cache.put("lineItemsDf", engine.eval(dfCode));
    }
    return (ListVector) cache.get("lineItemsDf");
  }

  @Test
  public void testToDataFrame() throws ScriptException {
    assertNotNull(lineItemsDf(), "lineItemsDf is null, something wrong with initialization");
    //engine.eval("str(lineItems); print(summary(lineItems))");
    Table table = new Table(lineItemsDf());
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
  public void testStringsOnlyTable() throws ScriptException {
    assertNotNull(lineItemsDf(), "lineItemsDf is null, something wrong with initialization");
    Table table = new Table(lineItemsDf(), true);
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
