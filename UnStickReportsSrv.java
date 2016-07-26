package com.sre.app.care.unstickreports;

//<editor-fold defaultstate="collapsed" desc="Imports, click the + to expand.">
import com.sre.common.Constants;
import com.sre.common.util.AppTools;
import com.sre.common.util.Formatter;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//</editor-fold>

/**
 * Unsticks stuck/errored reports via use of a SQL proc JSP uses a Multipart
 * POST
 *
 * @author Michael Potts
 */

@WebServlet("/as/temp")
@MultipartConfig
public class UnStickReportsSrv extends HttpServlet {

    static final Logger logger = LogManager.getLogger(UnStickReportsSrv.class.getName());
    static final int SQL_STRING = Types.LONGVARCHAR;

    //<editor-fold defaultstate="collapsed" desc="Unused servlet methods.  Click + to expand.">
    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
    }

    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        //processRequest(request, response);
    }
    //</editor-fold>

    /**
     * Calls supporting methods to process the input and submit the set of
     * RPT_QUEUE_IDs to the proc as an Array
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();
        logger.info("User: " + request.getRemoteUser() + " ran UnStickReport");
        double elapsedTime;
        String sdID;
        String queueIds = request.getParameter("stuckReportsFixManual");
        boolean fixAll = request.getParameter("fixall").equalsIgnoreCase("y");

        if ((sdID = request.getParameter("stuckReportsFixSD")).length() <= 1) {
            sdID = "N/A";
        }

        try {
            UnStickReports usr = new UnStickReports();
            ArrayList<displayHelper> dataList;
            if (fixAll) {
                dataList = usr.shotgunHandler(sdID);
            } else {
                dataList = usr.queueIDHandler(queueIds.split("[\\s,;\\n\\t]"),
                        sdID);
            }
            
            //Should never happen
            if (dataList == null || dataList.size() <= 0) {
                String error = "An unknown error has occurred.";
                logger.log(Level.FATAL, error);
                request.setAttribute("errorUnStickReports", error);
                request.getRequestDispatcher("/jsp/maint/unstickReports.jsp").forward(request, response);
                return;
            }

            if (dataList.get(0).getError().equals("-1")) {
                String error = "QueueId list is invalid.";
                logger.log(Level.WARN, error);
                request.setAttribute("errorUnStickReports", error);
                request.getRequestDispatcher("/jsp/maint/unstickReports.jsp").forward(request, response);
                return;
            }

            if (dataList.get(0).getError().equals("-2")) {
                String error = "Error occurred while creating DB Connection.";
                logger.log(Level.ERROR, error);
                request.setAttribute("errorUnStickReports", error);
                request.getRequestDispatcher("/jsp/maint/unstickReports.jsp").forward(request, response);
                return;
            }

            if (dataList.get(0).getError().equals("-3")) {
                String error = "Error occurred while creating Java.sql.Array object.";
                logger.log(Level.ERROR, error);
                request.setAttribute("errorUnStickReports", error);
                request.getRequestDispatcher("/jsp/maint/unstickReports.jsp").forward(request, response);
                return;
            }
            
            if (dataList.get(0).getError().equals("-4")) {
                String error = "Service Desk ticket number is not formatted correctly or is invalid.";
                logger.log(Level.ERROR, error);
                request.setAttribute("errorUnStickReports", error);
                request.getRequestDispatcher("/jsp/maint/unstickReports.jsp").forward(request, response);
            }

            response.setContentType("text/html;charset=UTF-8");

            //Create random String variable to link to dataList object and add to session
            String dataListId = UUID.randomUUID().toString();
            request.getSession().setAttribute(dataListId, dataList);
            request.setAttribute("dataListId", dataListId);

            request.setAttribute("dataList", dataList);
            if (fixAll) {
                request.setAttribute("title", "Unstick All Reports Results");
            } else {
                request.setAttribute("title", "Unstick Reports Results");
            }
            request.setAttribute("breadcrumb", "UnStickReports");

            if (!fixAll) {
                String[] temp = queueIds.split("[\\s,;\\n\\t]");
                ArrayList<String> fixedIds = new ArrayList();

                fixedIds.addAll(Arrays.asList(temp));

                request.setAttribute("fixedIds", fixedIds);
            }

            elapsedTime = AppTools.getElapsedTimeInSecs(startTime);
            if (elapsedTime < Constants.SLOW_RUNNING_PROCESS_IN_SECS) {
                logger.info("UnStickReportsSrv took " + elapsedTime + " secs." + " Records:" + Formatter.toLargeInt(dataList.size()));
            } else {
                logger.warn("UnStickReportsSrv slow running execution, exceeded " + Constants.SLOW_RUNNING_PROCESS_IN_SECS + " seconds. Elapsed time: " + elapsedTime + " secs. " + " Records:" + Formatter.toLargeInt(dataList.size()));
            }

            request.getRequestDispatcher(
                    "/jsp/maint/displayRequeueResults.jsp").forward(request, response);
        } catch (Exception e) {
            logger.error(AppTools.getStackTrace(e));
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Unable to connect to SQL Server, please contact an Admin.");
        }
    }

    //<editor-fold defaultstate="collapsed" desc="ServletInfo">
    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Fixes stuck reports.  Expects a CSV file upload OR a series of report queue ids seperated by tabs, spaces, or commas.";
    }// </editor-fold>

}
