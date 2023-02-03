package mod.bespectacled.modernbeta.world.chunk.provider;

import java.util.Random;

import mod.bespectacled.modernbeta.api.world.biome.climate.ClimateSampler;
import mod.bespectacled.modernbeta.api.world.biome.climate.Clime;
import mod.bespectacled.modernbeta.api.world.chunk.NoiseChunkProvider;
import mod.bespectacled.modernbeta.api.world.chunk.SurfaceConfig;
import mod.bespectacled.modernbeta.settings.ModernBetaBiomeSettings;
import mod.bespectacled.modernbeta.util.BlockStates;
import mod.bespectacled.modernbeta.util.chunk.HeightmapChunk;
import mod.bespectacled.modernbeta.util.mersenne.MTRandom;
import mod.bespectacled.modernbeta.util.noise.PerlinOctaveNoise;
import mod.bespectacled.modernbeta.util.noise.SimpleNoisePos;
import mod.bespectacled.modernbeta.world.biome.ModernBetaBiomeSource;
import mod.bespectacled.modernbeta.world.biome.provider.PEBiomeProvider;
import mod.bespectacled.modernbeta.world.chunk.ModernBetaChunkGenerator;
import mod.bespectacled.modernbeta.world.spawn.PESpawnLocator;
import net.minecraft.block.BlockState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.AquiferSampler;
import net.minecraft.world.gen.noise.NoiseConfig;

public class PEChunkProvider extends NoiseChunkProvider {
    private PerlinOctaveNoise minLimitOctaveNoise;
    private PerlinOctaveNoise maxLimitOctaveNoise;
    private PerlinOctaveNoise mainOctaveNoise;
    private PerlinOctaveNoise beachOctaveNoise;
    private PerlinOctaveNoise surfaceOctaveNoise;
    private PerlinOctaveNoise scaleOctaveNoise;
    private PerlinOctaveNoise depthOctaveNoise;
    private PerlinOctaveNoise forestOctaveNoise;
    
    private ClimateSampler climateSampler;
    
    public PEChunkProvider(ModernBetaChunkGenerator chunkGenerator) {
        super(chunkGenerator);
    }
    
    @Override
    public boolean initProvider(long seed) {
        // Use Mersenne Twister random instead of Java random
        MTRandom mtRand = new MTRandom(chunkGenerator.getWorldSeed());
        
        // Noise Generators
        this.minLimitOctaveNoise = new PerlinOctaveNoise(mtRand, 16, true);
        this.maxLimitOctaveNoise = new PerlinOctaveNoise(mtRand, 16, true);
        this.mainOctaveNoise = new PerlinOctaveNoise(mtRand, 8, true);
        this.beachOctaveNoise = new PerlinOctaveNoise(mtRand, 4, true);
        this.surfaceOctaveNoise = new PerlinOctaveNoise(mtRand, 4, true);
        this.scaleOctaveNoise = new PerlinOctaveNoise(mtRand, 10, true);
        this.depthOctaveNoise = new PerlinOctaveNoise(mtRand, 16, true);
        this.forestOctaveNoise = new PerlinOctaveNoise(mtRand, 8, true);
        
        this.setForestOctaveNoise(this.forestOctaveNoise);
        
        PEBiomeProvider biomeProvider = (
                this.chunkGenerator.getBiomeSource() instanceof ModernBetaBiomeSource biomeSource &&
                biomeSource.getBiomeProvider() instanceof PEBiomeProvider peBiomeProvider
            ) ? peBiomeProvider : new PEBiomeProvider(new ModernBetaBiomeSettings().toCompound(), null);
        
        biomeProvider.initProvider(this.chunkGenerator.getWorldSeed());
        
        this.climateSampler = biomeProvider;
        this.spawnLocator = new PESpawnLocator(this, this.beachOctaveNoise);
        
        return true;
    }
    
