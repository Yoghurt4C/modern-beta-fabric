package com.bespectacled.modernbeta.client.gui.screen.cavebiome;

import java.util.function.Consumer;

import com.bespectacled.modernbeta.api.client.gui.screen.SettingsScreen;
import com.bespectacled.modernbeta.api.client.gui.wrapper.ActionOptionWrapper;
import com.bespectacled.modernbeta.api.client.gui.wrapper.BooleanCyclingOptionWrapper;
import com.bespectacled.modernbeta.api.client.gui.wrapper.DoubleOptionWrapper;
import com.bespectacled.modernbeta.api.client.gui.wrapper.TextOptionWrapper;
import com.bespectacled.modernbeta.client.gui.Settings;
import com.bespectacled.modernbeta.client.gui.WorldSettings.WorldSetting;
import com.bespectacled.modernbeta.client.gui.screen.WorldScreen;
import com.bespectacled.modernbeta.config.ModernBetaConfigCaveBiome.CaveBiomeVoronoiPoint;
import com.bespectacled.modernbeta.util.GuiUtil;
import com.bespectacled.modernbeta.util.NbtTags;
import com.bespectacled.modernbeta.util.NbtUtil;
import net.minecraft.client.gui.screen.CustomizeBuffetLevelScreen;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;

public class VoronoiCaveBiomeScreen extends SettingsScreen {
    private static final String VERTICAL_SCALE_DISPLAY_STRING = "createWorld.customize.caveBiome.verticalNoiseScale";
    private static final String HORIZONTAL_SCALE_DISPLAY_STRING = "createWorld.customize.caveBiome.horizontalNoiseScale";
    
    private static final String BIOME_DISPLAY_STRING = "createWorld.customize.biome";
    private static final String TEMP_DISPLAY_STRING = "createWorld.customize.temp";
    private static final String RAIN_DISPLAY_STRING = "createWorld.customize.rain";

    private static final String VORONOI_POINTS_DISPLAY_STRING = "createWorld.customize.biome.voronoi.points";
    private static final String ADD_POINT_DISPLAY_STRING = "createWorld.customize.biome.voronoi.addPoint";
    private static final String REMOVE_POINT_DISPLAY_STRING = "createWorld.customize.biome.voronoi.removePoint";
    
    private NbtList voronoiPoints;
    
    private VoronoiCaveBiomeScreen(WorldScreen parent, WorldSetting worldSetting, Consumer<Settings> consumer, Settings settings) {
        super(parent, worldSetting, consumer, settings);
    }
    
    private VoronoiCaveBiomeScreen(WorldScreen parent, WorldSetting worldSetting, Consumer<Settings> consumer) {
        this(parent, worldSetting, consumer, new Settings(parent.getWorldSettings().getNbt(worldSetting)));
    }
    
    public static VoronoiCaveBiomeScreen create(WorldScreen worldScreen, WorldSetting worldSetting) {
        return new VoronoiCaveBiomeScreen(
            worldScreen,
            worldSetting,
            settings -> worldScreen.getWorldSettings().putCompound(worldSetting, settings.getNbt())
        );
    }
    
    @Override
    protected void init() {
        super.init();

        // Create voronoi point list from existing biome settings
        this.voronoiPoints = NbtUtil.readListOrThrow(NbtTags.BIOMES, this.settings.getNbt());
        
        DoubleOptionWrapper<Integer> verticalScaleOption = new DoubleOptionWrapper<>(
            VERTICAL_SCALE_DISPLAY_STRING,
            1D, 64D, 1f,
            () -> NbtUtil.toIntOrThrow(this.getSetting(NbtTags.VERTICAL_NOISE_SCALE)),
            value -> this.putSetting(NbtTags.VERTICAL_NOISE_SCALE, NbtInt.of(value.intValue()))
        );
        
        DoubleOptionWrapper<Integer> horizontalScaleOption = new DoubleOptionWrapper<>(
            HORIZONTAL_SCALE_DISPLAY_STRING,
            1D, 64D, 1f,
            () -> NbtUtil.toIntOrThrow(this.getSetting(NbtTags.HORIZONTAL_NOISE_SCALE)),
            value -> this.putSetting(NbtTags.HORIZONTAL_NOISE_SCALE, NbtInt.of(value.intValue()))
        );
        
        TextOptionWrapper voronoiHeader = new TextOptionWrapper(VORONOI_POINTS_DISPLAY_STRING);
        voronoiHeader.formatting(Formatting.YELLOW).formatting(Formatting.BOLD);
            
        this.addOption(verticalScaleOption);
        this.addOption(horizontalScaleOption);
        this.addOption(voronoiHeader);
        
        this.addOption(this.addVoronoiEntry());
        this.voronoiPoints.forEach(
            element -> {
                NbtCompound compound = (NbtCompound)element;
                
                this.createVoronoiOption(compound);
            }
        );
    }
    
