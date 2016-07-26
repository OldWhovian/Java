package com.sre.app.care.unstickreports;

import com.sre.common.db.QueryResult;
import java.util.ArrayList;

/**
 * Made purely to take advantage of dynamic result display page.
 *
 * @author Michael Potts
 */

public class displayHelper implements QueryResult {

    String v_status;
    String v_errmsg;
    String v_errnbr;
    Boolean foundResult;
    Boolean hasQueueIds;
    String errorCode;

    public displayHelper(String header, Boolean foundResult) {
        this.foundResult = foundResult;
    }

    /**
     * Constructor method, takes three string arguments and assigns them.
     *
     * @param v_status Contains the success/failure status.
     * @param v_errnbr Contains the error number, 0 if no error.
     * @param v_errmsg Contains an error message or null.
     * @param foundResult
     */
    protected displayHelper(String v_status, String v_errnbr, String v_errmsg, Boolean foundResult, Boolean hasQueueIds) {
        this.v_status = v_status;
        this.v_errmsg = v_errmsg;
        this.v_errnbr = v_errnbr;
        this.foundResult = foundResult;
        this.hasQueueIds = hasQueueIds;
    }
    
    protected displayHelper(String errorCode){
        this.errorCode = errorCode;
    }

    @Override
    public Boolean getFoundResult() {
        return foundResult;
    }
    

    /**
     * Used to feed data into displayResults.jsp
     *
     * @return Returns an ArrayList(String)
     */
    @Override
    public ArrayList<String> getInfoList() {
        ArrayList<String> list = new ArrayList<>();
        list.add(v_status);
        list.add(v_errnbr);
        list.add(v_errmsg);
        return list;
    }

    /**
     * Used to feed data headers into displayResults.jsp
     *
     * @return Returns an ArrayList(String)
     */
    @Override
    public ArrayList<String> getHeaderList() {
        ArrayList<String> list = new ArrayList<>();
        list.add("Result Status");
        list.add("Status Number");
        list.add("Status Message");
        return list;
    }
    
    public String getError(){
        if(errorCode != null)
            return errorCode;
        else
            return "1";
    }
    
    public String getStatus(){
        return v_status;
    }

    @Override
    public Boolean getQueueIds() {
        return hasQueueIds;
    }

}
