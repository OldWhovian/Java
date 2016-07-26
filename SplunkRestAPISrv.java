package com.sre.app.splunkapi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author Michael Potts
 */
public class SplunkRestAPISrv extends HttpServlet {

    static final Logger logger = LogManager.getLogger(SplunkRestAPISrv.class.getName());

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

    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        ArrayList<SplunkResult> dataList = new ArrayList<>();

        SplunkRestAPI splunkRestAPI = new SplunkRestAPI(request.getParameter("userName"), request.getParameter("userPass"), request.getParameter("startDate"), request.getParameter("endDate"));

        int errorCode = splunkRestAPI.runQuery();

        if (errorCode == 0) {
            //Create random String variable to link to dataList object and add to session
            String dataListId = UUID.randomUUID().toString();
            request.getSession().setAttribute(dataListId, dataList);
            request.setAttribute("dataListId", dataListId);

            request.setAttribute("dataList", dataList);
            request.setAttribute("breadcrumb", "SplunkFleetHitPages");
            request.setAttribute("title", "Page Hit Count by Fleet via Splunk " + request.getParameter("startDate") + " - " + request.getParameter("endDate"));
            request.getRequestDispatcher(
                    "/jsp/maint/displayResults.jsp").forward(request, response);
            return;
        } else if (errorCode == -1) {
            String error = "Start and End times are either invalid or not selected.";
            logger.log(Level.ERROR, error);
            request.setAttribute("errorSplunk", error);
            request.getRequestDispatcher("/jsp/maint/splunkFleetHitPages.jsp").forward(request, response);
            return;
        } else if (errorCode == -2) {
            String error = "A Thread error has occured, please contact AppOps.";
            logger.log(Level.ERROR, error);
            request.setAttribute("errorSplunk", error);
            request.getRequestDispatcher("/jsp/maint/splunkFleetHitPages.jsp").forward(request, response);
            return;
        } else if (errorCode == -3) {
            String error = "The query has failed to return results, please check selected options.";
            logger.log(Level.ERROR, error);
            request.setAttribute("errorSplunk", error);
            request.getRequestDispatcher("/jsp/maint/splunkFleetHitPages.jsp").forward(request, response);
            return;
        } else if (errorCode == -4) {
            String error = "Login Information was incorrect or server is down.  Please check login information.\nIf error persists, please check server status.";
            logger.log(Level.ERROR, error);
            request.setAttribute("errorSplunk", error);
            request.getRequestDispatcher("/jsp/maint/splunkFleetHitPages.jsp").forward(request, response);
            return;
        }

    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Queries user usage rates.";
    }// </editor-fold>

}
