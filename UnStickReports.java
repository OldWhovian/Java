/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sre.app.care.unstickreports;

import com.sre.common.db.Resource;
import com.sre.common.util.AppTools;
import com.sre.common.util.stringChecker;
import java.io.IOException;
import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Struct;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import oracle.jdbc.OracleConnection;
import org.apache.commons.dbutils.DbUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tomcat.dbcp.dbcp2.DelegatingConnection;

/**
 *
 * @author Michael Potts
 */
public class UnStickReports {
    static final Logger logger = LogManager.getLogger(UnStickReports.class.getName());          
    
    Connection conn;
    CallableStatement cs;
    static final int SQL_STRING = Types.LONGVARCHAR;
    private static String OBJID;
    private static String VARRAY;
    private static long startTime;
    
    UnStickReports() throws Exception{
        startTime = System.currentTimeMillis();
        double elapsedTime;
        //Gets main connection object rather than the JNDI Connection Object (Required for calling procs)
        conn = ((DelegatingConnection) Resource.getMaintConnection()).getInnermostDelegate();
        elapsedTime = AppTools.getElapsedTimeInSecs(startTime);
        logger.info("Connection establishment took " + elapsedTime + " secs.");
        
        //DEV Objects contain a non-standard container name so we have to check which environment we're fixing
        if (Resource.getSSI().getDbUrlMaint().contains("pacman")) {
            OBJID = "T3OWN.OBJ_ID";
            VARRAY = "T3OWN.V_ID_VARRAY";
        } else {
            OBJID = "NCOWN.OBJ_ID";
            VARRAY = "NCOWN.V_ID_VARRAY";
        }
        
    }
    
    /**
     * Handles requests to requeue specific RPT_QUEUE_IDs
     * @param queueIds - Contains a list of all RPT_QUEUE_IDs to be requeued
     * @param sdID - The Service Desk ID to be used
     * @return - Returns a formatted <code>ArrayList</code> for JSP display
     * @throws SQLException
     * @throws IOException 
     */
    protected ArrayList<displayHelper> queueIDHandler(String[] queueIds, String sdID) throws Exception{
        logger.log(Level.INFO, "Fixing entered Reports");
        try {
            queueIds = removeEmpty(queueIds);
            logReports(queueIds);
            
            //Checks if queueIds have valid formatting (length/alphanumeric) and checks if the Service Desk ID is reasonable (only alphanumeric)
            if (!queueIdValidator(queueIds)) {
                ArrayList<displayHelper> error = new ArrayList<>();
                error.add(new displayHelper("-1")); //Error with QueueIDs or sdID
                return error;
            }
            if (!isValidSD(sdID)){
                ArrayList<displayHelper> error = new ArrayList<>();
                error.add(new displayHelper("-4"));
                return error;
            }
            
            //Should only be tripped if an environment is down (in which case this tool should not be used in the given environment)
            //Or if there is a failure in JNDI resources (bad URL, etc)
            if (conn == null){
                ArrayList<displayHelper> error = new ArrayList<>();
                error.add(new displayHelper("-2")); // Error with creating connection
                return error;
            }
            
            cs = conn.prepareCall("{call T3_SRE_UTILS.REQUE_REPORTS(?,?,?,?,?)}");

            //JDBCArray creator
            Array jdbcArray = arrayMaker(queueIds, conn);
            
            //Should never happen, implies issue with JDBC Drivers (check includes and dependencies)
            if (jdbcArray == null) {
                ArrayList<displayHelper> error = new ArrayList<>();
                error.add(new displayHelper("-3")); // Could not create Java.sql.Array object
                return error;
            }

            cs.setArray(1, jdbcArray);
            cs.setString(2, sdID);
            cs.registerOutParameter(3, SQL_STRING);
            cs.registerOutParameter(4, SQL_STRING);
            cs.registerOutParameter(5, SQL_STRING);
            cs.execute();

            ArrayList<displayHelper> dataList = formatReturn(cs.getString(3), cs.getString(4), cs.getString(5), true);
            logger.log(Level.INFO, dataList.get(0).getStatus());
            return dataList;
            
        } finally {
            DbUtils.closeQuietly(cs);
            DbUtils.closeQuietly(conn);
        }
    }
    
