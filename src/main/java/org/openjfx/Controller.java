package org.openjfx;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.scene.image.Image;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

// PicsToMapGUI.fxml:
// <VBox fx:id="outerVBox" prefHeight="400.0" prefWidth="640.0" xmlns="http://javafx.com/javafx/11.0.1" xmlns:fx="http://javafx.com/fxml/1" fx:controller="org.openjfx.Controller">
// fx:controller="org.openjfx.Controller"

public class Controller implements Initializable {

    List<Picture> parsedPictures = null; // where all the parsed EXIF data of all the pictures in the currently selected folder is stored

    BufferedImage currentlyDisplayedImage = null; // null when no image is displayed yet
    Image currentlyDisplayedImageFX = null; // represents the same image as above (just to make frequent calls to resizeDisplayedImage() more efficient)
    // !!! --> saveAction() uses currentlyDisplayedImage, while
    // !!! --> resizeDisplayedImage() uses currentlyDisplayedImageFX

    @FXML
    private MenuItem openMenuItem;
    @FXML
    private MenuItem saveMenuItem;
    @FXML
    private MenuItem quitMenuItem;

    @FXML
    private VBox outerVBox; // the outermost element defined by the Gluon SceneBuilder
    // private final javafx.stage.Window window = outerVBox.getScene().getWindow(); // == null (doesn't work!)

    @FXML
    private TextField folderTextField;

    @FXML
    private ListView<String> fileList;

    @FXML
    private Button generateMapButton;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // ---------- ---------- 1) Resize the displayed image whenever the (window/)imagePane is resized: ---------- ----------
        // -> https://stackoverflow.com/questions/38216268/how-to-listen-resize-event-of-stage-in-javafx
        ChangeListener<Number> sizeListener = (observable, oldValue, newValue) ->
                resizeDisplayedImage();
        // Stage stage = (Stage) outerVBox.getScene().getWindow();
        // ---> throws NullPointerException (see https://stackoverflow.com/questions/13246211/javafx-how-to-get-stage-from-controller-during-initialization)
        imagePane.widthProperty().addListener(sizeListener);
        imagePane.heightProperty().addListener(sizeListener);

        // ---------- ---------- 2) Initialize the ComboBox entries (OSM tile server list) ---------- ----------
        // -> https://stackoverflow.com/questions/35260061/combobox-items-via-scene-builder
        tileServerURLComboBox.getItems().removeAll(tileServerURLComboBox.getItems());
        // -> https://wiki.openstreetmap.org/wiki/Tile_servers
        tileServerURLComboBox.getItems().addAll(
                "https://a.tile.openstreetmap.de/${z}/${x}/${y}.png",
                "https://b.tile.openstreetmap.de/${z}/${x}/${y}.png",
                "https://c.tile.openstreetmap.de/${z}/${x}/${y}.png",
                new Separator(), // -> https://stackoverflow.com/questions/25914924/javafx-choicebox-add-separator-with-type-safety
                "https://a.tile.openstreetmap.org/${z}/${x}/${y}.png",
                "https://b.tile.openstreetmap.org/${z}/${x}/${y}.png",
                "https://c.tile.openstreetmap.org/${z}/${x}/${y}.png",
                new Separator(),
                "http://a.tile.openstreetmap.fr/hot/${z}/${x}/${y}.png",
                "http://b.tile.openstreetmap.fr/hot/${z}/${x}/${y}.png",
                new Separator(),
                "http://a.tile.openstreetmap.fr/osmfr/${z}/${x}/${y}.png",
                "http://b.tile.openstreetmap.fr/osmfr/${z}/${x}/${y}.png",
                "http://c.tile.openstreetmap.fr/osmfr/${z}/${x}/${y}.png",
                new Separator(),
                "https://tiles.wmflabs.org/hikebike/${z}/${x}/${y}.png",
                "http://tiles.wmflabs.org/hillshading/${z}/${x}/${y}.png",
                "https://tiles.wmflabs.org/bw-mapnik/${z}/${x}/${y}.png",
                "https://tiles.wmflabs.org/osm-no-labels/${z}/${x}/${y}.png",
                new Separator(),
                "http://a.tile.stamen.com/toner/${z}/${x}/${y}.png",
                "http://c.tile.stamen.com/watercolor/${z}/${x}/${y}.jpg",
                new Separator(),
                "http://tile.memomaps.de/tilegen/${z}/${x}/${y}.png",
                "https://a.tile.opentopomap.org/${z}/${x}/${y}.png",
                "https://cdn.lima-labs.com/${z}/${x}/${y}.png?free");
        tileServerURLComboBox.getSelectionModel().select("https://a.tile.openstreetmap.de/${z}/${x}/${y}.png");

        // -------------------- All the code below is for the renameChoiceBox: --------------------

        // ---------- ---------- 3) Override the requestFocus() method of renameChoiceBox to {} so that it cannot receive focus anymore ---------- ----------
        // --> Got the idea from: http://a-hackers-craic.blogspot.com/2012/11/disabling-focus-in-javafx.html
        // a) Create a "copy" of the renameChoiceBox with requestFocus() set to { }:
        ChoiceBox<String> newRenameChoiceBox = new ChoiceBox<>() {
            @Override
            public void requestFocus() { } // This prevents the newRenameChoiceBox from receiving focus!!!!!
            // Class initializer:
            {
                // Copy all properties from the renameChoiceBox to this newRenameChoiceBox:
                renameChoiceBox.getProperties().forEach((key, value) -> {
                    this.getProperties().put(key, value);
                    // pane-top-anchor -> 69.0 and
                    // pane-right-anchor -> 10.0 are actually the only two mappings!!!
                });
                // See PicsToMapGUI.fxml file: <ChoiceBox fx:id="renameChoiceBox" ... />
                this.maxWidthProperty().set(renameChoiceBox.getMaxWidth());
                this.minWidthProperty().set(renameChoiceBox.getMinWidth());
                this.prefWidthProperty().set(renameChoiceBox.getPrefWidth());
            }
        };
        // b) "Replace" the renameChoiceBox with the newRenameChoiceBox:
        renameToolAnchorPane.getChildren().removeIf(node -> node instanceof ChoiceBox);
        renameToolAnchorPane.getChildren().add(newRenameChoiceBox);

        // ---------- ---------- 4) Initialize the newRenameChoiceBox entries ("Extra: Rename Tool") ---------- ----------
        newRenameChoiceBox.getItems().removeAll(newRenameChoiceBox.getItems());
        newRenameChoiceBox.getItems().addAll(
                "See all placeholders...",
                "{FILE_NAME} (e.g. image1.jpg)",
                "{FILE_NAME_WO_SUFFIX} (e.g. image1)",
                "{FILE_SUFFIX} (e.g. jpg)",
                "{TIMESTAMP_UNIX} (e.g. 946684800)",
                "{DAY_OF_MONTH} (e.g. 9)",
                "[DAY_OF_MONTH] (e.g. 09)",
                "{DAY_OF_YEAR} (e.g. 42)",
                "[DAY_OF_YEAR] (e.g. 042)",
                "{MONTH_NUMERIC} (e.g. 1)",
                "[MONTH_NUMERIC] (e.g. 01)",
                "{MONTH_SHORT} (e.g. JAN)",
                "{MONTH_LONG} (e.g. JANUARY)",
                "{YEAR} (e.g. 2000)",
                "{HOUR} (e.g. 7)",
                "[HOUR] (e.g. 07)",
                "{MINUTE} (e.g. 7)",
                "[MINUTE] (e.g. 07)",
                "{SECOND} (e.g. 7)",
                "[SECOND] (e.g. 07)");
        newRenameChoiceBox.getSelectionModel().select("See all placeholders...");

