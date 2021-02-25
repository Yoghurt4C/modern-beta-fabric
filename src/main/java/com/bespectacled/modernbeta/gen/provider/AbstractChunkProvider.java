package com.bespectacled.modernbeta.gen.provider;

import java.util.Random;

import com.bespectacled.modernbeta.biome.OldBiomeSource;
import com.bespectacled.modernbeta.decorator.OldDecorators;
import com.bespectacled.modernbeta.gen.OldGeneratorSettings;
import com.bespectacled.modernbeta.mixin.MixinAquiferSamplerInvoker;
import com.bespectacled.modernbeta.noise.PerlinOctaveNoise;
import com.bespectacled.modernbeta.util.BlockStates;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.AquiferSampler;
import net.minecraft.world.gen.BlockInterpolator;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.GrimstoneInterpolator;
import net.minecraft.world.gen.NoiseCaveSampler;
import net.minecraft.world.gen.SimpleRandom;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.StructureWeightSampler;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;

/*
 * Some vanilla settings, for reference:
 * 
 * sizeVertical = 2
 * sizeHorizontal = 1
 * height = 128 (or 256 in vanilla)
 * 
 * verticalNoiseResolution = sizeVertical * 4 (8)
 * horizontalNoiseResolution = sizeHorizontal * 4 (4)
 * 
 * noiseSizeX = 16 / horizontalNoiseResolution (4)
 * noiseSizeZ = 16 / horizontalNoiseResolution (4)
 * noiseSizeY = height / verticalNoiseResolution (16)
 * 
 */
public abstract class AbstractChunkProvider {
    protected static final Random RAND = new Random();
    
    //protected static final Object2ObjectLinkedOpenHashMap<BlockPos, Integer> HEIGHTMAP_CACHE = new Object2ObjectLinkedOpenHashMap<>(512);
    //protected static final int[][] HEIGHTMAP_CHUNK = new int[16][16];
    
    protected final ChunkGeneratorSettings generatorSettings;
    protected final CompoundTag providerSettings;
    
    protected final int minY;
    protected final int worldHeight;
    protected final int seaLevel;
    
    protected final int bedrockFloor;
    protected final int bedrockCeiling;
    
    protected final int verticalNoiseResolution;   // Number of blocks in a horizontal subchunk
    protected final int horizontalNoiseResolution; // Number of blocks in a vertical subchunk 
    
    protected final int noiseSizeX; // Number of horizontal subchunks along x
    protected final int noiseSizeZ; // Number of horizontal subchunks along z
    protected final int noiseSizeY; // Number of vertical subchunks
    protected final int noiseMinY;  // Subchunk index of bottom of the world
    protected final int noisePosY;  // Number of positive (y >= 0) vertical subchunks

    protected final double xzScale;
    protected final double yScale;
    
    protected final double xzFactor;
    protected final double yFactor;
    
    protected final boolean generateNoiseCaves;
    protected final boolean generateAquifers;
    protected final boolean generateGrimstone;
    
    protected final BlockState defaultBlock;
    protected final BlockState defaultFluid;
    
    protected final NoiseCaveSampler noiseCaveSampler;
    
    protected final DoublePerlinNoiseSampler doublePerlinSampler0;
    protected final DoublePerlinNoiseSampler doublePerlinSampler1;
    
    protected final BlockInterpolator deepslateInterpolator;
    
    public AbstractChunkProvider(long seed, OldGeneratorSettings settings) {
        this(
            seed,
            settings.generatorSettings.getGenerationShapeConfig().getMinimumY(),
            settings.generatorSettings.getGenerationShapeConfig().getHeight(),
            settings.generatorSettings.getSeaLevel(),
            settings.generatorSettings.getBedrockFloorY(),
            settings.generatorSettings.getBedrockCeilingY(),
            settings.generatorSettings.getGenerationShapeConfig().getSizeVertical(),
            settings.generatorSettings.getGenerationShapeConfig().getSizeHorizontal(),
            settings.generatorSettings.getGenerationShapeConfig().getSampling().getXZScale(),
            settings.generatorSettings.getGenerationShapeConfig().getSampling().getYScale(),
            settings.generatorSettings.getGenerationShapeConfig().getSampling().getXZFactor(),
            settings.generatorSettings.getGenerationShapeConfig().getSampling().getYFactor(),
            true,
            true,
            true,
            settings.generatorSettings.getDefaultBlock(),
            settings.generatorSettings.getDefaultFluid(),
            settings
        );
    }
    
