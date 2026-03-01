package dev.custompaintings;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.image.BufferedImage;

public final class StaticImageMapRenderer extends MapRenderer {
    private final BufferedImage image;
    private boolean rendered;

    public StaticImageMapRenderer(BufferedImage image) {
        super(true);
        this.image = image;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (rendered) {
            return;
        }

        canvas.drawImage(0, 0, image);
        rendered = true;
    }
}
