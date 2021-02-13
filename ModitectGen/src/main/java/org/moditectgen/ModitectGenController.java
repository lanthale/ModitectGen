package org.moditectgen;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import javafx.fxml.FXML;

import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.concurrent.Task;
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
    @FXML
    private CheckBox genOpenModuleCheckbox;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        Logger.getLogger(ModitectGenController.class.getName()).log(Level.SEVERE, "Init Controller");
        executor = Executors.newSingleThreadExecutor();
        menuBar.useSystemMenuBarProperty().set(true);
        progressInfo.setVisible(false);
        progressInfo.setManaged(false);
        util = new UtilityTools();
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
            progressLabel.setText("Executing...");
            //jdeps --generate-open-module . --multi-release 11 --module-path ./modules ./modules/common-image-3.6.jar
            String tempDirStr = System.getProperty("java.io.tmpdir") + "moditect_" + System.currentTimeMillis();
            boolean mkdir = new File(tempDirStr).mkdir();
            if (mkdir == false) {
                util.showError("Cannot create moditect output directory!", "Error output directory", new Exception(System.getProperty("java.io.tmpdir") + "moditect"));
                return;
            }
            String ignoreMissingDepsStr = "";
            if (ignoreMIssingDepsCheckbox.isSelected()) {
                ignoreMissingDepsStr = "--ignore-missing-deps";
            }
            String genOpenModuleStr = "";
            if (genOpenModuleCheckbox.isSelected()) {
                genOpenModuleStr = "--generate-open-module";
            } else {
                genOpenModuleStr = "--generate-module-info";
            }
            String multiReleaseStr = "";
            if (multireleaseCheckbox.isSelected()) {
                multiReleaseStr = "--multi-release " + releaseVersionField.getText();
            }

            String cmd = "jdeps " + ignoreMissingDepsStr + " " + genOpenModuleStr + " " + tempDirStr + " " + multiReleaseStr + " --module-path " + jarDirectory.getAbsolutePath() + " " + selectedJarFile.getAbsolutePath();
            Task<Void> task = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    Runtime rt = Runtime.getRuntime();
                    Process exec = rt.exec(cmd);
                    int exitValue = exec.waitFor();
                    if (exitValue != 0) {
                        StringBuffer output = new StringBuffer();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(exec.getInputStream()));

                        String line;
                        while ((line = reader.readLine()) != null) {
                            output.append(line + "\n");
                        }
                        Platform.runLater(() -> {
                            util.showError(output.toString(), "Cannot execute " + cmd, new Exception("Error on cmd " + cmd));
                        });
                        return null;
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
                    Platform.runLater(() -> {
                        try {
                            String line;
                            textArea.appendText("<module>\n");
                            textArea.appendText("\t<artifact>\n");
                            textArea.appendText("\t\t<groupId>xxxx</groupId>\n");
                            textArea.appendText("\t\t<artifactId>xxxxx</artifactId>\n");
                            textArea.appendText("\t</artifact>\n");
                            textArea.appendText("\t<moduleInfoSource>\n");
                            while ((line = br.readLine()) != null) {
                                textArea.appendText("\t\t" + line + "\n");
                            }
                            textArea.appendText("\t</moduleInfoSource>\n");
                            textArea.appendText("</module>\n");
                        } catch (IOException ex) {
                            Logger.getLogger(ModitectGenController.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    });
                    //moduleFile.deleteOnExit();                    
                    return null;
                }
            };
            task.setOnFailed((t) -> {
                Logger.getLogger(ModitectGenController.class.getName()).log(Level.SEVERE, null, t.getSource().getException());
                util.showError("Cannot execute jdeps!", "Error in jdeps command", new Exception(t.getSource().getException()));
                progressInfo.setVisible(false);
                progressInfo.setManaged(false);
                progressLabel.setText("");
            });
            task.setOnSucceeded((t) -> {
                progressInfo.setVisible(false);
                progressInfo.setManaged(false);
                progressLabel.setText("");
            });
            Thread th = new Thread(task);
            th.start();
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
