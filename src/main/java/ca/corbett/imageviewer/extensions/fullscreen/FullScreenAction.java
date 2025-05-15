package ca.corbett.imageviewer.extensions.fullscreen;

import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;

/**
 * Invoked to initiate full-screen mode (does nothing if full-screen mode is already in progress).
 *
 * @author scorbett
 */
public class FullScreenAction extends AbstractAction {

    private final FullScreenExtension owner;

    public FullScreenAction(FullScreenExtension owner) {
        super("Full screen mode");
        this.owner = owner;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        owner.goFullScreen();
    }

}
