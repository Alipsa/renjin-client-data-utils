package se.alipsa.renjin.client.datautils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.renjin.primitives.matrix.Matrix;
import org.renjin.primitives.sequence.IntSequence;
import org.renjin.sexp.*;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static se.alipsa.renjin.client.datautils.RDataTransformer.*;

/**
 * Data frames in R are "column based" (variable based) which is very convenient for analysis but Java is
 * row / Observation based so a Table which essentially is just a List of rows (observations), makes it much easier
 * to work with the data in Java.
 * Once the Table it created, the data is immutable.
 * You can, however, set the decimal formatter which determines how conversion to decimal data (double and floats) are
 * performed when you retrieve the data though the convenience methods {@link #getValueAsDouble(int, int)}
 * and {@link #getValueAsFloat(int, int)}
 * (The default DecimalFormatter is using the default (current) Locale of the jvm).
 */
public class Table {

  private NumberFormat numberFormat = DecimalFormat.getInstance();

  private List<String> headerList;
  private List<List<Object>> rowList;
  private List<DataType> columnTypes;

  // derived from rowlist, calculated on first use and cached (this is why rowList is immutable)
  private List<List<Object>> columnList = null;

  public Table() {
    // Empty
  }

  public static Table createTable(SEXP sexp) {
    String type = sexp.getTypeName();
    if (sexp instanceof ListVector) {
      return new Table((ListVector) sexp);
    } else if (sexp instanceof Vector) {
      Vector vec = (Vector) sexp;
      if (vec.hasAttributes()) {
        AttributeMap attributes = vec.getAttributes();
        Vector dim = attributes.getDim();
        if (dim == null) {
          return new Table(vec);
        } else {
          if (dim.length() == 2) {
            Matrix mat = new Matrix(vec);
            return new Table(mat);
          } else {
            throw new IllegalArgumentException("Array of type, "
                + sexp.getTypeName() + " cannot be shown , Result is an array with "
                + dim.length() + " dimensions. Convert this object to a data.frame to use it!");
          }
        }
      } else {
        return new Table(vec);
      }
    } else {
      throw new DataTransformationRuntimeException("Unknown type, " + type + " ( class: " + sexp.getClass()
          + "), convert this object to a data.frame or vector to use it");
    }
  }

  public Table(ListVector df) {
    this(df, false);
  }

  public Table(ListVector df, boolean contentAsStrings) {
    headerList = Collections.unmodifiableList(toHeaderList(df));
    columnTypes = Collections.unmodifiableList(toTypeList(df, contentAsStrings));
    rowList = Collections.unmodifiableList(toRowlist(df, contentAsStrings));
  }

  public Table(List<String> headers, AbstractAtomicVector... columns) {
    if (columns.length == 0) {
      throw new IllegalArgumentException("No column data provided");
    }
    ListVector.NamedBuilder builder = new ListVector.NamedBuilder();
    for (AbstractAtomicVector vec : columns) {
      builder.add(vec);
    }
    builder.setAttribute("row.names", new IntSequence(1, 1, columns[0].length()));
    builder.setAttribute("class", StringVector.valueOf("data.frame"));
    ListVector df = builder.build();
    headerList = Collections.unmodifiableList(headers);
    columnTypes = Collections.unmodifiableList(toTypeList(df, false));
    rowList = Collections.unmodifiableList(toRowlist(df, false));
  }

  @SafeVarargs
  public Table(List<String> headerList, List<List<Object>> rowList, List<DataType>... dataTypesOpt) {
    setHeaderList(headerList);
    setRowList(rowList);
    if (dataTypesOpt.length > 0) {
      setColumnTypes(dataTypesOpt[0]);
    } else {
      List<DataType> columns = new ArrayList<>();
      for(int i = 0; i < headerList.size(); i++) {
        columns.add(DataType.UNKNOWN);
      }
      setColumnTypes(columns);
    }
  }

  public Table(Matrix mat) {
    DataType dataType;
    if("integer".equals(mat.getVector().getTypeName())) {
      dataType = DataType.INTEGER;
    } else { // No other types supported in Renjin at the moment
      dataType = DataType.DOUBLE;
    }
    List<DataType> columns = new ArrayList<>();
    for (int i = 0; i < mat.getNumCols(); i++) {
      columns.add(dataType);
    }
    setColumnTypes(columns);

    List<String> headers = new ArrayList<>();
    for (int i = 0; i < mat.getNumCols(); i++) {
      String colName = mat.getColName(i) == null ? i + "" : mat.getColName(i);
      headers.add(colName);
    }
    setHeaderList(headers);

    List<Object> row;
    List<List<Object>> rows = new ArrayList<>();
    for (int i = 0; i < mat.getNumRows(); i++) {
      row = new ArrayList<>();
      for (int j = 0; j < mat.getNumCols(); j++) {
        if (DataType.INTEGER == dataType) {
          row.add(mat.getElementAsInt(i, j));
        } else {
          row.add(mat.getElementAsDouble(i, j));
        }
      }
      rows.add(row);
    }
    setRowList(rows);
  }

