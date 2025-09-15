package ca.corbett.imageviewer.extensions.fullscreen;

import ca.corbett.extensions.AppExtensionInfo;
import ca.corbett.extras.image.ImageUtil;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.extras.properties.ComboProperty;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.ToolBarManager;
import ca.corbett.imageviewer.extensions.ImageViewerExtension;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;

import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * An extension to ImageViewer that provides a full-screen viewing mode for the current directory.
 * The usual keyboard shortcuts will work to navigate forwards or backwards, or to delete
 * the selected image. The main image panel will be given a popup menu that matches the
 * one in the main window, to allow access to quick move and other options. This extension
 * is also compatible with the QuickAccess extension! If the QuickAccess extension is present
 * and enabled, and a quick access panel has been set up in the main window, it will also
 * show here when full-screen mode is initiated.
 *
 * @author scorbett
 */
public class FullScreenExtension extends ImageViewerExtension {

    private static final Logger logger = Logger.getLogger(FullScreenExtension.class.getName());
    private final AppExtensionInfo extInfo;
    private FullScreenWindow fullScreenWindow;

    private final String fullScreenIndexPropName = "UI.Fullscreen.monitorIndex";
    private final BufferedImage fullScreenIconImage;

    public FullScreenExtension() {
        extInfo = AppExtensionInfo.fromExtensionJar(getClass(),
                                                    "/ca/corbett/imageviewer/extensions/fullscreen/extInfo.json");
        if (extInfo == null) {
            throw new RuntimeException("FullScreenExtension: can't parse extInfo.json!");
        }

        // Load our image icon for full screen mode (this MUST be done in constructor... see swing-extras #34)
        try {
            fullScreenIconImage = ImageUtil.loadFromResource(getClass(),
                                                             "/ca/corbett/imageviewer/extensions/fullscreen/icon-fullscreen.png",
                                                             ToolBarManager.iconSize,
                                                             ToolBarManager.iconSize);
        }
        catch (IOException ioe) {
            throw new RuntimeException("FullScreenExtension: can't load jar resources!", ioe);
        }
    }

    public int getFullScreenMonitorIndex() {
        return ((ComboProperty)AppConfig.getInstance().getPropertiesManager()
                                        .getProperty(fullScreenIndexPropName)).getSelectedIndex();
    }

    @Override
    public AppExtensionInfo getInfo() {
        return extInfo;
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
        list.add(new ComboProperty<>(fullScreenIndexPropName, "Full screen monitor", displayChoices, 0, false));
        return list;
    }

    @Override
    public List<JButton> getToolBarButtons() {
        List<JButton> list = new ArrayList<>();
        list.add(ToolBarManager.buildButton(fullScreenIconImage, "Full Screen mode", new FullScreenAction(this)));
        return list;
    }

    @Override
    public List<JMenuItem> getMenuItems(String topLevelMenu, MainWindow.BrowseMode browseMode) {
        if ("View".equals(topLevelMenu)) {
            List<JMenuItem> list = new ArrayList<>();
            JMenuItem item = new JMenuItem(new FullScreenAction(this));
            item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
            list.add(item);
            return list;
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

    @Override
    public void imagePanelBackgroundChanged(Color newColor) {
        if (fullScreenWindow != null) {
            fullScreenWindow.setCustomBackground(newColor);
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

    public boolean isFullscreenActive() {
        return (fullScreenWindow != null);
    }

    public void fullScreenEnded() {
        fullScreenWindow = null;
    }

}