        // ---------- ---------- 5) Give the newRenameChoiceBox its actual functionality using a ChangeListener ("Extra: Rename Tool") ---------- ----------
        // a) Do not let the user actually SELECT one of the above items!
        // b) NEW NICE FEATURE: Replace the selection in renameTextField with the selected placeholder.
        // -> https://stackoverflow.com/questions/14522680/javafx-choicebox-events
        newRenameChoiceBox.getSelectionModel().selectedIndexProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observableValue, Number oldValue, Number newValue) {
                // b):
                // Doesn't work: String selectedPlaceholder = newRenameChoiceBox.getSelectionModel().getSelectedItem();
                if (oldValue.intValue() == 0 && newValue.intValue() != 0) {
                    // If a change from "See all placeholders..." to something else occurred:
                    String selectedPlaceholder = newRenameChoiceBox.getItems().get(newValue.intValue());
                    renameTextField.replaceSelection(selectedPlaceholder.split(" ")[0]);
                    // The .split(" ")[0] turns "{FILE_SUFFIX} (e.g. jpg)" into just "{FILE_SUFFIX}"

                    // --> https://docs.oracle.com/javase/8/javafx/api/javafx/scene/control/TextInputControl.html#replaceSelection-java.lang.String-
                    // "Replaces the selection with the given replacement String. If there is no selection,
                    // then the replacement text is simply inserted at the current caret position.
                    // If there was a selection, then the selection is cleared and the given replacement text
                    // inserted."
                }

                // a):
                newRenameChoiceBox.getSelectionModel().select("See all placeholders..."); // reset selection
            }
        });
    }

    final String EXIF_REGEX = ".+\\.(jpg|jpeg|jpe|jfif|heif|heic)";

    @FXML
    private Button chooseButton;

    @FXML
    private void chooseAction() {
        // folderTextField.setText("test"); // works!
        /*
        The javax.swing.JFileChooser.showOpenDialog(null) does not work properly here since
        you have to pass null instead of a proper parent component from Swing. The dialog
        is shown behind the main scene (which becomes unresponsive) and thus it looks like it is hanging.
        See:
        https://stackoverflow.com/questions/53745572/jfilechooser-hangs-on-calling-showopendialog
         */
        File folder = new DirectoryChooser().showDialog(outerVBox.getScene().getWindow());
             // JavaDoc: "the selected directory or null if no directory has been selected"
        if (folder != null && folder.isDirectory()) {
            if (folderTextField.getText().equals(folder.getAbsolutePath())) {
                return; // User selected the SAME folder again: behave AS IF the user had clicked cancel in the open dialog.
            }
            resetGUIAfterNewFolderSelected(); //!!!
            folderTextField.setText(folder.getAbsolutePath());
            // --- List the files in that folder in the ListView: ---
            File[] fileArray = folder.listFiles();
            if (fileArray == null) {
                fileList.setItems(FXCollections.observableArrayList("Error loading the files in this folder!"));
                resetParsedEXIFData();
            } else {
                ObservableList<String> exifCompatibleFiles = FXCollections.observableList( // see: https://docs.oracle.com/javafx/2/collections/jfxpub-collections.htm
                        Arrays.stream(fileArray).filter(File::isFile).map(File::getName)
                                .filter(s -> s.toLowerCase().matches(EXIF_REGEX)).collect(Collectors.toList()));
                //    see: https://stackoverflow.com/questions/14830313/retrieving-a-list-from-a-java-util-stream-stream-in-java-8
                if (exifCompatibleFiles.isEmpty()) {
                    fileList.setItems(FXCollections.observableArrayList("No JPEG or HEIF images in this folder!"));
                    resetParsedEXIFData();
                } else {
                    fileList.setItems(exifCompatibleFiles);
                    parseEXIFDataInSelectedFolder(); //!!!!!!!!!
                }
            }

            resetRenameTool(); // (among others reset the "Undo" button to become the "Rename" button again)
        }

    }

    // Does the necessary GUI resets after the user has selected a new folder:
    private void resetGUIAfterNewFolderSelected() {
        generateMapButton.setDisable(true); // disable the "Generate Map" button (because there is no parsed EXIF data available yet)
        currentlyDisplayedImage = null;
        currentlyDisplayedImageFX = null;
        resizeDisplayedImage(); // (this will actually remove it, as we've just set it to null above)
        progressLabel.setText("");
    }

    private void resetParsedEXIFData() {
        this.parsedPictures = null;
    }

    /**
     * This is one of the actually interesting parts of this program:
     * As soon as the user has selected a folder with at least one EXIF-parsable JPEG or HEIF file,
     * this function will begin parsing all of them (and show progress) and saves the result in the this.parsedPictures ArrayList variable.
     * When this function is done, the "Generate Map" button is enabled and the generateMapAction() function
     * (which is called by the "Generate Map" button) can use the data stored in the this.parsedPictures ArrayList variable.
     */
    private void parseEXIFDataInSelectedFolder() {
        if ("".equals(folderTextField.getText().trim())) {
            return; // nothing selected! (shouldn't actually happen, as this function is called after a folder selection)
        }
        File folder = new File(folderTextField.getText());
        if (!folder.isDirectory()) {
            return; // a file and not a directory is selected (shouldn't actually happen, as a DirectoryChooser is used)
        }

        File[] fileArray = folder.listFiles();
        if (fileArray == null) {
            return; // error; shouldn't actually occur, as this check is already done in chooseAction() which calls this function
        }

        /* // Short and simple way of doing it, but no progress shown :(
        this.parsedPictures = Arrays.stream(fileArray)
                .filter(File::isFile) // only files, not directories
                .filter(f -> f.getName().toLowerCase().matches(EXIF_REGEX)) // only files with JPEG or HEIF suffix
                .map(Picture::createFromFile) // (try to) parse those
                .collect(Collectors.toList());
         */

        List<File> fileList = Arrays.stream(fileArray)
                .filter(File::isFile) // only files, not directories
                .filter(f -> f.getName().toLowerCase().matches(EXIF_REGEX)) // only files with JPEG or HEIF suffix
                .collect(Collectors.toList());
        int fileListSize = fileList.size();

        this.parsedPictures = new ArrayList<>(); // !!!DON'T FORGET!!!

        // ---------- Concurrency part: ---------- ---------- ----------
        // -> https://stackoverflow.com/questions/22772379/updating-ui-from-different-threads-in-javafx
        // -> https://docs.oracle.com/javafx/2/api/javafx/concurrent/Task.html
        Task<Void> task = new Task<Void>() {
            @Override public Void call() {
                updateMessage(progressText(0, fileListSize)); // (initialize (textual) "progress bar")
                for (int i = 0; i < fileList.size(); i++) { // for each File in List<File> fileList:
                    Picture parsedPicture = Picture.createFromFile(fileList.get(i));
                    if (parsedPicture != null) { // Only add non-null Pictures (i.e. those that could be parsed):
                        parsedPictures.add(parsedPicture);
                    }
                    updateMessage(progressText(i + 1, fileListSize)); // update (textual) "progress bar"
                    // (don't forget to add the +1, after iteration 0 1 file is done!!)
                }
                // ---------- ---------- ---------- ---------- ----------
                Collections.sort(parsedPictures); // sort the parsed pictures by their timestamp
                if (parsedPictures.size() >= 2) {
                    updateMessage("Parsing EXIF data: Finished! " + parsedPictures.size() + "/" + fileListSize + " were successful!");
                    generateMapButton.setDisable(false); // The user can now use the "Generate Map" button!!!
                } else { // No or only one picture was successfully parsed for EXIF data -> cannot generate Map!!!
                    updateMessage("Parsing EXIF data: Finished! Only " + parsedPictures.size() + "/" + fileListSize + " were successful! :(");
                    generateMapButton.setDisable(true); // cannot generate a map from just 0 or 1 picture(s) !!!
                }
                try {
                    Thread.sleep(100);
                    //!!!!VERY IMPORTANT -> otherwise the unbind() below happens too quickly, so that
                    // the updateMessage() calls from above don't get through anymore!!
                } catch (InterruptedException ex) {
                    //
                }
                progressLabel.textProperty().unbind(); //!!!!VERY IMPORTANT -> OTHERWISE THE WHOLE APP DOESN'T REALLY WORK ANYMORE!!!
                return null;
            }
        };
        progressLabel.textProperty().bind(task.messageProperty());
        new Thread(task).start();
    }

    private static String progressText(int noDone, int noTotal) {
        // Parsing EXIF data: [-------------------------] 0% (0/678)
        // Parsing EXIF data: [=========================] 100% (678/678)

        double progress = (double) noDone / noTotal;
        String percentage = "" + (int)Math.floor(100*progress) + "%"; // floor -> 99% until last pic is done (100% means actually finished!)

        final int TOTAL_NO_OF_SYMBOLS = 25; // 25 symbols: [-------------------------]
        int noOfDoneSymbols = (int)Math.floor(TOTAL_NO_OF_SYMBOLS*progress); // floor (see above)

        return "Parsing EXIF data: ["
                + "=".repeat(noOfDoneSymbols) + "-".repeat(TOTAL_NO_OF_SYMBOLS-noOfDoneSymbols)
                + "] "
                + percentage
                + " (" + noDone + "/" + noTotal + ")";
    }

    @FXML
    private void saveAction() {
        if (currentlyDisplayedImage == null) {
            java.awt.Toolkit.getDefaultToolkit().beep();
            // https://stackoverflow.com/questions/10771441/java-equivalent-of-c-sharp-system-beep
        } else {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setInitialFileName("PicsToMap-image.png"); // (default)
            fileChooser.getExtensionFilters().addAll( // ????
                    new ExtensionFilter("PNG", "*.png"), // (default selection)
                    new ExtensionFilter("GIF", "*.gif"),
                    new ExtensionFilter("TIFF", "*.tiff"),
                    new ExtensionFilter("JPEG", "*.jpg"), // does not support alpha/transparency! (see work-around below)
                    new ExtensionFilter("BMP", "*.bmp")); // does not support alpha/transparency!
            File destFile = fileChooser.showSaveDialog(outerVBox.getScene().getWindow());
            if (destFile != null) {
                String suffix = destFile.getName().substring(destFile.getName().lastIndexOf(".") + 1);
                try {
                    if (!javax.imageio.ImageIO.write(currentlyDisplayedImage, suffix, destFile)) { // returns false "if no appropriate writer is found" (JPG/BMP)
                        if (!javax.imageio.ImageIO.write(removeTransparency(currentlyDisplayedImage), suffix, destFile)) {
                            java.awt.Toolkit.getDefaultToolkit().beep(); // even second try (with transparency removed) failed
                        }
                    }
                } catch (java.io.IOException ex) {
                    java.awt.Toolkit.getDefaultToolkit().beep();
                    ex.printStackTrace();
                }
            }
        }
    }

    // Visual Settings:
    @FXML
    private CheckBox grayscaleCheckbox;
    @FXML
    private Slider grayscaleSlider;
    @FXML
    private TextField marginSizeTextField;
    @FXML
    private TextField customColorsTextField;
    // Filters:
    @FXML
    private CheckBox ignoreOutliersCheckbox;
    @FXML
    private TextField maxDeviationsTextField;
    @FXML
    private CheckBox ignoreBeforeCheckbox;
    @FXML
    private DatePicker beforeDatePicker;
    @FXML
    private TextField beforeHourTextField;
    @FXML
    private TextField beforeMinuteTextField;
    @FXML
    private CheckBox ignoreAfterCheckbox;
    @FXML
    private DatePicker afterDatePicker;
    @FXML
    private TextField afterHourTextField;
    @FXML
    private TextField afterMinuteTextField;
    @FXML
    private CheckBox ignoreRegexCheckbox;
    @FXML
    private TextField regexTextField;
    // OpenStreetMap Settings:
    @FXML
    private ComboBox<Object> tileServerURLComboBox;
    // Why Object?! -> https://stackoverflow.com/questions/25914924/javafx-choicebox-add-separator-with-type-safety
    @FXML
    private TextField userAgentTextField;
    @FXML
    private TextField changeZoomLevelTextField;
    @FXML
    private TextField maxTileDownloadsTextField;

    @FXML
    private Label progressLabel;

    @FXML
    private Pane imagePane;

    @FXML
    private void generateMapAction() {
        // Disable the "Generate Map" Button after the user has clicked it, so that the user cannot hit it twice
        // (which would (probably?) lead to a second Task<> being created, competing with the first!!).
        // Also disable the "Choose..." Button so that the user cannot start a NEW EXIF-parsing !!!!!
        // (which would/could jumble everything up) !!!!!
        generateMapButton.setDisable(true); // this is reverted in task.setOnSucceeded() & task.setOnFailed() (see "Re-enable" part below)
        chooseButton.setDisable(true);

        // Note: this 'wrapper function' is necessary to separate
        // a) the lengthy code responsible for generating the map (in generateMap())
        // from
        // b) the code responsible for concurrency
        //    (we want to see the progress, especially the OSM Tile downloads are very time-intensive!), see below:
        Task<Void> task = new Task<Void>() {
            @Override
            public Void call() {
                generateMap(this::updateMessage); // (the actual code that we want to execute in a separate Thread)
                // ----- ---- Code below copied from parseEXIFDataInSelectedFolder(): ----- -----
                /*try {
                    Thread.sleep(100);
                    //!!!!VERY IMPORTANT -> otherwise the unbind() below happens too quickly, so that
                    // the updateMessage() calls from above don't get through anymore!!
                } catch (InterruptedException ex) {
                    //
                }*/
                return null;
            }
        };
        progressLabel.textProperty().bind(task.messageProperty());

        // -> setOnSucceeded() found on: https://stackoverflow.com/questions/32773115/javafx-show-dialogue-after-thread-task-is-completed
        task.setOnSucceeded(e -> {
            progressLabel.textProperty().unbind(); //!!!!VERY IMPORTANT -> OTHERWISE THE WHOLE APP DOESN'T REALLY WORK ANYMORE!!!
            resizeDisplayedImage(); // display the newly generated map
            progressLabel.setText("Successfully generated map and displayed it. Click 'Save...' button to save as file.");

            // Re-enable the two disabled Buttons "Generate Map" and "Choose...":
            generateMapButton.setDisable(false);
            chooseButton.setDisable(false);
        });
        task.setOnFailed(e -> {
            progressLabel.textProperty().unbind(); //!!!!VERY IMPORTANT -> OTHERWISE THE WHOLE APP DOESN'T REALLY WORK ANYMORE!!!
            currentlyDisplayedImage = null;
            currentlyDisplayedImageFX = null;
            resizeDisplayedImage(); // reset the map display.
            progressLabel.setText("Map generation failed: " + throwableToString(task.getException()));
            // -> http://discoveration.de/it/java-fx-concurrency-tasks-und-services/625/

            // Re-enable the two disabled Buttons "Generate Map" and "Choose...":
            generateMapButton.setDisable(false);
            chooseButton.setDisable(false);
        });
        // Note that "Task states [SUCCEEDED/FAILED] are not intended to be used for user logic"
        // -> https://stackoverflow.com/questions/13935366/javafx-concurrent-task-setting-state
        new Thread(task).start();
    }

    /**
     * (c.f. the old Main Class, a lot of the code below is copied from there!!)
     * @param updateMessageConsumer accepted Strings will be passed to Task.updateMessage() in generateMapAction()
     *                              and eventually update the bound progressLabel.textProperty()
     */
    private void generateMap(Consumer<String> updateMessageConsumer) {
        if (this.parsedPictures == null || this.parsedPictures.size() <= 1) {
            // Actually no (or just a single) pictures have been parsed yet:
            // -> something went wrong, cannot actually generate a map :(
            java.awt.Toolkit.getDefaultToolkit().beep();
            generateMapButton.setDisable(true);
        }

        updateMessageConsumer.accept("Started map generation...");

        // ---------- 1) Collect all the settings made by the user in the nice GUI: ----------
        //////progressLabel.setText("Initializing user settings...");
        // Visual Settings:
        boolean sett_GRAYSCALE_BACKGROUND = grayscaleCheckbox.isSelected(); //Doc: "Indicates whether this CheckBox is checked."
        int sett_GRAYSCALE_PERCENTAGE = (int) grayscaleSlider.getValue(); // (between 0 and 100)
        double sett_MARGIN_PERCENTAGE = parsePositiveDoubleDefault(marginSizeTextField.getText(), 20.0);
        String sett_CUSTOM_COLORS = customColorsTextField.getText();
        // Filters:
        boolean sett_IGNORE_OUTLIERS = ignoreOutliersCheckbox.isSelected();
        double sett_STANDARD_DEVIATIONS = parsePositiveDoubleDefault(maxDeviationsTextField.getText(), 5.0);
        boolean sett_IGNORE_TAKEN_BEFORE = ignoreBeforeCheckbox.isSelected();
        LocalDate sett_DATE_BEFORE = beforeDatePicker.getValue();
        int sett_HOUR_BEFORE = parsePositiveIntegerDefault(beforeHourTextField.getText(), 0);
        int sett_MINUTE_BEFORE = parsePositiveIntegerDefault(beforeMinuteTextField.getText(), 0);
        boolean sett_IGNORE_TAKEN_AFTER = ignoreAfterCheckbox.isSelected();
        LocalDate sett_DATE_AFTER = afterDatePicker.getValue();
        int sett_HOUR_AFTER = parsePositiveIntegerDefault(afterHourTextField.getText(), 23);
        int sett_MINUTE_AFTER = parsePositiveIntegerDefault(afterMinuteTextField.getText(), 59);
        boolean sett_IGNORE_REGEX = ignoreRegexCheckbox.isSelected();
        String sett_IGNORE_REGEX_STR = regexTextField.getText();
        // OpenStreetMap Settings:
        String sett_OSM_TILE_SERVER_URL = (String) tileServerURLComboBox.valueProperty().getValue();
        if (sett_OSM_TILE_SERVER_URL.trim().equals("")) { // User has left TILE_SERVER_URL TextField blank, use default:
            sett_OSM_TILE_SERVER_URL = "https://a.tile.openstreetmap.de/${z}/${x}/${y}.png"; // = default!
        }
        String sett_OSM_USER_AGENT = userAgentTextField.getText();
        int sett_OSM_CHANGE_ZOOM_LEVEL = parseIntegerDefault(changeZoomLevelTextField.getText(), 0);
        int sett_OSM_MAX_TILE_DOWNLOADS = parsePositiveIntegerDefault(maxTileDownloadsTextField.getText(), 500);

        // ---------- 2) Initialize the PictureFilter (see Command Line Args in old Main Class): ----------
        // Note: The PictureFilter Class supports:
        // "filterDate", "filterDates", "filterDateTime", "filterDatesTime" or "filterMillisecondRange"
        PictureFilter picFilter;
        if (sett_IGNORE_TAKEN_BEFORE == false && sett_IGNORE_TAKEN_AFTER == false) {
            picFilter = new PictureFilter(); // (Constructor 0: don't filter at all)
        } else {
            // Use the brand new PictureFilter Constructor, specifically created for this GUI version:
                picFilter = new PictureFilter(sett_IGNORE_TAKEN_BEFORE, sett_DATE_BEFORE, sett_HOUR_BEFORE, sett_MINUTE_BEFORE,
                        sett_IGNORE_TAKEN_AFTER, sett_DATE_AFTER, sett_HOUR_AFTER, sett_MINUTE_AFTER);
        }

        // ---------- 3) Apply the PictureFilter (before/after Date/Time) above: ----------
        List<Picture> parsedPicturesCopy = this.parsedPictures.stream()
                .filter(pic -> !picFilter.isFiltered(pic)) // only keep those that are NOT filtered!

        // ---------- 4) Apply the RegEx filter: ----------
                .filter(pic -> !sett_IGNORE_REGEX || !pic.originalFileName.matches(sett_IGNORE_REGEX_STR))
                // -> when sett_IGNORE_REGEX == false, nothing will be filtered here!!
                .collect(Collectors.toList());

        // ---------- 4.5) See if we've just filtered out too many pictures!: ----------
        if (parsedPicturesCopy.size() <= 1) {
            showErrorDialogLater("Error: Filters too strict!",
            "The date & time / Regex filters you specified are too strict. "
                + "There are now only " + parsedPicturesCopy.size() + " of " + this.parsedPictures.size()
                    + " total pictures remaining that were not filtered. "
                + "Please disable some of your filters or make them less strict.");
            throw new RuntimeException("Date & time / Regex filters too strict!"); // (instead of a simple return;)
        }

        // ---------- 5) Apply the outlier filter: ----------
        if (sett_IGNORE_OUTLIERS) { //!!(I initially totally forgot this check)!!
            // We have to do a little workaround here as my originally coded removeOutliers() works on an array instead of a List:
            Picture[] picArray = parsedPicturesCopy.toArray(new Picture[0]);
            Main.removeOutliers(picArray, sett_STANDARD_DEVIATIONS); // this function sets the outliers to 'null'
            parsedPicturesCopy = Arrays.stream(picArray).filter(Objects::nonNull).collect(Collectors.toList());


            // ---------- 5.5) See if we've just filtered out too many pictures!: ----------
            if (parsedPicturesCopy.size() <= 1) {
                showErrorDialogLater("Error: 'Outlier' filter is too strict!",
                        "Please increase the 'max. standard deviations' setting or disable the 'outlier' filter entirely.");
                throw new RuntimeException("'Outlier' filter is too strict!"); // (instead of a simple return;)
            }
        }

        // ----- 5.75) Sort the pictures by timestamp again, now after all the filters have been applied: -----
        Collections.sort(parsedPicturesCopy);

        // ---------- 6) Determine the map boundaries, including the user-specified margin: ----------
        // Ye olde Main class used a loop with 2 if statements, here we're doing a 4-liner with streams!:
        double minLatitude = parsedPicturesCopy.stream().map(pic -> pic.latitude).min(Comparator.naturalOrder()).get();
        double maxLatitude = parsedPicturesCopy.stream().map(pic -> pic.latitude).max(Comparator.naturalOrder()).get();
        double minLongitude = parsedPicturesCopy.stream().map(pic -> pic.longitude).min(Comparator.naturalOrder()).get();
        double maxLongitude = parsedPicturesCopy.stream().map(pic -> pic.longitude).max(Comparator.naturalOrder()).get();
        if (sett_MARGIN_PERCENTAGE > 0.0) { // (we have to do nothing further when the user wants a 0% margin!)
            // ----- Copied from the old Main class: -----
            // in(de)crease the min/max lat/long values accordingly:
            double latitudeMargin = (sett_MARGIN_PERCENTAGE/100.0)*Math.abs(maxLatitude-minLatitude); // Math.abs() just to be sure...
            double longitudeMargin = (sett_MARGIN_PERCENTAGE/100.0)*Math.abs(maxLongitude-minLongitude);
            minLatitude -= latitudeMargin;
            maxLatitude += latitudeMargin;
            minLongitude -= longitudeMargin;
            maxLongitude += longitudeMargin;
        }

        // ---------- 7) Draw the map background, using the Map class (show download progress!!!): ----------
        updateMessageConsumer.accept("Drawing map background: Started.");
        Map m = new Map(minLatitude, maxLatitude, minLongitude, maxLongitude, parsedPicturesCopy.size());
        String errorMsg = m.drawOnlineMapBackground(
                sett_GRAYSCALE_BACKGROUND, sett_GRAYSCALE_PERCENTAGE,
                sett_OSM_TILE_SERVER_URL, sett_OSM_USER_AGENT, sett_OSM_CHANGE_ZOOM_LEVEL, sett_OSM_MAX_TILE_DOWNLOADS,
                updateMessageConsumer);
        if (!errorMsg.equals("")) { // Drawing the map background from online data was not successful!
            showErrorDialogLater("Error: OSM Tile Server Download failed!",
                    "Drawing the map background from online data failed. "
                + "Try choosing a different Tile Server URL or consider specifying a custom User Agent. "
                + "Error message:\n\n"
                + errorMsg);
            updateMessageConsumer.accept("Drawing map background: Failed :(");
            throw new RuntimeException("OSM Tile Server Download failed!"); // (instead of a simple return;)
        }
        updateMessageConsumer.accept("Drawing map background: Finished successfully.");

        // ---------- 8) Draw the points onto the map, using the user-specified custom colors: ----------
        ArrayList<Integer> legendNumbersList = new ArrayList<>();
        ArrayList<Color> legendColorsList = new ArrayList<>();
        String customColors = sett_CUSTOM_COLORS.replace(" ", ""); // remove whitespaces (makes parsing a lot simpler)
        // --- Initialize the (custom) colors: ---
        Color[] colors;
        if (customColors.equals("")) { // user wants to use default colors:
            colors = new Color[]{ // The 12 default colors (color wheel by Johannes Itten):
                    new Color(244, 229, 0), // yellow
                    new Color(196, 3, 125), // pink
                    new Color(0, 142, 91), // dark green
                    new Color(234, 98, 31), // dark orange
                    new Color(42, 113, 176), // blue
                    new Color(253, 198, 11), // light orange
                    new Color(109, 57, 139), // purple
                    new Color(140, 187, 38), // light green
                    new Color(227, 35, 34), // red
                    new Color(6, 150, 187), // light blue
                    new Color(241, 142, 28), // orange
                    new Color(68, 78, 153) // dark blue
            };
        } else { // user wants to use his own colors, parse his/her String:
            String[] strColors = customColors.split(",");
            colors = new Color[strColors.length];
            try {
                for (int i = 0; i < strColors.length; i++) {
                    String colStr = strColors[i];
                    if (colStr.startsWith("#")) {
                        colStr = colStr.substring(1); // remove a leading '#' if present
                    }
                    int red = Integer.parseInt(colStr.substring(0, 2), 16);
                    int green = Integer.parseInt(colStr.substring(2, 4), 16);
                    int blue = Integer.parseInt(colStr.substring(4, 6), 16);
                    colors[i] = new Color(red, green, blue); // Constructor: Color(int r, int g, int b)
                }
            } catch (Exception nfex) { // e.g. '#00zz00'
                showErrorDialogLater("Error: Invalid color specifications!",
                        "Please check whether your colors are correctly formatted.");
                throw new RuntimeException("Invalid color specifications!"); // (instead of a simple return;)
            }
        }
        // start at i=1 because the starting point will be drawn at the very end!
        for (int i = 1; i < parsedPicturesCopy.size(); i++) { // for all pictures whose EXIF data shall be a point on the map:
            Picture currentPicture = parsedPicturesCopy.get(i);
            Picture previousPicture = parsedPicturesCopy.get(i - 1);
            if (currentPicture == null) {
                continue; // (skip nulls, in this GUI version this shouldn't actually occur!!)
            }
            // --- NEW FEATURE: change color every day: ---
            if (i==1 || (currentPicture.getDayOfYear() != previousPicture.getDayOfYear())) { // A new day has dawned. (or we just started drawing, otherwise the first day would always be default Color.RED)
                // Change the color we use for drawing:
                m.currentColor = colors[currentPicture.getDayOfYear() % colors.length]; // (in the GUI adaptation, this line now encompasses both the default & custom color choice!)
                // Add this information to the map legend:
                legendNumbersList.add(currentPicture.getDayOfMonth());
                legendColorsList.add(m.currentColor);
            }
            // --- --- --- --- --- --- --- --- --- --- ---
            m.drawPoint(currentPicture.latitude, currentPicture.longitude, false); // last param is boolean:isStartPoint
            // connect the newly drawn point to the previous one (unless its a different day!!):
            if (currentPicture.getDayOfYear() == previousPicture.getDayOfYear()) {
                m.drawLine(currentPicture.latitude, currentPicture.longitude, previousPicture.latitude, previousPicture.longitude);
            }
        }
        // !!NEW: Draw the (green) starting point last so that is doesn't vanish behind the other stuff:
        m.drawPoint(parsedPicturesCopy.get(0).latitude, parsedPicturesCopy.get(0).longitude, true); // last param is boolean:isStartPoint

        // ---------- 9) At last: draw the headline, color legend and min/max lat/long values: ----------
        m.drawHeadline(parsedPicturesCopy.get(0).printableDateTime() + " – " + parsedPicturesCopy.get(parsedPicturesCopy.size()-1).printableDateTime());
        // (draw headline after drawing the map so that it is definitely still fully visible)
        // ----- ----- ----- ----- ----- Draw color legend: ----- ----- ----- ----- -----
        Integer[] legendNumbersIntegers = legendNumbersList.toArray(new Integer[0]);
        int[] legendNumbers = new int[legendNumbersIntegers.length];
        for (int i = 0; i < legendNumbersIntegers.length; i++) {
            legendNumbers[i] = legendNumbersIntegers[i]; // convert Integer Array into int Array ...
        }
        Color[] legendColors = legendColorsList.toArray(new Color[0]);
        m.drawLegend(legendNumbers, legendColors);
        // ----- ----- ----- ----- ----- Draw min/max lat/long values: ----- ----- ----- ----- -----
        // Write down min/max lat/long values at the bottom of the image ():
        // !!! South is positive, North is negative !!! Remember: top left is (0,0) --> therefore multiply all latitudes by (-1) & swap their min/max-order !!!
        String str = "Latitude (North/South) range: " + Main.formatGeo(maxLatitude-minLatitude) + " (" + Main.formatGeo((-1)*maxLatitude) + " to " + Main.formatGeo((-1)*minLatitude) + ")";
        str += "  |  Longitude (East/West) range: " + Main.formatGeo(maxLongitude-minLongitude) + " (" + Main.formatGeo(minLongitude) + " to " + Main.formatGeo(maxLongitude) + ")";
        str += "  |  Center: " + Main.formatGeo((-1)*(minLatitude+(maxLatitude-minLatitude)/2)) + ","+ Main.formatGeo(minLongitude+(maxLongitude-minLongitude)/2);
        m.drawCaption(str);
        // ----- ----- ----- ----- ----- ----- ----- ----- ----- ----- ----- ----- ----- ----- -----

        // ---------- 10) PREPARE TO Display the now completely finished map in the imagePane: ----------
        currentlyDisplayedImage = m.getImage(); // = generateTestBufferedImage() // !!TADA!!
        currentlyDisplayedImageFX = convertToFxImage(currentlyDisplayedImage);
        // For some reason, the code below didn't work here -> moved to task.setOnSucceeded() (above)
        //resizeDisplayedImage();
        //updateMessageConsumer.accept("Finished generating map and displayed it. Click 'Save...' button to save as file.");
    }

    /*
    https://stackoverflow.com/questions/19190832/what-makes-a-javafx-2-task-succeed-fail:
    "Your code is not threadsafe you should not being showing dialogs and updating items in
    list views in your task call method without wrapping those operations in Platform.runLater."
     */

    // Used above in generateMap() whenever some of the user's settings have lead to some sort of error:
    private static void showErrorDialogLater(String shortText, String longText) {
        // "schedule the display of the dialog on the FX Application thread, from the background thread:"
        // ---> https://stackoverflow.com/questions/32773115/javafx-show-dialogue-after-thread-task-is-completed
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, "", ButtonType.OK);
            alert.setTitle("Error");
            alert.setHeaderText(shortText);
            alert.setContentText(longText);
            alert.showAndWait();
        });
    }

    // Used below in renameAction():
    private static void showErrorDialogNow(String shortText, String longText) {
        Alert alert = new Alert(Alert.AlertType.ERROR, "", ButtonType.OK);
        alert.setTitle("Error");
        alert.setHeaderText(shortText);
        alert.setContentText(longText);
        alert.showAndWait();
    }

    private static void beepAndShowErrorDialogNow(String shortText, String longText) {
        java.awt.Toolkit.getDefaultToolkit().beep();
        showErrorDialogNow(shortText, longText);
    }

    // Called when clicking the "Generate Map" button, but also everytime the (window/)imagePane is resized:
    private void resizeDisplayedImage() {
        // Image fxImage = convertToFxImage(currentlyDisplayedImage); // how it dit it previously, however very inefficient

        if (currentlyDisplayedImageFX != null) {
            ImageView imageView = new ImageView(); // -> see https://docs.oracle.com/javase/8/javafx/api/javafx/scene/image/ImageView.html
            imageView.setImage(currentlyDisplayedImageFX);
            imageView.setPreserveRatio(true); // !!!important!!!
            imageView.setSmooth(true);
            imageView.setCache(true);
            imageView.setFitWidth(imagePane.getWidth());
            imageView.setFitHeight(imagePane.getHeight());
            imagePane.getChildren().removeAll(imagePane.getChildren()); // important: remove the currently displayed image
            imagePane.getChildren().add(imageView);
        } else { // currentlyDisplayedImageFX == null -->
            imagePane.getChildren().removeAll(imagePane.getChildren()); // --> remove the currently displayed image!!
        }
    }

    private double parseDoubleDefault(String str, double defaultValue) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private double parsePositiveDoubleDefault(String str, double defaultValue) {
        try {
            double parsedValue = Double.parseDouble(str);
            if (parsedValue >= 0.0) {
                return parsedValue;
            } else {
                return defaultValue;
            }
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private int parseIntegerDefault(String str, int defaultValue) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private int parsePositiveIntegerDefault(String str, int defaultValue) {
        try {
            int parsedValue = Integer.parseInt(str);
            if (parsedValue >= 0) {
                return parsedValue;
            } else {
                return defaultValue;
            }
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    // For some(?) reason javafx.embed.swing.SwingFXUtils cannot be found and Maven also cannot find the
    // dependency javafx-swing (https://mvnrepository.com/artifact/org.openjfx/javafx-swing/14).
    // That means we cannot just use SwingFXUtils.toFXImage()!
    // - https://stackoverflow.com/questions/52558103/what-has-happened-to-swingfxutils
    // The code below is copied from: https://stackoverflow.com/questions/30970005/bufferedimage-to-javafx-image
    private static Image convertToFxImage(BufferedImage image) {
        WritableImage wr = null;
        if (image != null) {
            wr = new WritableImage(image.getWidth(), image.getHeight());
            PixelWriter pw = wr.getPixelWriter();
            for (int x = 0; x < image.getWidth(); x++) {
                for (int y = 0; y < image.getHeight(); y++) {
                    pw.setArgb(x, y, image.getRGB(x, y));
                }
            }
        }
        return new ImageView(wr).getImage();
    }

    // This function enables us to also save the map image (which has an alpha channel!) as a JPEG or BMP file:
    // Code copied from -> https://stackoverflow.com/questions/26918675/removing-transparency-in-png-bufferedimage
    private static BufferedImage removeTransparency(BufferedImage img) {
        BufferedImage copy = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = copy.createGraphics();
        g2d.setColor(Color.WHITE); // (Or what ever fill color you want...)
        g2d.fillRect(0, 0, copy.getWidth(), copy.getHeight());
        g2d.drawImage(img, 0, 0, null);
        g2d.dispose();
        return copy;
    }

    // (for testing purposes only, when the PicsToMap generating logic wasn't implemented/copied yet)
    private static BufferedImage generateTestBufferedImage() {
        BufferedImage bimg = new BufferedImage(2000, 1000, BufferedImage.TYPE_INT_ARGB);
        Graphics gr = bimg.getGraphics();
        gr.setColor(Color.RED);
        gr.fillRect(0, 0, 2000, 1000);
        gr.setColor(Color.BLUE);
        gr.fillRect(0, 0, 2000, 200);
        gr.fillRect(0, 800, 2000, 200);
        gr.fillRect(0, 0, 200, 1000);
        gr.fillRect(1800, 0, 200, 1000);
        /*
        // ----- REMOVE: javax.swing.GrayFilter Test (copied from Map Class) -----
        java.awt.image.ImageFilter filter = new javax.swing.GrayFilter(true, 0); // JavaDoc: "an int in the range 0..100 that determines the percentage of gray, where 100 is the darkest gray, and 0 is the lightest"
        java.awt.image.ImageProducer producer = new java.awt.image.FilteredImageSource(bimg.getSource(), filter);
        bimg.getGraphics().drawImage(java.awt.Toolkit.getDefaultToolkit().createImage(producer), 0, 0, null);
        // ----- REMOVE -----
         */
        return bimg;
    }


    @FXML // (called by clearTileCacheButton)
    private void clearTileCacheAction() {
        // Clear the HashMap<String, Image> that stores all the OSM Tiles that have been downloaded since the app started:
        OSMTile.temporarilyStoredImages.clear();
    }


    // ----- ----- ----- Menu Actions: ----- ----- -----

    @FXML
    private void menuAboutAction() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.OK);
        alert.setTitle("PicsToMap");
        alert.setHeaderText("by Kendrick Grünberg (2020)");
        alert.setContentText("This little program parses all JPEG and HEIF pictures in a specified folder " +
                "for their time and location EXIF data. From that, it generates a map " +
                "showing the movement of the camera holder graphically.\n" +
                "Especially useful for vacation photos!");
        alert.showAndWait();
        // Dialog.show() = non-blocking dialog ; Dialog.showAndWait() = blocking dialog
    }

    @FXML
    private void menuQuitAction() {
        System.exit(0);
    }

    // ----- ----- ----- "Extra: Rename Tool" ----- ----- -----

    @FXML
    private AnchorPane renameToolAnchorPane;
    @FXML
    private TextField renameTextField;
    @FXML
    private Button renameUndoButton;
    @FXML
    private ProgressBar renameProgressBar;
    @FXML
    private Label renameProgressLabel;
    @FXML
    private ChoiceBox<String> renameChoiceBox;

    private final Collection<RenameOperation> renameOperations = new ArrayList<>();

    // - Executed whenever a new folder is selected by the user.
    //   (sticking with the "Undo" operation isn't sensible anymore; it's too late for the user to do that now...)
    // - Also executed after the "Undo" operation has finished.
    private void resetRenameTool() {
        renameUndoButton.setText("Rename");
        renameProgressBar.setProgress(0.0);
        renameProgressLabel.setText("–––%");

        renameOperations.clear();
        // --> We don't want the operations from the previous folder to stick around!
        // Otherwise, using "Undo" on the second folder would result in BOTH renamings to be undone. Very bad!
    }

    @FXML
    private void renameUndoAction() {
        // The renameUndoButton is essentially two Buttons in one, both "Rename" and "Undo":
        if ("Rename".equals(renameUndoButton.getText())) {
            renameAction();
        } else if ("Undo".equals(renameUndoButton.getText())) {
            undoAction();
        } else {
            java.awt.Toolkit.getDefaultToolkit().beep(); // (should never happen)
        }
    }

    /*
    ===== RegEx explanation (you can also paste the regex string above into https://regexr.com/): =====
    - One or more of the following capturing group:
        - 0 or more of any character that's not {,[,} or ]
        - a { or [ character
        - 1 or more characters from capital A-Z (underscores _ are also allowed)
        - a } or ] character
        - 0 or more of any character that's not {,[,} or ]
             */
    private static final String PLACEHOLDER_STRING_REGEX = "([^{\\[}\\]]*[{\\[][A-Z_]+[}\\]][^{\\[}\\]]*)+";

    private void renameAction() {
        // Save all the information we need -
        // to prevent weird behaviour in case the user decides to select a new folder or change the
        // placeholder String mid-progress!!
        final String folderStr = folderTextField.getText();
        final Path folderPath = new File(folderStr).toPath();
        final String renamePlaceholderStr = renameTextField.getText();
        final ArrayList<Picture> parsedPicturesRenameCopy = parsedPictures == null ? new ArrayList<>() : new ArrayList<>(parsedPictures);

        if ("".equals(folderStr)) { // no folder specified
            beepAndShowErrorDialogNow("No folder specified yet!", "");
        } else if (!renamePlaceholderStr.matches(PLACEHOLDER_STRING_REGEX)) {
            beepAndShowErrorDialogNow("You specified an invalid Placeholder String!",
                    "Please check your parentheses and whether all placeholders consist only of capital letters and underscores. " +
                    "You need to include at least one placeholder.");
        } else if (generateMapButton.isDisabled()) {
            beepAndShowErrorDialogNow(
                    "Please wait for EXIF-parsing to finish or choose a folder with at least 2 EXIF-parsable pictures!",
                    "");
        } else if (parsedPicturesRenameCopy.isEmpty()) {
            // (this case shouldn't actually occur)
            beepAndShowErrorDialogNow("Error: There are no parsed pictures to rename!", "");
        } else {
            // --- Before starting the renaming Task, check whether only available placeholders were used: ---
            Collection<String> unsupportedPlaceholders;
            try {
                unsupportedPlaceholders = extractPlaceholders(renamePlaceholderStr);
            } catch (Exception ex) { // extractPlaceholders() threw an Exception!!
                beepAndShowErrorDialogNow("Your placeholder String caused an internal error:",
                    throwableToString(ex));
                return; // 'cancel' the "Rename" action
            }
            unsupportedPlaceholders.removeAll(ALL_AVAILABLE_PLACEHOLDERS);
            if (!unsupportedPlaceholders.isEmpty()) {
                beepAndShowErrorDialogNow("The following placeholders are not supported: ",
                        String.join(", ", unsupportedPlaceholders));
                // -> https://stackoverflow.com/questions/395401/printing-java-collections-nicely-tostring-doesnt-return-pretty-output
                // using String.join() becauseCollection.toString() has enclosing [ and ] 's
                //     which is confusing because the placeholders themselves use [] as well
                return; // 'cancel' the "Rename" action
            }


            // Disable the "Rename" Button after the user has clicked it, so that the user cannot hit it twice
            // (which would (probably?) lead to a second Task<> being created, competing with the first!!):
            renameUndoButton.setDisable(true); // setDisable(false) is done in task.setOnSucceeded() & task.setOnFailed()

            // Cf. code in generateMapAction() (for detailed comments/explanations) !!:
            Task<Void> task = new Task<Void>() {
                @Override
                public Void call() {
                    int errorCounter = 0;
                    for (int i = 0; i < parsedPicturesRenameCopy.size(); i++) {
                        Picture pic = parsedPicturesRenameCopy.get(i); // (no for-each-loop because we need the index for updateProgress())

                        String newName = replacePlaceholders(renamePlaceholderStr, pic);

                        // Rename folder/pic.originalFileName to newName (if possible) (cf. PhotoCollectionSorter.renameFile()):
                        try {
                            //Files.move(folderPath.resolve(pic.originalFileName), folderPath.resolve(newName)); // old, without possibility to undo later on
                            renameOperations.add(new RenameOperation(folderPath, pic.originalFileName, newName)._do());
                        } catch (Exception ex) { // (renaming might fail, e.g. FileAlreadyExistsException)
                            errorCounter++; // this is so we can see at the end whether there were "too many" such failures!
                        }

                        this.updateProgress(i + 1, parsedPicturesRenameCopy.size());
                        this.updateMessage("" +
                                (int)Math.floor( // percentage done (rounded down)
                                        100*((double)(i+1)/parsedPicturesRenameCopy.size()))
                                + "%");
                    }
                    // ----- Show an error dialog when there were "too many" renaming failures: -----
                    if (errorCounter == parsedPicturesRenameCopy.size()) { // 100% failure rate:
                        showErrorDialogLater(
                                "Renaming of all " + errorCounter + "/" + errorCounter + " files failed!",
                                "");
                    } else if (errorCounter >= 0.8*parsedPicturesRenameCopy.size()) { // >= 80% failure rate:
                        showErrorDialogLater(
                                "Renaming of almost all (" + errorCounter + "/" + parsedPicturesRenameCopy.size() + ") files failed!",
                                "");
                    }
                    return null;
                }
            };
            renameProgressBar.progressProperty().bind(task.progressProperty());
            renameProgressLabel.textProperty().bind(task.messageProperty());

            task.setOnSucceeded(e -> {
                renameProgressBar.progressProperty().unbind(); //!!!!VERY IMPORTANT -> OTHERWISE THE WHOLE APP DOESN'T REALLY WORK ANYMORE!!!
                renameProgressLabel.textProperty().unbind();
                renameUndoButton.setDisable(false);
                renameUndoButton.setText("Undo"); // The renaming operation is done, now the user can undo it.
                // ---- Now, at last: Open the folder with the now renamed files: -----
                revealInFileManagerIfPossible(new File(folderStr));
            });
            task.setOnFailed(e -> {
                renameProgressBar.progressProperty().unbind(); //!!!!VERY IMPORTANT -> OTHERWISE THE WHOLE APP DOESN'T REALLY WORK ANYMORE!!!
                renameProgressLabel.textProperty().unbind();
                renameUndoButton.setDisable(false);
                showErrorDialogLater("An error occurred when renaming pictures!", throwableToString(task.getException()));
            });
            new Thread(task).start();
        }
    }

    /**
     * Extracts all the used placeholders from a placeholder String.
     *
     * @param placeholderString e.g. "{TIMESTAMP_UNIX}-----{MONTH_SHORT}-{YEAR}-----{FILE_NAME}"
     * @return e.g. a Collection containing "TIMESTAMP_UNIX", "MONTH_SHORT", "YEAR" and "FILE_NAME"
     *      or null if the specified placeholderString isn't even valid (i.e. mismatched parentheses)
     */
    private static Collection<String> extractPlaceholders(final String placeholderString) {
        if (placeholderString == null || !placeholderString.matches(PLACEHOLDER_STRING_REGEX)) {
            return null; // the specified placeholderString isn't even valid
        } else {
            String[] placeholders = placeholderString.split("[}\\]][^{\\[}\\]]*[{\\[]");
            // the regex above matches [either } or] ] [0 more more non-parenthesis chars] [either { or [ ]
            // e.g. "xyz{ABC}xyz[DEF]xyz{GHI}xyz" ---> String[3] { "xyz{ABC", "DEF", "GHI}xyz" }
            // e.g. "xyz{ABC}xyz[DEF]xyz" ---> String[2] { "xyz{ABC", "DEF]xyz" }
            // e.g. "xyz{ABC}xyz" ---> String[1] { "xyz{ABC}xyz" }

            if (placeholders.length == 1) { // e.g. "xyz{ABC}xyz" ---> String[1] { "xyz{ABC}xyz" }
                placeholders[0] = placeholders[0].split("[{\\[]")[1].split("[}\\]]")[0];
            } else { // e.g. "xyz{ABC}xyz[DEF]xyz{GHI}xyz" ---> String[3] { "xyz{ABC", "DEF", "GHI}xyz" }
                // --- Fix the very first and the very last element of the placeholders Array: ---
                if (placeholders[0].contains("{") || placeholders[0].contains("[")) { // (sanity check)
                    placeholders[0] = placeholders[0].split("[{\\[]")[1]; // "xyz{ABC" --> "ABC"
                }
                if (placeholders[placeholders.length-1].contains("}") || placeholders[placeholders.length-1].contains("]")) { // (sanity check)
                    placeholders[placeholders.length-1] = placeholders[placeholders.length-1].split("[}\\]]")[0]; // "GHI}xyz" --> "GHI"
                }
            }

            return new ArrayList<String>(Arrays.asList(placeholders));
            // --> because Arrays.asList() returns a partially unmodifiable implementation leading to an UnsupportedOperationException eventually
            // https://stackoverflow.com/questions/14201379/unsupportedoperationexception-the-removeall-method-is-not-supported-by-this-co
        }
    }

    private static final List<String> ALL_AVAILABLE_PLACEHOLDERS =
            List.of( // see also the initialize() function at the very top of this .java file ^
            "FILE_NAME",
            "FILE_NAME_WO_SUFFIX",
            "FILE_SUFFIX",
            "TIMESTAMP_UNIX",
            "DAY_OF_MONTH",
            "DAY_OF_YEAR",
            "MONTH_NUMERIC",
            "MONTH_SHORT",
            "MONTH_LONG",
            "YEAR",
            "HOUR",
            "MINUTE",
            "SECOND");

    /**
     * Takes a Placeholder String (user-specified) and replaces the individual placeholders (e.g. "[DAY_OF_MONTH]")
     * with the data saved in the Picture instance pic.
     *
     * @param placeholderString e.g. "{TIMESTAMP_UNIX}-----[DAY_OF_MONTH]-{MONTH_SHORT}-{YEAR}-----{FILE_NAME}"
     * @param pic Picture (contains the pic.originalFileName & pic.timestamp properties)
     * @return e.g. "1583936544-----11-MAR-2020-----IMG_8510.HEIC"
     */
    private static String replacePlaceholders(String placeholderString, Picture pic) {
        // ----- Copied from PhotoCollectionSorter: -----
        // a) Determine the placeholder values:
        String placeholder_FILE_NAME = pic.originalFileName;
        String placeholder_FILE_NAME_WO_SUFFIX;
        if (pic.originalFileName.contains(".")) { // important check if we don't want a StringIndexOutOfBoundsException when there's no dot in the file name!!
            placeholder_FILE_NAME_WO_SUFFIX = pic.originalFileName.substring(0, pic.originalFileName.lastIndexOf("."));
        } else {
            placeholder_FILE_NAME_WO_SUFFIX = placeholder_FILE_NAME;
        }
        String placeholder_FILE_SUFFIX = pic.originalFileName.substring(pic.originalFileName.lastIndexOf(".")+1);
        Calendar cal = pic.getCalendar(); //!!!NEW!!!
        String placeholder_TIMESTAMP_UNIX = "" + (pic.timestamp/1000); // (timestamp is in milliseconds!)
        String placeholder_DAY_OF_MONTH = "" + cal.get(Calendar.DAY_OF_MONTH);
        String placeholder_DAY_OF_YEAR = "" + cal.get(Calendar.DAY_OF_YEAR); //Year.of(year).atMonth(month).atDay(day).getDayOfYear();
        String placeholder_MONTH_NUMERIC = "" + (cal.get(Calendar.MONTH) + 1); // Calender says JANUARY is 0, we say it's 1 !!!
        String placeholder_MONTH_SHORT = shortMonthName(Integer.parseInt(placeholder_MONTH_NUMERIC));
        String placeholder_MONTH_LONG = longMonthName(Integer.parseInt(placeholder_MONTH_NUMERIC));
        String placeholder_YEAR = "" + cal.get(Calendar.YEAR);
        String placeholder_HOUR = "" + cal.get(Calendar.HOUR_OF_DAY); // !! HOUR_OF_DAY = 24h ; HOUR = 12h !!
        String placeholder_MINUTE = "" + cal.get(Calendar.MINUTE);
        String placeholder_SECOND = "" + cal.get(Calendar.SECOND);
        // b) Determine the new file name:
        String newName = placeholderString;
        newName = newName.replace("{FILE_NAME}", placeholder_FILE_NAME);
        newName = newName.replace("{FILE_NAME_WO_SUFFIX}", placeholder_FILE_NAME_WO_SUFFIX);
        newName = newName.replace("{FILE_SUFFIX}", placeholder_FILE_SUFFIX);
        //newName = newName.replace("{TIMESTAMP_INDEX}", );
        newName = newName.replace("{TIMESTAMP_UNIX}", placeholder_TIMESTAMP_UNIX);
        //newName = newName.replace("{DAY_INDEX}", );
        newName = newName.replace("{DAY_OF_MONTH}", placeholder_DAY_OF_MONTH);
        newName = newName.replace("[DAY_OF_MONTH]", leadingZeros(placeholder_DAY_OF_MONTH, 2));
        newName = newName.replace("{DAY_OF_YEAR}", placeholder_DAY_OF_YEAR);
        newName = newName.replace("[DAY_OF_YEAR]", leadingZeros(placeholder_DAY_OF_YEAR, 3));
        newName = newName.replace("{MONTH_NUMERIC}", placeholder_MONTH_NUMERIC);
        newName = newName.replace("[MONTH_NUMERIC]", leadingZeros(placeholder_MONTH_NUMERIC, 2));
        newName = newName.replace("{MONTH_SHORT}", placeholder_MONTH_SHORT);
        newName = newName.replace("{MONTH_LONG}", placeholder_MONTH_LONG);
        newName = newName.replace("{YEAR}", placeholder_YEAR);
        newName = newName.replace("{HOUR}", placeholder_HOUR);
        newName = newName.replace("[HOUR]", leadingZeros(placeholder_HOUR, 2));
        newName = newName.replace("{MINUTE}", placeholder_MINUTE);
        newName = newName.replace("[MINUTE]", leadingZeros(placeholder_MINUTE, 2));
        newName = newName.replace("{SECOND}", placeholder_SECOND);
        newName = newName.replace("[SECOND]", leadingZeros(placeholder_SECOND, 2));
        return newName;
    }

    private static String shortMonthName(int month) { // Copied from PhotoCollectionSorter.
        switch (month) {
            case 1: return "JAN";
            case 2: return "FEB";
            case 3: return "MAR";
            case 4: return "APR";
            case 5: return "MAY";
            case 6: return "JUN";
            case 7: return "JUL";
            case 8: return "AUG";
            case 9: return "SEP";
            case 10: return "OCT";
            case 11: return "NOV";
            case 12: return "DEC";
            default: return "???";
        }
    }

    private static String longMonthName(int month) { // Copied from PhotoCollectionSorter.
        switch (month) {
            case 1: return "JANUARY";
            case 2: return "FEBRUARY";
            case 3: return "MARCH";
            case 4: return "APRIL";
            case 5: return "MAY";
            case 6: return "JUNE";
            case 7: return "JULY";
            case 8: return "AUGUST";
            case 9: return "SEPTEMBER";
            case 10: return "OCTOBER";
            case 11: return "NOVEMBER";
            case 12: return "DECEMBER";
            default: return "?MONTH?";
        }
    }

    private void undoAction() {
        // renameOperations.forEach(RenameOperation::_undo); // -> unhandled Exception!!
        int errorCounter = 0;
        for (RenameOperation renameOperation : renameOperations) {
            try {
                renameOperation._undo();
            } catch (IOException ex) {
                errorCounter++;
            }
        }
        if (errorCounter > 0) {
            showErrorDialogNow("" + errorCounter + " IO errors occurred when trying to undo the " +
                    renameOperations.size() + " renamings.", "");
        }
        resetRenameTool(); // -> includes renameUndoButton.setText("Rename"); AND renameOperations.clear();
        revealInFileManagerIfPossible(new File(folderTextField.getText())); // show what's been undone
    }


    // ----- ----- ----- "Extra: Slideshow Tool" ----- ----- -----

    @FXML
    private TextField slideshowToolTextField;
    @FXML
    private CheckBox takenBetweenSlideshowCheckBox;
    @FXML
    private DatePicker slideshowDatePicker1;
    @FXML
    private DatePicker slideshowDatePicker2;
    @FXML
    private CheckBox takenWithinSlideshowCheckBox;
    @FXML
    private TextField slideshowRadiusTextField;
    @FXML
    private TextField slideshowGeolocationTextField;
    @FXML
    private Button slideshowGoButton;
    @FXML
    private ProgressBar slideshowProgressBar;

    @FXML
    private void slideshowGoAction() { // TODO !!!
        List<Node> allSlideshowGUIElements = List.of(
                slideshowToolTextField,
                takenBetweenSlideshowCheckBox, slideshowDatePicker1, slideshowDatePicker2,
                takenWithinSlideshowCheckBox, slideshowRadiusTextField, slideshowGeolocationTextField,
                slideshowGoButton
        );


        // ---------- Cf. renameAction(): ----------
        // Save all the information we need -
        // to prevent weird behaviour in case the user decides to select a new folder (or alike)!!
        final String folderStr = folderTextField.getText();
        final Path folderPath = new File(folderStr).toPath();
        final ArrayList<Picture> parsedPicturesSlideshowCopy = parsedPictures == null ? new ArrayList<>() : new ArrayList<>(parsedPictures);

        if ("".equals(folderStr)) { // no folder specified
            beepAndShowErrorDialogNow("No folder specified yet!", "");
        } else if (generateMapButton.isDisabled()) {
            beepAndShowErrorDialogNow(
                    "Please wait for EXIF-parsing to finish or choose a folder with at least 2 EXIF-parsable pictures!",
                    "");
        } else if (parsedPicturesSlideshowCopy.isEmpty()) {
            // (this case shouldn't actually occur)
            beepAndShowErrorDialogNow("Error: There are no parsed pictures to rename!", "");
        } else {
            // ----- ----- Try to parse the slideshowToolTextField: ----- -----
            final String noOfSlideshowPhotosAsString = slideshowToolTextField.getText();
            int noOfSlideshowPhotos;
            if (noOfSlideshowPhotosAsString.endsWith("%")) { // Format: e.g. "Select 10% photos..."
                int totalNoOfPhotos = parsedPicturesSlideshowCopy.size();
                int percentage;
                try {
                    percentage = Integer.parseInt(noOfSlideshowPhotosAsString.substring(0, noOfSlideshowPhotosAsString.length() - 1));
                } catch (NumberFormatException | StringIndexOutOfBoundsException ex) {
                    beepAndShowErrorDialogNow("Please enter a valid number (e.g. '100') or percentage (e.g. '10%') into the text field!", "");
                    return; // cancel!!
                }
                noOfSlideshowPhotos = (totalNoOfPhotos*percentage)/100;
            } else { // Format: e.g. "Select 100 photos..."
                try {
                    noOfSlideshowPhotos = Integer.parseInt(noOfSlideshowPhotosAsString);
                } catch (NumberFormatException ex) {
                    beepAndShowErrorDialogNow("Please enter a valid number (e.g. '100') or percentage (e.g. '10%') into the text field!", "");
                    return; // cancel!!
                }
            }
            if (noOfSlideshowPhotos <= 0) { // number specified is too small!
                beepAndShowErrorDialogNow("Please enter a positive number!", "");
                return; // cancel!!
            } else if (noOfSlideshowPhotos >= parsedPicturesSlideshowCopy.size()) { // number specified is too large!
                beepAndShowErrorDialogNow("Please specify fewer photos for the slideshow!", "");
                return; // cancel!!
            }
            // ----- ----- ----- ----- -----
            allSlideshowGUIElements.forEach(node -> node.setDisable(true));
            // TODO
            beepAndShowErrorDialogNow("Sorry! The Slideshow Tool is not yet implemented.",
                    "The Slideshow would have included " + noOfSlideshowPhotos + " photos.");
            // TODO
            allSlideshowGUIElements.forEach(node -> node.setDisable(false));
        }
    }



    // ----- ----- ----- Some more utility functions: ----- ----- -----

    private static String throwableToString(Throwable ex) {
        return (ex == null) ? "null" : ex + " (" + ex.getMessage() + ")";
    }

    private static String leadingZeros(String str, int desiredLength) { // (cf. PhotoCollectionSorter)
        return "0".repeat(Math.max(0, desiredLength - str.length())) + str; // (negative values throw IllegalArgumentEx)
    }

    private static void revealInFileManagerIfPossible(final File f) {
        // -> https://stackoverflow.com/questions/7357969/how-to-use-java-code-to-open-windows-file-explorer-and-highlight-the-specified-f
        // -> https://stackoverflow.com/questions/15875295/open-a-folder-in-explorer-using-java/15875367
        if (Desktop.isDesktopSupported() && !GraphicsEnvironment.isHeadless()) {
            try {
                Desktop.getDesktop().open(f);
                /*
                "Launches the associated application to open the file.
                If the specified file is a directory, the file manager
                of the current platform is launched to open it."
                */
            } catch (IllegalArgumentException ex) { // "if the specified file doesn't exist"
                showErrorDialogLater("Unexpected error: Could not show the folder in the file manager because it does not exist anymore!!",
                        throwableToString(ex));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

}