  public Table(Vector vec) {
    setHeaderList(Collections.singletonList(vec.getTypeName())); // TODO: should be name of the list, not the type name
    setColumnTypes(Collections.singletonList(DataType.forVectorType(vec.getVectorType())));
    List<Vector> values = new ArrayList<>();
    values.add(vec);
    setRowList(transpose(values));
  }

  /**
   * Constructor to create a Table from a live java.sql.ResultSet.
   * The resultSet meta data is used to determine column types.
   *
   * @param rs the ResultSet to create the Table from
   * @throws SQLException if a database issue occurs
   */
  public Table(ResultSet rs) throws SQLException {
    ResultSetMetaData rsmd = rs.getMetaData();

    int ncols = rsmd.getColumnCount();

    List<String> headers = new ArrayList<>();
    List<DataType> types = new ArrayList<>();
    for (int i = 1; i <= ncols; i++) {
      headers.add(rsmd.getColumnName(i));
      types.add(DataType.forSqlType(rsmd.getColumnType(i)));
    }
    setHeaderList(headers);
    setColumnTypes(types);

    List<List<Object>> rows = new ArrayList<>();
    while (rs.next()) {
      List<Object> row = new ArrayList<>();
      for (int i = 1; i <= ncols; i++) {
        row.add(rs.getObject(i));
      }
      rows.add(row);
    }
    setRowList(rows);
  }

  /**
   * @return a transposed version of the Table i.e. the table is "tilted" 90 degrees so that each row becomes a column
   * and each column becomes a row.
   */
  public Table asTransposed() {
    return new Table(headerList, getColumnList(), columnTypes);
  }

  @SuppressFBWarnings("EI_EXPOSE_REP")
  public List<List<Object>> getColumnList() {
    if (columnList == null) {
      List<List<Object>> columns = new ArrayList<>();
      for (List<Object> row : rowList) {
        if (columns.size() == 0) {
          for (int i = 0; i < row.size(); i++) {
            columns.add(new ArrayList<>());
          }
        }
        for (int j = 0; j < row.size(); j++) {
          columns.get(j).add(row.get(j));
        }
      }
      columnList = Collections.unmodifiableList(columns);
    }
    return columnList;
  }

  @SuppressFBWarnings("EI_EXPOSE_REP")
  public List<String> getHeaderList() {
    return headerList;
  }

  public int getHeaderSize() {
    return headerList.size();
  }

  @SuppressFBWarnings("EI_EXPOSE_REP")
  public List<List<Object>> getRowList() {
    return rowList;
  }

  public int getRowSize() {
    return rowList.size();
  }

  /**
   * @param index the row index (0 based) for which row to get
   * @return a List of Objects where each object in the list is the cell data
   */
  public List<Object> getRow(int index) {
    return rowList.get(index);
  }

  private void setHeaderList(List<String> headers) {
    headerList = Collections.unmodifiableList(headers);
  }

  private void setRowList(List<List<Object>> rows) {
    rowList = Collections.unmodifiableList(rows);
  }

  private void setColumnTypes(List<DataType> dataTypes) {
    columnTypes = Collections.unmodifiableList(dataTypes);
  }

  /**
   * Find the first row based on a cell value specified
   * Note: Overloading methods does not work in Birt (Rhino), must use a unique name for each method so cannot name it getRow
   * @param value the cell value to search for
   * @param column the column index to look in, defaults to 0 (first column)
   * @return the row as a List of Objects or null if no row found
   */
  public List<Object> getRowForName(String value, int... column) {
    int col = column.length == 0 ? 0 : column[0];
    for (List<Object> row : rowList) {
      if (value.equals(row.get(col))) {
        return row;
      }
    }
    return null;
  }

  @SuppressFBWarnings("EI_EXPOSE_REP")
  public List<DataType> getColumnTypes() {
    return columnTypes;
  }

  public List<Object> getColumnForName(String name) {
    int index = -1;
    for (int i = 0; i < headerList.size(); i++) {
      if (name.equals(headerList.get(i))) {
        index = i;
        break;
      }
    }
    if(index == -1) {
      throw new IllegalArgumentException("There is no column named " + name);
    }
    return getColumnList().get(index);
  }

  /**
   *
   * @param colName the name of the column
   * @return the index number matching the first occurrence of the column name or -1 if no match is found
   */
  public int getColumnIndex(String colName) {
    for (int colIdx = 0; colIdx < headerList.size(); colIdx++) {
      if (headerList.get(colIdx).equals(colName)) return colIdx;
    }
    return -1;
  }

  /**
   * Similar to getRowList this gives you all the values of a particular column
   * @param index the index number of the column to get
   * @return a List of Objects where each Object is the value
   */
  public List<Object> getColumn(int index) {
    return getColumnList().get(index);
  }

