package org.rmj.cas.pos.reports;

import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ResourceBundle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.MiscUtil;
import org.rmj.appdriver.SQLUtil;
import org.rmj.appdriver.agentfx.ShowMessageFX;

public class BrowseReportController implements Initializable {
    @FXML
    private AnchorPane dataPane;
    @FXML
    private Button btnExit;
    @FXML
    private TableView<TableModel> tableResult;
    @FXML
    private Button btnOk;
    @FXML
    private Button btnCancel;
    
    private GRider oApp;
    private ResultSet oReport;
    
    private boolean pbLoaded;
    private boolean pbCancelled = true;
    private String psReportID = "";
    private int pnRow = 0;
    private int pnEntryNox = 0;
    
    private ObservableList<TableModel> data = FXCollections.observableArrayList();
    
    public void setGRider(GRider foApp){oApp = foApp;}
    public boolean isCancelled(){return pbCancelled;}
    public String getReportID(){return psReportID;}
    
    public void setReportID(String fsValue){
        System.setProperty("store.report.id", fsValue);
        psReportID = fsValue;
    }
    
    public int getEntryNox(){return pnEntryNox;}
    
    private Stage getStage(){
        return (Stage) btnOk.getScene().getWindow();
    }
    
    private void unloadScene(){
        Stage stage = getStage();
        stage.close();
    }
    
    private void cmdButton_Click(ActionEvent event) {
        String lsButton = ((Button)event.getSource()).getId();
        
        switch (lsButton){
            case "btnCancel":
            case "btnExit":
                pbCancelled = true;
                break;
            default:
                pbCancelled = false;
        }
        
        unloadScene();
    }
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (!pbLoaded){
            if (oApp == null){
                ShowMessageFX.Warning(getStage(), "Application driver was not set.", "Warning", "Please inform MIS Department.");
                unloadScene();
            }
        }
        
        btnCancel.setOnAction(this::cmdButton_Click);
        btnOk.setOnAction(this::cmdButton_Click);
        btnExit.setOnAction(this::cmdButton_Click);
        
        initGrid();
        pbLoaded = loadDetail();
    }    

    @FXML
    private void tableResult_Click(MouseEvent event) {
        pnRow = tableResult.getSelectionModel().getSelectedIndex();
        
        if (pnRow >= 0) {
            try {   
                oReport.absolute(pnRow +1);
                pnEntryNox =  oReport.getInt("nEntryNox");
            } catch (SQLException ex) {
                ShowMessageFX.Error(getStage(), ex.getMessage(), "Error", "Please inform MIS Department.");
                unloadScene();
            }
        } 
    }
    
    private boolean loadDetail(){
        try {
            String lsSQL = "SELECT" +
                    "  nEntryNox" +
                    ", sReportHd" +
                    " FROM xxxReportDetail" +
                    " WHERE sReportID = " + SQLUtil.toSQL(psReportID) +
                    " ORDER BY sReportHd";
            
            oReport = oApp.executeQuery(lsSQL);
            
            data.clear();
            for (int lnCtr = 1; lnCtr <= MiscUtil.RecordCount(oReport); lnCtr++){
                oReport.absolute(lnCtr);
                data.add(new TableModel(String.valueOf(lnCtr), oReport.getString("sReportHd"), "", "", ""));
            }
            
            if (!data.isEmpty()){
                pnRow = 1;
                oReport.absolute(pnRow);
                pnEntryNox = oReport.getInt("nEntryNox");
            }
            
        } catch (SQLException ex) {
            ShowMessageFX.Error(getStage(), ex.getMessage(), "Error", "Please inform MIS Department.");
            unloadScene();
        }
        return true;
    }
    
    private void initGrid(){
        TableColumn index01 = new TableColumn("");
        TableColumn index02 = new TableColumn("");
        
        index01.setSortable(false); index01.setResizable(false);
        index02.setSortable(false); index02.setResizable(false);
        
        tableResult.getColumns().clear();
        
        index01.setText("No."); tableResult.getColumns().add(index01);
        index01.setPrefWidth(35);
        index01.setCellValueFactory(new PropertyValueFactory<TableModel,String>("index01"));
        
        index02.setText("Report Name"); tableResult.getColumns().add(index02);
        index02.setPrefWidth(361);
        index02.setCellValueFactory(new PropertyValueFactory<TableModel,String>("index02"));
        
        tableResult.setItems(data);
    }
}
