package dev.custompaintings;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class PaintingCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = Arrays.asList("import", "give", "list", "remove", "reload");

    private final PaintingManager paintingManager;

    public PaintingCommand(PaintingManager paintingManager) {
        this.paintingManager = paintingManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        try {
            switch (sub) {
                case "import" -> handleImport(sender, args);
                case "give" -> handleGive(sender, args);
                case "list" -> handleList(sender);
                case "remove" -> handleRemove(sender, args);
                case "reload" -> handleReload(sender);
                default -> sendUsage(sender, label);
            }
        } catch (IOException | IllegalArgumentException e) {
            sender.sendMessage("§cError: " + e.getMessage());
        }

        return true;
    }

    private void handleImport(CommandSender sender, String[] args) throws IOException {
        requireAdmin(sender);
        if (args.length < 3) {
            sender.sendMessage("§eUsage: /painting import <name> <upload-file.png> [widthMaps] [heightMaps]");
            return;
        }

        String name = args[1];
        String uploadFile = args[2];
        int width = 1;
        int height = 1;

        if (args.length >= 4) {
            width = parseAmount(args[3], "widthMaps");
        }
        if (args.length >= 5) {
            height = parseAmount(args[4], "heightMaps");
        }

        PaintingManager.PaintingDefinition definition = paintingManager.importPainting(name, uploadFile, width, height);
        sender.sendMessage("§aImported painting '" + definition.name() + "' as " + definition.width() + "x" + definition.height() + " maps from " + uploadFile);
    }

    private void handleGive(CommandSender sender, String[] args) throws IOException {
        if (args.length < 2) {
            sender.sendMessage("§eUsage: /painting give <name> [player] [sets]");
            return;
        }

        String paintingName = args[1];
        Player target;

        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found: " + args[2]);
                return;
            }
            if (!sender.hasPermission("custompaintings.admin") && !sender.getName().equalsIgnoreCase(target.getName())) {
                sender.sendMessage("§cYou can only give paintings to yourself.");
                return;
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cConsole must specify a player: /painting give <name> <player>");
                return;
            }
            target = player;
        }

        int sets = 1;
        if (args.length >= 4) {
            sets = parseAmount(args[3], "sets");
            sets = Math.max(1, Math.min(64, sets));
        }

        PaintingManager.PaintingDefinition definition = paintingManager.getDefinition(paintingName)
                .orElseThrow(() -> new IOException("Painting not found: " + paintingName));

        int deliveredItems = 0;
        for (int i = 0; i < sets; i++) {
            List<ItemStack> setItems = paintingManager.createPaintingMapSet(paintingName, target);
            for (ItemStack item : setItems) {
                target.getInventory().addItem(item).values().forEach(overflow -> target.getWorld().dropItemNaturally(target.getLocation(), overflow));
                deliveredItems++;
            }
        }

        String descriptor = definition.width() + "x" + definition.height() + " mural";
        sender.sendMessage("§aGave " + sets + " set(s) of '" + definition.name() + "' (" + descriptor + ", " + deliveredItems + " map items) to " + target.getName());
        if (!sender.getName().equalsIgnoreCase(target.getName())) {
            target.sendMessage("§aYou received " + sets + " set(s) of painting '" + definition.name() + "' (" + descriptor + ").");
        }
    }

    private void handleList(CommandSender sender) {
        Set<String> names = paintingManager.getNames();
        if (names.isEmpty()) {
            sender.sendMessage("§eNo paintings are registered yet.");
            sender.sendMessage("§7Upload PNG files to: " + paintingManager.getUploadsDir().getAbsolutePath());
            return;
        }

        sender.sendMessage("§aPaintings (" + names.size() + "):");
        for (String name : names) {
            PaintingManager.PaintingDefinition def = paintingManager.getDefinition(name).orElse(null);
            if (def == null) {
                continue;
            }
            sender.sendMessage("§f- " + def.name() + " §7(" + def.width() + "x" + def.height() + ")");
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        requireAdmin(sender);
        if (args.length < 2) {
            sender.sendMessage("§eUsage: /painting remove <name>");
            return;
        }

        String name = args[1].toLowerCase(Locale.ROOT);
        if (!paintingManager.hasPainting(name)) {
            sender.sendMessage("§cPainting not found: " + name);
            return;
        }

        paintingManager.removePainting(name);
        sender.sendMessage("§aRemoved painting '" + name + "'.");
    }

    private void handleReload(CommandSender sender) {
        requireAdmin(sender);
        paintingManager.loadIndex();
        sender.sendMessage("§aPaintings index reloaded.");
    }

    private void requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("custompaintings.admin")) {
            throw new IllegalArgumentException("No permission (custompaintings.admin)");
        }
    }

    private static int parseAmount(String raw, String label) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a number.");
        }
    }

    private static void sendUsage(CommandSender sender, String label) {
        sender.sendMessage("§eCustomPaintings commands:");
        sender.sendMessage("§f/" + label + " import <name> <upload-file.png> [widthMaps] [heightMaps] §7(admin)");
        sender.sendMessage("§f/" + label + " give <name> [player] [sets]");
        sender.sendMessage("§f/" + label + " list");
        sender.sendMessage("§f/" + label + " remove <name> §7(admin)");
        sender.sendMessage("§f/" + label + " reload §7(admin)");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(args[0], SUBCOMMANDS);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ((sub.equals("give") || sub.equals("remove")) && args.length == 2) {
            return partial(args[1], new ArrayList<>(paintingManager.getNames()));
        }

        if (sub.equals("give") && args.length == 3) {
            return partial(args[2], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
        }

        if (sub.equals("import") && (args.length == 4 || args.length == 5)) {
            return partial(args[args.length - 1], Arrays.asList("1", "2", "3", "4"));
        }

        return Collections.emptyList();
    }

    private static List<String> partial(String arg, List<String> values) {
        String lower = arg.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(v -> v.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted()
                .collect(Collectors.toList());
    }
}
