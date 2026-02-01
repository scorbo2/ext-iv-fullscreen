package ca.corbett.imageviewer.extensions.fullscreen.actions;

import ca.corbett.extras.EnhancedAction;
import ca.corbett.imageviewer.extensions.fullscreen.FullScreenExtension;

import java.awt.event.ActionEvent;

/**
 * When fullscreen mode is active, this action toggles the visibility of any extra panels
 * that are present, such as QuickAccess or ICE quick tag panels. If no extension is currently
 * providing any extra panels, this action does nothing.
 *
 * @author <a href="https://github.com/scorbo2">scorbo2</a>
 */
public class ToggleExtraPanelsAction extends EnhancedAction {

    private final FullScreenExtension extension;

    public ToggleExtraPanelsAction(FullScreenExtension extension) {
        super("Toggle extra panels");
        if (extension == null) {
            throw new IllegalArgumentException("ToggleExtraPanelsAction: extension cannot be null!");
        }
        this.extension = extension;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (extension.getFullScreenWindow() == null) {
            // Not in full-screen mode, so nothing to do:
            return;
        }

        extension.getFullScreenWindow().toggleExtraPanelVisibility();
    }
}
