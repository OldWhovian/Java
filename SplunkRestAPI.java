/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sre.app.splunkapi;

import com.splunk.HttpService;
import com.splunk.Job;
import com.splunk.JobArgs;
import com.splunk.JobResultsArgs;
import com.splunk.ResultsReaderCsv;
import com.splunk.SSLSecurityProtocol;
import com.splunk.Service;
import com.splunk.ServiceArgs;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author Michael Potts
 */
public class SplunkRestAPI {

    final String QUERY = "search index=apache_xo sourcetype=\"access_combined\" \"portal/\""
            + " NOT(\"jsf\" OR \"css\" OR \"js\" OR \"png\" OR \"block\" OR \"gif\" OR \"woff\" OR \"ico\" OR \"xhtml\" OR \".htc\" OR \"xmlhttp\" OR \"resource\""
            + " OR \"img\" OR \"images\" OR \"loginator\" OR \"logout\" OR \"dynaTraceMonitor\" OR \"jsessionid\" OR \"xls\" OR \"printable\" OR \"resetPassword\""
            + " OR \"login\" OR \"images\" OR \"xhtml\" OR \"~~themedata~~\" OR \"~~colorschememapping~~\" OR \"csv\" OR \"jpg\" OR \";cid\")  | rex field=uri_path"
            + " \"(?<uri_path>(/[^/]+){1,3})\" | stats count(clientip) as \"Number of Requests\" by clientip,uri_path | rename uri_path as \"Portal URI\", clientip"
            + " as \"Client IP\"";
    final String HOST = "splunk-xo.location.com";
    final int PORT = 8089;

    String userName;
    String userPass;
    String startDate;
    String endDate;

    boolean hasResult = false;
    ArrayList<SplunkResult> dataList;
    
    protected SplunkRestAPI(String userName, String userPass, String startDate, String endDate) {
        this.userName = userName;
        this.userPass = userPass;
        this.startDate = startDate != null ? dateFormatter(startDate):null;
        this.endDate = endDate != null ? dateFormatter(endDate):null;
    }

    protected int runQuery() {
        InputStream is = null;
        ResultsReaderCsv csvReader = null;
        Job job = null;

        try {
            
            if(startDate == null || endDate == null) {
                return -1; //no date has been passed
            }
            
            HttpService.setSslSecurityProtocol(SSLSecurityProtocol.TLSv1_2); //Use TSL security over SSL
            ServiceArgs loginArgs = new ServiceArgs();
            loginArgs.setUsername(userName);
            loginArgs.setPassword(userPass);
            loginArgs.setHost(HOST);
            loginArgs.setPort(PORT);
            
            Service service = Service.connect(loginArgs);
            JobArgs jobArgs = new JobArgs();
            jobArgs.setEarliestTime(startDate);
            jobArgs.setLatestTime(endDate);
            job = service.getJobs().create(QUERY, jobArgs);
            
            //Job is not run immediately, we must wait until job completion to fetch results
            while(!job.isDone()){
                try{
                    Thread.sleep(2000); //Sleep thread for 2 seconds while job is finishing in Splunk
                } catch (InterruptedException e){
                    return -2; //Sleep caused a thread failure
                }
            }
            
            JobResultsArgs rArgs = new JobResultsArgs();
            rArgs.setOutputMode(JobResultsArgs.OutputMode.CSV);
            rArgs.setCount(0);
            
            is = job.getResults(rArgs);
            dataList = new ArrayList<>();
            
            if(is != null){
                csvReader = new ResultsReaderCsv(is);
                
                HashMap<String, String> event;
                
                // indexes: 0 = Number of Requests, 1 = Portal URI, 2 = Client IP
                String tempStorage[] = new String[3];
                int index = 0;
                while((event = csvReader.getNextEvent()) != null){
                    
                    for (String key : event.keySet()){
                        tempStorage[index++] = event.get(key);
                    }
                    index = 0;
                    dataList.add(new SplunkResult(tempStorage[2], tempStorage[1], tempStorage[0], true));
                }
            }
            else{
                return -3; //Query failed to find any results
            }
        } catch (IOException ex) {
            return -4; //Failure to connect, incorrect login info
        } finally {
            try {
                if (csvReader != null) {
                    csvReader.close();
                }
                if (is != null) {
                    is.close();
                }
                if (job != null) {
                    job.cancel();
                }
            } catch (IOException ex) {//do nothing...}
            }
        }
        hasResult = true;
        return 0;
    }
    
    /**
     * Formats the date passed from the jsp into a Splunk acceptable format
     *
     * @param date - MM/DD/YYYY HH:MM AM
     * @return YYYY-MM-DDT00:00:00.000-08:00 (-08:00 is the timezone)
     */
    private String dateFormatter(String date) {
        StringBuilder newDate = new StringBuilder();
        String[] dates = date.split("/");
        String[] year = dates[2].split(" ");
        String[] time = year[1].split(":");
        String hour = null;
        String minute = time[1];
        if (!year[2].equals("AM")) {
            int temp = Integer.valueOf(time[0]) + 12;
            if (temp == 24)
                temp = 0;
            hour = String.valueOf(temp);
        } else {
            hour = time[0];
        }

        newDate.append(year[0]);
        newDate.append("-");
        newDate.append(dates[0]);
        newDate.append("-");
        newDate.append(dates[1]).append("T");

        if (time.length == 0 || time[0].length() == 0 || time[1].length() == 0) {
            newDate.append("00:00:00.000-08:00");
        } else {
            hour = null;
            if (!year[2].equals("AM")) {
                int temp = Integer.valueOf(time[0]) + 12;
                if (temp == 24) {
                    temp = 0;
                }
                hour = String.valueOf(temp);
            } else {
                hour = time[0];
            }

            newDate.append(hour).append(":").append(minute).append(":00.000-08:00");
        }

        return newDate.toString();
    }
    
    protected ArrayList<SplunkResult> getResult(){
        if(hasResult)
            return dataList;
        else
            return null;
    }
}
