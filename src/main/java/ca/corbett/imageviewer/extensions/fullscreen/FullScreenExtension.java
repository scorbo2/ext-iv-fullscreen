package ca.corbett.imageviewer.extensions.fullscreen;

import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.EnhancedAction;
import ca.corbett.extras.io.KeyStrokeManager;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.extras.properties.ComboProperty;
import ca.corbett.extras.properties.KeyStrokeProperty;
import ca.corbett.extras.properties.PropertiesManager;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.extensions.ImageViewerExtension;
import ca.corbett.imageviewer.extensions.fullscreen.actions.FullScreenAction;
import ca.corbett.imageviewer.extensions.fullscreen.actions.ToggleExtraPanelsAction;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;
import ca.corbett.imageviewer.ui.ReservedKeyStrokeWorkaround;
import ca.corbett.imageviewer.ui.UIReloadable;
import ca.corbett.imageviewer.ui.actions.ReloadUIAction;

import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * An extension to ImageViewer that provides a full-screen viewing mode for the current
 * directory or image set. When full-screen mode is initiated, a new borderless window
 * is created on the selected monitor (as per the extension's configuration setting),
 * and the currently selected image is shown there at best fit size. The user can then
 * navigate through the images in the current file list using the keyboard, and exit
 * full-screen mode by pressing the Escape key.
 * <p>
 * The usual keyboard shortcuts will work to navigate forwards or backwards, or to delete
 * the selected image. The usual popup menu options are also supported in fullscreen mode.
 * This extension is compatible with other extensions! For example, if the QuickAccess
 * extension is present and enabled, then the QuickAccess panel will also
 * show here when full-screen mode is initiated. The same is true for the ICE quick tag
 * extension option, or other extensions that add additional image panel options and features.
 * </p>
 * <p>
 * <B>What if I'm on a laptop that sometimes has an external monitor, and sometimes not?</B>
 * It's not a problem. You can select monitor 2 in application settings when connected to the
 * external monitor. The application will remember that preference. If fullscreen mode is
 * started when the external monitor is not connected, the extension will detect that,
 * and automatically revert to using the primary monitor instead, for that session. Later,
 * when the monitor is connected again, it will work as per the saved preference.
 * </p>
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class FullScreenExtension extends ImageViewerExtension implements UIReloadable {

    private static final Logger logger = Logger.getLogger(FullScreenExtension.class.getName());
    private final AppExtensionInfo extInfo;
    private FullScreenWindow fullScreenWindow;

    private static final String fullScreenIndexProp = "UI.Fullscreen.monitorIndex";
    private static final String fullScreenKeyProp = AppConfig.KEYSTROKE_PREFIX + "Fullscreen mode.toggleKeyStroke";
    private static final String extraPanelKeyProp = AppConfig.KEYSTROKE_PREFIX + "Fullscreen mode.toggleExtraPanelKeyStroke";

    public FullScreenExtension() {
        extInfo = AppExtensionInfo.fromExtensionJar(getClass(),
                                                    "/ca/corbett/imageviewer/extensions/fullscreen/extInfo.json");
        if (extInfo == null) {
            throw new RuntimeException("FullScreenExtension: can't parse extInfo.json!");
        }
    }

    /**
     * Returns the index of the monitor to use for full-screen mode, as per the
     * extension's configuration setting.
     */
    public int getFullScreenMonitorIndex() {
        PropertiesManager propsManager = AppConfig.getInstance().getPropertiesManager();
        AbstractProperty prop = propsManager.getProperty(fullScreenIndexProp);
        if (prop instanceof ComboProperty<?> comboProp) {
            return comboProp.getSelectedIndex();
        }
        return 0; // failsafe
    }

    @Override
    public AppExtensionInfo getInfo() {
        return extInfo;
    }

    @Override
    public void loadJarResources() {
        // Our icon resource is provided by the parent application, so nothing to do here.
    }

    @Override
    public void onActivate() {
        // Listen for UI reloads so we can update ourselves as needed:
        ReloadUIAction.getInstance().registerReloadable(this);
    }

    @Override
    public void onDeactivate() {
        // Stop listening for UI reloads:
        ReloadUIAction.getInstance().unregisterReloadable(this);
    }

    @Override
    protected List<AbstractProperty> createConfigProperties() {
        // Figure out how many displays we have to work with here:
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        int monitorCount = env.getScreenDevices().length;
        List<String> displayChoices = new ArrayList<>();
        for (int i = 0; i < monitorCount; i++) {
            displayChoices.add("Screen " + (i + 1));
        }

        List<AbstractProperty> list = new ArrayList<>();
        list.add(new ComboProperty<>(fullScreenIndexProp,
                                     "Full screen monitor",
                                     displayChoices,
                                     0, // default first monitor
                                     false));
        list.add(new KeyStrokeProperty(fullScreenKeyProp, "Fullscreen mode:",
                                       KeyStrokeManager.parseKeyStroke("Ctrl+F"),
                                       FullScreenAction.getInstance(this))
                     .setAllowBlank(true)
                     .addFormFieldGenerationListener(new ReservedKeyStrokeWorkaround()));
        list.add(new KeyStrokeProperty(extraPanelKeyProp, "Toggle extra panels:",
                                       KeyStrokeManager.parseKeyStroke("Ctrl+P"),
                                       ToggleExtraPanelsAction.getInstance(this))
                     .setAllowBlank(true)
                     .addFormFieldGenerationListener(new ReservedKeyStrokeWorkaround()));

        return list;
    }

    @Override
    public List<EnhancedAction> getMainToolBarActions() {
        return List.of(FullScreenAction.getInstance(this));
    }

    @Override
    public List<EnhancedAction> getMenuActions(String topLevelMenu, MainWindow.BrowseMode browseMode) {
        if ("View".equals(topLevelMenu)) {
            return List.of(FullScreenAction.getInstance(this));
        }
        return null;
    }

    /**
     * Overridden here so that when the selected image in the MainWindow changes, we also
     * show the same image here. This allows us to use MainWindow's selectNextImage() and
     * selectPreviousImage() methods for navigation, without having to write that code here.
     *
     * @param selectedImage The newly selected image.
     */
    @Override
    public void imageSelected(ImageInstance selectedImage) {
        if (fullScreenWindow != null) {
            fullScreenWindow.setImage(selectedImage);
        }
    }

    /**
     * Overridden here so we can regenerate our popup menu when the quick move tree changes.
     */
    @Override
    public void quickMoveTreeChanged() {
        if (fullScreenWindow != null) {
            fullScreenWindow.setImagePanelPopupMenu(
                MainWindow.getInstance().getMenuManager().buildImagePanelPopupMenu());
        }
    }

    @Override
    public void browseModeChanged(MainWindow.BrowseMode newBrowseMode) {
        if (fullScreenWindow != null) {
            fullScreenWindow.setImagePanelPopupMenu(
                MainWindow.getInstance().getMenuManager().buildImagePanelPopupMenu());
        }
    }

    /**
     * Invoked when the user OKs the settings dialog or the extension manager dialog.
     * We use this to update our full-screen window background color and rebuild its
     * layout, as the list of extension-supplied extra panels may have changed.
     */
    @Override
    public void reloadUI() {
        if (fullScreenWindow != null) {
            fullScreenWindow.setCustomBackground(AppConfig.getInstance().getImagePanelBackgroundColor());
            fullScreenWindow.rebuildLayout(); // extensions providing extra panels may have changed
            fullScreenWindow.configureKeyStrokes(); // keyboard shortcuts may have changed
        }
    }

    public void goFullScreen() {
        // If a full screen window is already showing, do nothing:
        if (fullScreenWindow != null) {
            logger.warning("Full-screen mode already in progress; ignoring request to go full screen.");
            return;
        }
        if (MainWindow.getInstance().getCurrentFileList().isEmpty()) {
            MainWindow.getInstance().showMessageDialog("Full-screen mode", "No images to display.");
            return;
        }
        fullScreenWindow = new FullScreenWindow(this);
        ImageInstance currentImage = MainWindow.getInstance().getSelectedImage();
        fullScreenWindow.setImage(currentImage);
        fullScreenWindow.setImagePanelPopupMenu(MainWindow.getInstance().getMenuManager().buildImagePanelPopupMenu());
        fullScreenWindow.goFullScreen();
    }

    public FullScreenWindow getFullScreenWindow() {
        return fullScreenWindow;
    }

    public boolean isFullscreenActive() {
        return (fullScreenWindow != null);
    }

    public void fullScreenEnded() {
        fullScreenWindow = null;
    }
}
