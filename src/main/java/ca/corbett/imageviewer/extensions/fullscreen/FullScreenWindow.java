package ca.corbett.imageviewer.extensions.fullscreen;

import ca.corbett.extras.LookAndFeelManager;
import ca.corbett.extras.image.ImagePanel;
import ca.corbett.extras.image.ImagePanelConfig;
import ca.corbett.extras.io.KeyStrokeManager;
import ca.corbett.extras.properties.KeyStrokeProperty;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.extensions.ImageViewerExtension;
import ca.corbett.imageviewer.extensions.ImageViewerExtensionManager;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
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
     * Toggles the visibility status of whatever extra panels are currently
     * showing in this full-screen window. If no extra panels are present,
     * this method does nothing.
     */
    public void toggleExtraPanelVisibility() {
        // Note: the logic here isn't great.
        //   a) we only see JComponents, we have no idea what they actually are.
        //   b) we can't cast them to anything, as we don't have access to the extension code that provides them.
        //   c) the supplying extension may have its own visibility rules that we are violating here.
        //   d) we just do a blind toggle to invert the current visibility state.
        //
        // It's pretty much the best we can do, and it works okay in practice.
        // The user also has the option of disabling extra panels in application settings,
        // if they really don't want to see them in full-screen mode.

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
        //graphicsDevice.setFullScreenWindow(this);
        setVisible(true); // tmp
    }

    public void stopFullScreen() {
        //graphicsDevice.setFullScreenWindow(null);
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
        // Detect extra panels, if any are supplied by other extensions:
        ImageViewerExtensionManager extManager = ImageViewerExtensionManager.getInstance();
        westComponent = extManager.getExtraPanelComponent(ImageViewerExtension.ExtraPanelPosition.Left);
        eastComponent = extManager.getExtraPanelComponent(ImageViewerExtension.ExtraPanelPosition.Right);
        northComponent = extManager.getExtraPanelComponent(ImageViewerExtension.ExtraPanelPosition.Top);
        southComponent = extManager.getExtraPanelComponent(ImageViewerExtension.ExtraPanelPosition.Bottom);

        JPanel wrapperPanel = new JPanel();
        wrapperPanel.setLayout(new BorderLayout());
        if (westComponent != null) {
            wrapperPanel.add(westComponent, BorderLayout.WEST);
        }
        if (eastComponent != null) {
            wrapperPanel.add(eastComponent, BorderLayout.EAST);
        }
        if (northComponent != null) {
            wrapperPanel.add(northComponent, BorderLayout.NORTH);
        }
        if (southComponent != null) {
            wrapperPanel.add(southComponent, BorderLayout.SOUTH);
        }
        wrapperPanel.add(imagePanel, BorderLayout.CENTER);

        getContentPane().removeAll();
        setLayout(new BorderLayout());
        add(wrapperPanel, BorderLayout.CENTER);
        invalidate();
        revalidate();
        repaint();
    }
}
