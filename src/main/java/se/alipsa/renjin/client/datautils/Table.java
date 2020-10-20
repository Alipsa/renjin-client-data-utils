package se.alipsa.renjin.client.datautils;

import org.renjin.primitives.matrix.Matrix;
import org.renjin.sexp.*;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static se.alipsa.renjin.client.datautils.RDataTransformer.*;

/**
 * Data frames in R are "column based" (variable based) which is very convenient for analysis but Java is
 * Object / Observation based so a Table which essentially is just a List of rows (observations), makes is much easier
 * to work with the data in Java.
 */
public class Table {

  List<String> headerList;
  List<List<Object>> rowList;

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
      throw new IllegalArgumentException("Unknown type, " + type + " ( class: " + sexp.getClass()
          + "), convert this object to a data.frame or vector to use it");
    }
  }

  public Table(ListVector df) {
    headerList = toHeaderList(df);
    rowList = toRowlist(df);
  }

  public Table(List<String> columnList, List<List<Object>> rowList) {
    headerList = columnList;
    this.rowList = rowList;
  }

  public Table(Matrix mat) {
    String type = mat.getVector().getTypeName();
    headerList = new ArrayList<>();
    for (int i = 0; i < mat.getNumCols(); i++) {
      String colName = mat.getColName(i) == null ? i + "" : mat.getColName(i);
      headerList.add(colName);
    }

    rowList = new ArrayList<>();

    List<Object> row;
    for (int i = 0; i < mat.getNumRows(); i++) {
      row = new ArrayList<>();
      for (int j = 0; j < mat.getNumCols(); j++) {
        if ("integer".equals(type)) {
          row.add(mat.getElementAsInt(i, j));
        } else {
          row.add(mat.getElementAsDouble(i, j));
        }
      }
      rowList.add(row);
    }
  }

  public Table(Vector vec) {
    headerList = new ArrayList<>();
    headerList.add(vec.getTypeName());

    List<Vector> values = new ArrayList<>();
    values.add(vec);

    rowList = transpose(values);
  }

  public Table(ResultSet rs) throws SQLException {
    ResultSetMetaData rsmd = rs.getMetaData();
    headerList = new ArrayList<>();
    int ncols = rsmd.getColumnCount();
    for (int i = 1; i <= ncols; i++) {
      headerList.add(rsmd.getColumnName(i));
    }
    rowList = new ArrayList<>();
    while (rs.next()) {
      List<Object> row = new ArrayList<>();
      for (int i = 1; i <= ncols; i++) {
        row.add(rs.getObject(i));
      }
      rowList.add(row);
    }
  }

  public List<String> getHeaderList() {
    return headerList;
  }

  public List<List<Object>> getRowList() {
    return rowList;
  }

  /**
   *
   * @return this table as a ListVector (data.frame) for easy handling in R
   */
  public ListVector asDataFrame() {
    return toDataFrame(this);
  }


}