    private void createVoronoiOption(NbtCompound compound) {
        CaveBiomeVoronoiPoint point = new CaveBiomeVoronoiPoint(
            NbtUtil.readStringOrThrow(NbtTags.BIOME, compound),
            NbtUtil.readDoubleOrThrow(NbtTags.TEMP, compound),
            NbtUtil.readDoubleOrThrow(NbtTags.RAIN, compound),
            NbtUtil.readBooleanOrThrow(NbtTags.NULL_BIOME, compound)
        );

        DoubleOptionWrapper<Double> tempOption = new DoubleOptionWrapper<>(
            TEMP_DISPLAY_STRING,
            0.0, 1.0, 0.01f,
            () -> NbtUtil.readDoubleOrThrow(NbtTags.TEMP, compound),
            value -> {
                point.temp = value;
                this.updateVoronoiEntry(point, compound);
            }
        );
        
        DoubleOptionWrapper<Double> rainOption = new DoubleOptionWrapper<>(
            RAIN_DISPLAY_STRING,
            0.0, 1.0, 0.01f,
            () -> NbtUtil.readDoubleOrThrow(NbtTags.RAIN, compound),
            value -> {
                point.rain = value;
                this.updateVoronoiEntry(point, compound);
            }
        );
        
        BooleanCyclingOptionWrapper nullBiomeOption = new BooleanCyclingOptionWrapper(
            "Null biome",
            () -> NbtUtil.readBooleanOrThrow(NbtTags.NULL_BIOME, compound),
            value -> {
                point.nullBiome = value;
                this.updateVoronoiEntry(point, compound);
            }
        );
        
        boolean allowRemoveEntry = this.voronoiPoints.size() > 1;
        
        this.addOption(this.createBiomeSelectionScreen(point, compound));
        this.addOption(tempOption);
        this.addOption(rainOption);
        this.addDualOption(nullBiomeOption, this.removeVoronoiEntry(compound), true, allowRemoveEntry);
    }
    
    private ActionOptionWrapper createBiomeSelectionScreen(CaveBiomeVoronoiPoint point, NbtCompound compound) {
        Identifier biomeId = new Identifier(point.biome);
        
        return new ActionOptionWrapper(
            BIOME_DISPLAY_STRING,
            buttonWidget -> this.client.setScreen(new CustomizeBuffetLevelScreen(
                this,
                this.registryManager,
                biome -> {
                    point.biome = this.registryManager.<Biome>get(Registry.BIOME_KEY).getId(biome).toString();
                    
                    this.updateVoronoiEntry(point, compound);
                },
                this.registryManager.<Biome>get(Registry.BIOME_KEY).get(biomeId)
            ))
        ).suffix(GuiUtil.createTranslatableBiomeStringFromId(biomeId)).truncate(false);
    }
    
    private ActionOptionWrapper addVoronoiEntry() {
        return new ActionOptionWrapper(
            ADD_POINT_DISPLAY_STRING,
            buttonWidget -> {
                this.voronoiPoints.add(0, CaveBiomeVoronoiPoint.DEFAULT.toCompound());
                
                this.client.setScreen(new VoronoiCaveBiomeScreen(
                    (WorldScreen)this.parent,
                    this.worldSetting,
                    this.consumer,
                    this.settings
                ));
            }
        ).truncate(false).formatting(Formatting.GREEN).formatting(Formatting.BOLD);
    }
    
    private ActionOptionWrapper removeVoronoiEntry(NbtCompound compound) {
        return new ActionOptionWrapper(
            REMOVE_POINT_DISPLAY_STRING,
            buttonWidget -> {
                this.voronoiPoints.remove(compound);
                
                this.client.setScreen(new VoronoiCaveBiomeScreen(
                    (WorldScreen)this.parent,
                    this.worldSetting,
                    this.consumer,
                    this.settings
                ));
            }
        ).truncate(false).formatting(Formatting.RED).formatting(Formatting.BOLD);
    }
    
    private void updateVoronoiEntry(CaveBiomeVoronoiPoint point, NbtCompound compound) {
        compound.putString(NbtTags.BIOME, point.biome);
        compound.putDouble(NbtTags.TEMP, point.temp);
        compound.putDouble(NbtTags.RAIN, point.rain);
        compound.putBoolean(NbtTags.NULL_BIOME, point.nullBiome);
    }
}