    @Override
    public void provideSurface(ChunkRegion region, Chunk chunk, ModernBetaBiomeSource biomeSource, NoiseConfig noiseConfig) {
        double scale = 0.03125D;

        ChunkPos chunkPos = chunk.getPos();
        int chunkX = chunkPos.x;
        int chunkZ = chunkPos.z;
        
        int startX = chunk.getPos().getStartX();
        int startZ = chunk.getPos().getStartZ();
        
        int bedrockFloor = this.worldMinY + this.bedrockFloor;
        
        Random rand = this.createSurfaceRandom(chunkX, chunkZ);
        BlockPos.Mutable pos = new BlockPos.Mutable();

        AquiferSampler aquiferSampler = this.getAquiferSampler(chunk, noiseConfig);
        HeightmapChunk heightmapChunk = this.getHeightmapChunk(chunkX, chunkZ);
        SimpleNoisePos noisePos = new SimpleNoisePos();
        
        double[] sandNoise = beachOctaveNoise.sampleBeta(
            chunkX * 16, chunkZ * 16, 0.0D, 
            16, 16, 1,
            scale, scale, 1.0D);
        
        double[] gravelNoise = beachOctaveNoise.sampleBeta(
            chunkX * 16, 109.0134D, chunkZ * 16, 
            16, 1, 16, 
            scale, 1.0D, scale);
        
        double[] surfaceNoise = surfaceOctaveNoise.sampleBeta(
            chunkX * 16, chunkZ * 16, 0.0D, 
            16, 16, 1,
            scale * 2D, scale * 2D, scale * 2D
        );

        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int x = startX + localX;
                int z = startZ + localZ;
                int surfaceTopY = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG).get(localX, localZ);
                int surfaceMinY = (this.hasNoisePostProcessor()) ? 
                    heightmapChunk.getHeight(x, z, HeightmapChunk.Type.SURFACE_FLOOR) - 8 : 
                    this.worldMinY;
                
                // MCPE uses nextFloat() instead of nextDouble()
                boolean genSandBeach = sandNoise[localZ + localX * 16] + rand.nextFloat() * 0.2D > 0.0D;
                boolean genGravelBeach = gravelNoise[localZ + localX * 16] + rand.nextFloat() * 0.2D > 3D;
                int surfaceDepth = (int) (surfaceNoise[localZ + localX * 16] / 3D + 3D + rand.nextFloat() * 0.25D);
                
                int runDepth = -1;
                
                RegistryEntry<Biome> biome = biomeSource.getBiomeForSurfaceGen(region, pos.set(x, surfaceTopY, z));

                SurfaceConfig surfaceConfig = SurfaceConfig.getSurfaceConfig(biome);
                BlockState topBlock = surfaceConfig.topBlock();
                BlockState fillerBlock = surfaceConfig.fillerBlock();
                
