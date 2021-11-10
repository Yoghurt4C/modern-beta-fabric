package com.bespectacled.modernbeta.world.gen.provider;

import java.util.Random;

import com.bespectacled.modernbeta.ModernBeta;
import com.bespectacled.modernbeta.api.world.biome.ClimateBiomeProvider;
import com.bespectacled.modernbeta.api.world.biome.climate.ClimateSampler;
import com.bespectacled.modernbeta.api.world.biome.climate.Clime;
import com.bespectacled.modernbeta.api.world.gen.NoiseChunkProvider;
import com.bespectacled.modernbeta.noise.PerlinOctaveNoise;
import com.bespectacled.modernbeta.noise.SimplexNoise;
import com.bespectacled.modernbeta.util.BlockColumnHolder;
import com.bespectacled.modernbeta.util.BlockStates;
import com.bespectacled.modernbeta.util.GenUtil;
import com.bespectacled.modernbeta.util.NbtTags;
import com.bespectacled.modernbeta.util.NbtUtil;
import com.bespectacled.modernbeta.util.chunk.HeightmapChunk;
import com.bespectacled.modernbeta.world.biome.OldBiomeSource;
import com.bespectacled.modernbeta.world.biome.provider.climate.BetaClimateSampler;
import com.bespectacled.modernbeta.world.gen.OldChunkGenerator;
import com.bespectacled.modernbeta.world.gen.sampler.InterpolatedNoiseSampler;
import com.bespectacled.modernbeta.world.spawn.BeachSpawnLocator;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.HeightContext;
import net.minecraft.world.gen.chunk.AquiferSampler;
import net.minecraft.world.gen.surfacebuilder.MaterialRules;

public class BetaIslandsChunkProvider extends NoiseChunkProvider {
    private final InterpolatedNoiseSampler noiseSampler;
    
    private final PerlinOctaveNoise beachNoiseOctaves;
    private final PerlinOctaveNoise surfaceNoiseOctaves;
    private final PerlinOctaveNoise scaleNoiseOctaves;
    private final PerlinOctaveNoise depthNoiseOctaves;
    private final PerlinOctaveNoise forestNoiseOctaves;
    
    private final SimplexNoise islandNoise;
    
    private final boolean generateOuterIslands;
    private final int centerIslandRadius;
    private final float centerIslandFalloff;
    private final int centerOceanLerpDistance;
    private final int centerOceanRadius;
    private final float outerIslandNoiseScale;
    private final float outerIslandNoiseOffset;

    private final ClimateSampler climateSampler;
    
    public BetaIslandsChunkProvider(OldChunkGenerator chunkGenerator) {
        super(chunkGenerator);
        
        // Noise Generators
        this.noiseSampler = new InterpolatedNoiseSampler(
            this.rand,
            684.412 * this.xzScale,
            684.412 * this.yScale,
            this.xzFactor,
            this.yFactor,
            512D,
            128.0,
            this.noiseSizeX,
            this.noiseSizeY,
            this.noiseSizeZ, this.noiseMinY
        );
        
        this.beachNoiseOctaves = new PerlinOctaveNoise(rand, 4, true);
        this.surfaceNoiseOctaves = new PerlinOctaveNoise(rand, 4, true);
        this.scaleNoiseOctaves = new PerlinOctaveNoise(rand, 10, true);
        this.depthNoiseOctaves = new PerlinOctaveNoise(rand, 16, true);
        this.forestNoiseOctaves = new PerlinOctaveNoise(rand, 8, true);
        this.islandNoise = new SimplexNoise(rand);

        setForestOctaves(forestNoiseOctaves);
        
        // Get climate sampler from biome provider if exists and enabled,
        // else create new default Beta climate sampler.
        boolean sampleClimate = NbtUtil.readBoolean(
            NbtTags.SAMPLE_CLIMATE, 
            providerSettings, 
            ModernBeta.GEN_CONFIG.infGenConfig.sampleClimate
        );

        ClimateSampler climateSampler = new BetaClimateSampler(chunkGenerator.getWorldSeed());
        
        if (chunkGenerator.getBiomeSource() instanceof OldBiomeSource oldBiomeSource && 
            oldBiomeSource.getBiomeProvider() instanceof ClimateBiomeProvider climateBiomeProvider
        ) {
            if (sampleClimate) {
                climateSampler = climateBiomeProvider.getClimateSampler();
            }
        }
        
        this.climateSampler = climateSampler;
        this.spawnLocator = new BeachSpawnLocator(this, this.beachNoiseOctaves);
        
        // Beta Islands settings
        this.generateOuterIslands = NbtUtil.readBoolean(NbtTags.GEN_OUTER_ISLANDS, providerSettings, ModernBeta.GEN_CONFIG.islandGenConfig.generateOuterIslands);
        this.centerIslandRadius = NbtUtil.readInt(NbtTags.CENTER_ISLAND_RADIUS, providerSettings, ModernBeta.GEN_CONFIG.islandGenConfig.centerIslandRadius);
        this.centerIslandFalloff = NbtUtil.readFloat(NbtTags.CENTER_ISLAND_FALLOFF, providerSettings, ModernBeta.GEN_CONFIG.islandGenConfig.centerIslandFalloff);
        this.centerOceanLerpDistance = NbtUtil.readInt(NbtTags.CENTER_OCEAN_LERP_DIST, providerSettings, ModernBeta.GEN_CONFIG.islandGenConfig.centerOceanLerpDistance);
        this.centerOceanRadius = NbtUtil.readInt(NbtTags.CENTER_OCEAN_RADIUS, providerSettings, ModernBeta.GEN_CONFIG.islandGenConfig.centerOceanRadius);
        this.outerIslandNoiseScale = NbtUtil.readFloat(NbtTags.OUTER_ISLAND_NOISE_SCALE, providerSettings, ModernBeta.GEN_CONFIG.islandGenConfig.outerIslandNoiseScale);
        this.outerIslandNoiseOffset = NbtUtil.readFloat(NbtTags.OUTER_ISLAND_NOISE_OFFSET, providerSettings, ModernBeta.GEN_CONFIG.islandGenConfig.outerIslandNoiseOffset);     
    }
    
