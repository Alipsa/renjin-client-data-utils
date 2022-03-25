package se.alipsa.renjin.client.datautils;

public class DataTransformationRuntimeException extends RuntimeException {

  static final long serialVersionUID = 1L;

  public DataTransformationRuntimeException() {
  }

  public DataTransformationRuntimeException(String message) {
    super(message);
  }

  public DataTransformationRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }
}
