package timely.api.request;

import timely.api.request.timeseries.HttpRequest;

public interface HttpPostRequest extends HttpRequest {

    HttpPostRequest parseBody(String content) throws Exception;

}
