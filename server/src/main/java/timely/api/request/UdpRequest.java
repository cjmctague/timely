package timely.api.request;

public interface UdpRequest {

    /**
     * Parse a line of text received and populate this object
     * 
     * @param line
     */
    void parse(String line);

}
