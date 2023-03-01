/**
 * BIR Reports Object
 * -----------------------------------------------------------------------------
 * mac 2020.02.11
 *      started creating this object.
 * mac 2020.02.12
 *      filling of BIR Sales Summary Report.
 */

package org.rmj.cas.pos.reports;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JsonDataSource;
import net.sf.jasperreports.view.JasperViewer;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.rmj.appdriver.GLogger;
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.MiscUtil;
import org.rmj.appdriver.SQLUtil;
import org.rmj.appdriver.agentfx.CommonUtils;
import org.rmj.appdriver.agentfx.ShowMessageFX;
import org.rmj.appdriver.iface.GReport;
import org.rmj.appdriver.agentfx.ui.showFXDialog;

public class BIRReports implements GReport{
    private GRider _instance;
    private boolean _preview = true;
    private String _message = "";
    private LinkedList _rptparam = null;
    private JasperPrint _jrprint = null;
    private String pxeModuleName = BIRReports.class.getName().toString();
    
    private double xOffset = 0; 
    private double yOffset = 0;
    
    public String getMessage(){
        return _message;
    }
    
    public BIRReports(){
        _rptparam = new LinkedList();
        _rptparam.add("store.report.id");
        _rptparam.add("store.report.no");
        _rptparam.add("store.report.name");
        _rptparam.add("store.report.jar");
        _rptparam.add("store.report.class");
        _rptparam.add("store.report.is_save");
        _rptparam.add("store.report.is_log");
        
        _rptparam.add("store.report.criteria.presentation");
        _rptparam.add("store.report.criteria.branch");      
        _rptparam.add("store.report.criteria.group");        
        _rptparam.add("store.report.criteria.date");     
    }
    
    @Override
    public void setGRider(Object foApp) {
        _instance = (GRider) foApp;
    }
    
    @Override
    public void hasPreview(boolean show) {
        _preview = show;
    }

    @Override
    public boolean getParam() {
        Parent parent;
        Scene scene;
        
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("BrowseReport.fxml"));
        fxmlLoader.setLocation(getClass().getResource("BrowseReport.fxml"));

        BrowseReportController instance = new BrowseReportController();
        instance.setGRider(_instance);
        instance.setReportID("BIRRep");
        
