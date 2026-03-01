package dev.custompaintings;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class PaintingManager {
    private static final int MAP_SIZE = 128;
    private static final int MIN_MAPS = 1;
    private static final int MAX_MAPS = 8;

    private final JavaPlugin plugin;
    private final File uploadsDir;
    private final File processedDir;
    private final File indexFile;
    private final Map<String, PaintingDefinition> entries = new HashMap<>();

    public PaintingManager(JavaPlugin plugin) {
        this.plugin = plugin;
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new IllegalStateException("Unable to create plugin data folder");
        }

        this.uploadsDir = new File(dataFolder, "uploads");
        this.processedDir = new File(dataFolder, "paintings");
        this.indexFile = new File(dataFolder, "paintings.yml");

        if (!uploadsDir.exists()) {
            uploadsDir.mkdirs();
        }
        if (!processedDir.exists()) {
            processedDir.mkdirs();
        }
    }

    public File getUploadsDir() {
        return uploadsDir;
    }

    public void loadIndex() {
        entries.clear();
        if (!indexFile.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(indexFile);
        ConfigurationSection root = yaml.getConfigurationSection("paintings");
        if (root == null) {
            return;
        }

        for (String key : root.getKeys(false)) {
            String normalized = key.toLowerCase(Locale.ROOT);
            String basePath = "paintings." + key;

            if (yaml.isConfigurationSection(basePath)) {
                int width = yaml.getInt(basePath + ".width", 1);
                int height = yaml.getInt(basePath + ".height", 1);
                String prefix = yaml.getString(basePath + ".prefix", normalized);
                entries.put(normalized, new PaintingDefinition(normalized, width, height, prefix));
                continue;
            }

            String legacyFile = yaml.getString(basePath);
            if (legacyFile != null && !legacyFile.isBlank()) {
                String prefix = legacyFile.endsWith(".png") ? legacyFile.substring(0, legacyFile.length() - 4) : normalized;
                entries.put(normalized, new PaintingDefinition(normalized, 1, 1, prefix));
            }
        }
    }

    public void saveIndex() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, PaintingDefinition> entry : entries.entrySet()) {
            String path = "paintings." + entry.getKey();
            PaintingDefinition def = entry.getValue();
            yaml.set(path + ".width", def.width());
            yaml.set(path + ".height", def.height());
            yaml.set(path + ".prefix", def.filePrefix());
        }

        try {
            yaml.save(indexFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save paintings index: " + e.getMessage());
        }
    }

    public Set<String> getNames() {
        return new TreeSet<>(entries.keySet());
    }

    public Optional<PaintingDefinition> getDefinition(String name) {
        return Optional.ofNullable(entries.get(normalize(name)));
    }

    public boolean hasPainting(String name) {
        return entries.containsKey(normalize(name));
    }

    public void removePainting(String name) {
        String key = normalize(name);
        PaintingDefinition definition = entries.remove(key);
        if (definition == null) {
            return;
        }

        for (int y = 0; y < definition.height(); y++) {
            for (int x = 0; x < definition.width(); x++) {
                File tile = new File(processedDir, tileFileName(definition.filePrefix(), x, y));
                if (tile.exists()) {
                    tile.delete();
                }
            }
        }

        saveIndex();
    }

    public PaintingDefinition importPainting(String name, String uploadFileName, int widthMaps, int heightMaps) throws IOException {
        String key = normalize(name);
        validateName(key);
        validateDimensions(widthMaps, heightMaps);

        File source = new File(uploadsDir, uploadFileName);
        String uploadsRoot = uploadsDir.getCanonicalPath() + File.separator;
        if (!source.getCanonicalPath().startsWith(uploadsRoot)) {
            throw new IOException("Invalid upload filename path.");
        }
        if (!source.exists() || !source.isFile()) {
            throw new IOException("Upload file not found: " + uploadFileName);
        }
        if (!uploadFileName.toLowerCase(Locale.ROOT).endsWith(".png")) {
            throw new IOException("Only .png files are supported.");
        }

        BufferedImage original = ImageIO.read(source);
        if (original == null) {
            throw new IOException("File is not a valid image: " + uploadFileName);
        }

        int canvasWidth = widthMaps * MAP_SIZE;
        int canvasHeight = heightMaps * MAP_SIZE;
        BufferedImage mural = resizeToCanvas(original, canvasWidth, canvasHeight);

        PaintingDefinition old = entries.get(key);
        if (old != null) {
            removePainting(key);
        }

        PaintingDefinition definition = new PaintingDefinition(key, widthMaps, heightMaps, key);
        for (int y = 0; y < heightMaps; y++) {
            for (int x = 0; x < widthMaps; x++) {
                BufferedImage tile = mural.getSubimage(x * MAP_SIZE, y * MAP_SIZE, MAP_SIZE, MAP_SIZE);
                File outFile = new File(processedDir, tileFileName(definition.filePrefix(), x, y));
                ImageIO.write(tile, "PNG", outFile);
            }
        }

        entries.put(key, definition);
        saveIndex();
        return definition;
    }

    public List<ItemStack> createPaintingMapSet(String paintingName, Player player) throws IOException {
        String key = normalize(paintingName);
        PaintingDefinition definition = entries.get(key);
        if (definition == null) {
            throw new IOException("Painting not found: " + paintingName);
        }

        List<ItemStack> maps = new ArrayList<>();
        World world = player.getWorld();

        for (int y = 0; y < definition.height(); y++) {
            for (int x = 0; x < definition.width(); x++) {
                File file = new File(processedDir, tileFileName(definition.filePrefix(), x, y));
                if (!file.exists()) {
                    throw new IOException("Painting tile missing on disk: " + file.getName());
                }

                BufferedImage image = ImageIO.read(file);
                if (image == null) {
                    throw new IOException("Painting tile is corrupt: " + file.getName());
                }

                MapView mapView = Bukkit.createMap(world);
                mapView.getRenderers().forEach(mapView::removeRenderer);
                mapView.addRenderer(new StaticImageMapRenderer(image));
                mapView.setTrackingPosition(false);
                mapView.setUnlimitedTracking(false);

                ItemStack map = new ItemStack(Material.FILLED_MAP);
                MapMeta meta = (MapMeta) map.getItemMeta();
                if (meta == null) {
                    throw new IOException("Failed to build map item metadata.");
                }

                meta.setMapView(mapView);
                meta.setDisplayName("§fPainting: §e" + key + " §7[" + (x + 1) + "/" + definition.width() + ", " + (y + 1) + "/" + definition.height() + "]");
                map.setItemMeta(meta);
                maps.add(map);
            }
        }

        return maps;
    }

    private static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static String tileFileName(String prefix, int x, int y) {
        return prefix + "_" + x + "_" + y + ".png";
    }

    private static void validateName(String name) throws IOException {
        if (!name.matches("[a-z0-9_-]{1,32}")) {
            throw new IOException("Name must match [a-z0-9_-]{1,32}");
        }
    }

    private static void validateDimensions(int width, int height) throws IOException {
        if (width < MIN_MAPS || width > MAX_MAPS || height < MIN_MAPS || height > MAX_MAPS) {
            throw new IOException("Mural size must be from 1 to 8 maps in each direction.");
        }
    }

    private static BufferedImage resizeToCanvas(BufferedImage input, int canvasWidth, int canvasHeight) {
        BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, canvasWidth, canvasHeight);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int srcW = input.getWidth();
            int srcH = input.getHeight();
            double scale = Math.min((double) canvasWidth / srcW, (double) canvasHeight / srcH);
            int dstW = Math.max(1, (int) Math.round(srcW * scale));
            int dstH = Math.max(1, (int) Math.round(srcH * scale));
            int x = (canvasWidth - dstW) / 2;
            int y = (canvasHeight - dstH) / 2;

            g.drawImage(input, x, y, dstW, dstH, null);
        } finally {
            g.dispose();
        }
        return canvas;
    }

    public record PaintingDefinition(String name, int width, int height, String filePrefix) {
    }
}
