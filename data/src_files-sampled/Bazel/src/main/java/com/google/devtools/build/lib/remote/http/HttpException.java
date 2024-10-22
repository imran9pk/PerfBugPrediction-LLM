package com.google.devtools.build.lib.remote.http;

import io.netty.handler.codec.http.HttpResponse;
import java.io.IOException;

final class HttpException extends IOException {
  private final HttpResponse response;

  HttpException(HttpResponse response, String message, Throwable cause) {
    super(message, cause);
    this.response = response;
  }

  public HttpResponse response() {
    return response;
  }
}