                // Generate from top to bottom of world
                for (int y = this.worldTopY - 1; y >= this.worldMinY; y--) {
                    BlockState blockState;
                    
                    pos.set(localX, y, localZ);
                    blockState = chunk.getBlockState(pos);
                    
                    // Place bedrock
                    if (y <= bedrockFloor + rand.nextInt(5)) {
                        chunk.setBlockState(pos, BlockStates.BEDROCK, false);
                        continue;
                    }
                    
                    // Skip if at surface min y
                    if (y < surfaceMinY) {
                        continue;
                    }

                    if (blockState.isAir()) { // Skip if air block
                        runDepth = -1;
                        continue;
                    }

                    if (!blockState.isOf(this.defaultBlock.getBlock())) { // Skip if not stone
                        continue;
                    }

                    if (runDepth == -1) {
                        if (surfaceDepth <= 0) { // Generate stone basin if noise permits
                            topBlock = BlockStates.AIR;
                            fillerBlock = this.defaultBlock;
                            
                        } else if (y >= this.seaLevel - 4 && y <= this.seaLevel + 1) { // Generate beaches at this y range
                            topBlock = surfaceConfig.topBlock();
                            fillerBlock = surfaceConfig.fillerBlock();

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
                            BlockState fluidBlock = aquiferSampler.apply(noisePos.setBlockCoords(x, y, z), 0.0);
                            
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
                    if (runDepth == 0 && fillerBlock.isOf(BlockStates.SAND.getBlock())) {
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
        
        double depthNoiseScaleX = this.chunkSettings.depthNoiseScaleX;
        double depthNoiseScaleZ = this.chunkSettings.depthNoiseScaleZ;
        
        double coordinateScale = this.chunkSettings.coordinateScale;
        double heightScale = this.chunkSettings.heightScale;
        
        double mainNoiseScaleX = this.chunkSettings.mainNoiseScaleX;
        double mainNoiseScaleY = this.chunkSettings.mainNoiseScaleY;
        double mainNoiseScaleZ = this.chunkSettings.mainNoiseScaleZ;

        double lowerLimitScale = this.chunkSettings.lowerLimitScale;
        double upperLimitScale = this.chunkSettings.upperLimitScale;
        
        double baseSize = this.chunkSettings.baseSize;
        double heightStretch = this.chunkSettings.stretchY;

        double islandOffset = this.getIslandOffset(startNoiseX, startNoiseZ, localNoiseX, localNoiseZ);
        
        Clime clime = this.climateSampler.sample(x, z);
        double temp = clime.temp();
        double rain = clime.rain() * temp;
        
        rain = 1.0D - rain;
        rain *= rain;
        rain *= rain;
        rain = 1.0D - rain;

        double scale = this.scaleOctaveNoise.sampleXZ(noiseX, noiseZ, 1.121D, 1.121D);
        scale = (scale + 256D) / 512D;
        scale *= rain;
        
        if (scale > 1.0D) {
            scale = 1.0D;
        }
        
        double depth = this.depthOctaveNoise.sampleXZ(noiseX, noiseZ, depthNoiseScaleX, depthNoiseScaleZ);
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
        
        for (int y = 0; y < primaryBuffer.length; ++y) {
            int noiseY = y + this.noiseMinY;
            
            double density;
            double heightmapDensity;
            
            double densityOffset = this.getOffset(noiseY, heightStretch, depth, scale);
            
            double mainNoise = (this.mainOctaveNoise.sample(
                noiseX, noiseY, noiseZ,
                coordinateScale / mainNoiseScaleX, 
                heightScale / mainNoiseScaleY, 
                coordinateScale / mainNoiseScaleZ
            ) / 10D + 1.0D) / 2D;
            
            if (mainNoise < 0.0D) {
                density = this.minLimitOctaveNoise.sample(
                    noiseX, noiseY, noiseZ,
                    coordinateScale, 
                    heightScale, 
                    coordinateScale
                ) / lowerLimitScale;
                
            } else if (mainNoise > 1.0D) {
                density = this.maxLimitOctaveNoise.sample(
                    noiseX, noiseY, noiseZ,
                    coordinateScale, 
                    heightScale, 
                    coordinateScale
                ) / upperLimitScale;
                
            } else {
                double minLimitNoise = this.minLimitOctaveNoise.sample(
                    noiseX, noiseY, noiseZ,
                    coordinateScale, 
                    heightScale, 
                    coordinateScale
                ) / lowerLimitScale;
                
                double maxLimitNoise = this.maxLimitOctaveNoise.sample(
                    noiseX, noiseY, noiseZ,
                    coordinateScale, 
                    heightScale, 
                    coordinateScale
                ) / upperLimitScale;
                
                density = minLimitNoise + (maxLimitNoise - minLimitNoise) * mainNoise;
            }
            
            density -= densityOffset;
            density += islandOffset;
            
            // Sample without noise caves
            heightmapDensity = density;
            
            // Sample for noise caves
            density = this.sampleNoisePostProcessor(density, noiseX, noiseY, noiseZ);
            
            // Apply slides
            density = this.applySlides(density, y);
            heightmapDensity = this.applySlides(heightmapDensity, y);
            
            primaryBuffer[y] = density;
            heightmapBuffer[y] = heightmapDensity;
        }
    }
    
    /*
     * MCPE uses different values to seed random surface generation.
     */
    @Override
    protected Random createSurfaceRandom(int chunkX, int chunkZ) {
        long seed = (long)chunkX * 0x14609048 + (long)chunkZ * 0x7ebe2d5;
        
        return new MTRandom(seed);
    }
    
    private double getOffset(int noiseY, double heightStretch, double depth, double scale) {
        double offset = (((double)noiseY - depth) * heightStretch) / scale;
        
        if (offset < 0D)
            offset *= 4D;
        
        return offset;
    }
} 