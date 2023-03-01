package org.rmj.cas.pos.reports;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.net.URL;
import java.text.ParseException;
import java.time.LocalDate;
import java.util.Date;
import java.util.ResourceBundle;
import javafx.beans.property.ReadOnlyBooleanPropertyBase;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import static javafx.scene.input.KeyCode.ENTER;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.print.event.PrintJobAdapter;
import javax.print.event.PrintJobEvent;
import org.rmj.appdriver.GRider;
import org.rmj.appdriver.agentfx.ShowMessageFX;
import org.rmj.appdriver.agentfx.CommonUtils;
import javax.print.Doc;
import javax.print.PrintService;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

public class EJournalController implements Initializable {

    @FXML
    private AnchorPane dataPane;
    @FXML
    private StackPane stack;
    @FXML
    private TextField txtField01;
    @FXML
    private TextField txtField02;
    @FXML
    private Button btnOk;
    @FXML
    private Button btnCancel;
    @FXML
    private Button btnExit;
    @FXML
    private FontAwesomeIconView glyphExit;
    @FXML
    private TextArea txtFieldArea;
    @FXML
    private ComboBox cmbType;
    
    private boolean pbCancelled = true;
    private boolean pbSingleDate = false;
    private String psDateFrom = "";
    private String psDateThru = "";
    
    public boolean isCancelled(){return pbCancelled;}
    public String getDateFrom(){return psDateFrom;}
    public String getDateTo(){return psDateThru;}
    public void singleDayOnly(boolean foValue){pbSingleDate = foValue;}
    
    private ObservableList<String> sDeviceType = FXCollections.observableArrayList("e-Journal", "X-Reading", "Z-Reading");
   
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        btnExit.setOnAction(this::cmdButton_Click);
        btnOk.setOnAction(this::cmdButton_Click);
        btnCancel.setOnAction(this::cmdButton_Click);
        
        txtField01.setOnKeyPressed(this::txtField_KeyPressed);
        txtField02.setOnKeyPressed(this::txtField_KeyPressed);
        
        txtField01.focusedProperty().addListener(txtField_Focus);
        txtField02.focusedProperty().addListener(txtField_Focus);
        
        txtField02.setDisable(pbSingleDate);
        
        cmbType.setItems(sDeviceType);
        cmbType.getSelectionModel().select(0);
        
        loadRecord();
        
