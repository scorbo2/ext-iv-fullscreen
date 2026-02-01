package ca.corbett.imageviewer.extensions.fullscreen.actions;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.imageviewer.AppConfig;
import ca.corbett.imageviewer.ImageViewerResources;
import ca.corbett.imageviewer.extensions.fullscreen.FullScreenExtension;

import javax.swing.ImageIcon;
import java.awt.event.ActionEvent;

/**
 * Invoked to initiate full-screen mode (does nothing if full-screen mode is already in progress).
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class FullScreenAction extends EnhancedAction {

    private static final String NAME = "Full screen mode";

    private final FullScreenExtension owner;

    public FullScreenAction(FullScreenExtension owner) {
        this(owner, AppConfig.getInstance().getToolbarIconSize());
    }

    public FullScreenAction(FullScreenExtension owner, int iconSize) {
        super(NAME);
        if (owner == null) {
            throw new IllegalArgumentException("FullScreenAction: owner cannot be null!");
        }
        this.owner = owner;
        setTooltip(NAME);
        setIcon(new ImageIcon(ImageViewerResources.getIconFullscreen(iconSize)));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        owner.goFullScreen();
    }
}
