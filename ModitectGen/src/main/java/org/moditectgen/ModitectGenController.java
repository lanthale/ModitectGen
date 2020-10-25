package org.moditectgen;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import javafx.fxml.FXML;

import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;

public class ModitectGenController implements Initializable {

    private FontAwesomeIconView iconview;
    private FontAwesomeIconView iconviewDelete;
    private UtilityTools util;
    private File selectedJarFile;

    private ExecutorService executor;
    @FXML
    private MenuBar menuBar;
    @FXML
    private AnchorPane pane;
    @FXML
    private VBox vbox;
    @FXML
    private Button jarSelectionButton;
    @FXML
    private Button dirSelectionButton;
    @FXML
    private TextArea textArea;
    @FXML
    private StackPane progressInfo;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label progressLabel;
    @FXML
    private Label infoLabel;
    private File jarDirectory;
    @FXML
    private Button generateButton;
    @FXML
    private TextField jarSelectionText;
    @FXML
    private TextField jarDirSelection;
    @FXML
    private CheckBox multireleaseCheckbox;
    @FXML
    private CheckBox ignoreMIssingDepsCheckbox;
    @FXML
    private TextField releaseVersionField;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        Logger.getLogger(ModitectGenController.class.getName()).log(Level.SEVERE, "Init Controller");
        executor = Executors.newSingleThreadExecutor();
        menuBar.useSystemMenuBarProperty().set(true);
        progressInfo.setVisible(false);
        progressInfo.setManaged(false);
    }

    @FXML
    public void exit() {
        executor.shutdownNow();
        App.saveSettings((Stage) menuBar.getScene().getWindow(), this);
        System.exit(0);
    }

    public ExecutorService getExecutor() {
        return executor;
    }

    @FXML
    private void jarSelectionAction(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select jar to generate module descriptor");
        fileChooser.getExtensionFilters().addAll(
                new ExtensionFilter("Jar Files", "*.jar"));
        selectedJarFile = fileChooser.showOpenDialog(pane.getScene().getWindow());
        if (selectedJarFile != null) {
            jarSelectionText.setText(selectedJarFile.getAbsolutePath());
        }
    }

    @FXML
    private void dirSelectionAction(ActionEvent event) {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select director of all other jar files");
        jarDirectory = dirChooser.showDialog(pane.getScene().getWindow());
        if (jarDirectory != null) {
            jarDirSelection.setText(jarDirectory.getAbsolutePath());
        }
    }

    @FXML
    private void generateAction(ActionEvent event) {
        if (jarDirectory != null && selectedJarFile != null) {
            progressInfo.setVisible(true);
            progressInfo.setManaged(true);
            progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
            //jdeps --generate-open-module . --multi-release 11 --module-path ./modules ./modules/common-image-3.6.jar
            String tempDirStr = System.getProperty("java.io.tmpdir") + "moditect_" + System.currentTimeMillis();
            boolean mkdir = new File(tempDirStr).mkdir();
            if (mkdir == false) {
                util.showError("Cannot create moditect output directory!", new Exception(System.getProperty("java.io.tmpdir") + "moditect"));
                return;
            }
            String ignoreMissingDepsStr = "";
            if (ignoreMIssingDepsCheckbox.isSelected()) {
                ignoreMissingDepsStr = "--ignore-missing-deps";
            }
            String multiReleaseStr = "";
            if (multireleaseCheckbox.isSelected()) {
                multiReleaseStr = "--multi-release " + releaseVersionField.getText();
            }

            String cmd = "jdeps " + ignoreMissingDepsStr + " --generate-open-module " + tempDirStr + " " + multiReleaseStr + " --module-path " + jarDirectory.getAbsolutePath() + " " + selectedJarFile.getAbsolutePath();
            Runtime rt = Runtime.getRuntime();
            try {
                Process exec = rt.exec(cmd);
                int exitValue = exec.waitFor();
                if (exitValue != 0) {
                    util.showError("Cannot execute jdeps " + cmd, new Exception("Error on cmd " + cmd));
                    return;
                }
                File moduleFile = new File(tempDirStr);
                while (moduleFile.isDirectory() == true) {
                    File[] directories = moduleFile.listFiles(File::isDirectory);
                    if (directories.length == 0) {
                        moduleFile = new File(moduleFile.getAbsolutePath() + "/module-info.java");
                    } else {
                        moduleFile = directories[0];
                    }
                }
                BufferedReader br = new BufferedReader(new FileReader(moduleFile));
                String line;
                textArea.appendText("<module>\n");
                textArea.appendText("\t<artifact>\n");
                textArea.appendText("\t\t<groupId>xxxx</groupId>\n");
                textArea.appendText("\t\t<artifactId>xxxxx</artifactId>\n");
                textArea.appendText("\t</artifact>\n");
                textArea.appendText("\t<moduleInfoSource>\n");
                while ((line = br.readLine()) != null) {
                    textArea.appendText("\t\t"+line + "\n");
                }
                textArea.appendText("\t</moduleInfoSource>\n");
                textArea.appendText("</module>\n");
                //moduleFile.deleteOnExit();
            } catch (IOException ex) {
                Logger.getLogger(ModitectGenController.class.getName()).log(Level.SEVERE, null, ex);
                util.showError("Cannot execute jdeps!", ex);
            } catch (InterruptedException ex) {
                Logger.getLogger(ModitectGenController.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                progressInfo.setVisible(false);
                progressInfo.setManaged(false);
            }
        } else {
            infoLabel.setText("Please select jar file and directory first!");
        }
    }

    @FXML
    private void multiReleaseAction(ActionEvent event) {
        if (multireleaseCheckbox.isSelected()) {
            releaseVersionField.setDisable(false);
            releaseVersionField.setEditable(true);
        } else {
            releaseVersionField.setDisable(true);
        }
    }

}