  /**
   * ListVector is the implementation of a data.frame in Renjin R. If you have data in Java that you want to
   * work with in R you can convert the Table to a ListVector using this method and then insert it into the
   * session using the put method on the RenjinScriptEngine e.g.
   * <p>
   * <code>engine.put("salaryData", myTable.asDataframe());</code>
   * </p>
   * @param stringsOnlyOpt Optional param - if true, the resulting ListVector (data.frame) will consist of Strings (characters)
   * @return this table as a ListVector (data.frame) for easy handling in R
   */
  public ListVector asDataframe(boolean... stringsOnlyOpt) {
    return toDataframe(this, stringsOnlyOpt.length > 0 && stringsOnlyOpt[0]);
  }

  /**
   *
   * @param row the row index
   * @param column the column index
   * @return the value of the cell specified
   */
  public Object getValue(int row, int column) {
    return rowList.get(row).get(column);
  }

  /**
   *
   * @param row the row index
   * @param column the column index
   * @return the value of the cell specified converted to a String
   */
  public String getValueAsString(int row, int column) {
    Object val = getValue(row, column);
    if (val == null) {
      return null;
    }
    return String.valueOf(val);
  }

  /**
   *
   * @param row the row index
   * @param column the column index
   * @return the value of the cell specified converted to a Double. If the underlaying value is a String,
   * the default DecimalFormatter for the JVM on the particular system it is running on is used to determine what
   * the group and decimal separator characters are. This can be adjusted if needed by setting the DecimalFormatter
   * using the {@link #setNumberFormat(NumberFormat)} method.
   */
  public Double getValueAsDouble(int row, int column) {
    return ValueConverter.asDouble(getValue(row, column), numberFormat);
  }

  /**
   *
   * @param row the row index
   * @param column the column index
   * @return the value of the cell specified converted to a Boolean. If the underlaying value is a String,
   * Boolean.TRUE is returned if the string contains "true", "sant", "1", "on", "yes", "ja", or "真" (case insensitive).
   * If the underlying value is a Double and a NaN then null is returned
   */
  @SuppressFBWarnings("NP_BOOLEAN_RETURN_NULL")
  public Boolean getValueAsBoolean(int row, int column) {
    return ValueConverter.asBoolean(getValue(row, column));
  }

  public Integer getValueAsInteger(int row, int column) {
    return ValueConverter.asInteger(getValue(row, column), numberFormat);
  }

  public Long getValueAsLong(int row, int column) {
    return ValueConverter.asLong(getValue(row, column), numberFormat);
  }

  public Float getValueAsFloat(int row, int column) {
    Double val = getValueAsDouble(row, column);
    return val == null ? null : val.floatValue();
  }

  public Byte getValueAsByte(int rowIdx, int colIdx) {
    Object val = getValue(rowIdx, colIdx);
    if (val instanceof Byte) {
      return (Byte)val;
    }
    return val == null ? null : Byte.valueOf(String.valueOf(val));
  }

  public LocalDate getValueAsLocalDate(int rowIdx, int colIdx) {
    return getValueAsLocalDate(rowIdx, colIdx, "yyyy-MM-dd");
  }

  public LocalDate getValueAsLocalDate(int rowIdx, int colIdx, String format) {
    Object val = getValue(rowIdx, colIdx);
    if (val == null) {
      return null;
    }
    if (val instanceof Double) {
      return LocalDate.ofEpochDay(((Double)val).longValue());
    } else {
      return LocalDate.parse(String.valueOf(val), DateTimeFormatter.ofPattern(format));
    }
  }

  public LocalDateTime getValueAsLocalDateTime(int rowIdx, int colIdx) {
    return getValueAsLocalDateTime(rowIdx, colIdx, ZoneOffset.UTC);
  }

  public LocalDateTime getValueAsLocalDateTime(int rowIdx, int colIdx, ZoneOffset offset) {
    Object val = getValue(rowIdx, colIdx);
    if (val == null) {
      return null;
    }
    if (val instanceof Double) {
      long longVal = ((Double)val).longValue();
      //System.out.println("LocalDateTime from " + longVal + ", offset = " + offset);
      return LocalDateTime.ofEpochSecond(longVal, 0, offset);
    } else {
      String strVal = String.valueOf(val);
      if (strVal.length() == 10 ) {
        return LocalDateTime.parse(strVal, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
      } else if (strVal.length() == 19) {
        return LocalDateTime.parse(strVal, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
      }
      throw new IllegalArgumentException("Unknown date time format for " + strVal);
    }
  }

  @Override
  public String toString() {
    return "Table with " + getHeaderSize() + " columns and " + getRowSize() + " rows";
  }

  public DataType getColumnType(int i) {
    return columnTypes.get(i);
  }

  /**
   * Set the decimal formatter to use in convenience methods getting data as double or float.
   * Once set, the decimal formatter is no longer mutable i.e. a change to the decimal formatter
   * after it has been set in the table does not affect the decimal formatter in the table.
   *
   * @param numberFormat the {@link java.text.NumberFormat} to use when converting Strings to Float and Double
   */
  public void setNumberFormat(NumberFormat numberFormat) {
    this.numberFormat = (NumberFormat)numberFormat.clone();
  }
}
