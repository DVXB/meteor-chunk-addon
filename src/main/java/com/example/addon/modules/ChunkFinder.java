package example.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class ChunkFinder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // --- GENERAL SETTINGS ---
    private final Setting<List<Block>> targetBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("target-blocks")
        .description("The blocks to look for (e.g., Deepslate variants, Torches, Wood).")
        .defaultValue(Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.TORCH)
        .build()
    );

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y")
        .description("The minimum Y level to monitor blocks.")
        .defaultValue(0)
        .sliderRange(-64, 319)
        .build()
    );

    private final Setting<Integer> sensitivity = sgGeneral.add(new IntSetting.Builder()
        .name("sensitivity")
        .description("How many matching blocks must be found in a chunk to flag it.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 20)
        .build()
    );

    // --- RENDER SETTINGS ---
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the marked chunks are drawn.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("The side color of the highlighted chunk.")
        .defaultValue(new SettingColor(255, 0, 0, 50))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("The line color of the highlighted chunk.")
        .defaultValue(new SettingColor(255, 0, 0, 205))
        .build()
    );

    // Maps a chunk coordinate to the unique block positions recorded inside it
    private final Map<ChunkPos, HashSet<BlockPos>> trackedChunks = new HashMap<>();

    public ChunkFinder() {
        super(Categories.World, "chunk-finder", "Finds active/player chunks based on abnormal block footprints.");
    }

    @Override
    public void onActivate() {
        trackedChunks.clear();
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (mc.world == null) return;

        BlockPos pos = event.pos;
        Block newBlock = event.newState.getBlock();

        // Check if the block matches your configured criteria (Y-level and Block type)
        if (pos.getY() >= minY.get() && targetBlocks.get().contains(newBlock)) {
            ChunkPos chunkPos = new ChunkPos(pos);
            
            // Add the position to the chunk's list tracking unique coordinates
            trackedChunks.computeIfAbsent(chunkPos, k -> new HashSet<>()).add(pos.toImmutable());
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (trackedChunks.isEmpty()) return;

        for (Map.Entry<ChunkPos, HashSet<BlockPos>> entry : trackedChunks.entrySet()) {
            // Only render if the block count meets or exceeds your sensitivity value
            if (entry.getValue().size() >= sensitivity.get()) {
                ChunkPos cPos = entry.getKey();
                
                // Establish box coordinates for the full chunk (from build limit min to max)
                double x1 = cPos.getStartX();
                double z1 = cPos.getStartZ();
                double x2 = cPos.getEndX() + 1;
                double z2 = cPos.getEndZ() + 1;
                
                double y1 = mc.world.getBottomY();
                double y2 = mc.world.getTopY();

                // Render bounding boxes around flagged chunks
                event.renderer.box(x1, y1, z1, x2, y2, z2, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            }
        }
    }
}