    public AbstractChunkProvider(
        long seed,
        int minY, 
        int worldHeight, 
        int seaLevel,
        int bedrockFloor,
        int bedrockCeiling,
        int sizeVertical, 
        int sizeHorizontal, 
        double xzScale, 
        double yScale, 
        double xzFactor, 
        double yFactor,
        boolean generateNoiseCaves,
        boolean generateAquifers,
        boolean generateGrimstone,
        BlockState defaultBlock,
        BlockState defaultFluid,
        OldGeneratorSettings settings
    ) {
        this.minY = minY;
        this.worldHeight = worldHeight;
        this.seaLevel = seaLevel;
        
        this.bedrockFloor = bedrockFloor;
        this.bedrockCeiling = bedrockCeiling;
        
        this.verticalNoiseResolution = sizeVertical * 4;
        this.horizontalNoiseResolution = sizeHorizontal * 4;
        
        this.noiseSizeX = 16 / this.horizontalNoiseResolution;
        this.noiseSizeZ = 16 / this.horizontalNoiseResolution;
        this.noiseSizeY = this.worldHeight / this.verticalNoiseResolution;
        this.noiseMinY = this.minY / this.verticalNoiseResolution;
        this.noisePosY = (this.worldHeight + this.minY) / this.verticalNoiseResolution;
        
        this.xzScale = xzScale;
        this.yScale = yScale;
        
        this.xzFactor = xzFactor;
        this.yFactor = yFactor;
        
        this.generateNoiseCaves = generateNoiseCaves;
        this.generateAquifers = generateAquifers;
        this.generateGrimstone = generateGrimstone;
        
        this.defaultBlock = defaultBlock;
        this.defaultFluid = defaultFluid;
        
        this.generatorSettings = settings.generatorSettings;
        this.providerSettings = settings.providerSettings;
        
        ChunkRandom chunkRandom = new ChunkRandom(seed);
        this.doublePerlinSampler0 = DoublePerlinNoiseSampler.create(new SimpleRandom(chunkRandom.nextLong()), -3, 1.0);
        this.doublePerlinSampler1 = DoublePerlinNoiseSampler.create(new SimpleRandom(chunkRandom.nextLong()), -3, 1.0, 0.0, 2.0);
        
        this.noiseCaveSampler = this.generateNoiseCaves ? new NoiseCaveSampler(chunkRandom, this.noiseMinY) : null;
        this.deepslateInterpolator = new GrimstoneInterpolator(seed, this.defaultBlock, Blocks.GRIMSTONE.getDefaultState());
        
        RAND.setSeed(seed);
        //HEIGHTMAP_CACHE.clear();
    }
    
    public abstract Chunk provideChunk(StructureAccessor structureAccessor, Chunk chunk, OldBiomeSource biomeSource);
    public abstract void provideSurface(ChunkRegion region, Chunk chunk, OldBiomeSource biomeSource);
    public abstract int getHeight(int x, int z, Heightmap.Type type);
    public abstract PerlinOctaveNoise getBeachNoiseOctaves();
    
    public int getWorldHeight() {
        return this.worldHeight;
    }
    
    public int getSeaLevel() {
        return this.seaLevel;
    }
    
    public int getVerticalNoiseResolution() {
        return this.verticalNoiseResolution;
    }
    
