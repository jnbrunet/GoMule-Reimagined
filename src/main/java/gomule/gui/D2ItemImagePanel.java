package gomule.gui;

import gomule.item.D2Item;

import javax.swing.*;
import java.awt.*;

/**
 * A small preview panel that paints one item's inventory sprite (its real in-game look) on a black
 * background, centered both ways.
 * <p>
 * Deliberately not a JLabel + ImageIcon like the clipboard view (D2ViewClipboard) does: a JLabel
 * crops the icon as soon as the component gets smaller than the image, and gives no control over
 * the background or the scaling. Here an oversized sprite is shrunk to fit instead of being cut.
 */
public class D2ItemImagePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    // The biggest inventory sprite in the game is 2x4 cells, i.e. about 56x112 px with the
    // GRID_SIZE = 28 used by D2ViewChar, so this fixed band always fits a sprite at 1:1.
    private static final int PREFERRED_WIDTH = 120;
    private static final int PREFERRED_HEIGHT = 140;

    private static final String NO_IMAGE_TEXT = "(no image)";

    private D2Item item;

    public D2ItemImagePanel() {
        setOpaque(true);
        setBackground(Color.BLACK);
        setPreferredSize(new Dimension(PREFERRED_WIDTH, PREFERRED_HEIGHT));
    }

    /**
     * Sets the item to preview. A null item clears the preview (black background only).
     */
    public void setItem(D2Item item) {
        this.item = item;
        repaint();
    }

    public D2Item getItem() {
        return item;
    }

    /**
     * Resolves the sprite for an item, never throwing.
     * <p>
     * D2ImageCache.getDC6Image(D2Item) is used as-is by the .d2s and .d2i views and is not touched,
     * but it has two failure modes that a .d2x stash full of modded Reimagined items can actually
     * hit: it dereferences pItem.getItemName() without a null check, and it happily builds a path
     * for a .dc6 that does not exist under resources/gfx (D2dc6 only falls back to invsple.dc6 when
     * the file exists but is empty). A missing sprite must never break the stash view, so anything
     * thrown here degrades to "no image" instead. Package-private so it can be unit tested.
     */
    static Image imageFor(D2Item item) {
        if (item == null) {
            return null;
        }
        try {
            return D2ImageCache.getDC6Image(item);
        } catch (RuntimeException | Error e) {
            return null; // a missing sprite must never break the stash view
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (item == null) {
            return; // nothing selected: just the black background
        }

        Image image = imageFor(item);
        if (image == null) {
            paintNoImage(g);
            return;
        }

        // Image.getWidth/getHeight return -1 while the image is not fully available yet; the DC6
        // images are decoded BufferedImages so this should not happen, but a negative or zero
        // dimension would make the scale computation below meaningless.
        int imageWidth = image.getWidth(null);
        int imageHeight = image.getHeight(null);
        if (imageWidth <= 0 || imageHeight <= 0) {
            return;
        }

        int availableWidth = getWidth();
        int availableHeight = getHeight();
        if (availableWidth <= 0 || availableHeight <= 0) {
            return;
        }

        // Shrink only, and by the same factor on both axes so the sprite keeps its aspect ratio.
        // Never blow a sprite up past 1:1: DC6 art is tiny and upscaling it just looks blurry.
        double scale = Math.min(1.0d,
                Math.min((double) availableWidth / imageWidth, (double) availableHeight / imageHeight));
        int drawWidth = Math.max(1, (int) Math.round(imageWidth * scale));
        int drawHeight = Math.max(1, (int) Math.round(imageHeight * scale));
        int x = (availableWidth - drawWidth) / 2;
        int y = (availableHeight - drawHeight) / 2;

        Graphics2D graphics = (Graphics2D) g.create();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(image, x, y, drawWidth, drawHeight, null);
        } finally {
            graphics.dispose();
        }
    }

    // An item whose sprite could not be resolved still gets a visible, non-alarming placeholder --
    // same spirit as the parser falling back to a raw name instead of aborting a whole load.
    private void paintNoImage(Graphics g) {
        g.setColor(Color.GRAY);
        FontMetrics metrics = g.getFontMetrics();
        int x = (getWidth() - metrics.stringWidth(NO_IMAGE_TEXT)) / 2;
        int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
        g.drawString(NO_IMAGE_TEXT, x, y);
    }
}