        pbLoaded = true;
    }
    
    private void loadRecord(){
        txtField01.setText(CommonUtils.xsDateMedium((Date) java.sql.Date.valueOf(LocalDate.now())));
        txtField02.setText(CommonUtils.xsDateMedium((Date) java.sql.Date.valueOf(LocalDate.now())));
    }
    
    private Stage getStage(){
        return (Stage) btnOk.getScene().getWindow();
    }
    
    private void cmdButton_Click (ActionEvent event){
        String lsButton = ((Button)event.getSource()).getId();
        switch(lsButton){
            case "btnCancel":
                if (ShowMessageFX.YesNo(getStage(), null, "Confirm", "Do you want to print this e-journal?")){
                    try {
                        String defaultPrinter =
                        PrintServiceLookup.lookupDefaultPrintService().getName();
                        System.out.println("Default printer: " + defaultPrinter);
                        PrintService service = PrintServiceLookup.lookupDefaultPrintService();

                        // prints the famous hello world! plus a form feed
                        String str = txtFieldArea.getText() + "\f";

                        InputStream is = new ByteArrayInputStream(str.getBytes("UTF8"));

                        PrintRequestAttributeSet  pras = new HashPrintRequestAttributeSet();
                        pras.add(new Copies(1));

                        DocFlavor flavor = DocFlavor.INPUT_STREAM.AUTOSENSE;
                        Doc doc = new SimpleDoc(is, flavor, null);
                        DocPrintJob job = service.createPrintJob();

                        PrintJobWatcher pjw = new PrintJobWatcher(job);
                        job.print(doc, pras);
                        pjw.waitForDone();
                        is.close();
                    } catch (Exception e) {
                        ShowMessageFX.Warning(getStage(), e.getMessage(), "Warning", null); 
                    }
                }    
                break;
            case "btnOk":
                try {
                    if(CommonUtils.isDate(txtField01.getText(), pxeDateFormat))
                        psDateFrom = txtField01.getText();
                    else psDateFrom = CommonUtils.xsDateShort(txtField01.getText());
                    
                    if(CommonUtils.isDate(txtField02.getText(), pxeDateFormat))
                        psDateThru = txtField02.getText();
                    else 
                        psDateThru = CommonUtils.xsDateShort(txtField02.getText());
                } catch (ParseException e) {
                    ShowMessageFX.Error(getStage(), e.getMessage(), DateCriteriaController.class.getSimpleName(), "Please inform MIS Department.");
                    //System.exit(1);
                }                
                
                if(psDateFrom.compareTo(psDateThru) > 0){
                    ShowMessageFX.Warning(getStage(), "Please verify your entry and try again.!", pxeModuleName, "Invalid date range.");
                    return;
                }
                
                int lnFrom = Integer.parseInt(psDateFrom.replace("-", ""));
                int lnThru = Integer.parseInt(psDateThru.replace("-", ""));
                
                String lsDIR = System.getProperty("pos.clt.dir.ejournal");
                String lsMIN = System.getProperty("pos.clt.crm.no") + " ";
                
                switch(cmbType.getSelectionModel().getSelectedIndex()){
                    case 1:
                        lsMIN = System.getProperty("pos.clt.crm.no") + " X-";
                        break;
                    case 2:
                        lsMIN = System.getProperty("pos.clt.crm.no") + " Z-";
                        break;
                }
                
                String fileName;
                File file;
                FileReader fr;
                BufferedReader br;
                String line = "";
                String str = "";
                
                try {
                    while (lnFrom <= lnThru){
                        fileName = lsDIR + lsMIN + lnFrom + ".txt";
                        file = new File(fileName);
                        
                        if (file.exists()){
                            fr = new FileReader(file);
                            br = new BufferedReader(fr);

                            while((line = br.readLine()) != null){
                                str += line + "\n";
                            }
                        }
                        
                        lnFrom += 1;
                    }
                    
                    txtFieldArea.setText(str);
                } catch (Exception e) {
                    ShowMessageFX.Warning(getStage(), e.getMessage(), "Warning", null);
                }

                return;
            case "btnExit":
                pbCancelled = true; break;
            default:
                ShowMessageFX.Warning(null, pxeModuleName, "Button with name "+ lsButton + " not registered!");
        }
        CommonUtils.closeStage(btnExit);
    }
    
    private void txtField_KeyPressed(KeyEvent event){
        TextField txtField = (TextField)event.getSource();
        
        switch(event.getCode()){
            case DOWN:
            case ENTER:
                CommonUtils.SetNextFocus(txtField);
                break;
            case UP:
                CommonUtils.SetPreviousFocus(txtField);
        }
    }
    
    public void setGrider(GRider foGRider) {
        this.poGRider = foGRider;
    }
    
    public final String pxeModuleName = "org.rmj.reportmenufx.views.DateCriteriaController";
    private static GRider poGRider;
    private final String pxeDateFormat = "yyyy-MM-dd";
    private static final String pxeDefaultDate =java.time.LocalDate.now().toString();
    private boolean pbLoaded = false;
    private int pnIndex = -1;
    
    final ChangeListener<? super Boolean> txtField_Focus = (o,ov,nv)->{
        if (!pbLoaded) return;
        
        TextField txtField = (TextField)((ReadOnlyBooleanPropertyBase)o).getBean();
        int lnIndex = Integer.parseInt(txtField.getId().substring(8, 10));
        String lsValue = txtField.getText();
        
        if (lsValue == null) return;
        
        if(!nv){ /*Lost Focus*/
            switch (lnIndex){
               
                case 1: /*dDateFrom*/
                case 2: /*dDateThru*/
                   if(CommonUtils.isDate(txtField.getText(), pxeDateFormat)){
                         txtField.setText(CommonUtils.xsDateMedium(CommonUtils.toDate(txtField.getText())));
                    }else{
                        txtField.setText(CommonUtils.xsDateMedium(CommonUtils.toDate(pxeDefaultDate)));
                    }
                   
                   if (pbSingleDate) txtField02.setText(txtField01.getText());
                   break;
                default:
                    ShowMessageFX.Warning(null, pxeModuleName, "Text field with name " + txtField.getId() + " not registered.");
            }
            pnIndex = lnIndex;
        }else{
            switch (lnIndex){
                case 1:
                case 2:
                    try{
                        txtField.setText(CommonUtils.xsDateShort(lsValue));
                    }catch(ParseException e){
                        ShowMessageFX.Error(getStage(), e.getMessage(), pxeModuleName, null);
                    }
                    txtField.selectAll();
                    break;
                default:
            }
            pnIndex = lnIndex;
            txtField.selectAll();
        }
    };
    
}

class PrintJobWatcher {
  boolean done = false;

  PrintJobWatcher(DocPrintJob job) {
    job.addPrintJobListener(new PrintJobAdapter() {
      public void printJobCanceled(PrintJobEvent pje) {
        allDone();
      }
      public void printJobCompleted(PrintJobEvent pje) {
        allDone();
      }
      public void printJobFailed(PrintJobEvent pje) {
        allDone();
      }
      public void printJobNoMoreEvents(PrintJobEvent pje) {
        allDone();
      }
      void allDone() {
        synchronized (PrintJobWatcher.this) {
          done = true;
          System.out.println("Printing done ...");
          PrintJobWatcher.this.notify();
        }
      }
    });
  }
  public synchronized void waitForDone() {
    try {
      while (!done) {
        wait();
      }
    } catch (InterruptedException e) {
    }
  }
}