    /**
     * Handles requests to requeue all errored reports in an environment
     * (named shotgunHandler because this method is shotgun-esque)
     * @param sdID - Contains the name of a Service Desk ID
     * @return - Returns a formatted <code>ArrayList</code> for JSP display
     * @throws SQLException 
     */
    protected ArrayList<displayHelper> shotgunHandler(String sdID) throws Exception{        
        try{
            //Check JNDI Resources if this is being tripped
            if (conn == null){
                ArrayList<displayHelper> error = new ArrayList<>();
                error.add(new displayHelper("-2")); // Could not make connection
                return error;
            }
            
            //No Processing required
            cs = conn.prepareCall("{call T3_SRE_UTILS.REQUE_ERR_REPORTS(?,?,?,?)}");
            cs.setString(1, sdID);
            cs.registerOutParameter(2, SQL_STRING);
            cs.registerOutParameter(3, SQL_STRING);
            cs.registerOutParameter(4, SQL_STRING);
            cs.execute();
            
            ArrayList<displayHelper> dataList = formatReturn(cs.getString(2), cs.getString(3), cs.getString(4), false);
            logger.log(Level.INFO, "Fixing ALL reports. "+ ((dataList!=null)?dataList.get(0).getStatus():""));
            return dataList;
        }
        finally{
            DbUtils.closeQuietly(cs);
            DbUtils.closeQuietly(conn);
        }
    }
    
    
    /**
     * Validates queueIds to prevent incorrect input being passed to the proc.
     * @param queueIds Contains all queueIds that have been passed to the servlet in this instance.
     * @return Returns true if queueIds are valid, returns False if any are not.
     */
    private boolean queueIdValidator(String[] queueIds){
        if(queueIds.length <= 0)
            return false;
        
        for(String id : queueIds){
            //passes queueId and the integer base (10/Decimal)
            if(!stringChecker.isInteger(id, 10))
                return false;
        }
        return true;
    }  
    
    /**
     * Takes the Proc response strings and formats them into an ArrayList of an object.
     * @param v_status Contains the Proc status return message.
     * @param v_errnbr Contains the error number returned by the proc (0 by default).
     * @param v_errmsg Contains the error message returned by the proc (null by default).
     * @return 
     */
    private static ArrayList<displayHelper> formatReturn(String v_status, String v_errnbr, String v_errmsg, Boolean hasQueueIds){
        //When no errors occur, these values return as null
        if(v_status == null)
            v_status = "No Status Message";
        if(v_errnbr == null) // May no longer be necessary...
            v_errnbr = "0";
        if(v_errmsg == null)
            v_errmsg = "No Error Message";
        
        ArrayList<displayHelper> dataList = new ArrayList<>();
        dataList.add(new displayHelper(v_status, v_errnbr, v_errmsg,true,hasQueueIds));
        return dataList;
    }
    
    /**
     * Converts a <code>String</code> array into an <code>Array</code>.
     * @param queueIds A <code>String</code> array containing queue ids.
     * @param conn A <code>T4Connection</code> object that is connected to the a DB Env.
     * @return Returns an <code>Array</code>.
     * @throws SQLException Thrown if user doesn't have access or connection failed.
     * @throws java.io.IOException Thrown if <code>Struct</code> formation fails.
     */
    private static Array arrayMaker(String[] queueIds, Connection conn) throws SQLException, IOException{
        if(conn == null){
            return null;
        }
        
        Struct struct[] = new Struct[queueIds.length];
        
        if(struct.length >= 1){
            int index = 0;
            for(String s : queueIds){
                Object objs[] = {s};
                struct[index++] = conn.createStruct(OBJID,objs);
            }
        }
        else
            return null; //Should never happen (Implies empty queueId list made it through)
        
        
        return ((OracleConnection)conn).createOracleArray(VARRAY,struct);
    }
    
    /**
     * Removes empty entries that may appear in the QueueIds from split function call
     * @param sa - String array of QueueIds that may contain empty entries
     * @return QueueIds array without any empty entries.
     */
    private static String[] removeEmpty(String[] sa){
        if(sa.length <= 0) return sa;
        
        ArrayList<String> temp = new ArrayList<>();
        for (String sa1 : sa) {
            if (sa1.length() >= 1) {
                temp.add(sa1);
            }
        }
        
        return Arrays.copyOf(temp.toArray(),temp.size(),String[].class);
    }
    
    private static void logReports(String[] queueIds){
        StringBuilder sb = new StringBuilder();
        for(int i=0; i<queueIds.length; i++) {
            sb.append(
                ((i>0 && queueIds.length> i)?", ":"") + queueIds[i]
            );
        }
        logger.log(Level.INFO,"Entered Reports: " + sb.toString());
    }
    
    private static boolean isValidSD(String serviceDeskTicket){
        String regex = "(SDDS)-(\\d+)";
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(serviceDeskTicket);
        if(serviceDeskTicket.equals("N/A") || m.matches()){
            return true;
        }
        return false;
    }
}
