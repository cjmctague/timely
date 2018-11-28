package timely.api.request.timeseries;

import io.netty.handler.codec.http.FullHttpRequest;
import timely.api.request.Request;

public interface HttpRequest extends Request {

    void setHttpRequest(FullHttpRequest httpRequest);

    FullHttpRequest getHttpRequest();
}
