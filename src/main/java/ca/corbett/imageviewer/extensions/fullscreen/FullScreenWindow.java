package ca.corbett.imageviewer.extensions.fullscreen;

import ca.corbett.extras.LookAndFeelManager;
import ca.corbett.extras.image.ImagePanel;
import ca.corbett.extras.image.ImagePanelConfig;
import ca.corbett.imageviewer.extensions.ImageViewerExtension;
import ca.corbett.imageviewer.extensions.ImageViewerExtensionManager;
import ca.corbett.imageviewer.ui.ImageInstance;
import ca.corbett.imageviewer.ui.MainWindow;

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
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a full-screen window that can be used by ImageViewer to provide a full-screen
 * view of the current directory.
 *
 * @author scorbett
 */
public final class FullScreenWindow extends JFrame {

    private static final Logger logger = Logger.getLogger(FullScreenWindow.class.getName());
    private final GraphicsDevice graphicsDevice;
    private final boolean isFullscreenSupported;
    private final int monitorCount;
    private final ImagePanel imagePanel;
    private final ImagePanelConfig imagePanelConf;
    private final JPanel wrapperPanel;
    private final FullScreenExtension owner;

    public FullScreenWindow(FullScreenExtension owner) {
        super("ImageViewer Fullscreen");
        this.owner = owner;
        setIconImage(MainWindow.getInstance().getIconImage());

        // Sanity check our preferred display... it may not exist.
        // This can happen if you set it up on a laptop when you were docked to an external
        // monitor, but now you're running on the standalone laptop where your second
        // display is no longer present.
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        monitorCount = env.getScreenDevices().length;
        int preferredMonitorIndex = owner.getFullScreenMonitorIndex();
        if (preferredMonitorIndex >= monitorCount) {
            logger.log(Level.INFO, "Preferred fullscreen monitor not available; defaulting to primary.");
            preferredMonitorIndex = 0; // failsafe default
        }
        DisplayMode displayMode = env.getScreenDevices()[preferredMonitorIndex].getDisplayMode();
        setSize(displayMode.getWidth(), displayMode.getHeight()); // apparently initial size matters
        graphicsDevice = env.getScreenDevices()[preferredMonitorIndex];
        isFullscreenSupported = graphicsDevice.isFullScreenSupported();
        logger.log(Level.INFO, "isFullscreenSupported: {0}", isFullscreenSupported);

        // Turn off decorations on this window (otherwise you get an ugly title bar/window controls):
        getRootPane().setWindowDecorationStyle(JRootPane.NONE);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        imagePanelConf = ImagePanelConfig.createSimpleReadOnlyProperties();
        imagePanelConf.setBgColor(LookAndFeelManager.getLafColor("Panel.background", Color.LIGHT_GRAY));
        imagePanel = new ImagePanel(imagePanelConf);
        setLayout(new BorderLayout());

        wrapperPanel = new JPanel();
        wrapperPanel.setLayout(new BorderLayout());

        // Add extra panels, if any are supplied by our extensions:
        // (this is how we support QuickAccess extension here, even though this code doesn't know that exists)
        JComponent westComponent = ImageViewerExtensionManager.getInstance().getExtraPanelComponent(
            ImageViewerExtension.ExtraPanelPosition.Left);
        JComponent eastComponent = ImageViewerExtensionManager.getInstance().getExtraPanelComponent(
            ImageViewerExtension.ExtraPanelPosition.Right);
        JComponent northComponent = ImageViewerExtensionManager.getInstance().getExtraPanelComponent(
            ImageViewerExtension.ExtraPanelPosition.Top);
        JComponent southComponent = ImageViewerExtensionManager.getInstance().getExtraPanelComponent(
            ImageViewerExtension.ExtraPanelPosition.Bottom);
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

        add(wrapperPanel, BorderLayout.CENTER);

        // Add the key listener once:
        KeyListener keyListener = new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                switch (e.getKeyCode()) {
                    // Left or Up arrow for "previous":
                    case KeyEvent.VK_LEFT:
                    case KeyEvent.VK_UP:
                        MainWindow.getInstance().selectPreviousImage();
                        break;

                    // Right or down arrow for "next":
                    case KeyEvent.VK_RIGHT:
                    case KeyEvent.VK_DOWN:
                        MainWindow.getInstance().selectNextImage();
                        break;

                    // ESC to exit full screen:
                    case KeyEvent.VK_ESCAPE:
                        stopFullScreen();
                        break;

                    // DEL to delete the current image:
                    case KeyEvent.VK_DELETE:
                        MainWindow.getInstance().deleteSelectedImage();
                        break;
                }
            }

        };
        addKeyListener(keyListener);
        wrapperPanel.addKeyListener(keyListener);

        // This is a long shot, as we don't know what these components are exactly, but we
        // should at least try to ensure that if one of them has focus, we can still respond
        // to keyboard input.
        if (northComponent != null) {
            northComponent.addKeyListener(keyListener);
        }
        if (southComponent != null) {
            southComponent.addKeyListener(keyListener);
        }
        if (westComponent != null) {
            westComponent.addKeyListener(keyListener);
        }
        if (eastComponent != null) {
            eastComponent.addKeyListener(keyListener);
        }

        addListeners();
    }

    public void setCustomBackground(Color c) {
        imagePanelConf.setBgColor(c);
        imagePanel.applyProperties(imagePanelConf);
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

    private void addListeners() {

        // Add the focus listener once:
        final int deviceCount = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices().length;
        addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent arg0) {
                setAlwaysOnTop(true);
            }

            @Override
            public void focusLost(FocusEvent arg0) {
                // If there's only one monitor, stop full screen mode when focus is lost.
                // This almost certainly means someone alt+tabbed away from the visualizer,
                // and so we'll just kill it.
                // If there's more than one monitor, ignore this event as it's possible
                // to leave the visualizer up on monitor 2 while doing stuff on monitor 1.
                if (deviceCount == 1) {
                    //setAlwaysOnTop(false);
                    //stopFullScreen();
                    // The above works okay in MusicPlayer but I'm not wild about it here.
                    // On single monitor setup, you lose focus as soon as you bring
                    // up the popup menu, which immediately kills fullscreen mode.
                }

                // Thumbpanel steals focus when selectNext() or selectPrevious() is invoked.
                // Steal it back so we don't lose our ESCAPE to exit thing.
                // This does cause wonky taskbar flashing on single-monitor setups, but the
                // alternative is that the thumbpanel (behind the full screen window) will somehow
                // have keyboard focus, and you have to click the window before ESC will count).
                //requestFocus();
                // (added logic to ThumbPanel to not do that if full screen window is up)
            }

        });

        // add a window state listener:
        addWindowStateListener(new WindowStateListener() {
            @Override
            public void windowStateChanged(WindowEvent e) {
                logger.log(Level.INFO, "Full-screen window state changed: {0} to {1}",
                           new Object[]{e.getOldState(), e.getNewState()});
            }

        });

        imagePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                requestFocusInWindow();
            }

        });

        // We need this windowClosing listener so we can be informed if the window is closed
        // through some user action that we otherwise can't trap (like right clicking it on the
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

}
