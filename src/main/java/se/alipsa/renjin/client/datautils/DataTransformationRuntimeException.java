package se.alipsa.renjin.client.datautils;

public class DataTransformationRuntimeException extends RuntimeException {

  public DataTransformationRuntimeException() {
  }

  public DataTransformationRuntimeException(String message) {
    super(message);
  }

  public DataTransformationRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }
}