    protected BlockState getBlockStateSky(StructureWeightSampler weightSampler, int x, int y, int z, double density) {
        double clampedDensity = MathHelper.clamp(density / 200.0, -1.0, 1.0);
        clampedDensity = clampedDensity / 2.0 - clampedDensity * clampedDensity * clampedDensity / 24.0;
        clampedDensity += weightSampler.getWeight(x, y, z);
        
        BlockState blockStateToSet = BlockStates.AIR;
        
        if (clampedDensity > 0.0) {
            blockStateToSet = this.defaultBlock;
        }
        
        return blockStateToSet;
    }
    
    protected BlockState getBlockState(StructureWeightSampler weightSampler, AquiferSampler aquiferSampler, int x, int y, int z, double density) {
        double clampedDensity = MathHelper.clamp(density / 200.0, -1.0, 1.0);
        clampedDensity = clampedDensity / 2.0 - clampedDensity * clampedDensity * clampedDensity / 24.0;
        clampedDensity += weightSampler.getWeight(x, y, z);
        
        if (aquiferSampler != null) {
            ((MixinAquiferSamplerInvoker)aquiferSampler).invokeApply(x, y, z);
            clampedDensity += aquiferSampler.getDensityAddition();
        }
        
        BlockState blockStateToSet = BlockStates.AIR;
        
        if (clampedDensity > 0.0) {
            blockStateToSet = this.deepslateInterpolator.sample(x, y, z, this.generatorSettings);
        } else {
            int localSeaLevel = (aquiferSampler == null) ? this.getSeaLevel() : aquiferSampler.getWaterLevel();
            
            if (y < localSeaLevel) {
                blockStateToSet = this.defaultFluid;
            }
        }
        
        return blockStateToSet;
    }
    
    protected double sampleNoiseCave(int x, int y, int z, double offset, double noise) {
        if (this.noiseCaveSampler != null) {
            return this.noiseCaveSampler.sample(x, y, z, offset, noise);
        }
        
        return noise;
    }
    
    protected AquiferSampler createAquiferSampler(int chunkX, int chunkZ) {
        return this.generateAquifers ? 
            new AquiferSampler(
                chunkX, 
                chunkZ, 
                this.doublePerlinSampler0, 
                this.doublePerlinSampler1, 
                this.generatorSettings, 
                null, 
                this.noiseSizeY * this.verticalNoiseResolution
            ) : 
            null;     
    }
    
    protected double applyBottomSlide(double noise, int noiseY) {
        int slideOffset = noiseY - this.noiseMinY;
        int bottomSlideSize = this.generatorSettings.getGenerationShapeConfig().getBottomSlide().getSize();
        int bottomSlideOffset = this.generatorSettings.getGenerationShapeConfig().getBottomSlide().getOffset();
        int bottomSlideTarget = this.generatorSettings.getGenerationShapeConfig().getBottomSlide().getTarget();
        
        if (bottomSlideSize > 0.0) {
            double lerpedNoise = (slideOffset - bottomSlideOffset) / bottomSlideSize;
            noise = MathHelper.clampedLerp(bottomSlideTarget, noise, lerpedNoise);
        }
        
        return noise;
    }
    
    protected ChunkRandom createChunkRand(int chunkX, int chunkZ) {
        ChunkRandom chunkRand = new ChunkRandom();
        chunkRand.setTerrainSeed(chunkX, chunkZ);
        
        return chunkRand;
    }
    
    public static Biome getBiomeForSurfaceGen(BlockPos pos, ChunkRegion region, OldBiomeSource biomeSource) {
        if (biomeSource.isBeta()) {
            return biomeSource.getBiomeForSurfaceGen(pos.getX(), 0, pos.getZ());
        }
        
        return region.getBiome(pos);
    }
    
    public static void setForestOctaves(PerlinOctaveNoise forestOctaves) {
        OldDecorators.COUNT_BETA_NOISE_DECORATOR.setOctaves(forestOctaves);
        OldDecorators.COUNT_ALPHA_NOISE_DECORATOR.setOctaves(forestOctaves);
        OldDecorators.COUNT_INFDEV_NOISE_DECORATOR.setOctaves(forestOctaves);
    }
}
