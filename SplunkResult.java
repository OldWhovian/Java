package com.sre.app.splunkapi;

import com.sre.common.db.QueryResult;
import java.util.ArrayList;

/**
 *
 * @author Michael Potts
 */
public class SplunkResult implements QueryResult {

    private String clientIP;
    private String portalURI;
    private String requestCount;
    private final Boolean foundResult;

    public SplunkResult(Boolean foundResult) {
        this.foundResult = foundResult;
    }

    public SplunkResult(String requestCount, String clientIP, String portalURI, Boolean foundResult) {
        this.clientIP = clientIP;
        this.portalURI = portalURI;
        this.requestCount = requestCount;
        this.foundResult = foundResult;

    }

    public String getClientIP() {
        return clientIP;
    }

    public String getPortalURI() {
        return portalURI;
    }

    public String getRequestCount() {
        return requestCount;
    }

    @Override
    public Boolean getFoundResult() {
        return foundResult;
    }

    @Override
    public ArrayList<String> getInfoList() {
        ArrayList<String> list = new ArrayList<>();
        list.add(getClientIP());
        list.add(getPortalURI());
        list.add(getRequestCount());
        return list;
    }

    @Override
    public ArrayList<String> getHeaderList() {
        ArrayList<String> list = new ArrayList<>();
        list.add("Client_IP");
        list.add("Portal_URI");
        list.add("Number_of_Requests");
        return list;
    }

    @Override
    public Boolean getQueueIds() {
        return false;
    }
}
