package ca.corbett.imageviewer.extensions.fullscreen;

import ca.corbett.extras.LookAndFeelManager;
import ca.corbett.extras.image.ImagePanel;
import ca.corbett.extras.image.ImagePanelConfig;
import ca.corbett.extras.io.KeyStrokeManager;
import ca.corbett.extras.properties.KeyStrokeProperty;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JTabbedPane;
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
public final class FullScreenWindow extends JFrame {

    private static final Logger logger = Logger.getLogger(FullScreenWindow.class.getName());
    private GraphicsDevice graphicsDevice;
    private final ImagePanel imagePanel;
    private final ImagePanelConfig imagePanelConf;
    private final FullScreenExtension owner;
    private JComponent westComponent;
    private JComponent eastComponent;
    private JComponent northComponent;
    private JComponent southComponent;
    private final KeyStrokeManager keyStrokeManager;

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
        graphicsDevice.setFullScreenWindow(this);
    }

    public void stopFullScreen() {
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
        JPanel wrapperPanel = MainWindow.buildImagePanelWrapperPanel(imagePanel);

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
}
