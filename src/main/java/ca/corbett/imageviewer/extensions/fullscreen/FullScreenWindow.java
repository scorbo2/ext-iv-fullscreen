package ca.corbett.imageviewer.extensions.fullscreen;

import ca.corbett.extras.LookAndFeelManager;
import ca.corbett.extras.image.ImagePanel;
import ca.corbett.extras.image.ImagePanelConfig;
import ca.corbett.extras.image.animation.FadeLayerUI;
import ca.corbett.extras.io.KeyStrokeManager;
import ca.corbett.extras.properties.AbstractProperty;
import ca.corbett.extras.properties.BooleanProperty;
import ca.corbett.extras.properties.IntegerProperty;
import ca.corbett.extras.properties.KeyStrokeProperty;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;
import ca.corbett.imageviewer.ui.ThumbContainerPanel;
import ca.corbett.imageviewer.ui.ThumbContainerPanelListener;
import ca.corbett.imageviewer.ui.ThumbPanel;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayer;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a full-screen window that can be used by ImageViewer to provide a full-screen
 * view of the current directory. This is more than just a full-screen image panel.
 * The window is constructed by the same rules as the main image panel in the application's
 * main window. This means that any extra panels provided by other extensions (for example,
 * QuickAccess or ICE's quick tag panel) will also be shown here, if those extensions
 * are present and enabled. You can hit Ctrl+P (or whatever this shortcut has been remapped
 * to) while in full-screen mode to toggle the visibility of those extra panels.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public final class FullScreenWindow extends JFrame implements ThumbContainerPanelListener {

    private static final Logger logger = Logger.getLogger(FullScreenWindow.class.getName());
    private GraphicsDevice graphicsDevice;
    private final ImagePanel imagePanel;
    private final JLayer<JPanel> layeredPanel;
    private final FadeLayerUI fadeLayerUI;
    private final ImagePanelConfig imagePanelConf;
    private final FullScreenExtension owner;
    private JComponent westComponent;
    private JComponent eastComponent;
    private JComponent northComponent;
    private JComponent southComponent;
    private final KeyStrokeManager keyStrokeManager;
    private Timer kioskTimer;

    public FullScreenWindow(FullScreenExtension owner) {
        super("ImageViewer Fullscreen");
        this.owner = owner;
        setIconImage(MainWindow.getInstance().getIconImage()); // steal icon from main window

        prepareForFullScreen();

        // Turn off decorations on this window (otherwise you get an ugly title bar/window controls):
        getRootPane().setWindowDecorationStyle(JRootPane.NONE);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Prepare our image panel:
        imagePanelConf = ImagePanelConfig.createSimpleReadOnlyProperties();
        imagePanelConf.setBgColor(LookAndFeelManager.getLafColor("Panel.background", Color.LIGHT_GRAY));
        imagePanel = new ImagePanel(imagePanelConf);
        fadeLayerUI = buildFadeUI();
        layeredPanel = new JLayer<>(imagePanel, fadeLayerUI);

        rebuildLayout();

        // Set up our keyboard shortcuts:
        keyStrokeManager = new KeyStrokeManager(this);
        configureKeyStrokes();

        addListeners();
    }

    public void setCustomBackground(Color c) {
        imagePanelConf.setBgColor(c);
        imagePanel.applyProperties(imagePanelConf);
    }

    /**
     * Toggles the visibility status of extra components in our layout, if any are present.
     * This is a simple toggle, to invert whatever their current visibility state is.
     */
    public void toggleExtraComponentVisibility() {
        // Note: the components we see here are not the extra components supplied directly
        //       by extensions, but rather JTabbedPane wrappers provided by the utility
        //       method in MainWindow. So, we don't have to worry about extension-specific
        //       logic for hiding/showing these components, because the extensions don't actually
        //       see these tabbed panes at all. We can safely just invert their visibility status.

        if (westComponent != null) {
            westComponent.setVisible(!westComponent.isVisible());
        }
        if (eastComponent != null) {
            eastComponent.setVisible(!eastComponent.isVisible());
        }
        if (northComponent != null) {
            northComponent.setVisible(!northComponent.isVisible());
        }
        if (southComponent != null) {
            southComponent.setVisible(!southComponent.isVisible());
        }
    }

    public void setImage(ImageInstance image) {
        if (image.isEmpty()) {
            imagePanel.setImage(null);
        }
        if (image.isRegularImage()) {
            imagePanel.setImage(image.getRegularImage());
        }
        else if (image.isAnimatedGIF()) {
            imagePanel.setImageIcon(image.getGifImage());
        }
    }

    public void setImagePanelPopupMenu(JPopupMenu menu) {
        imagePanel.setPopupMenu(menu);
    }

    /**
     * Adds various listeners to this full-screen window and its components.
     */
    private void addListeners() {
        // add a window state listener for logging purposes:
        addWindowStateListener(e -> logger.log(Level.FINE, "Full-screen window state changed: {0} to {1}",
                                               new Object[]{e.getOldState(), e.getNewState()}));

        // I don't remember why this is needed...
        imagePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                requestFocusInWindow();
            }
        });

        // We need this windowClosing listener so we can be informed if the window is closed
        // through some user action that we otherwise can't trap (like right-clicking it on the
        // taskbar and closing it from there):
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                owner.fullScreenEnded();
            }
        });
    }

    public void goFullScreen() {
        if (kioskTimer != null) {
            kioskTimer.stop();
            kioskTimer = null;
        }
        if (isKioskModeEnabled()) {
            kioskTimer = new Timer(getKioskModeDelay(), e -> handleKioskNext());
            kioskTimer.setRepeats(true);
            kioskTimer.start();
        }
        MainWindow.getInstance().addThumbContainerPanelListener(this);

        graphicsDevice.setFullScreenWindow(this);
    }

    public void stopFullScreen() {
        if (kioskTimer != null) {
            kioskTimer.stop();
            kioskTimer = null;
        }
        MainWindow.getInstance().removeThumbContainerPanelListener(this);
        graphicsDevice.setFullScreenWindow(null);
        setAlwaysOnTop(false);
        setVisible(false);
        owner.fullScreenEnded();
    }

    /**
     * Configures our KeyStrokeManager with the current application keystroke settings.
     */
    public void configureKeyStrokes() {
        keyStrokeManager.clear();

        // I'm not wild about effectively duplicating MainWindow's KeyStrokesManager logic
        // here, but there doesn't seem to be a way to share that instance,
        // or copy it programmatically. Really, what I want to do is add this window to
        // the existing KeyStrokesManager instance in MainWindow, but KeyStrokeManager
        // currently only supports a single Window target.
        // https://github.com/scorbo2/swing-extras/issues/327 will address this.
        for (KeyStrokeProperty prop : AppConfig.getInstance().getKeyStrokeProperties()) {
            // If there's no Action attached, or if there is no keystroke assigned to it, skip it:
            if (prop.getAction() == null || prop.getKeyStroke() == null) {
                continue;
            }

            // Register it!
            keyStrokeManager.registerHandler(prop.getKeyStroke(), prop.getAction());
        }

        // Add default Escape key to exit full-screen mode:
        keyStrokeManager.registerHandler(KeyStrokeManager.parseKeyStroke("esc"), new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stopFullScreen();
            }
        });
    }

    private static FadeLayerUI buildFadeUI() {
        FadeLayerUI ui = new FadeLayerUI();
        ui.setFadeColor(AppConfig.getInstance().getImagePanelBackgroundColor());
        ui.setAnimationDuration(FadeLayerUI.AnimationDuration.Medium);
        ui.setAnimationSpeed(FadeLayerUI.AnimationSpeed.Fast);
        return ui;
    }

    /**
     * Does a sanity check on our preferred display, because it may not exist.
     * This can happen if you set it up on a laptop when you were docked to an external
     * monitor, but now you're running on the standalone laptop where your second
     * display is no longer present. If the actual monitor count is less than what
     * the user prefers, we just default to the primary monitor (index 0).
     */
    private void prepareForFullScreen() {
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        int monitorCount = env.getScreenDevices().length;
        int preferredMonitorIndex = owner.getFullScreenMonitorIndex();
        if (preferredMonitorIndex >= monitorCount) {
            logger.log(Level.INFO, "Preferred fullscreen monitor not available; defaulting to primary.");
            preferredMonitorIndex = 0; // failsafe default
        }
        DisplayMode displayMode = env.getScreenDevices()[preferredMonitorIndex].getDisplayMode();
        setSize(displayMode.getWidth(), displayMode.getHeight()); // apparently initial size matters
        graphicsDevice = env.getScreenDevices()[preferredMonitorIndex];
        logger.log(Level.FINE, "isFullscreenSupported: {0}", graphicsDevice.isFullScreenSupported());
    }

    /**
     * Handles building or rebuilding the layout of this full-screen window,
     * including any extra panels provided by other extensions.
     */
    public void rebuildLayout() {
        // Build our wrapper panel:
        JPanel wrapperPanel = MainWindow.buildImagePanelWrapperPanel(layeredPanel);

        // Interrogate it to find the tabbed panes in each position, if present:
        if (wrapperPanel.getLayout() instanceof BorderLayout borderLayout) {
            westComponent = findComponent(borderLayout.getLayoutComponent(BorderLayout.WEST));
            eastComponent = findComponent(borderLayout.getLayoutComponent(BorderLayout.EAST));
            northComponent = findComponent(borderLayout.getLayoutComponent(BorderLayout.NORTH));
            southComponent = findComponent(borderLayout.getLayoutComponent(BorderLayout.SOUTH));
        }
        else {
            // This *should* never happen, but let's play it safe:
            // (if this does happen, it disables our ability to toggle extra component visibility)
            logger.log(Level.WARNING, "FullScreenExtension: "
                           + "Unexpected wrapper panel layout: {0} (expected BorderLayout); "
                           + "will be unable to toggle extra component visibility.",
                       wrapperPanel.getLayout().getClass().getName());
            westComponent = null;
            eastComponent = null;
            northComponent = null;
            southComponent = null;
        }

        getContentPane().removeAll();
        setLayout(new BorderLayout());
        add(wrapperPanel, BorderLayout.CENTER);
        invalidate();
        revalidate();
        repaint();
    }

    private JTabbedPane findComponent(Object candidate) {
        return (candidate instanceof JTabbedPane) ? (JTabbedPane)candidate : null;
    }

    /**
     * Looks up our "kiosk mode" checkbox and returns whether it is currently checked.
     */
    private boolean isKioskModeEnabled() {
        AppConfig appConfig = AppConfig.getInstance();
        AbstractProperty prop = appConfig.getPropertiesManager().getProperty(FullScreenExtension.kioskModeProp);
        return (prop instanceof BooleanProperty) && ((BooleanProperty)prop).getValue();
    }

    /**
     * Reports whether animated transitions are enabled in AppConfig.
     */
    private boolean isKioskModeAnimationEnabled() {
        AppConfig appConfig = AppConfig.getInstance();
        AbstractProperty prop = appConfig.getPropertiesManager()
                                         .getProperty(FullScreenExtension.kioskModeAnimationProp);
        return (prop instanceof BooleanProperty) && ((BooleanProperty)prop).getValue();
    }

    /**
     * Looks up our "kiosk mode delay" property and returns the current value as an integer.
     */
    private int getKioskModeDelay() {
        AppConfig appConfig = AppConfig.getInstance();
        AbstractProperty prop = appConfig.getPropertiesManager().getProperty(FullScreenExtension.kioskModeDelayProp);
        if (prop instanceof IntegerProperty intProp) {
            return intProp.getValue() * 1000; // convert seconds to milliseconds for Timer
        }
        return Integer.MAX_VALUE; // failsafe default, basically disable kiosk mode
    }

    /**
     * In "Kiosk mode", we will automatically advance to the next image after a configurable delay.
     * If we hit the end of the current directory or image set, we will "rewind" back to the first
     * image and keep going from there. This is handy for throwing a slideshow up on a monitor
     * and just leaving it, almost like a screensaver.
     */
    private void handleKioskNext() {

        // If we're not animating, just do a simple flip to the next image:
        if (!isKioskModeAnimationEnabled()) {
            flipToNext();
        }

        // Otherwise, let's do a quick fade-out/fade-in to show the next image:
        else {
            fadeLayerUI.fadeOut(() -> {
                flipToNext();
                fadeLayerUI.fadeIn(null);
            });
        }

        // We'll re-check our animation delay each time, because the user can actually
        // change it while we're running:
        if (kioskTimer != null) {
            int delayMS = getKioskModeDelay();

            // Only update the timer settings if the delay has actually changed, to avoid
            // unnecessary stop/start cycles and excessive log output.
            if (kioskTimer.getDelay() != delayMS) {

                // Changing the timer delay on the fly is unexpectedly difficult.
                // Best way I've found is to stop it entirely, update its settings,
                // then start it again, but on the EDT just to be safe, since Timer is not thread-safe.
                // Even with all this code, the "current" cycle of the timer will use the old value
                // (even though we stop and start it, annoyingly)
                // The following cycle will use the new value.
                kioskTimer.stop();
                kioskTimer.setDelay(delayMS);
                kioskTimer.setInitialDelay(delayMS);
                SwingUtilities.invokeLater(() -> kioskTimer.start()); // restart the timer on the EDT, to be safe
            }

            // While we're at it, we'll just confirm that kiosk mode is still enabled,
            // because if the user disabled it while we were running, we want to stop the timer entirely:
            if (!isKioskModeEnabled()) {
                kioskTimer.stop();
                kioskTimer = null;
                logger.info("Kiosk mode disabled; full screen mode must be restarted if you want to re-enable it.");
                // Note: user must exit and re-enter full-screen mode to re-enable kiosk mode.
            }
        }
    }

    /**
     * Performs an immediate flip to the next image in the current directory or image set,
     * with wraparound back to the first image if we hit the end.
     */
    private void flipToNext() {
        int totalCount = MainWindow.getInstance().getThumbnailCount();
        int currentIndex = MainWindow.getInstance().getThumbnailSelectionIndex();

        if (totalCount == 0 || currentIndex == -1) {
            // No images, or no image selected, so nothing to do:
            return;
        }

        // We don't want our own thumb selection changes to trigger the kiosk timer to reset,
        // so we temporarily remove ourselves as a listener while we change the selection:
        MainWindow.getInstance().removeThumbContainerPanelListener(this);

        if (currentIndex >= totalCount - 1) {
            MainWindow.getInstance().selectThumbnailAtIndex(0); // rewind to start if we hit the end
        }
        else {
            MainWindow.getInstance().selectNextImage();
        }

        // Now start listening again:
        MainWindow.getInstance().addThumbContainerPanelListener(this);
    }

    @Override
    public void thumbnailSelected(ThumbContainerPanel source, ThumbPanel selectedPanel) {
        if (kioskTimer != null) {
            // If we're in kiosk mode, we want to reset the timer every time the user manually selects an image,
            // so that we don't have the timer suddenly fire while they're looking at an image they just selected.
            kioskTimer.restart();
        }
    }

    @Override
    public void selectionCleared(ThumbContainerPanel source) {
        // From our ThumbContainerPanelListener interface - ignored
    }

    @Override
    public void loadStarting(ThumbContainerPanel source) {
        // From our ThumbContainerPanelListener interface - ignored
    }

    @Override
    public void loadCompleted(ThumbContainerPanel source) {
        // From our ThumbContainerPanelListener interface - ignored
    }
}