        try {
            fxmlLoader.setController(instance);
            parent = fxmlLoader.load();
            Stage stage1 = new Stage();

            /*SET FORM MOVABLE*/
            parent.setOnMousePressed(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    xOffset = event.getSceneX();
                    yOffset = event.getSceneY();
                }
            });
            parent.setOnMouseDragged(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    stage1.setX(event.getScreenX() - xOffset);
                    stage1.setY(event.getScreenY() - yOffset);
                }
            });
            /*END SET FORM MOVABLE*/

            scene = new Scene(parent);
            stage1.initModality(Modality.APPLICATION_MODAL);
            stage1.initStyle(StageStyle.UNDECORATED);
            stage1.setAlwaysOnTop(true);
            stage1.setScene(scene);
            stage1.showAndWait();
        } catch (IOException e) {
            ShowMessageFX.Error(e.getMessage(), this.getClass().getSimpleName(), "Please inform MIS Department.");
            System.exit(1);
        }
        
        if (instance.isCancelled()) return false;
        
        System.setProperty("store.report.no", String.valueOf(instance.getEntryNox()));
        System.setProperty("store.default.debug", "true");
        System.setProperty("store.report.is_log", "true");
        System.setProperty("store.report.criteria.presentation", "");
        System.setProperty("store.report.criteria.group", "");
        System.setProperty("store.report.criteria.branch", "");
        System.setProperty("store.report.criteria.date", "");
        
        return true;
    }
    
    @Override
    public boolean processReport() {
        boolean bResult = false;
        
        //Load the jasper report to be use by this object
        String lsSQL = "SELECT sFileName, sReportHd" + 
                      " FROM xxxReportDetail" + 
                      " WHERE sReportID = " + SQLUtil.toSQL(System.getProperty("store.report.id")) +
                        " AND nEntryNox = " + SQLUtil.toSQL(System.getProperty("store.report.no"));
        
        //Check if in debug mode...
        if(System.getProperty("store.default.debug").equalsIgnoreCase("true")){
            System.out.println(System.getProperty("store.report.class") + ".processReport: " + lsSQL);
        }
        
        ResultSet loRS = _instance.executeQuery(lsSQL);
        
        try {
            if(!loRS.next()){
                _message = "Invalid report was detected...";
                closeReport();
                return false;
            }
            
            if (Integer.valueOf(System.getProperty("store.report.no")) == 9){
                return getEJournal();
            }
            
            System.setProperty("store.report.file", loRS.getString("sFileName"));
            System.setProperty("store.report.header", loRS.getString("sReportHd"));
            
            switch(Integer.valueOf(System.getProperty("store.report.no"))){
                case 1: //accumulated grand total
                    bResult = printAccGrandTotal(); break;
                case 2: //activity log
                    bResult = printActivityLog(); break;
                case 3: //BIR sales summary
                    bResult = printBIRSales(); break;
                case 4: //cancelled invoice
                    bResult = printCancelledInvoice(); break;
                case 5: //void transaction
                    bResult = printVoidTransactions(); break;
                case 6: //pos items
                    bResult = printPOSItems(); break;
                case 7: //sales report
                    bResult = printSalesReport();
                    break;
                case 8: //discounts
                    bResult = printDiscountedSalesReport();   
                    break;
                case 10: //accumulated sales summary
                    bResult = printSalesSummary();
            }
            
            if(bResult){
                if(System.getProperty("store.report.is_log").equalsIgnoreCase("true")){
                    logReport();
                }
                JasperViewer jv = new JasperViewer(_jrprint, false);     
                jv.setVisible(true);                
            }
        } catch (SQLException ex) {
            _message = ex.getMessage();
            
            if(System.getProperty("store.default.debug").equalsIgnoreCase("true")){ex.printStackTrace();}         
            GLogger.severe(System.getProperty("store.report.class"), "processReport", ExceptionUtils.getStackTrace(ex));
            
            closeReport();
            return false;
        }
        
        closeReport();
        return true;
    }

    @Override
    public void list() {
        _rptparam.forEach(item->System.out.println(item));
    }
    
    private boolean printSummary(){
        System.out.println("Printing Summary");
        return true;
    }
    
    private boolean printAccGrandTotal(){       
        try {
            ResultSet loRS;
            
            double lnCashTotl = 0.00;
            double lnCheckTtl = 0.00;
            double lnGCertTtl = 0.00;
            double lnCredtTtl = 0.00;
            double lnGrandTtl = 0.00;
            JSONObject json_obj;
            JSONArray json_arr = new JSONArray();
            json_arr.clear();             

            String lsSQL = "SELECT" + 
                        "  (a.nVATSales + a.nVATAmtxx) nSalesAmt" + 
                        ", a.nVATSales" + 
                        ", a.nVATAmtxx" +
                        ", a.nNonVATSl" +
                        ", a.nZroVATSl" +
                        ", (b.nTranTotl * b.nDiscount) + b.nAddDiscx nDiscount" +  
                        ", a.nCWTAmtxx" +
                        ", a.nAdvPaymx" +
                        ", a.nCashAmtx" +	
                        ", a.sSourceCd" +
                        ", a.sSourceNo" +
                        ", a.sORNumber" +
                        ", b.dTransact" +
                        ", a.sTransNox" +
                    " FROM  Receipt_Master a" +  
                        ", Sales_Master b" + 
                    " WHERE a.sSourceNo = b.sTransNox" + 
                        " AND a.sSourceCd = 'SL'" + 
                        " AND a.cTranStat <> '3'" +  
                        " AND a.sTransNox LIKE 'C%'" +
                    " ORDER BY a.sTransNox, b.dTransact ASC, a.sORNumber ASC" ;
            
            loRS = _instance.executeQuery(lsSQL);
            ResultSet loRSx;
            
            double lnSalesAmt = 0.00;
            double lnVATSales = 0.00;
            double lnVATAmtxx = 0.00;
            double lnZeroRatd = 0.00;
            double lnDiscount = 0.00;
            double lnPWDDiscx = 0.00;
            double lnVatDiscx = 0.00;
            
            //Add payment form computation
            double lnCashAmtx = 0.00;
            double lnChckAmnt = 0.00;
            double lnCrdtAmnt = 0.00;
            double lnGiftAmnt = 0.00;
            double lnChrgAmnt = 0.00;
            double lnVoidAmnt = 0.00;
            
            Date ldDateFrom = null;
            Date ldDateThru = null;
            
            String lsTerminal = "";
            ResultSet loTerminal;
            
            int lnCtr = 0;
            int lnRow = (int) MiscUtil.RecordCount(loRS) - 1;
            
            while(loRS.next()){                                
                lsTerminal = loRS.getString("sTransNox").substring(4, 6);
                
                if (ldDateFrom == null) {ldDateFrom = loRS.getDate("dTransact");}           
                
                ldDateThru = loRS.getDate("dTransact");
                
                lnSalesAmt = lnSalesAmt + loRS.getDouble("nSalesAmt");
                lnVATSales = lnVATSales + loRS.getDouble("nVATSales");
                lnVATAmtxx = lnVATAmtxx + loRS.getDouble("nVATAmtxx");
                lnZeroRatd = lnZeroRatd + loRS.getDouble("nZroVATSl");

                lnPWDDiscx = 0.00; //lnPWDDiscx + loRS.getDouble("nPWDDiscx");
                lnVatDiscx = 0.00; //lnVatDiscx + loRS.getDouble("nVatDiscx");
                lnDiscount = lnDiscount + loRS.getDouble("nDiscount");
                
                lsSQL = "SELECT a.cPaymForm, a.nAmountxx" +
                        " FROM Sales_Payment a" +
                            ", Receipt_Master b" +
                        " WHERE a.sSourceCd = 'ORec'" +
                            " AND a.sSourceNo = b.sTransNox" +
                            " AND a.sSourceNo = " + SQLUtil.toSQL(loRS.getString("sSourceNo")) +
                            " AND b.cTranStat <> '3'";
                
                loRSx = _instance.executeQuery(lsSQL);
                
                double lnOtherPaym = 0.00;
                while (loRSx.next()){
                    switch (loRSx.getString("cPaymForm")){
                        case "1":
                            lnChckAmnt += loRSx.getDouble("nAmountxx");
                            lnCheckTtl += loRSx.getDouble("nAmountxx");
                            //lnCheckTtl = lnChckAmnt + loRSx.getDouble("nAmountxx");
                            break;
                        case "2":
                            lnCrdtAmnt += loRSx.getDouble("nAmountxx");
                            lnCredtTtl += loRSx.getDouble("nAmountxx");
                            //lnCredtTtl += lnCrdtAmnt + loRSx.getDouble("nAmountxx");
                            break;
                        case "3":
                            lnGiftAmnt += loRSx.getDouble("nAmountxx");
                            lnGCertTtl += loRSx.getDouble("nAmountxx");
                            //lnGCertTtl += lnGiftAmnt + loRSx.getDouble("nAmountxx");
                    }
                    lnOtherPaym += loRSx.getDouble("nAmountxx");
                }
                
                //Add payment form computation
                lnCashTotl += loRS.getDouble("nSalesAmt") - lnOtherPaym;
                
                if (lnCtr == lnRow){
                    lnGrandTtl = lnCashTotl + lnCredtTtl+ lnCheckTtl + lnGCertTtl;
                    
                    //todo: assign values to json.
                    json_obj = new JSONObject();
                    json_obj.put("sField00", SQLUtil.dateFormat(ldDateFrom, SQLUtil.FORMAT_SHORT_DATE) + " to " + SQLUtil.dateFormat(ldDateThru, SQLUtil.FORMAT_SHORT_DATE));
                    json_obj.put("nField00", lnCashTotl);
                    json_obj.put("nField01", lnCredtTtl);                     
                    json_obj.put("nField02", lnCheckTtl);
                    json_obj.put("nField03", lnGCertTtl);
                    json_obj.put("nField04", lnGrandTtl);
                    
                    lsSQL = "SELECT * FROM Cash_Reg_Machine WHERE nPOSNumbr = " + lsTerminal;
                    loTerminal = _instance.executeQuery(lsSQL);
                    
                    if (loTerminal.next()) 
                        json_obj.put("sField01", loTerminal.getString("sIDNumber"));
                    else
                        json_obj.put("sField01", "");
                    
                    json_arr.add(json_obj);
                } else {
                    if (loRS.next()){
                        if (!lsTerminal.equals(loRS.getString("sTransNox").substring(4, 6))){
                            lnGrandTtl = lnCashTotl + lnCredtTtl+ lnCheckTtl + lnGCertTtl;
                            
                            //todo: assign values to json.
                            json_obj = new JSONObject();
                            json_obj.put("sField00", SQLUtil.dateFormat(ldDateFrom, SQLUtil.FORMAT_SHORT_DATE) + " to " + SQLUtil.dateFormat(ldDateThru, SQLUtil.FORMAT_SHORT_DATE));
                            json_obj.put("nField00", lnCashTotl);
                            json_obj.put("nField01", lnCredtTtl);                     
                            json_obj.put("nField02", lnCheckTtl);
                            json_obj.put("nField03", lnGCertTtl);
                            json_obj.put("nField04", lnGrandTtl);

                            lsSQL = "SELECT * FROM Cash_Reg_Machine WHERE nPOSNumbr = " + lsTerminal;
                            loTerminal = _instance.executeQuery(lsSQL);

                            if (loTerminal.next()) 
                                json_obj.put("sField01", loTerminal.getString("sIDNumber"));
                            else
                                json_obj.put("sField01", "");

                            json_arr.add(json_obj);
                            
                            lnCashTotl = 0.00;
                            lnCredtTtl = 0.00;
                            lnCheckTtl = 0.00;
                            lnGCertTtl = 0.00;
                            lnGrandTtl = 0.00;

                            ldDateFrom = null;
                            ldDateThru = null;
                        }
                        loRS.absolute(lnCtr + 1);
                    }
                }
                
                lnCtr += 1;
            }           
            //todo: assign json to jasper.
            Map<String, Object> params = new HashMap<>();
            params.put("sCompnyNm", _instance.getBranchName());  
            params.put("sAddressx", _instance.getAddress() + ", " + _instance.getTownName() + " " + _instance.getProvince());
            params.put("sTINNoxxx", System.getProperty("pos.clt.tin"));
            
            switch (_instance.getProductID().toLowerCase()) {
                case "integsys":
                    params.put("sProdctID", "IntegSysFX POS System");
                    break;
                case "telecom":
                    params.put("sProdctID", "TelecomFX POS System");
                    break;
                default:
                    params.put("sProdctID", "");
            }
            //params.put("sSerialNo", System.getProperty("pos.clt.srial.no"));
            //params.put("sMchineNo", System.getProperty("pos.clt.crm.no"));
            
            params.put("sPrintdBy", System.getProperty("user.name"));

            InputStream stream = new ByteArrayInputStream(json_arr.toJSONString().getBytes("UTF-8"));
            JsonDataSource jrjson = new JsonDataSource(stream); 

            _jrprint = JasperFillManager.fillReport(_instance.getReportPath() + 
                                                    System.getProperty("store.report.file"), params, jrjson);
        } catch (SQLException | JRException | UnsupportedEncodingException ex) {
            _message = ex.getMessage();
            if(System.getProperty("store.default.debug").equalsIgnoreCase("true")){ex.printStackTrace(); }         
            GLogger.severe(System.getProperty("store.report.class"), "printAccGrandTotal", ExceptionUtils.getStackTrace(ex));
            return false;
        }
        
        return true;
    }
    
    private boolean printActivityLog(){
        try {
            String lsSQL = "SELECT * FROM Cash_Reg_Machine WHERE dExpiratn >= " + SQLUtil.toSQL(_instance.getServerDate());
            ResultSet rs = _instance.executeQuery(lsSQL);

            if (MiscUtil.RecordCount(rs) == 0){
                _message = "No record found.";
                ShowMessageFX.Warning(_message, "Notice", null);
                return false;
            }

            JSONObject loJSON = showFXDialog.jsonBrowse(_instance, rs, "MIN»Permit No»Terminal»Serial", "sIDNumber»sPermitNo»nPOSNumbr»sSerialNo");

            if (loJSON == null){
                System.setProperty("pos.clt.accrd.no", "");
                System.setProperty("pos.clt.prmit.no", "");
                System.setProperty("pos.clt.srial.no", "");
                System.setProperty("pos.clt.trmnl.no", "");
                System.setProperty("pos.clt.zcounter", "");
                return false;
            } else {
                System.setProperty("pos.clt.accrd.no", (String) loJSON.get("sAccredtn"));
                System.setProperty("pos.clt.prmit.no", (String) loJSON.get("sPermitNo"));
                System.setProperty("pos.clt.srial.no", (String) loJSON.get("sSerialNo"));
                System.setProperty("pos.clt.trmnl.no", (String) loJSON.get("nPOSNumbr"));
                System.setProperty("pos.clt.zcounter", (String) loJSON.get("nZReadCtr"));
            }               
            
            lsSQL = "SELECT DISTINCT(sCashierx) sCashierx FROM Daily_Summary";
            
            lsSQL = "SELECT" + 
                        "  a.sUserIDxx" +
                        ", b.sClientNm" +
                    " FROM xxxSysUser a" +
                        ", Client_Master b" +
                    " WHERE a.sEmployNo = b.sClientID" +
                        " AND a.sUserIDxx IN (" + lsSQL + ")";
            
            rs = _instance.executeQuery(lsSQL);
            
            if (MiscUtil.RecordCount(rs) == 0){
                _message = "No record found.";
                ShowMessageFX.Warning(_message, "Notice", null);
                return false;
            }

            loJSON = showFXDialog.jsonBrowse(_instance, rs, "User ID»Cashier", "sUserIDxx»sClientNm");
            
            if (loJSON == null){
                System.setProperty("pos.clt.user.id", "");
            } else
                System.setProperty("pos.clt.user.id", (String) loJSON.get("sUserIDxx"));
            
            lsSQL = getSQ_ActivityLog();
            
            if (!System.getProperty("pos.clt.user.id").isEmpty()) lsSQL = MiscUtil.addCondition(lsSQL, "a.sUserIDxx = " + SQLUtil.toSQL(System.getProperty("pos.clt.user.id")));
            
            String lsOldTrans = "";
            String lsEventNo;
            String lsEventDesc;
            String lsRemarks;
            String lsMachineNo;
            String lsUsername;
            String lsDateTime;
            
            rs = _instance.executeQuery(lsSQL);
            
            if (MiscUtil.RecordCount(rs) <= 0){
                _message = "No record found.";
                ShowMessageFX.Warning(_message, "Notice", null);
                return false;
            }
            
            JSONObject json_obj;
            JSONArray json_arr = new JSONArray();
            json_arr.clear();
            
            while(rs.next()){
                if (!lsOldTrans.equals(rs.getString("sEventIDx"))){
                    lsOldTrans = rs.getString("sEventDsc");
                    //detail
                    lsEventNo = rs.getString("sEventIDx");
                    lsEventDesc = rs.getString("sEventDsc");
                    lsRemarks = rs.getString("sNotesxxx");
                    lsMachineNo = rs.getString("sMachinex");
                    lsUsername = _instance.Decrypt(rs.getString("sLogNamex")) + "/" + rs.getString("sUserName");
                    lsDateTime = rs.getString("dModified");
                    
                    //todo: assign values to json.
                        json_obj = new JSONObject();
                        json_obj.put("sField00", lsEventNo);
                        json_obj.put("sField01", lsEventDesc);                     
                        json_obj.put("sField02", lsRemarks);
                        json_obj.put("sField03", lsMachineNo);
                        json_obj.put("sField04", lsUsername);
                        json_obj.put("sField05", lsDateTime);
                        json_arr.add(json_obj);
                }
            }
            //todo: assign json to jasper.
            Map<String, Object> params = new HashMap<>();
            params.put("sCompnyNm", _instance.getBranchName());  
            params.put("sAddressx", _instance.getAddress() + ", " + _instance.getTownName() + " " + _instance.getProvince());
            
            params.put("sPrintdBy", System.getProperty("user.name"));

            InputStream stream = new ByteArrayInputStream(json_arr.toJSONString().getBytes("UTF-8"));
            JsonDataSource jrjson = new JsonDataSource(stream); 

            _jrprint = JasperFillManager.fillReport(_instance.getReportPath() + 
                                                    System.getProperty("store.report.file"), params, jrjson);
            } catch (SQLException | JRException | UnsupportedEncodingException ex) {
                _message = ex.getMessage();
                if(System.getProperty("store.default.debug").equalsIgnoreCase("true")){ex.printStackTrace(); }         
                GLogger.severe(System.getProperty("store.report.class"), "printActivitylog", ExceptionUtils.getStackTrace(ex));
                return false;
            }        
        return true;
    }
    
    private boolean printPOSItems(){
        try {
            String lsSQL = getSQ_Inventory();
            String lsOldTrans = "";
            
            String lsItemCode;
            String lsDescription;
            String lsBriefDesc;
            String lsSellPrce;
            String lsStatus;
            
            ResultSet rs = _instance.executeQuery(lsSQL);
            
            JSONObject json_obj;
            JSONArray json_arr = new JSONArray();
            json_arr.clear();
            
            while(rs.next()){
                if (!lsOldTrans.equals(rs.getString("sEventIDx"))){
                    lsOldTrans = rs.getString("sEventDsc");
                    //detail
                    lsItemCode = rs.getString("");
                    lsDescription = rs.getString("");
                    lsBriefDesc = rs.getString("");
                    lsSellPrce = rs.getString("");
                    lsStatus = rs.getString("");
                    
                    //todo: assign values to json.
                        json_obj = new JSONObject();
                        json_obj.put("sField00", lsItemCode);
                        json_obj.put("sField01", lsDescription);                     
                        json_obj.put("sField02", lsBriefDesc);
                        json_obj.put("nField00", lsSellPrce);
                        json_obj.put("sField03", lsStatus);
                        json_arr.add(json_obj);
                }
            }
            //todo: assign json to jasper.
            Map<String, Object> params = new HashMap<>();
            params.put("sCompnyNm", _instance.getBranchName());  
            params.put("sAddressx", _instance.getAddress() + ", " + _instance.getTownName() + " " + _instance.getProvince());
            
            params.put("sPrintdBy", System.getProperty("user.name"));

            InputStream stream = new ByteArrayInputStream(json_arr.toJSONString().getBytes("UTF-8"));
            JsonDataSource jrjson = new JsonDataSource(stream); 

            _jrprint = JasperFillManager.fillReport(_instance.getReportPath() + 
                                                    System.getProperty("store.report.file"), params, jrjson);
        } catch (SQLException | JRException | UnsupportedEncodingException ex) {
            _message = ex.getMessage();
            if(System.getProperty("store.default.debug").equalsIgnoreCase("true")){ex.printStackTrace(); }         
            GLogger.severe(System.getProperty("store.report.class"), "printPOSItems", ExceptionUtils.getStackTrace(ex));
            return false;
        }
        return true;
    }
    
    private boolean printCancelledInvoice(){
        try {
            String lsSQL = "SELECT * FROM Cash_Reg_Machine WHERE dExpiratn >= " + SQLUtil.toSQL(_instance.getServerDate());
            ResultSet rs = _instance.executeQuery(lsSQL);

            if (MiscUtil.RecordCount(rs) == 0){
                _message = "No record found.";
                ShowMessageFX.Warning(_message, "Notice", null);
                return false;
            }

            JSONObject loJSON = showFXDialog.jsonBrowse(_instance, rs, "MIN»Permit No»Terminal»Serial", "sIDNumber»sPermitNo»nPOSNumbr»sSerialNo");

            if (loJSON == null){
                System.setProperty("pos.clt.accrd.no", "");
                System.setProperty("pos.clt.prmit.no", "");
                System.setProperty("pos.clt.srial.no", "");
                System.setProperty("pos.clt.trmnl.no", "");
                System.setProperty("pos.clt.zcounter", "");
                return false;
            } else {
                System.setProperty("pos.clt.accrd.no", (String) loJSON.get("sAccredtn"));
                System.setProperty("pos.clt.prmit.no", (String) loJSON.get("sPermitNo"));
                System.setProperty("pos.clt.srial.no", (String) loJSON.get("sSerialNo"));
                System.setProperty("pos.clt.trmnl.no", (String) loJSON.get("nPOSNumbr"));
                System.setProperty("pos.clt.zcounter", (String) loJSON.get("nZReadCtr"));
            }
            
            lsSQL = MiscUtil.addCondition(getSQ_Sales(), "a.cTranStat = '3'");
            String lsOldTrans = "";
            String lsORNumber = "";
            String lsSalesman = "";
            String lsRemarksx = "";
            
            double lnSubTotal = 0.00;
            double lnMasDiscx = 0.00;
            double lnTotlDisc = 0.00;
            double lnTranTotl = 0.00;
            double lnVATSales = 0.00;
            double lnVATAmntx = 0.00;
            double lnVATExmpt = 0.00;
            double lnZroRated = 0.00;
            double lnRegDiscx = 0.00;
            
            String lsBarCodex = "";
            String lsDescript = "";
            String lsSerial01 = "";
            String lsSerial02 = "";
            
            int lnQuantity = 0;
            double lnUnitPrce = 0.00;
            double lnDiscount = 0.00;
            double lnPWDDiscx = 0.00;
            
            int lnRow = 0;
            int lnCtr = 0;
            
            if (!getDateParam()) return false;
            
            String lsDate = "";
            if (!System.getProperty("store.report.criteria.datefrom").equals("") &&
                !System.getProperty("store.report.criteria.datethru").equals("")){

                lsDate = SQLUtil.toSQL(System.getProperty("store.report.criteria.datefrom") + " 00:00:00") + " AND " +
                            SQLUtil.toSQL(System.getProperty("store.report.criteria.datethru") + " 23:59:30");

                lsSQL = MiscUtil.addCondition(lsSQL, "a.dTransact BETWEEN " + lsDate);
            }
            
            rs = _instance.executeQuery(lsSQL);
            lnRow = (int) MiscUtil.RecordCount(rs);
            
            if (lnRow == 0) {
                _message = "No record found for the given criteria.";
                ShowMessageFX.Warning(_message, "Notice", null);
                return false;
            }
            
            ResultSet rsMas;
            
            JSONObject json_obj;
            JSONArray json_arr = new JSONArray();
            json_arr.clear();
            
            rs.beforeFirst();
            while(rs.next()){
                lnCtr += 1;
                if (!lsOldTrans.equals(rs.getString("sTransNox"))){
                    lsOldTrans = rs.getString("sTransNox");
                    
                    lnMasDiscx = 0.00;
                    lnTotlDisc = 0.00;
                    lnSubTotal = 0.00;
                    lnTranTotl = 0.00;
                }
                
                //detail
                lsBarCodex = rs.getString("sBarCodex");
                lsDescript = rs.getString("sDescript");
                lsSerial01 = rs.getString("sSerial01");
                lsSerial02 = rs.getString("sSerial02");

                lnQuantity = rs.getInt("nQuantity");
                lnUnitPrce = rs.getDouble("nUnitPrce");
                lnDiscount = rs.getDouble("nTotlDisc");
                lnPWDDiscx = 0.00;
                //end of detail

                //master
                lsSQL = MiscUtil.addCondition(getSQ_SaleInvoice(), "b.sSourceNo = " + SQLUtil.toSQL(lsOldTrans));
                rsMas = _instance.executeQuery(lsSQL);

                if (rsMas.next()){
                    lsORNumber = rsMas.getString("sORNumber");
                    lnVATSales = rsMas.getDouble("nVATSales"); //lnTranTotl / nVATRatex
                    lnVATAmntx = rsMas.getDouble("nVATAmtxx"); //(lnTranTotl / nVATRatex) * (nVATRatex - 1)
                    lnVATExmpt = 0.00; //default is zero since we are not issuing sc/pwd discount
                    lnZroRated = 0.00; //default is zero since we are not issuing zero rated sales
                    lsSalesman = rsMas.getString("sCashierx"); //cashier name
                    
                    if (lnCtr < lnRow) {
                        rs.next(); //move to next record to get the transaction number
                        
                        if (lsOldTrans.equals(rs.getString("sTransNox"))){
                            lnSubTotal += lnDiscount; //temporary store the running detail discounts here
                        } else {
                            lnMasDiscx = rsMas.getDouble("nTotlDisc");
                            lnTotlDisc = lnDiscount + lnSubTotal;
                            
                            //add freight charge, transaction total and the total discount
                            lnSubTotal = lnTotlDisc + rsMas.getDouble("nTranTotl") + rsMas.getDouble("nFreightx"); 
                            
                            lnTranTotl = lnSubTotal - lnTotlDisc - lnMasDiscx;
                        }
                        
                        rs.previous(); //move to previous record
                    } else {
                        lnMasDiscx = rsMas.getDouble("nTotlDisc");
                        lnTotlDisc = lnDiscount + lnSubTotal;
                            
                        //add freight charge, transaction total and the total discount
                        lnSubTotal = lnTotlDisc + rsMas.getDouble("nTranTotl") + rsMas.getDouble("nFreightx"); 

                        lnTranTotl = lnSubTotal - lnTotlDisc - lnMasDiscx;
                    }
                } else {
                    _message = "Transaction discrepancy detected.";
                    return false;
                }               

                json_obj = new JSONObject();
                json_obj.put("sField00", System.getProperty("store.report.criteria.datefrom") + " to " + System.getProperty("store.report.criteria.datethru"));
                json_obj.put("sField02", lsORNumber);                     
                json_obj.put("sField03", lsDescript);
                json_obj.put("sField04", lsRemarksx);
                json_obj.put("sField05", lsSalesman);
                json_obj.put("sField06", lsBarCodex);
                json_obj.put("sField07", lsSerial01);
                json_obj.put("sField08", lsSerial02);
                json_obj.put("nField00", lnQuantity); //Quantity
                json_obj.put("nField01", lnUnitPrce); //Amount
                json_obj.put("nField02", lnDiscount); //Discount
                json_obj.put("nField03", ((lnQuantity * lnUnitPrce) - lnDiscount) / 1.12);
                json_obj.put("nField12", (((lnQuantity * lnUnitPrce) - lnDiscount) / 1.12) * 0.12);

                json_obj.put("nField04", lnSubTotal);
                json_obj.put("nField05", lnTotlDisc);
                json_obj.put("nField06", lnTranTotl);
                json_obj.put("nField07", lnVATSales);
                json_obj.put("nField08", lnVATAmntx);
                json_obj.put("nField09", lnVATExmpt);
                json_obj.put("nField10", lnZroRated);
                json_obj.put("nField11", lnMasDiscx);

                json_arr.add(json_obj);
            }
            
            Map<String, Object> params = new HashMap<>();
            params.put("sCompnyNm", _instance.getBranchName());  
            params.put("sAddressx", _instance.getAddress() + ", " + _instance.getTownName() + " " + _instance.getProvince());  
            params.put("sTINNoxxx", System.getProperty("pos.clt.tin"));
            
            switch (_instance.getProductID().toLowerCase()) {
                case "integsys":
                    params.put("sProdctID", "IntegSysFX POS System");
                    break;
                case "telecom":
                    params.put("sProdctID", "TelecomFX POS System");
                    break;
                default:
                    params.put("sProdctID", "");
            }
            params.put("sSerialNo", System.getProperty("pos.clt.srial.no"));
            params.put("sMchineNo", System.getProperty("pos.clt.crm.no"));
            
            params.put(lsSalesman, System.getProperty("user.name"));
            
            params.put("sPrintdBy", System.getProperty("user.name"));

            InputStream stream = new ByteArrayInputStream(json_arr.toJSONString().getBytes("UTF-8"));
            JsonDataSource jrjson = new JsonDataSource(stream); 

            _jrprint = JasperFillManager.fillReport(_instance.getReportPath() + 
                                                    System.getProperty("store.report.file"), params, jrjson);
        } catch (SQLException | JRException | UnsupportedEncodingException ex) {
            _message = ex.getMessage();
            if(System.getProperty("store.default.debug").equalsIgnoreCase("true")){ex.printStackTrace();}         
            GLogger.severe(System.getProperty("store.report.class"), "printBIRSales", ExceptionUtils.getStackTrace(ex));
            return false;
        }
        
        return true;
    }
    
    private boolean printVoidTransactions(){
         try {
            String lsSQL = "SELECT * FROM Cash_Reg_Machine WHERE dExpiratn >= " + SQLUtil.toSQL(_instance.getServerDate());
            ResultSet rs = _instance.executeQuery(lsSQL);

            if (MiscUtil.RecordCount(rs) == 0){
                _message = "No record found.";
                ShowMessageFX.Warning(_message, "Notice", null);
                return false;
            }

            JSONObject loJSON = showFXDialog.jsonBrowse(_instance, rs, "MIN»Permit No»Terminal»Serial", "sIDNumber»sPermitNo»nPOSNumbr»sSerialNo");

            if (loJSON == null){
                System.setProperty("pos.clt.accrd.no", "");
                System.setProperty("pos.clt.prmit.no", "");
                System.setProperty("pos.clt.srial.no", "");
                System.setProperty("pos.clt.trmnl.no", "");
                System.setProperty("pos.clt.zcounter", "");
                return false;
            } else {
                System.setProperty("pos.clt.accrd.no", (String) loJSON.get("sAccredtn"));
                System.setProperty("pos.clt.prmit.no", (String) loJSON.get("sPermitNo"));
                System.setProperty("pos.clt.srial.no", (String) loJSON.get("sSerialNo"));
                System.setProperty("pos.clt.trmnl.no", (String) loJSON.get("nPOSNumbr"));
                System.setProperty("pos.clt.zcounter", (String) loJSON.get("nZReadCtr"));
            }
             
            lsSQL = MiscUtil.addCondition(getSQ_Sales(), "a.cTranStat = '4'");
            String lsOldTrans = "";
            String lsSalesman = "";
            String lsRemarksx = "";
            
            double lnSubTotal = 0.00;
            double lnMasDiscx = 0.00;
            double lnTotlDisc = 0.00;
            double lnTranTotl = 0.00;
            double lnRegDiscx = 0.00;
            
            String lsBarCodex = "";
            String lsDescript = "";
            String lsSerial01 = "";
            String lsSerial02 = "";
            
            int lnQuantity = 0;
            double lnUnitPrce = 0.00;
            double lnDiscount = 0.00;
            double lnPWDDiscx = 0.00;
            
            int lnRow = 0;
            int lnCtr = 0;
            
            if (!getDateParam()) return false;
            
            String lsDate = "";
            if (!System.getProperty("store.report.criteria.datefrom").equals("") &&
                !System.getProperty("store.report.criteria.datethru").equals("")){

                lsDate = SQLUtil.toSQL(System.getProperty("store.report.criteria.datefrom") + " 00:00:00") + " AND " +
                            SQLUtil.toSQL(System.getProperty("store.report.criteria.datethru") + " 23:59:30");

                lsSQL = MiscUtil.addCondition(lsSQL, "a.dTransact BETWEEN " + lsDate);
            }
            
            rs = _instance.executeQuery(lsSQL);
            lnRow = (int) MiscUtil.RecordCount(rs);
            
            if (lnRow <= 0) {
                _message = "No record found for the given criteria.";
                ShowMessageFX.Warning(_message, "Notice", null);
                return false;
            }
            
            JSONObject json_obj;
            JSONArray json_arr = new JSONArray();
            json_arr.clear();
            
            rs.beforeFirst();
            while(rs.next()){
                if (!lsOldTrans.equals(rs.getString("sTransNox"))){
                    lsOldTrans = rs.getString("sTransNox");
                    
                    lnMasDiscx = 0.00;
                    lnTotlDisc = 0.00;
                    lnSubTotal = 0.00;
                    lnTranTotl = 0.00;
                }
                //detail
                lsBarCodex = rs.getString("sBarCodex");
                lsDescript = rs.getString("sDescript");
                lsSerial01 = rs.getString("sSerial01");
                lsSerial02 = rs.getString("sSerial02");

                lnQuantity = rs.getInt("nQuantity");
                lnUnitPrce = rs.getDouble("nUnitPrce");
                lnDiscount = rs.getDouble("nTotlDisc");
                lnPWDDiscx = 0.00;
                //end of detail
                    
                lnMasDiscx = rs.getDouble("nMasDisc");
                lnTotlDisc = lnDiscount + lnSubTotal;

                //add freight charge, transaction total and the total discount
                lnSubTotal = lnTotlDisc + rs.getDouble("nTranTotl"); 

                lnTranTotl = lnSubTotal - lnTotlDisc - lnMasDiscx;  

                json_obj = new JSONObject();
                json_obj.put("sField00", System.getProperty("store.report.criteria.datefrom") + " to " + System.getProperty("store.report.criteria.datethru"));
                json_obj.put("sField02", lsOldTrans);                     
                json_obj.put("sField03", lsDescript);
                json_obj.put("sField04", lsRemarksx);
                json_obj.put("sField05", lsSalesman);
                json_obj.put("sField06", lsBarCodex);
                json_obj.put("sField07", lsSerial01);
                json_obj.put("sField08", lsSerial02);
                
                json_obj.put("nField00", lnQuantity); //Quantity
                json_obj.put("nField01", lnUnitPrce); //Amount
                json_obj.put("nField02", lnDiscount); //Discount
                json_obj.put("nField03", lnPWDDiscx); //PWDDiscount

                json_obj.put("nField05", lnSubTotal);
                json_obj.put("nField06", lnTotlDisc);
                json_obj.put("nField04", lnTranTotl);
                json_obj.put("nField07", lnMasDiscx);

                json_arr.add(json_obj);
            }
            
            Map<String, Object> params = new HashMap<>();
            params.put("sCompnyNm", _instance.getBranchName());  
            params.put("sAddressx", _instance.getAddress() + ", " + _instance.getTownName() + " " + _instance.getProvince());  
            params.put("sTINNoxxx", System.getProperty("pos.clt.tin"));
            
            switch (_instance.getProductID().toLowerCase()) {
                case "integsys":
                    params.put("sProdctID", "IntegSysFX POS System");
                    break;
                case "telecom":
                    params.put("sProdctID", "TelecomFX POS System");
                    break;
                default:
                    params.put("sProdctID", "");
            }
            params.put("sSerialNo", System.getProperty("pos.clt.srial.no"));
            params.put("sMchineNo", System.getProperty("pos.clt.crm.no"));
            
            params.put(lsSalesman, System.getProperty("user.name"));
            
            params.put("sPrintdBy", System.getProperty("user.name"));

            InputStream stream = new ByteArrayInputStream(json_arr.toJSONString().getBytes("UTF-8"));
            JsonDataSource jrjson = new JsonDataSource(stream); 

            _jrprint = JasperFillManager.fillReport(_instance.getReportPath() + 
                                                    System.getProperty("store.report.file"), params, jrjson);
        } catch (SQLException | JRException | UnsupportedEncodingException ex) {
            _message = ex.getMessage();
            if(System.getProperty("store.default.debug").equalsIgnoreCase("true")){ex.printStackTrace();}         
            GLogger.severe(System.getProperty("store.report.class"), "printBIRSales", ExceptionUtils.getStackTrace(ex));
            return false;
        }
        
        return true;
    }
    
    private boolean getDateParam(){
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("DateCriteria.fxml"));
        fxmlLoader.setLocation(getClass().getResource("DateCriteria.fxml"));

        DateCriteriaController instance = new DateCriteriaController();
        instance.singleDayOnly(false);
        
        try {
            
            fxmlLoader.setController(instance);
            Parent parent = fxmlLoader.load();
            Stage stage = new Stage();

            /*SET FORM MOVABLE*/
            parent.setOnMousePressed(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    xOffset = event.getSceneX();
                    yOffset = event.getSceneY();
                }
            });
            parent.setOnMouseDragged(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    stage.setX(event.getScreenX() - xOffset);
                    stage.setY(event.getScreenY() - yOffset);
                }
            });
            /*END SET FORM MOVABLE*/

            Scene scene = new Scene(parent);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.UNDECORATED);
            stage.setAlwaysOnTop(true);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (IOException e) {
            ShowMessageFX.Error(e.getMessage(), BIRReports.class.getSimpleName(), "Please inform MIS Department.");
            System.exit(1);
        }
        
        if (!instance.isCancelled()){            
            System.setProperty("store.default.debug", "true");
            System.setProperty("store.report.criteria.datefrom", instance.getDateFrom());
            System.setProperty("store.report.criteria.datethru", instance.getDateTo());
            
            System.setProperty("store.report.criteria.presentation", "");
            System.setProperty("store.report.criteria.supplier", "");
            System.setProperty("store.report.criteria.branch", "");
            System.setProperty("store.report.criteria.group", "");
            return true;
        }
        
        return false;
    }
    
    private boolean getEJournal(){       
        try {
            String lsSQL = "SELECT * FROM Cash_Reg_Machine WHERE dExpiratn >= " + SQLUtil.toSQL(_instance.getServerDate());
            ResultSet rs = _instance.executeQuery(lsSQL);

            if (MiscUtil.RecordCount(rs) == 0){
                _message = "No record found.";
                ShowMessageFX.Warning(_message, "Notice", null);
                return false;
            }

            JSONObject loJSON = showFXDialog.jsonBrowse(_instance, rs, "MIN»Permit No»Terminal»Serial", "sIDNumber»sPermitNo»nPOSNumbr»sSerialNo");

            if (loJSON == null){
                System.setProperty("pos.clt.accrd.no", "");
                System.setProperty("pos.clt.prmit.no", "");
                System.setProperty("pos.clt.srial.no", "");
                System.setProperty("pos.clt.trmnl.no", "");
                System.setProperty("pos.clt.zcounter", "");
                return false;
            } else {
                System.setProperty("pos.clt.accrd.no", (String) loJSON.get("sAccredtn"));
                System.setProperty("pos.clt.prmit.no", (String) loJSON.get("sPermitNo"));
                System.setProperty("pos.clt.srial.no", (String) loJSON.get("sSerialNo"));
                System.setProperty("pos.clt.trmnl.no", (String) loJSON.get("nPOSNumbr"));
                System.setProperty("pos.clt.zcounter", (String) loJSON.get("nZReadCtr"));
            } 

            lsSQL = "SELECT * FROM Cash_Reg_Machine WHERE nPOSNumbr = " + System.getProperty("pos.clt.trmnl.no");
            ResultSet loRS = _instance.executeQuery(lsSQL);
            
            if (loRS.next())
                System.setProperty("pos.clt.crm.no", loRS.getString("sIDNumber"));
            else{
                _message = "Terminal is not on record.";
                ShowMessageFX.Warning(_message, "Notice", null);
                return false;
            }
            
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("EJournal.fxml"));
            fxmlLoader.setLocation(getClass().getResource("EJournal.fxml"));

            EJournalController instance = new EJournalController();
            instance.singleDayOnly(false);
 
            fxmlLoader.setController(instance);
            Parent parent = fxmlLoader.load();
            Stage stage = new Stage();

            /*SET FORM MOVABLE*/
            parent.setOnMousePressed(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    xOffset = event.getSceneX();
                    yOffset = event.getSceneY();
                }
            });
            parent.setOnMouseDragged(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent event) {
                    stage.setX(event.getScreenX() - xOffset);
                    stage.setY(event.getScreenY() - yOffset);
                }
            });
            /*END SET FORM MOVABLE*/

            Scene scene = new Scene(parent);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initStyle(StageStyle.UNDECORATED);
            stage.setAlwaysOnTop(true);
            stage.setScene(scene);
            stage.showAndWait();
        } catch (IOException | SQLException e) {
            ShowMessageFX.Error(e.getMessage(), BIRReports.class.getSimpleName(), "Please inform MIS Department.");
            System.exit(1);
        }
        
        return true;
    }
    
    private boolean printSalesReport(){
        try {          
            String lsSQL = "SELECT * FROM Cash_Reg_Machine WHERE dExpiratn >= " + SQLUtil.toSQL(_instance.getServerDate());
            ResultSet rs = _instance.executeQuery(lsSQL);

            if (MiscUtil.RecordCount(rs) == 0){
                _message = "No record found.";
                ShowMessageFX.Warning(_message, "Notice", null);
                return false;
            }

            JSONObject loJSON = showFXDialog.jsonBrowse(_instance, rs, "MIN»Permit No»Terminal»Serial", "sIDNumber»sPermitNo»nPOSNumbr»sSerialNo");

            if (loJSON == null){
                System.setProperty("pos.clt.accrd.no", "");
                System.setProperty("pos.clt.prmit.no", "");
                System.setProperty("pos.clt.srial.no", "");
                System.setProperty("pos.clt.trmnl.no", "");
                System.setProperty("pos.clt.zcounter", "");
                return false;
            } else {
                System.setProperty("pos.clt.accrd.no", (String) loJSON.get("sAccredtn"));
                System.setProperty("pos.clt.prmit.no", (String) loJSON.get("sPermitNo"));
                System.setProperty("pos.clt.srial.no", (String) loJSON.get("sSerialNo"));
                System.setProperty("pos.clt.trmnl.no", (String) loJSON.get("nPOSNumbr"));
                System.setProperty("pos.clt.zcounter", (String) loJSON.get("nZReadCtr"));
            }
            
            lsSQL = MiscUtil.addCondition(getSQ_Sales(), "a.cTranStat = '1'");
            String lsOldTrans = "";
            String lsORNumber = "";
            String lsSalesman = "";
            String lsRemarksx = "";
            String lsTransNox = "";
            
            double lnSubTotal = 0.00;
            double lnMasDiscx = 0.00;
            double lnTotlDisc = 0.00;
            double lnTranTotl = 0.00;
            double lnVATSales = 0.00;
            double lnVATAmntx = 0.00;
            double lnVATExmpt = 0.00;
            double lnZroRated = 0.00;
            double lnRegDiscx = 0.00;
            
            String lsBarCodex = "";
            String lsDescript = "";
            String lsSerial01 = "";
            String lsSerial02 = "";
            
            int lnQuantity = 0;
            double lnUnitPrce = 0.00;
            double lnDiscount = 0.00;
            double lnPWDDiscx = 0.00;
            
            int lnRow = 0;
            int lnCtr = 0;
            
            if (!getDateParam()) return false;
            
            String lsDate = "";
            if (!System.getProperty("store.report.criteria.datefrom").equals("") &&
                !System.getProperty("store.report.criteria.datethru").equals("")){

                lsDate = SQLUtil.toSQL(System.getProperty("store.report.criteria.datefrom") + " 00:00:00") + " AND " +
                            SQLUtil.toSQL(System.getProperty("store.report.criteria.datethru") + " 23:59:30");

                lsSQL = MiscUtil.addCondition(lsSQL, "a.dTransact BETWEEN " + lsDate);
            }
            
            rs = _instance.executeQuery(lsSQL);
            lnRow = (int) MiscUtil.RecordCount(rs);
            
            if (lnRow <= 0) {
                _message = "No record found for the given criteria.";
                ShowMessageFX.Warning(_message, "Notice", null);
                return false;
            }
            
            ResultSet rsMas;
            
            JSONObject json_obj;
            JSONArray json_arr = new JSONArray();
            json_arr.clear();
            
            rs.beforeFirst();
            while(rs.next()){
                lnCtr += 1;
                if (!lsOldTrans.equals(rs.getString("sTransNox"))){
                    lsOldTrans = rs.getString("sTransNox");
                    
                    lnMasDiscx = 0.00;
                    lnTotlDisc = 0.00;
                    lnSubTotal = 0.00;
                    lnTranTotl = 0.00;
                }
                
                //detail
                lsBarCodex = rs.getString("sBarCodex");
                lsDescript = rs.getString("sDescript");
                lsSerial01 = rs.getString("sSerial01");
                lsSerial02 = rs.getString("sSerial02");

                lnQuantity = rs.getInt("nQuantity");
                lnUnitPrce = rs.getDouble("nUnitPrce");
                lnDiscount = rs.getDouble("nTotlDisc");
                lnPWDDiscx = 0.00;
                //end of detail

                //master
                lsSQL = MiscUtil.addCondition(getSQ_SaleInvoice(), "b.sSourceNo = " + SQLUtil.toSQL(lsOldTrans));
                rsMas = _instance.executeQuery(lsSQL);

                if (rsMas.next()){
                    lsTransNox = rsMas.getString("xTransNox");
                    lsORNumber = rsMas.getString("sORNumber");
                    lnVATSales = rsMas.getDouble("nVATSales"); //lnTranTotl / nVATRatex
                    lnVATAmntx = rsMas.getDouble("nVATAmtxx"); //(lnTranTotl / nVATRatex) * (nVATRatex - 1)
                    lnVATExmpt = 0.00; //default is zero since we are not issuing sc/pwd discount
                    lnZroRated = 0.00; //default is zero since we are not issuing zero rated sales
                    lsSalesman = rsMas.getString("sCashierx"); //cashier name
                    
                    if (lnCtr < lnRow) {
                        rs.next(); //move to next record to get the transaction number
                        
                        if (lsOldTrans.equals(rs.getString("sTransNox"))){
                            lnSubTotal += lnDiscount; //temporary store the running detail discounts here
                        } else {
                            lnMasDiscx = rsMas.getDouble("nTotlDisc");
                            lnTotlDisc = lnDiscount + lnSubTotal;
                            
                            //add freight charge, transaction total and the total discount
                            lnSubTotal = lnTotlDisc + rsMas.getDouble("nTranTotl") + rsMas.getDouble("nFreightx"); 
                            
                            lnTranTotl = lnSubTotal - lnTotlDisc - lnMasDiscx;
                        }
                        
                        rs.previous(); //move to previous record
                    } else {
                        lnMasDiscx = rsMas.getDouble("nTotlDisc");
                        lnTotlDisc = lnDiscount + lnSubTotal;
                            
                        //add freight charge, transaction total and the total discount
                        lnSubTotal = lnTotlDisc + rsMas.getDouble("nTranTotl") + rsMas.getDouble("nFreightx"); 

                        lnTranTotl = lnSubTotal - lnTotlDisc - lnMasDiscx;
                    }
                } else {
                    _message = "Transaction discrepancy detected.";
                    return false;
                }               

                json_obj = new JSONObject();
                json_obj.put("sField00", System.getProperty("store.report.criteria.datefrom") + " to " + System.getProperty("store.report.criteria.datethru"));
                json_obj.put("sField02", lsORNumber);                     
                json_obj.put("sField03", lsDescript);
                json_obj.put("sField04", lsRemarksx);
                json_obj.put("sField05", lsSalesman);
                json_obj.put("sField06", lsBarCodex);
                json_obj.put("sField07", lsSerial01);
                json_obj.put("sField08", lsSerial02);
                json_obj.put("sField09", lsTransNox);
                json_obj.put("nField00", lnQuantity); //Quantity
                json_obj.put("nField01", lnUnitPrce); //Amount
                json_obj.put("nField02", lnDiscount); //Discount
                json_obj.put("nField03", ((lnQuantity * lnUnitPrce) - lnDiscount) / 1.12);
                json_obj.put("nField12", (((lnQuantity * lnUnitPrce) - lnDiscount) / 1.12) * 0.12);

                json_obj.put("nField04", lnSubTotal);
                json_obj.put("nField05", lnTotlDisc);
                json_obj.put("nField06", lnTranTotl);
                json_obj.put("nField07", lnVATSales);
                json_obj.put("nField08", lnVATAmntx);
                json_obj.put("nField09", lnVATExmpt);
                json_obj.put("nField10", lnZroRated);
                json_obj.put("nField11", lnMasDiscx);

                json_arr.add(json_obj);
            }
            
            Map<String, Object> params = new HashMap<>();
            params.put("sCompnyNm", _instance.getBranchName());  
            params.put("sAddressx", _instance.getAddress() + ", " + _instance.getTownName() + " " + _instance.getProvince());  
            params.put("sTINNoxxx", System.getProperty("pos.clt.tin"));
            
            switch (_instance.getProductID().toLowerCase()) {
                case "integsys":
                    params.put("sProdctID", "IntegSysFX POS System");
                    break;
                case "telecom":
                    params.put("sProdctID", "TelecomFX POS System");
                    break;
                default:
                    params.put("sProdctID", "");
            }
            params.put("sSerialNo", System.getProperty("pos.clt.srial.no"));
            params.put("sMchineNo", System.getProperty("pos.clt.crm.no"));
            
            params.put(lsSalesman, System.getProperty("user.name"));
            
            params.put("sPrintdBy", System.getProperty("user.name"));

            InputStream stream = new ByteArrayInputStream(json_arr.toJSONString().getBytes("UTF-8"));
            JsonDataSource jrjson = new JsonDataSource(stream); 

            _jrprint = JasperFillManager.fillReport(_instance.getReportPath() + 
                                                    System.getProperty("store.report.file"), params, jrjson);
        } catch (SQLException | JRException | UnsupportedEncodingException ex) {
            _message = ex.getMessage();
            if(System.getProperty("store.default.debug").equalsIgnoreCase("true")){ex.printStackTrace();}         
            GLogger.severe(System.getProperty("store.report.class"), "printBIRSales", ExceptionUtils.getStackTrace(ex));
            return false;
        }
        
        return true;
    }
    
    private boolean printBIRSales(){
        String lsSQL = "SELECT * FROM Cash_Reg_Machine WHERE dExpiratn >= " + SQLUtil.toSQL(_instance.getServerDate());
        ResultSet rs = _instance.executeQuery(lsSQL);
        
        if (MiscUtil.RecordCount(rs) == 0){
            _message = "No record found.";
            ShowMessageFX.Warning(_message, "Notice", null);
            return false;
        }
        
        try {
            JSONObject loJSON = showFXDialog.jsonBrowse(_instance, rs, "MIN»Permit No»Terminal»Serial", "sIDNumber»sPermitNo»nPOSNumbr»sSerialNo");
            
            if (loJSON == null){
                _message = "No record selecte4d.";
                ShowMessageFX.Warning(_message, "Notice", null);
                return false;
            }
            
            System.setProperty("pos.clt.crm.no", (String) loJSON.get("sIDNumber"));
            
            lsSQL = getSQ_BIRSummary();
        
            rs = _instance.executeQuery(lsSQL);       

            if (MiscUtil.RecordCount(rs) <= 0){
                _message = "No record found.";
                ShowMessageFX.Warning(_message, "Notice", null);
                return false;
            }

            int lnRow = (int) MiscUtil.RecordCount(rs);
            
            if (lnRow == 0){
                _message = "No records found for the given criteria.";
                ShowMessageFX.Warning(_message, "Notice", null);
                return false;
            }
            
            rs.first();
            lsSQL = "SELECT dOpenedxx, dClosedxx, nAccuSale FROM Daily_Summary" +
                    " WHERE dClosedxx < " + SQLUtil.toSQL(rs.getDate("dOpenedxx")) +
                        " AND sCRMNumbr = " + SQLUtil.toSQL(System.getProperty("pos.clt.crm.no")) +
                    " ORDER BY dOpenedxx DESC LIMIT 1";
            
            ResultSet loRS = _instance.executeQuery(lsSQL);
            
            double lnBegBalxx = 0.00;
            double lnEndngBal = 0.00;
            
            if (loRS.next()){
                lnBegBalxx = loRS.getDouble("nAccuSale");
                lnEndngBal = loRS.getDouble("nAccuSale");
            }
            
            rs.first();
            String lsTranDate = rs.getString("sTranDate");
            String lsORNoFrom = rs.getString("sORNoFrom");
            String lsORNoThru = rs.getString("sORNoThru");
            
            String lsDateFrom = rs.getString("sTranDate");
            String lsDateThru = rs.getString("sTranDate");
            
            double lnNetTotal = 0.00;
            double lnSCDiscxx = 0.00;
            double lnRegularx = 0.00;
            double lnReturnxx = 0.00;
            double lnVoidxxxx = 0.00;
            double lnGrossTtl = 0.00;
            double lnVATablex = 0.00;
            double lnVATAmntx = 0.00;
            double lnVATExmpt = 0.00;
            double lnZeroRatd = 0.00;
            int lnResetCtr = 0;
            int lnZCounter = 0;      

            JSONObject json_obj;
            JSONArray json_arr = new JSONArray();
            json_arr.clear();

            int lnCtr = 1;
            rs.beforeFirst();
            while (rs.next()){
                if (lsTranDate.compareTo(rs.getString("sTranDate")) == 0){ //same date
                    if (lsORNoThru.compareTo(rs.getString("sORNoThru")) < 0) lsORNoThru = rs.getString("sORNoThru");
                    
                    lnNetTotal += rs.getDouble("nSalesAmt") - (rs.getDouble("nPWDDiscx") + rs.getDouble("nDiscount") + rs.getDouble("nVatDiscx"));
                    lnSCDiscxx += rs.getDouble("nPWDDiscx") + rs.getDouble("nVatDiscx");
                    lnRegularx += rs.getDouble("nDiscount");
                    lnBegBalxx += lnEndngBal;
                    lnEndngBal += rs.getDouble("nSalesAmt") - (rs.getDouble("nPWDDiscx") + rs.getDouble("nDiscount") + rs.getDouble("nVatDiscx"));
                    lnReturnxx += rs.getDouble("nReturnsx");
                    lnVoidxxxx += rs.getDouble("nVoidAmnt");
                    lnGrossTtl += rs.getDouble("nSalesAmt") + rs.getDouble("nReturnsx") + rs.getDouble("nVoidAmnt");
                    lnVATablex += rs.getDouble("nVATSales");
                    lnVATAmntx += rs.getDouble("nVATAmtxx");
                    lnVATExmpt += rs.getDouble("nNonVATxx");
                    lnZeroRatd += rs.getDouble("nZeroRatd");
                    lnResetCtr = 0;
                } else {
                    lsORNoFrom = rs.getString("sORNoFrom");
                    lsORNoThru = rs.getString("sORNoThru");
                    
                    lnNetTotal = rs.getDouble("nSalesAmt") - (rs.getDouble("nPWDDiscx") + rs.getDouble("nDiscount") + rs.getDouble("nVatDiscx"));
                    lnSCDiscxx = rs.getDouble("nPWDDiscx") + rs.getDouble("nVatDiscx");
                    lnRegularx = rs.getDouble("nDiscount");
                    lnBegBalxx = lnEndngBal;
                    lnEndngBal = rs.getDouble("nSalesAmt") - (rs.getDouble("nPWDDiscx") + rs.getDouble("nDiscount") + rs.getDouble("nVatDiscx"));
                    lnReturnxx = rs.getDouble("nReturnsx");
                    lnVoidxxxx = rs.getDouble("nVoidAmnt");
                    lnGrossTtl = rs.getDouble("nSalesAmt") + rs.getDouble("nReturnsx") + rs.getDouble("nVoidAmnt");
                    lnVATablex = rs.getDouble("nVATSales");
                    lnVATAmntx = rs.getDouble("nVATAmtxx");
                    lnVATExmpt = rs.getDouble("nNonVATxx");
                    lnZeroRatd = rs.getDouble("nZeroRatd");
                    lnResetCtr = 0;
                    
                    lsTranDate = rs.getString("sTranDate");
                }
                
                if (lnCtr != lnRow){
                    rs.next();
                    if (lsTranDate.compareTo(rs.getString("sTranDate")) != 0){
                        lnZCounter += 1;
                        lnEndngBal = lnEndngBal + lnBegBalxx;
                        
                        json_obj = new JSONObject();                        
                        json_obj.put("sField03", lsTranDate);
                        json_obj.put("sField00", lsORNoFrom);
                        json_obj.put("sField01", lsORNoThru);
                        json_obj.put("sField01", lsORNoThru);
                        json_obj.put("nField00", lnEndngBal); //4
                        json_obj.put("nField01", lnBegBalxx); //5
                        json_obj.put("nField02", lnEndngBal - lnBegBalxx); //6 = 4-5
                        json_obj.put("nField03", 0.00); //todo: put computation of manual receipts here. //7
                        json_obj.put("nField04", lnGrossTtl); //8
                        
                        double lxVATablex = Math.round((lnGrossTtl / 1.12) * 100D) / 100D;
                        double lxVATAmntx = lxVATablex * 0.12;
                        
                        json_obj.put("nField05", lxVATablex); //9 lnVATablex
                        json_obj.put("nField06", lxVATAmntx); //10 lnVATAmntx
                        json_obj.put("nField07", lnVATExmpt); //11
                        json_obj.put("nField08", lnZeroRatd); //12
                        json_obj.put("nField09", lnRegularx); //13
                        json_obj.put("nField10", lnSCDiscxx); //14
                        json_obj.put("nField11", lnReturnxx); //15
                        json_obj.put("nField12", lnVoidxxxx); //16
                        json_obj.put("nField13", lnRegularx + lnSCDiscxx + lnReturnxx + lnVoidxxxx); //17 = 13+14+15+16
                        json_obj.put("nField14", 0.00); //18
                        
                        double lxRetVATx = Math.round((lnReturnxx / 1.12) * 100D) / 100D;
                        double lxRetVatA = Math.round((lxRetVATx * 0.12) * 100D) / 100D;
                        
                        json_obj.put("nField15",  lxRetVatA); //19
                        
                        
                        double lxDiscVATx = Math.round(((lnRegularx / 1.12)) * 100D) / 100D;
                        double lxDiscVATA = Math.round((lxDiscVATx * 0.12) * 100D) / 100D;
                        
                        json_obj.put("nField16",  lxDiscVATA); //20
                        
                        json_obj.put("nField17", 0.00 + lxRetVatA + lxDiscVATA); //21 = 18+19+20
                        json_obj.put("nField18", lxVATAmntx - (0.00 + lxRetVatA + lxDiscVATA)); //22 = 10-21 lnVATAmntx - 0.00
                        json_obj.put("nField19", lnGrossTtl - (lnRegularx + lnSCDiscxx + lnReturnxx + lnVoidxxxx) - lnVATAmntx); //23 = 8-17-10
                        json_obj.put("nField20", 0.00); //24
                        json_obj.put("nField21", 0.00); //25
                        json_obj.put("nField22", (lnGrossTtl - (lnRegularx + lnSCDiscxx + lnReturnxx + lnVoidxxxx) - lnVATAmntx) + 0.00 + 0.00); //26=23+24+25
                        json_obj.put("nField23", lnZCounter); //27
                        json_obj.put("sField02", ""); //28
                        json_arr.add(json_obj);
                        
                        lsDateThru = lsTranDate;
                    }
                    rs.previous();
                } else {
                    lnZCounter += 1;
                    lnEndngBal = lnEndngBal + lnBegBalxx;
                    
                    json_obj = new JSONObject();                        
                    json_obj.put("sField03", lsTranDate);
                    json_obj.put("sField00", lsORNoFrom);
                    json_obj.put("sField01", lsORNoThru);
                    json_obj.put("sField01", lsORNoThru);
                    json_obj.put("nField00", lnEndngBal); //4
                    json_obj.put("nField01", lnBegBalxx); //5
                    json_obj.put("nField02", lnEndngBal - lnBegBalxx); //6 = 4-5
                    json_obj.put("nField03", 0.00); //todo: put computation of manual receipts here. //7
                    json_obj.put("nField04", lnGrossTtl); //8

                    double lxVATablex = Math.round((lnGrossTtl / 1.12) * 100D) / 100D;
                    double lxVATAmntx = lxVATablex * 0.12;

                    json_obj.put("nField05", lxVATablex); //9 lnVATablex
                    json_obj.put("nField06", lxVATAmntx); //10 lnVATAmntx
                    json_obj.put("nField07", lnVATExmpt); //11
                    json_obj.put("nField08", lnZeroRatd); //12
                    json_obj.put("nField09", lnRegularx); //13
                    json_obj.put("nField10", lnSCDiscxx); //14
                    json_obj.put("nField11", lnReturnxx); //15
                    json_obj.put("nField12", lnVoidxxxx); //16
                    json_obj.put("nField13", lnRegularx + lnSCDiscxx + lnReturnxx + lnVoidxxxx); //17 = 13+14+15+16
                    json_obj.put("nField14", 0.00); //18

                    double lxRetVATx = Math.round((lnReturnxx / 1.12) * 100D) / 100D;
                    double lxRetVatA = Math.round((lxRetVATx * 0.12) * 100D) / 100D;

                    json_obj.put("nField15",  lxRetVatA); //19


                    double lxDiscVATx = Math.round(((lnRegularx / 1.12)) * 100D) / 100D;
                    double lxDiscVATA = Math.round((lxDiscVATx * 0.12) * 100D) / 100D;

                    json_obj.put("nField16",  lxDiscVATA); //20

                    json_obj.put("nField17", 0.00 + lxRetVatA + lxDiscVATA); //21 = 18+19+20
                    json_obj.put("nField18", lxVATAmntx - (0.00 + lxRetVatA + lxDiscVATA)); //22 = 10-21 lnVATAmntx - 0.00
                    json_obj.put("nField19", lnGrossTtl - (lnRegularx + lnSCDiscxx + lnReturnxx + lnVoidxxxx) - lnVATAmntx); //23 = 8-17-10
                    json_obj.put("nField20", 0.00); //24
                    json_obj.put("nField21", 0.00); //25
                    json_obj.put("nField22", (lnGrossTtl - (lnRegularx + lnSCDiscxx + lnReturnxx + lnVoidxxxx) - lnVATAmntx) + 0.00 + 0.00); //26=23+24+25
                    json_obj.put("nField23", lnZCounter); //27
                    json_obj.put("sField02", ""); //28
                    json_arr.add(json_obj);
                    
                    lsDateThru = lsTranDate;
                }
                
                lnCtr += 1;
            }
            
            //Create the parameter
            Map<String, Object> params = new HashMap<>();
            params.put("sBranchNm", _instance.getBranchName());  
            params.put("sAddressx", _instance.getAddress() + ", " + _instance.getTownName() + " " + _instance.getProvince());  
            params.put("sTinNoxxx", System.getProperty("pos.clt.tin"));
            
            switch (_instance.getProductID().toLowerCase()) {
                case "integsys":
                    params.put("sProdctID", "IntegSysFX POS System");
                    break;
                case "telecom":
                    params.put("sProdctID", "TelecomFX POS System");
                    break;
                default:
                    params.put("sProdctID", "");
            }
            params.put("sSerialNo", System.getProperty("pos.clt.srial.no"));
            params.put("sMachinNo", System.getProperty("pos.clt.crm.no"));
            
            Date ldDateFrom = SQLUtil.toDate(lsDateFrom, SQLUtil.FORMAT_SHORT_DATE);
            Date ldDateThru = SQLUtil.toDate(lsDateThru, SQLUtil.FORMAT_SHORT_DATE);
            lsDateFrom = SQLUtil.dateFormat(ldDateFrom, SQLUtil.FORMAT_LONG_DATE);
            lsDateThru = SQLUtil.dateFormat(ldDateThru, SQLUtil.FORMAT_LONG_DATE);
            params.put("sReportDt", lsDateFrom + " to " + lsDateThru);
            
            params.put("sSalesMan", System.getProperty("user.name"));
            
            params.put("sPrntedBy", System.getProperty("user.name"));
            params.put("sPrntedDt", SQLUtil.dateFormat(_instance.getServerDate(), SQLUtil.FORMAT_TIMESTAMP));

            InputStream stream = new ByteArrayInputStream(json_arr.toJSONString().getBytes("UTF-8"));
            JsonDataSource jrjson = new JsonDataSource(stream); 

            _jrprint = JasperFillManager.fillReport(_instance.getReportPath() + 
                                                    System.getProperty("store.report.file"), params, jrjson);
        } catch (SQLException | JRException | UnsupportedEncodingException ex) {
            _message = ex.getMessage();
            if(System.getProperty("store.default.debug").equalsIgnoreCase("true")){ex.printStackTrace();}         
            GLogger.severe(System.getProperty("store.report.class"), "printBIRSales", ExceptionUtils.getStackTrace(ex));
            return false;
        }

        return true;
    }
    
    private boolean printSalesSummary(){        
        try {
            String lsSQL = "SELECT * FROM Cash_Reg_Machine WHERE dExpiratn >= " + SQLUtil.toSQL(_instance.getServerDate());
            ResultSet rsMachine = _instance.executeQuery(lsSQL);

            if (MiscUtil.RecordCount(rsMachine) == 0){
                _message = "No record found.";
                ShowMessageFX.Warning(_message, "Notice", null);
                return false;
            }
            
            JSONObject json_obj;
            JSONArray json_arr = new JSONArray();
            json_arr.clear();
            
            String lsDateFrom = "";
            String lsDateThru = "";
            
            while(rsMachine.next()){
                lsSQL = "SELECT" +
                        "  STR_TO_DATE(sTranDate, '%Y%m%d') sTranDate" +
                        ", nSalesAmt" +
                        ", nVATSales" +
                        ", nVATAmtxx" +
                        ", nNonVATxx" +
                        ", nZeroRatd" +
                        ", nDiscount" +
                        ", nVatDiscx" +
                        ", nPWDDiscx" +
                        ", nReturnsx" +
                        ", nVoidAmnt" +
                        ", sORNoFrom" +
                        ", sORNoThru" +
                        ", dOpenedxx" +
                        ", dClosedxx" +
                        ", sCRMNumbr" +
                    " FROM Daily_Summary" +
                    " WHERE cTranStat = '2'" +
                        " AND sCRMNumbr = " + SQLUtil.toSQL(rsMachine.getString("sIDNumber")) +
                    " ORDER BY sCRMNumbr, sTranDate, dOpenedxx";

                ResultSet rs = _instance.executeQuery(lsSQL);       


                int lnRow = (int) MiscUtil.RecordCount(rs);

                if (lnRow == 0){
                    //_message = "No records found for the given criteria.";
                    //ShowMessageFX.Warning(_message, "Notice", null);
                    //return false;
                    break;
                }

                rs.first();
                lsSQL = "SELECT dOpenedxx, dClosedxx, nAccuSale FROM Daily_Summary" +
                        " WHERE dClosedxx < " + SQLUtil.toSQL(rs.getDate("dOpenedxx")) +
                            " AND sCRMNumbr = " + SQLUtil.toSQL(rsMachine.getString("sIDNumber")) +
                        " ORDER BY dOpenedxx DESC LIMIT 1";

                ResultSet loRS = _instance.executeQuery(lsSQL);

                double lnBegBalxx = 0.00;
                double lnEndngBal = 0.00;

                if (loRS.next()){
                    lnBegBalxx = loRS.getDouble("nAccuSale");
                    lnEndngBal = loRS.getDouble("nAccuSale");
                }

                rs.first();
                String lsTranDate = rs.getString("sTranDate");
                String lsORNoFrom = rs.getString("sORNoFrom");
                String lsORNoThru = rs.getString("sORNoThru");

                lsDateFrom = rs.getString("sTranDate");
                lsDateThru = rs.getString("sTranDate");

                double lnNetTotal = 0.00;
                double lnSCDiscxx = 0.00;
                double lnRegularx = 0.00;
                double lnReturnxx = 0.00;
                double lnVoidxxxx = 0.00;
                double lnGrossTtl = 0.00;
                double lnVATablex = 0.00;
                double lnVATAmntx = 0.00;
                double lnVATExmpt = 0.00;
                double lnZeroRatd = 0.00;
                int lnResetCtr = 0;
                int lnZCounter = 0;      

                int lnCtr = 1;
                rs.beforeFirst();
                while (rs.next()){                
                    if (lsTranDate.compareTo(rs.getString("sTranDate")) == 0){ //same date
                        if (lsORNoThru.compareTo(rs.getString("sORNoThru")) < 0) lsORNoThru = rs.getString("sORNoThru");

                        lnNetTotal += rs.getDouble("nSalesAmt") - (rs.getDouble("nPWDDiscx") + rs.getDouble("nDiscount") + rs.getDouble("nVatDiscx"));
                        lnSCDiscxx += rs.getDouble("nPWDDiscx") + rs.getDouble("nVatDiscx");
                        lnRegularx += rs.getDouble("nDiscount");
                        lnBegBalxx += lnEndngBal;
                        lnEndngBal += rs.getDouble("nSalesAmt") - (rs.getDouble("nPWDDiscx") + rs.getDouble("nDiscount") + rs.getDouble("nVatDiscx"));
                        lnReturnxx += rs.getDouble("nReturnsx");
                        lnVoidxxxx += rs.getDouble("nVoidAmnt");
                        lnGrossTtl += rs.getDouble("nSalesAmt") + rs.getDouble("nReturnsx") + rs.getDouble("nVoidAmnt");
                        lnVATablex += rs.getDouble("nVATSales");
                        lnVATAmntx += rs.getDouble("nVATAmtxx");
                        lnVATExmpt += rs.getDouble("nNonVATxx");
                        lnZeroRatd += rs.getDouble("nZeroRatd");
                        lnResetCtr = 0;
                    } else {
                        lsORNoFrom = rs.getString("sORNoFrom");
                        lsORNoThru = rs.getString("sORNoThru");

                        lnNetTotal = rs.getDouble("nSalesAmt") - (rs.getDouble("nPWDDiscx") + rs.getDouble("nDiscount") + rs.getDouble("nVatDiscx"));
                        lnSCDiscxx = rs.getDouble("nPWDDiscx") + rs.getDouble("nVatDiscx");
                        lnRegularx = rs.getDouble("nDiscount");
                        lnBegBalxx = lnEndngBal;
                        lnEndngBal = rs.getDouble("nSalesAmt") - (rs.getDouble("nPWDDiscx") + rs.getDouble("nDiscount") + rs.getDouble("nVatDiscx"));
                        lnReturnxx = rs.getDouble("nReturnsx");
                        lnVoidxxxx = rs.getDouble("nVoidAmnt");
                        lnGrossTtl = rs.getDouble("nSalesAmt") + rs.getDouble("nReturnsx") + rs.getDouble("nVoidAmnt");
                        lnVATablex = rs.getDouble("nVATSales");
                        lnVATAmntx = rs.getDouble("nVATAmtxx");
                        lnVATExmpt = rs.getDouble("nNonVATxx");
                        lnZeroRatd = rs.getDouble("nZeroRatd");
                        lnResetCtr = 0;

                        lsTranDate = rs.getString("sTranDate");
                    }

                    if (lnCtr != lnRow){
                        rs.next();
                        if (lsTranDate.compareTo(rs.getString("sTranDate")) != 0){
                            lnZCounter += 1;
                            lnEndngBal = lnEndngBal + lnBegBalxx;

                            json_obj = new JSONObject();                        
                            json_obj.put("sField11", rs.getString("sCRMNumbr"));
                            json_obj.put("sField03", lsTranDate);
                            json_obj.put("sField00", lsORNoFrom);
                            json_obj.put("sField01", lsORNoThru);
                            json_obj.put("sField01", lsORNoThru);
                            json_obj.put("nField00", lnEndngBal); //4
                            json_obj.put("nField01", lnBegBalxx); //5
                            json_obj.put("nField02", lnEndngBal - lnBegBalxx); //6 = 4-5
                            json_obj.put("nField03", 0.00); //todo: put computation of manual receipts here. //7
                            json_obj.put("nField04", lnGrossTtl); //8

                            double lxVATablex = Math.round((lnGrossTtl / 1.12) * 100D) / 100D;
                            double lxVATAmntx = lxVATablex * 0.12;

                            json_obj.put("nField05", lxVATablex); //9 lnVATablex
                            json_obj.put("nField06", lxVATAmntx); //10 lnVATAmntx
                            json_obj.put("nField07", lnVATExmpt); //11
                            json_obj.put("nField08", lnZeroRatd); //12
                            json_obj.put("nField09", lnRegularx); //13
                            json_obj.put("nField10", lnSCDiscxx); //14
                            json_obj.put("nField11", lnReturnxx); //15
                            json_obj.put("nField12", lnVoidxxxx); //16
                            json_obj.put("nField13", lnRegularx + lnSCDiscxx + lnReturnxx + lnVoidxxxx); //17 = 13+14+15+16
                            json_obj.put("nField14", 0.00); //18

                            double lxRetVATx = Math.round((lnReturnxx / 1.12) * 100D) / 100D;
                            double lxRetVatA = Math.round((lxRetVATx * 0.12) * 100D) / 100D;

                            json_obj.put("nField15",  lxRetVatA); //19


                            double lxDiscVATx = Math.round(((lnRegularx / 1.12)) * 100D) / 100D;
                            double lxDiscVATA = Math.round((lxDiscVATx * 0.12) * 100D) / 100D;

                            json_obj.put("nField16",  lxDiscVATA); //20

                            json_obj.put("nField17", 0.00 + lxRetVatA + lxDiscVATA); //21 = 18+19+20
                            json_obj.put("nField18", lxVATAmntx - (0.00 + lxRetVatA + lxDiscVATA)); //22 = 10-21 lnVATAmntx - 0.00
                            json_obj.put("nField19", lnGrossTtl - (lnRegularx + lnSCDiscxx + lnReturnxx + lnVoidxxxx) - lnVATAmntx); //23 = 8-17-10
                            json_obj.put("nField20", 0.00); //24
                            json_obj.put("nField21", 0.00); //25
                            json_obj.put("nField22", (lnGrossTtl - (lnRegularx + lnSCDiscxx + lnReturnxx + lnVoidxxxx) - lnVATAmntx) + 0.00 + 0.00); //26=23+24+25
                            json_obj.put("nField23", lnZCounter); //27
                            json_obj.put("sField02", ""); //28
                            json_arr.add(json_obj);

                            lsDateThru = lsTranDate;
                        }
                        rs.previous();
                    } else {
                        lnZCounter += 1;
                        lnEndngBal = lnEndngBal + lnBegBalxx;

                        json_obj = new JSONObject();                        
                        json_obj.put("sField11", rs.getString("sCRMNumbr"));
                        json_obj.put("sField03", lsTranDate);
                        json_obj.put("sField00", lsORNoFrom);
                        json_obj.put("sField01", lsORNoThru);
                        json_obj.put("sField01", lsORNoThru);
                        json_obj.put("nField00", lnEndngBal); //4
                        json_obj.put("nField01", lnBegBalxx); //5
                        json_obj.put("nField02", lnEndngBal - lnBegBalxx); //6 = 4-5
                        json_obj.put("nField03", 0.00); //todo: put computation of manual receipts here. //7
                        json_obj.put("nField04", lnGrossTtl); //8

                        double lxVATablex = Math.round((lnGrossTtl / 1.12) * 100D) / 100D;
                        double lxVATAmntx = lxVATablex * 0.12;

                        json_obj.put("nField05", lxVATablex); //9 lnVATablex
                        json_obj.put("nField06", lxVATAmntx); //10 lnVATAmntx
                        json_obj.put("nField07", lnVATExmpt); //11
                        json_obj.put("nField08", lnZeroRatd); //12
                        json_obj.put("nField09", lnRegularx); //13
                        json_obj.put("nField10", lnSCDiscxx); //14
                        json_obj.put("nField11", lnReturnxx); //15
                        json_obj.put("nField12", lnVoidxxxx); //16
                        json_obj.put("nField13", lnRegularx + lnSCDiscxx + lnReturnxx + lnVoidxxxx); //17 = 13+14+15+16
                        json_obj.put("nField14", 0.00); //18

                        double lxRetVATx = Math.round((lnReturnxx / 1.12) * 100D) / 100D;
                        double lxRetVatA = Math.round((lxRetVATx * 0.12) * 100D) / 100D;

                        json_obj.put("nField15",  lxRetVatA); //19


                        double lxDiscVATx = Math.round(((lnRegularx / 1.12)) * 100D) / 100D;
                        double lxDiscVATA = Math.round((lxDiscVATx * 0.12) * 100D) / 100D;

                        json_obj.put("nField16",  lxDiscVATA); //20

                        json_obj.put("nField17", 0.00 + lxRetVatA + lxDiscVATA); //21 = 18+19+20
                        json_obj.put("nField18", lxVATAmntx - (0.00 + lxRetVatA + lxDiscVATA)); //22 = 10-21 lnVATAmntx - 0.00
                        json_obj.put("nField19", lnGrossTtl - (lnRegularx + lnSCDiscxx + lnReturnxx + lnVoidxxxx) - lnVATAmntx); //23 = 8-17-10
                        json_obj.put("nField20", 0.00); //24
                        json_obj.put("nField21", 0.00); //25
                        json_obj.put("nField22", (lnGrossTtl - (lnRegularx + lnSCDiscxx + lnReturnxx + lnVoidxxxx) - lnVATAmntx) + 0.00 + 0.00); //26=23+24+25
                        json_obj.put("nField23", lnZCounter); //27
                        json_obj.put("sField02", ""); //28
                        json_arr.add(json_obj);

                        lsDateThru = lsTranDate;
                    }

                    lnCtr += 1;
                }
            }
            
            //Create the parameter
            Map<String, Object> params = new HashMap<>();
            params.put("sBranchNm", _instance.getBranchName());  
            params.put("sAddressx", _instance.getAddress() + ", " + _instance.getTownName() + " " + _instance.getProvince());  
            params.put("sTinNoxxx", System.getProperty("pos.clt.tin"));
            
            switch (_instance.getProductID().toLowerCase()) {
                case "integsys":
                    params.put("sProdctID", "IntegSysFX POS System");
                    break;
                case "telecom":
                    params.put("sProdctID", "TelecomFX POS System");
                    break;
                default:
                    params.put("sProdctID", "");
            }
            params.put("sSerialNo", System.getProperty("pos.clt.srial.no"));
            params.put("sMachinNo", System.getProperty("pos.clt.crm.no"));
            
            Date ldDateFrom = SQLUtil.toDate(lsDateFrom, SQLUtil.FORMAT_SHORT_DATE);
            Date ldDateThru = SQLUtil.toDate(lsDateThru, SQLUtil.FORMAT_SHORT_DATE);
            lsDateFrom = SQLUtil.dateFormat(ldDateFrom, SQLUtil.FORMAT_LONG_DATE);
            lsDateThru = SQLUtil.dateFormat(ldDateThru, SQLUtil.FORMAT_LONG_DATE);
            params.put("sReportDt", lsDateFrom + " to " + lsDateThru);
            
            params.put("sSalesMan", System.getProperty("user.name"));
            
            params.put("sPrntedBy", System.getProperty("user.name"));
            params.put("sPrntedDt", SQLUtil.dateFormat(_instance.getServerDate(), SQLUtil.FORMAT_TIMESTAMP));

            InputStream stream = new ByteArrayInputStream(json_arr.toJSONString().getBytes("UTF-8"));
            JsonDataSource jrjson = new JsonDataSource(stream); 

            _jrprint = JasperFillManager.fillReport(_instance.getReportPath() + 
                                                    System.getProperty("store.report.file"), params, jrjson);
        } catch (SQLException | JRException | UnsupportedEncodingException ex) {
            _message = ex.getMessage();
            if(System.getProperty("store.default.debug").equalsIgnoreCase("true")){ex.printStackTrace();}         
            GLogger.severe(System.getProperty("store.report.class"), "printBIRSales", ExceptionUtils.getStackTrace(ex));
            return false;
        }

        return true;
    }
    
    private boolean printDiscountedSalesReport(){
        try {          
            String lsSQL = "SELECT * FROM Cash_Reg_Machine WHERE dExpiratn >= " + SQLUtil.toSQL(_instance.getServerDate());
            ResultSet rs = _instance.executeQuery(lsSQL);

            if (MiscUtil.RecordCount(rs) == 0){
                _message = "No record found.";
                ShowMessageFX.Warning(_message, "Notice", null);
                return false;
            }

            JSONObject loJSON = showFXDialog.jsonBrowse(_instance, rs, "MIN»Permit No»Terminal»Serial", "sIDNumber»sPermitNo»nPOSNumbr»sSerialNo");

            if (loJSON == null){
                System.setProperty("pos.clt.accrd.no", "");
                System.setProperty("pos.clt.prmit.no", "");
                System.setProperty("pos.clt.srial.no", "");
                System.setProperty("pos.clt.trmnl.no", "");
                System.setProperty("pos.clt.zcounter", "");
                return false;
            } else {
                System.setProperty("pos.clt.accrd.no", (String) loJSON.get("sAccredtn"));
                System.setProperty("pos.clt.prmit.no", (String) loJSON.get("sPermitNo"));
                System.setProperty("pos.clt.srial.no", (String) loJSON.get("sSerialNo"));
                System.setProperty("pos.clt.trmnl.no", (String) loJSON.get("nPOSNumbr"));
                System.setProperty("pos.clt.zcounter", (String) loJSON.get("nZReadCtr"));
            }
            
            lsSQL = MiscUtil.addCondition(getSQ_SalesDiscounted(), "a.cTranStat = '1'");
            String lsOldTrans = "";
            String lsORNumber = "";
            String lsSalesman = "";
            String lsRemarksx = "";
            String lsTransNox = "";
            
            double lnSubTotal = 0.00;
            double lnMasDiscx = 0.00;
            double lnTotlDisc = 0.00;
            double lnTranTotl = 0.00;
            double lnVATSales = 0.00;
            double lnVATAmntx = 0.00;
            double lnVATExmpt = 0.00;
            double lnZroRated = 0.00;
            double lnRegDiscx = 0.00;
            
            String lsBarCodex = "";
            String lsDescript = "";
            String lsSerial01 = "";
            String lsSerial02 = "";
            String lsDiscount = "";
            
            int lnQuantity = 0;
            double lnUnitPrce = 0.00;
            double lnDiscount = 0.00;
            double lnPWDDiscx = 0.00;
            
            int lnRow = 0;
            int lnCtr = 0;
            
            if (!getDateParam()) return false;
            
            String lsDate = "";
            if (!System.getProperty("store.report.criteria.datefrom").equals("") &&
                !System.getProperty("store.report.criteria.datethru").equals("")){

                lsDate = SQLUtil.toSQL(System.getProperty("store.report.criteria.datefrom") + " 00:00:00") + " AND " +
                            SQLUtil.toSQL(System.getProperty("store.report.criteria.datethru") + " 23:59:30");

                lsSQL = MiscUtil.addCondition(lsSQL, "a.dTransact BETWEEN " + lsDate);
            }
            
            rs = _instance.executeQuery(lsSQL);
            lnRow = (int) MiscUtil.RecordCount(rs);
            
            if (lnRow <= 0) {
                _message = "No record found for the given criteria.";
                ShowMessageFX.Warning(_message, "Notice", null);
                return false;
            }
            
            ResultSet rsMas;
            
            JSONObject json_obj;
            JSONArray json_arr = new JSONArray();
            json_arr.clear();
            
            rs.beforeFirst();
            while(rs.next()){
                lnCtr += 1;
                if (!lsOldTrans.equals(rs.getString("sTransNox"))){
                    lsOldTrans = rs.getString("sTransNox");
                    
                    lnMasDiscx = 0.00;
                    lnTotlDisc = 0.00;
                    lnSubTotal = 0.00;
                    lnTranTotl = 0.00;
                }
                
                //detail
                lsBarCodex = rs.getString("sBarCodex");
                lsDescript = rs.getString("sDescript");
                lsSerial01 = rs.getString("sSerial01");
                lsSerial02 = rs.getString("sSerial02");
                lsDiscount = rs.getString("xDiscount");

                lnQuantity = rs.getInt("nQuantity");
                lnUnitPrce = rs.getDouble("nUnitPrce");
                lnDiscount = rs.getDouble("nTotlDisc");
                lnPWDDiscx = 0.00;
                //end of detail

                //master
                lsSQL = MiscUtil.addCondition(getSQ_SaleInvoice(), "b.sSourceNo = " + SQLUtil.toSQL(lsOldTrans));
                rsMas = _instance.executeQuery(lsSQL);

                if (rsMas.next()){
                    lsTransNox = rsMas.getString("xTransNox");
                    lsORNumber = rsMas.getString("sORNumber");
                    lnVATSales = rsMas.getDouble("nVATSales"); //lnTranTotl / nVATRatex
                    lnVATAmntx = rsMas.getDouble("nVATAmtxx"); //(lnTranTotl / nVATRatex) * (nVATRatex - 1)
                    lnVATExmpt = 0.00; //default is zero since we are not issuing sc/pwd discount
                    lnZroRated = 0.00; //default is zero since we are not issuing zero rated sales
                    lsSalesman = rsMas.getString("sCashierx"); //cashier name
                    
                    if (lnCtr < lnRow) {
                        rs.next(); //move to next record to get the transaction number
                        
                        if (lsOldTrans.equals(rs.getString("sTransNox"))){
                            lnSubTotal += lnDiscount; //temporary store the running detail discounts here
                        } else {
                            lnMasDiscx = rsMas.getDouble("nTotlDisc");
                            lnTotlDisc = lnDiscount + lnSubTotal;
                            
                            //add freight charge, transaction total and the total discount
                            lnSubTotal = lnTotlDisc + rsMas.getDouble("nTranTotl") + rsMas.getDouble("nFreightx"); 
                            
                            lnTranTotl = lnSubTotal - lnTotlDisc - lnMasDiscx;
                        }
                        
                        rs.previous(); //move to previous record
                    } else {
                        lnMasDiscx = rsMas.getDouble("nTotlDisc");
                        lnTotlDisc = lnDiscount + lnSubTotal;
                            
                        //add freight charge, transaction total and the total discount
                        lnSubTotal = lnTotlDisc + rsMas.getDouble("nTranTotl") + rsMas.getDouble("nFreightx"); 

                        lnTranTotl = lnSubTotal - lnTotlDisc - lnMasDiscx;
                    }
                } else {
                    _message = "Transaction discrepancy detected.";
                    return false;
                }               

                json_obj = new JSONObject();
                json_obj.put("sField00", System.getProperty("store.report.criteria.datefrom") + " to " + System.getProperty("store.report.criteria.datethru"));
                json_obj.put("sField02", lsORNumber);                     
                json_obj.put("sField03", lsDescript);
                json_obj.put("sField04", lsRemarksx);
                json_obj.put("sField05", lsSalesman);
                json_obj.put("sField06", lsBarCodex);
                json_obj.put("sField07", lsSerial01);
                json_obj.put("sField08", lsSerial02);
                json_obj.put("sField09", lsTransNox);
                json_obj.put("sField10", lsDiscount);
                json_obj.put("nField00", lnQuantity); //Quantity
                json_obj.put("nField01", lnUnitPrce); //Amount
                json_obj.put("nField02", lnDiscount); //Discount
                json_obj.put("nField03", ((lnQuantity * lnUnitPrce) - lnDiscount) / 1.12);
                json_obj.put("nField12", (((lnQuantity * lnUnitPrce) - lnDiscount) / 1.12) * 0.12);

                json_obj.put("nField04", lnSubTotal);
                json_obj.put("nField05", lnTotlDisc);
                json_obj.put("nField06", lnTranTotl);
                json_obj.put("nField07", lnVATSales);
                json_obj.put("nField08", lnVATAmntx);
                json_obj.put("nField09", lnVATExmpt);
                json_obj.put("nField10", lnZroRated);
                json_obj.put("nField11", lnMasDiscx);

                json_arr.add(json_obj);
            }
            
            Map<String, Object> params = new HashMap<>();
            params.put("sCompnyNm", _instance.getBranchName());  
            params.put("sAddressx", _instance.getAddress() + ", " + _instance.getTownName() + " " + _instance.getProvince());  
            params.put("sTINNoxxx", System.getProperty("pos.clt.tin"));
            
            switch (_instance.getProductID().toLowerCase()) {
                case "integsys":
                    params.put("sProdctID", "IntegSysFX POS System");
                    break;
                case "telecom":
                    params.put("sProdctID", "TelecomFX POS System");
                    break;
                default:
                    params.put("sProdctID", "");
            }
            params.put("sSerialNo", System.getProperty("pos.clt.srial.no"));
            params.put("sMchineNo", System.getProperty("pos.clt.crm.no"));
            
            params.put(lsSalesman, System.getProperty("user.name"));
            
            params.put("sPrintdBy", System.getProperty("user.name"));

            InputStream stream = new ByteArrayInputStream(json_arr.toJSONString().getBytes("UTF-8"));
            JsonDataSource jrjson = new JsonDataSource(stream); 

            _jrprint = JasperFillManager.fillReport(_instance.getReportPath() + 
                                                    System.getProperty("store.report.file"), params, jrjson);
        } catch (SQLException | JRException | UnsupportedEncodingException ex) {
            _message = ex.getMessage();
            if(System.getProperty("store.default.debug").equalsIgnoreCase("true")){ex.printStackTrace();}         
            GLogger.severe(System.getProperty("store.report.class"), "printBIRSales", ExceptionUtils.getStackTrace(ex));
            return false;
        }
        
        return true;
    }
    
       
    private void closeReport(){
        _rptparam.forEach(item->System.clearProperty((String) item));
        System.clearProperty("store.report.file");
        System.clearProperty("store.report.header");
    }
    
    private void logReport(){
        _rptparam.forEach(item->System.clearProperty((String) item));
        System.clearProperty("store.report.file");
        System.clearProperty("store.report.header");
    }
        
    private String getSQ_BIRSummary(){
        return "SELECT" +
                    "  STR_TO_DATE(sTranDate, '%Y%m%d') sTranDate" +
                    ", nSalesAmt" +
                    ", nVATSales" +
                    ", nVATAmtxx" +
                    ", nNonVATxx" +
                    ", nZeroRatd" +
                    ", nDiscount" +
                    ", nVatDiscx" +
                    ", nPWDDiscx" +
                    ", nReturnsx" +
                    ", nVoidAmnt" +
                    ", sORNoFrom" +
                    ", sORNoThru" +
                    ", dOpenedxx" +
                    ", dClosedxx" +
                " FROM Daily_Summary" +
                " WHERE cTranStat = '2'" +
                    " AND sCRMNumbr = " + SQLUtil.toSQL(System.getProperty("pos.clt.crm.no")) + 
                " ORDER BY sTranDate, dOpenedxx";
    }
    
    private String getSQ_Sales(){
        return "SELECT" +
                    "  a.sTransNox" +
                    ", a.nTranTotl" +
                    ", c.sBarCodex" +
                    ", c.sDescript" +
                    ", b.nQuantity" +
                    ", b.nUnitPrce" +
                    ", b.nDiscount" +
                    ", b.nAddDiscx" +
                    ", IFNULL(b.sSerialID, '') sSerialID" +
                    ", IFNULL(d.sSerial01, '') sSerial01" +
                    ", IFNULL(d.sSerial02, '') sSerial02" +
                    ", b.nEntryNox" + 
                    ", (b.nQuantity * b.nUnitPrce * b.nDiscount) + b.nAddDiscx nTotlDisc" +
                    ", (b.nQuantity * b.nUnitPrce * a.nDiscount) + a.nAddDiscx nMasDisc" +
                " FROM Sales_Master a" +
                    ", Sales_Detail b" +
                        " LEFT JOIN Inventory c" +
                            " ON b.sStockIDx = c.sStockIDx" +
                        " LEFT JOIN Inv_Serial d" +
                            " ON b.sSerialID = d.sSerialID" +
                " WHERE a.sTransNox = b.sTransNox" + 
                    " AND a.sTransNox LIKE " + SQLUtil.toSQL(_instance.getBranchCode() + System.getProperty("pos.clt.trmnl.no") + "%") +
                " ORDER BY a.sTransNox, b.nEntryNox";
    }
    
    private String getSQ_SalesDiscounted(){
        return "SELECT" +
                    "  a.sTransNox" +
                    ", a.nTranTotl" +
                    ", c.sBarCodex" +
                    ", c.sDescript" +
                    ", b.nQuantity" +
                    ", b.nUnitPrce" +
                    ", b.nDiscount" +
                    ", b.nAddDiscx" +
                    ", IFNULL(b.sSerialID, '') sSerialID" +
                    ", IFNULL(d.sSerial01, '') sSerial01" +
                    ", IFNULL(d.sSerial02, '') sSerial02" +
                    ", b.nEntryNox" + 
                    ", (b.nQuantity * b.nUnitPrce * b.nDiscount) + b.nAddDiscx nTotlDisc" +
                    ", (b.nQuantity * b.nUnitPrce * a.nDiscount) + a.nAddDiscx nMasDisc" +
                    ", IFNULL(e.sDescript, '') xDiscount" +
                " FROM Sales_Master a" +
                    ", Sales_Detail b" +
                        " LEFT JOIN Inventory c" +
                            " ON b.sStockIDx = c.sStockIDx" +
                        " LEFT JOIN Inv_Serial d" +
                            " ON b.sSerialID = d.sSerialID" +
                        " LEFT JOIN Promo_Discount e" +
                            " ON b.nDiscount = e.nDiscRate" +
                                " AND b.nAddDiscx = e.nAddDiscx" +
                " WHERE a.sTransNox = b.sTransNox" + 
                    " AND a.sTransNox LIKE " + SQLUtil.toSQL(_instance.getBranchCode() + System.getProperty("pos.clt.trmnl.no") + "%") +
                " HAVING nTotlDisc > 0 OR nMasDisc > 0" +
                " ORDER BY a.sTransNox, b.nEntryNox";
    }
    
    private String getSQ_SaleInvoice(){
        return "SELECT" +
                    "  a.sTransNox" +
                    ", a.nTranTotl" +
                    ", a.nDiscount" +
                    ", a.nAddDiscx" + 
                    ", a.nFreightx" +
                    ", b.nVATSales" +
                    ", b.nVATAmtxx" +
                    ", b.nNonVATSl" +
                    ", b.nZroVATSl" +
                    ", b.nCWTAmtxx" +
                    ", b.sORNumber" + 
                    ", (a.nTranTotl * a.nDiscount) + a.nAddDiscx nTotlDisc" +
                    ", a.nVATRatex" +
                    ", d.sClientNm sCashierx" +
                    ", RIGHT(e.sTransNox, 10) xTransNox" +
                " FROM Sales_Master a" +
                    ", Receipt_Master b" +
                        " LEFT JOIN xxxSysUser c" +
                            " ON b.sCashierx = c.sUserIDxx" +
                        " LEFT JOIN Client_Master d" +
                             " ON c.sEmployNo = d.sClientID" +
                        " LEFT JOIN Sales_Transaction e" +
                            " ON e.sSourceCd = 'ORec'" +
                                " AND b.sTransNox = e.sSourceNo" +
                                " AND e.cReversex = '+'" +
                " WHERE a.sTransNox = b.sSourceNo" +
                    " AND b.sSourceCd = 'SL'";

    }
    
    private String getSQ_ActivityLog(){
        return "SELECT" +
                    "  a.sTransNox `sEventIDx`" +
                    ", b.sDescript `sEventDsc`" +
                    ", a.sRemarksx `sNotesxxx`" +	
                    ", a.sCRMNumbr `sMachinex`" +
                    ", d.sClientNm `sUserName`" + 
                    ", c.sLogNamex `sLogNamex`" +
                    ", a.dModified" +
                " FROM Event_Master a" +
                        " LEFT JOIN CRM_Events b" +
                            " ON a.sEventIDx = b.sEventIDx" +
                    " , xxxSysUser c" +
                        " LEFT JOIN Client_Master d" +
                            " ON c.sEmployNo = d.sClientID" +
                " WHERE a.sUserIDxx = c.sUserIDxx" + 
                    " AND a.sTransNox LIKE " + SQLUtil.toSQL(_instance.getBranchCode() + System.getProperty("pos.clt.trmnl.no") + "%");
    }
    
    private String getSQ_CancelledInvoice(){
        return "SELECT" +
                    "  a.sTransNox" +
                    ", c.sBarCodex" +
                    ", c.sDescript" +
                    ", b.nQuantity" +
                    ", b.nUnitPrce" +
                    ", b.nDiscount" +
                    ", b.nAddDiscx" +
                    ", b.sSerialID" +
                    ", d.sSerial01" +
                    ", d.sSerial02" +
                " FROM Sales_Master a" +
                    ", Sales_Detail b" +
                        " LEFT JOIN Inventory c" +
                            " ON b.sStockIDx = c.sStockIDx" +
                        " LEFT JOIN Inv_Serial d" +
                            " ON b.sSerialID = d.sSerialID" +
                " WHERE a.cTranStat = '3'" + 
                    " AND a.sTransNox LIKE " + SQLUtil.toSQL(_instance.getBranchCode() + System.getProperty("pos.clt.trmnl.no") + "%");
    }
    
    private String getSQ_Inventory(){
        return "";
    }
    
    private String getSQ_VoidTransactions(){
        return "SELECT" +
                    "  a.sTransNox" +
                    ", a.sRemarksx" +
                    ", a.sSalesman" +
                    ", c.sBarCodex" +
                    ", c.sDescript" +
                    ", b.nQuantity" +
                    ", b.nUnitPrce" +
                    ", b.nDiscount" +
                    ", b.nAddDiscx" +
                    ", b.sSerialID" +
                    ", d.sSerial01" +
                    ", d.sSerial02" +
                    ", (b.nQuantity * b.nUnitPrce * b.nDiscount) + b.nAddDiscx +( b.nUnitPrce * a.nDiscount) + a.nAddDiscx nTotlDisc" +
                " FROM Sales_Master a" +
                    ", Sales_Detail b" +
                        " LEFT JOIN Inventory c" +
                            " ON b.sStockIDx = c.sStockIDx" +
                        " LEFT JOIN Inv_Serial d" +
                            " ON b.sSerialID = d.sSerialID" +
                " WHERE a.sTransNox = b.sTransNox" + 
                " AND a.cTranStat = '4'" + 
                    " AND a.sTransNox LIKE " + SQLUtil.toSQL(_instance.getBranchCode() + System.getProperty("pos.clt.trmnl.no") + "%");
    }
}