    @Override
    public void provideSurface(ChunkRegion region, Chunk chunk, OldBiomeSource biomeSource) {
        double eighth = 0.03125D;

        ChunkPos chunkPos = chunk.getPos();
        int chunkX = chunkPos.x;
        int chunkZ = chunkPos.z;
        
        int bedrockFloor = this.worldMinY + this.bedrockFloor;
        
        Random rand = this.createSurfaceRandom(chunkX, chunkZ);
        BlockPos.Mutable pos = new BlockPos.Mutable();
        
        double[] sandNoise = this.createSurfaceArray();
        double[] gravelNoise = this.createSurfaceArray();
        double[] surfaceNoise = this.createSurfaceArray();

        sandNoise = beachNoiseOctaves.sampleArrShelf(
            sandNoise, 
            chunkX * 16, chunkZ * 16, 0.0D, 
            16, 16, 1,
            eighth, eighth, 1.0D);
        
        gravelNoise = beachNoiseOctaves.sampleArrShelf(
            gravelNoise, 
            chunkX * 16, 109.0134D, chunkZ * 16, 
            16, 1, 16, 
            eighth, 1.0D, eighth);
        
        surfaceNoise = surfaceNoiseOctaves.sampleArrShelf(
            surfaceNoise, 
            chunkX * 16, chunkZ * 16, 0.0D, 
            16, 16, 1,
            eighth * 2D, eighth * 2D, eighth * 2D
        );
        
        AquiferSampler aquiferSampler = this.getAquiferSampler(chunk);
        HeightmapChunk heightmapChunk = this.getHeightmapChunk(chunkX, chunkZ);

        // Surface builder stuff
        BlockColumnHolder blockColumn = new BlockColumnHolder(chunk);
        HeightContext context = new HeightContext(this.chunkGenerator, region);
        
        BiomeAccess biomeAccess = region.getBiomeAccess();
        Registry<Biome> biomeRegistry = region.getRegistryManager().get(Registry.BIOME_KEY);
        
        MaterialRules.MaterialRuleContext ruleContext = new MaterialRules.MaterialRuleContext(this.surfaceBuilder, chunk, biomeAccess::getBiome, biomeRegistry, context);
        MaterialRules.BlockStateRule blockStateRule = this.surfaceRule.apply(ruleContext);

        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int x = (chunkX << 4) + localX;
                int z = (chunkZ << 4) + localZ;
                int surfaceTopY = GenUtil.getLowestSolidHeight(chunk, this.worldHeight, this.worldMinY, localX, localZ, this.defaultFluid) + 1;
                int surfaceMinY = (this.generateNoiseCaves || this.generateNoodleCaves) ? 
                    heightmapChunk.getHeight(x, z, HeightmapChunk.Type.SURFACE_FLOOR) - 8 : 
                    this.worldMinY;
                
                boolean genSandBeach = sandNoise[localZ + localX * 16] + rand.nextDouble() * 0.20000000000000001D > 0.0D;
                boolean genGravelBeach = gravelNoise[localZ + localX * 16] + rand.nextDouble() * 0.20000000000000001D > 3D;
                int surfaceDepth = (int) (surfaceNoise[localZ + localX * 16] / 3D + 3D + rand.nextDouble() * 0.25D);
                
                int runDepth = -1;
                
                Biome biome = biomeSource.getBiomeForSurfaceGen(region, pos.set(x, surfaceTopY, z));

                BiomeBlocks biomeBlocks = BiomeBlocks.getBiomeBlocks(biome);
                BlockState biomeTopBlock = biomeBlocks.topBlock();
                BlockState biomeFillerBlock = biomeBlocks.fillerBlock();

                BlockState topBlock = biomeTopBlock;
                BlockState fillerBlock = biomeFillerBlock;

                boolean usedCustomSurface = this.surfaceBuilder.buildSurfaceColumn(
                    region.getRegistryManager().get(Registry.BIOME_KEY),
                    region.getBiomeAccess(), 
                    blockColumn, 
                    chunk, 
                    biome, 
                    ruleContext, 
                    blockStateRule,
                    localX,
                    localZ,
                    surfaceTopY
                );
                
                // Generate from top to bottom of world
                for (int y = this.worldTopY - 1; y >= this.worldMinY; y--) {
                    pos.set(localX, y, localZ);

                    // Randomly place bedrock from y=0 (or minHeight) to y=5
                    if (y <= bedrockFloor + rand.nextInt(5)) {
                        chunk.setBlockState(pos, BlockStates.BEDROCK, false);
                        continue;
                    }
                    
                    // Skip if at surface min y
                    if (y < surfaceMinY) {
                        continue;
                    }
                    
                    // Skip if used custom surface generation.
                    if (usedCustomSurface) {
                        continue;
                    }

                    BlockState blockState = chunk.getBlockState(pos);

                    if (blockState.isAir()) { // Skip if air block
                        runDepth = -1;
                        continue;
                    }

                    if (!blockState.isOf(this.defaultBlock.getBlock())) { // Skip if not stone
                        continue;
                    }
                    
                    // At the first default block
                    if (runDepth == -1) {
                        if (surfaceDepth <= 0) { // Generate stone basin if noise permits
                            topBlock = BlockStates.AIR;
                            fillerBlock = this.defaultBlock;
                            
                        } else if (y >= this.seaLevel - 4 && y <= this.seaLevel + 1) { // Generate beaches at this y range
                            topBlock = biomeTopBlock;
                            fillerBlock = biomeFillerBlock;

                            if (genGravelBeach) {
                                topBlock = BlockStates.AIR; // This reduces gravel beach height by 1
                                fillerBlock = BlockStates.GRAVEL;
                            }

                            if (genSandBeach) {
                                topBlock = BlockStates.SAND;
                                fillerBlock = BlockStates.SAND;
                            }
                        }

                        runDepth = surfaceDepth;
                        
                        if (y < this.seaLevel && topBlock.isAir()) { // Generate water bodies
                            BlockState fluidBlock = aquiferSampler.apply(x, y, z, 0.0, 0.0);
                            
                            boolean isAir = fluidBlock == null;
                            topBlock = isAir ? BlockStates.AIR : fluidBlock;
                            
                            this.scheduleFluidTick(chunk, aquiferSampler, pos, topBlock);
                        }
                        
                        blockState = (y >= this.seaLevel - 1 || (y < this.seaLevel - 1 && chunk.getBlockState(pos.up()).isAir())) ? 
                            topBlock : 
                            fillerBlock;
                        
                        chunk.setBlockState(pos, blockState, false);

                        continue;
                    }

                    if (runDepth <= 0) {
                        continue;
                    }

                    runDepth--;
                    chunk.setBlockState(pos, fillerBlock, false);

                    // Generates layer of sandstone starting at lowest block of sand, of height 1 to 4.
                    if (runDepth == 0 && fillerBlock.isOf(Blocks.SAND)) {
                        runDepth = rand.nextInt(4);
                        fillerBlock = BlockStates.SANDSTONE;
                    }
                }
            }
        }
    }

    @Override
    protected void sampleNoiseColumn(double[] primaryBuffer, double[] heightmapBuffer, int startNoiseX, int startNoiseZ, int localNoiseX, int localNoiseZ) {
        int horizNoiseResolution = 16 / (this.noiseSizeX + 1);
        int x = (startNoiseX / this.noiseSizeX * 16) + localNoiseX * horizNoiseResolution + horizNoiseResolution / 2;
        int z = (startNoiseZ / this.noiseSizeZ * 16) + localNoiseZ * horizNoiseResolution + horizNoiseResolution / 2;
        
        int noiseX = startNoiseX + localNoiseX;
        int noiseZ = startNoiseZ + localNoiseZ;
        
        int chunkX = startNoiseX / this.noiseSizeX;
        int chunkZ = startNoiseZ / this.noiseSizeZ;
        
        double depthNoiseScaleX = 200D;
        double depthNoiseScaleZ = 200D;
        
        double baseSize = 8.5D;
        double heightStretch = 12D;
        
        double densityScale = this.noiseSampler.getDensityScale();
        double tunnelThreshold = 200.0 / densityScale;
        
        Clime clime = this.climateSampler.sampleClime(x, z);
        double temp = clime.temp();
        double rain = clime.rain() * temp;
        
        rain = 1.0D - rain;
        rain *= rain;
        rain *= rain;
        rain = 1.0D - rain;
    
        double scale = this.scaleNoiseOctaves.sample(noiseX, noiseZ, 1.121D, 1.121D);
        scale = (scale + 256D) / 512D;
        scale *= rain;
        
        if (scale > 1.0D) {
            scale = 1.0D;
        }
        
        double depth = this.depthNoiseOctaves.sample(noiseX, noiseZ, depthNoiseScaleX, depthNoiseScaleZ);
        depth /= 8000D;
    
        if (depth < 0.0D) {
            depth = -depth * 0.3D;
        }
    
        depth = depth * 3D - 2D;
    
        if (depth < 0.0D) {
            depth /= 2D;
    
            if (depth < -1D) {
                depth = -1D;
            }
    
            depth /= 1.4D;
            depth /= 2D;
    
            scale = 0.0D;
    
        } else {
            if (depth > 1.0D) {
                depth = 1.0D;
            }
            depth /= 8D;
        }
    
        if (scale < 0.0D) {
            scale = 0.0D;
        }
    
        scale += 0.5D;
        depth = depth * baseSize / 8D;
        depth = baseSize + depth * 4D;
        
        double islandOffset = this.generateIslandOffset(
            startNoiseX, 
            startNoiseZ, 
            localNoiseX, 
            localNoiseZ
        ) / densityScale;
        
        for (int y = 0; y < primaryBuffer.length; ++y) {
            int noiseY = y + this.noiseMinY;
            
            double density;
            double heightmapDensity;
           
            density = this.noiseSampler.sample(chunkX, chunkZ, localNoiseX, y, localNoiseZ);
            density -= this.getOffset(noiseY, heightStretch, depth, scale) / densityScale;
            
            // Add island offset
            density += islandOffset;
            
            // Sample without noise caves
            heightmapDensity = density;
            
            // Sample for noise caves
            density = this.sampleNoiseCave(density, tunnelThreshold, noiseX, noiseY, noiseZ);
            
            // Apply slides
            density = this.applySlides(density, y);
            heightmapDensity = this.applySlides(heightmapDensity, y);
            
            primaryBuffer[y] = MathHelper.clamp(density, -64.0, 64.0);
            heightmapBuffer[y] = MathHelper.clamp(heightmapDensity, -64.0, 64.0);
        }
    }

    private double generateIslandOffset(int startNoiseX, int startNoiseZ, int curNoiseX, int curNoiseZ) {
        float noiseX = curNoiseX + startNoiseX;
        float noiseZ = curNoiseZ + startNoiseZ;
        
        float oceanDepth = 200.0F;
        
        int centerOceanLerpDistance = this.centerOceanLerpDistance * this.noiseSizeX;
        int centerOceanRadius = this.centerOceanRadius * this.noiseSizeX;
        
        float centerIslandRadius = this.centerIslandRadius * this.noiseSizeX;
        
        float outerIslandNoiseScale = this.outerIslandNoiseScale;
        float outerIslandNoiseOffset = this.outerIslandNoiseOffset;
        
        float dist = noiseX * noiseX + noiseZ * noiseZ;
        float radius = MathHelper.sqrt(dist);
        
        float islandOffset = centerIslandRadius - radius; 
        if (islandOffset < 0.0) 
            islandOffset *= this.centerIslandFalloff; // Increase the rate of falloff past the island radius
        
        islandOffset = MathHelper.clamp(islandOffset, -oceanDepth, 0.0F);
            
        if (this.generateOuterIslands && radius > centerOceanRadius) {
            float islandAddition = (float)this.islandNoise.sample(noiseX / outerIslandNoiseScale, noiseZ / outerIslandNoiseScale, 1.0, 1.0) + outerIslandNoiseOffset;
            
            // 0.885539 = Simplex upper range, but scale a little higher to ensure island centers have untouched terrain.
            islandAddition /= 0.8F;
            islandAddition = MathHelper.clamp(islandAddition, 0.0F, 1.0F);
            
            // Interpolate noise addition so there isn't a sharp cutoff at start of ocean ring edge.
            islandAddition = (float)MathHelper.clampedLerp(0.0F, islandAddition, (radius - centerOceanRadius) / centerOceanLerpDistance);
            
            islandOffset += islandAddition * oceanDepth;
            islandOffset = MathHelper.clamp(islandOffset, -oceanDepth, 0.0F);
        }
        
        return islandOffset;
    }
    
    private double getOffset(int noiseY, double heightStretch, double depth, double scale) {
        double offset = (((double)noiseY - depth) * heightStretch) / scale;
        
        if (offset < 0D)
            offset *= 4D;
        
        return offset;
    }
}
