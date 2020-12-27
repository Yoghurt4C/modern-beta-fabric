package com.bespectacled.modernbeta.gen.provider;

import java.util.Random;

import com.bespectacled.modernbeta.biome.OldBiomeSource;
import com.bespectacled.modernbeta.gen.GenUtil;
import com.bespectacled.modernbeta.noise.PerlinOctaveNoise;
import com.bespectacled.modernbeta.util.BlockStates;

import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.structure.JigsawJunction;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.Heightmap;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;

/**
 * 
 * @author Paulevs
 *
 */
public class InfdevChunkProvider extends AbstractChunkProvider {
    
    private final PerlinOctaveNoise noiseOctavesA;
    private final PerlinOctaveNoise noiseOctavesB;
    private final PerlinOctaveNoise noiseOctavesC;
    private final PerlinOctaveNoise beachNoiseOctaves;
    private final PerlinOctaveNoise stoneNoiseOctaves;
    private final PerlinOctaveNoise forestNoiseOctaves;
    
    private static final double HEIGHT_NOISE[][] = new double[33][4];
    
    private static final Random SANDSTONE_RAND = new Random();
    
    private final int verticalNoiseResolution;
    private final int horizontalNoiseResolution;
    
    private final int noiseSizeX;
    private final int noiseSizeZ;
    private final int noiseSizeY;
    
    public InfdevChunkProvider(long seed) {
        super(seed);
        SANDSTONE_RAND.setSeed(seed);
        
        this.verticalNoiseResolution = 1 * 4;
        this.horizontalNoiseResolution = 1 * 4;
        
        this.noiseSizeX = 16 / this.horizontalNoiseResolution;
        this.noiseSizeZ = 16 / this.horizontalNoiseResolution;
        this.noiseSizeY = this.worldHeight / this.verticalNoiseResolution;
        
        // Noise Generators
        noiseOctavesA = new PerlinOctaveNoise(RAND, 16, true);
        noiseOctavesB = new PerlinOctaveNoise(RAND, 16, true);
        noiseOctavesC = new PerlinOctaveNoise(RAND, 8, true);
        beachNoiseOctaves = new PerlinOctaveNoise(RAND, 4, true);
        stoneNoiseOctaves = new PerlinOctaveNoise(RAND, 4, true);

        new PerlinOctaveNoise(RAND, 5, true); // Unused in original source
        
        forestNoiseOctaves = new PerlinOctaveNoise(RAND, 5, true);

        setForestOctaves(forestNoiseOctaves);
    }

    @Override
    public void makeChunk(WorldAccess worldAccess, StructureAccessor structureAccessor, Chunk chunk, OldBiomeSource biomeSource) {
        RAND.setSeed((long) chunk.getPos().x * 341873128712L + (long) chunk.getPos().z * 132897987541L);
        SANDSTONE_RAND.setSeed((long) chunk.getPos().x * 341873128712L + (long) chunk.getPos().z * 132897987541L);

        generateTerrain(chunk, structureAccessor); 
    }

    @Override
    public void makeSurface(ChunkRegion region, Chunk chunk, OldBiomeSource biomeSource) {
        double thirtysecond = 0.03125D; // eighth
        
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                int absX = (chunkX << 4) + x;
                int absZ = (chunkZ << 4) + z;
                
                boolean genSandBeach = this.beachNoiseOctaves.sample(
                    absX * thirtysecond, 
                    absZ * thirtysecond, 
                    0.0) + RAND.nextDouble() * 0.2 > 0.0;
                
                boolean genGravelBeach = this.beachNoiseOctaves.sample(
                    absZ * thirtysecond, 
                    109.0134,
                    absX * thirtysecond) + RAND.nextDouble() * 0.2 > 3.0;
                
                int genStone = (int)(this.stoneNoiseOctaves.sample(
                    absX * thirtysecond * 2.0, 
                    absZ * thirtysecond * 2.0) / 3.0 + 3.0 + RAND.nextDouble() * 0.25);
                
                int flag = -1;
                
                Biome curBiome = region.getBiome(POS.set(absX, 0, absZ));
                
                BlockState biomeTopBlock = curBiome.getGenerationSettings().getSurfaceConfig().getTopMaterial();
                BlockState biomeFillerBlock = curBiome.getGenerationSettings().getSurfaceConfig().getUnderMaterial();
                
                BlockState topBlock = biomeTopBlock;
                BlockState fillerBlock = biomeFillerBlock;
                
                for (int y = this.worldHeight - 1; y >= 0; --y) {
                    
                    // Randomly place bedrock from y=0 to y=5
                    if (y <= 0 + RAND.nextInt(5)) {
                        chunk.setBlockState(POS.set(x, y, z), Blocks.BEDROCK.getDefaultState(), false);
                        continue;
                    }
                    
                    POS.set(x, y, z);
                    Block someBlock = chunk.getBlockState(POS).getBlock();
                    
                    if (someBlock.equals(Blocks.AIR)) {
                        flag = -1;
                        
                    } else if (someBlock.equals(Blocks.STONE)) {
                        if (flag == -1) {
                            if (genStone <= 0) {
                                topBlock = BlockStates.AIR;
                                fillerBlock = BlockStates.STONE;
                            } else if (y >= this.seaLevel - 4 && y <= this.seaLevel + 1) {
                                topBlock = biomeTopBlock;
                                fillerBlock = biomeFillerBlock;
                                
                                if (genGravelBeach) {
                                    topBlock = BlockStates.AIR;
                                    fillerBlock = BlockStates.GRAVEL;
                                }
                                
                                if (genSandBeach) {
                                    topBlock = BlockStates.SAND;
                                    fillerBlock = BlockStates.SAND;
                                }
                            }
                            
                            if (y < this.seaLevel && topBlock.equals(BlockStates.AIR)) {
                                topBlock = BlockStates.WATER;
                            }
                            
                            flag = genStone;
                            
                            if (y >= this.seaLevel - 1) {
                                chunk.setBlockState(POS, topBlock, false);
                            } else {
                                chunk.setBlockState(POS, fillerBlock, false);
                            }
                            
                        } else if (flag > 0) {
                            --flag;
                            chunk.setBlockState(POS, fillerBlock, false);
                            
                            // Gens layer of sandstone starting at lowest block of sand, of height 1 to 4.
                            // Beta backport.
                            if (flag == 0 && fillerBlock.equals(BlockStates.SAND)) {
                                flag = SANDSTONE_RAND.nextInt(4);
                                fillerBlock = BlockStates.SANDSTONE;
                            }
                        }
                    }
                }
            }
        }
        
    }

    @Override
    public int getHeight(int x, int z, Type type) {
        BlockPos structPos = new BlockPos(x, 0, z);
        
        if (GROUND_CACHE_Y.get(structPos) == null) {
            sampleHeightmap(x, z);
        }

        int groundHeight = GROUND_CACHE_Y.get(structPos);

        // Not ideal
        if (type == Heightmap.Type.WORLD_SURFACE_WG && groundHeight < this.seaLevel)
            groundHeight = this.seaLevel;

        return groundHeight;
    }
    
    @Override
    public PerlinOctaveNoise getBeachNoiseOctaves() {
        return this.beachNoiseOctaves;
    }
    
    private void generateTerrain(Chunk chunk, StructureAccessor structureAccessor) {
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        
        Heightmap heightmapOCEAN = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
        Heightmap heightmapSURFACE = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
        
        GenUtil.collectStructures(chunk, structureAccessor, STRUCTURE_LIST, JIGSAW_LIST);
        
        ObjectListIterator<StructurePiece> structureListIterator = (ObjectListIterator<StructurePiece>) STRUCTURE_LIST.iterator();
        ObjectListIterator<JigsawJunction> jigsawListIterator = (ObjectListIterator<JigsawJunction>) JIGSAW_LIST.iterator();
        
        for (int subChunkX = 0; subChunkX < this.noiseSizeX; ++subChunkX) {
            for (int subChunkZ = 0; subChunkZ < this.noiseSizeZ; ++ subChunkZ) {
                int bX = chunkX * this.noiseSizeX + subChunkX;
                int bZ = chunkZ * this.noiseSizeZ + subChunkZ;
                
                for (int bY = 0; bY < HEIGHT_NOISE.length; ++bY) {
                    HEIGHT_NOISE[bY][0] = this.generateHeightmap(bX, bY, bZ);
                    HEIGHT_NOISE[bY][1] = this.generateHeightmap(bX, bY, bZ + 1);
                    HEIGHT_NOISE[bY][2] = this.generateHeightmap(bX + 1, bY, bZ);
                    HEIGHT_NOISE[bY][3] = this.generateHeightmap(bX + 1, bY, bZ + 1);
                }
                
                for (int subChunkY = 0; subChunkY < this.noiseSizeY; ++subChunkY) {
                    double lower0 = HEIGHT_NOISE[subChunkY][0];
                    double lower1 = HEIGHT_NOISE[subChunkY][1];
                    double lower2 = HEIGHT_NOISE[subChunkY][2];
                    double lower3 = HEIGHT_NOISE[subChunkY][3];
                    
                    double upper0 = HEIGHT_NOISE[subChunkY + 1][0];
                    double upper1 = HEIGHT_NOISE[subChunkY + 1][1];
                    double upper2 = HEIGHT_NOISE[subChunkY + 1][2];
                    double upper3 = HEIGHT_NOISE[subChunkY + 1][3];
                    
                    for (int subY = 0; subY < this.verticalNoiseResolution; ++subY) {
                        int y = subY + subChunkY * this.verticalNoiseResolution;
                        
                        double mixY = subY / 4.0;
                        
                        double nx1 = lower0 + (upper0 - lower0) * mixY;
                        double nx2 = lower1 + (upper1 - lower1) * mixY;
                        double nx3 = lower2 + (upper2 - lower2) * mixY;
                        double nx4 = lower3 + (upper3 - lower3) * mixY;
                        
                        for (int subX = 0; subX < this.horizontalNoiseResolution; ++subX) {
                            int x = subX + subChunkX * this.horizontalNoiseResolution;
                            int absX = (chunk.getPos().x << 4) + x;
                            
                            double mixX = subX / 4.0;
                            
                            double nz1 = nx1 + (nx3 - nx1) * mixX;
                            double nz2 = nx2 + (nx4 - nx2) * mixX;
                            
                            for (int subZ = 0; subZ < this.horizontalNoiseResolution; ++subZ) {
                                int z = subZ + subChunkZ * this.horizontalNoiseResolution;
                                int absZ = (chunk.getPos().z << 4) + z;
                                
                                double mixZ = subZ / 4.0;
                                
                                double density = nz1 + (nz2 - nz1) * mixZ;
                                
                                double clampedDensity = MathHelper.clamp(density / 200.0, -1.0, 1.0);
                                clampedDensity = clampedDensity / 2.0 - clampedDensity * clampedDensity * clampedDensity / 24.0;
                                
                                clampedDensity += GenUtil.addStructDensity(
                                    structureListIterator, 
                                    jigsawListIterator, 
                                    STRUCTURE_LIST.size(), 
                                    JIGSAW_LIST.size(), 
                                    absX, y, absZ);
                                
                                BlockState blockToSet = getBlockState(clampedDensity, y, 0);
                                chunk.setBlockState(POS.set(x, y, z), blockToSet, false);
                                
                                heightmapOCEAN.trackUpdate(x, y, z, blockToSet);
                                heightmapSURFACE.trackUpdate(x, y, z, blockToSet);
                            }
                        }
                    }
                }
            }
        }
    }
    
    private double generateHeightmap(double x, double y, double z) {
        // Check if y (in scaled space) is below sealevel
        // and increase density accordingly.
        //double elevGrad = y * 4.0 - 64.0;
        double elevGrad = y * 4.0 - this.seaLevel;
        if (elevGrad < 0.0) {
            elevGrad *= 3.0;
        }
        
        double noise;
        double res;
        
        if ((noise = this.noiseOctavesC.sample(x * 8.55515, y * 1.71103, z * 8.55515) / 2.0) < -1) {
            res = MathHelper.clamp(
                this.noiseOctavesA.sample(x * 684.412, y * 984.412, z * 684.412) / 512.0 - elevGrad, 
                -10.0, 
                10.0
            );
            
        } else if (noise > 1.0) {
            res = MathHelper.clamp(
                this.noiseOctavesB.sample(x * 684.412, y * 984.412, z * 684.412) / 512.0 - elevGrad, 
                -10.0, 
                10.0
            );
            
        } else {
            double noise2 = MathHelper.clamp(
                this.noiseOctavesA.sample(x * 684.412, y * 984.412, z * 684.412) / 512.0 - elevGrad, 
                -10.0, 
                10.0
            );
            
            double noise3 = MathHelper.clamp(
                this.noiseOctavesB.sample(x * 684.412, y * 984.412, z * 684.412) / 512.0 - elevGrad, 
                -10.0, 
                10.0
            );
            
            double mix = (noise + 1.0) / 2.0;
            
            res = noise2 + (noise3 - noise2) * mix;
        }
        
        return res;
    }
    
    private void sampleHeightmap(int absX, int absZ) {
        
        int chunkX = absX >> 4;
        int chunkZ = absZ >> 4;
        
        for (int subChunkX = 0; subChunkX < this.noiseSizeX; ++subChunkX) {
            for (int subChunkZ = 0; subChunkZ < this.noiseSizeZ; ++ subChunkZ) {
                //int bX = (chunkX << 2) + subChunkX;
               // int bZ = (chunkZ << 2) + subChunkZ;
                
                int bX = chunkX * this.noiseSizeX + subChunkX;
                int bZ = chunkZ * this.noiseSizeZ + subChunkZ;
                
                for (int bY = 0; bY < HEIGHT_NOISE.length; ++bY) {
                    HEIGHT_NOISE[bY][0] = this.generateHeightmap(bX, bY, bZ);
                    HEIGHT_NOISE[bY][1] = this.generateHeightmap(bX, bY, bZ + 1);
                    HEIGHT_NOISE[bY][2] = this.generateHeightmap(bX + 1, bY, bZ);
                    HEIGHT_NOISE[bY][3] = this.generateHeightmap(bX + 1, bY, bZ + 1);
                }
                
                for (int subChunkY = 0; subChunkY < this.noiseSizeY; ++subChunkY) {
                    double lower0 = HEIGHT_NOISE[subChunkY][0];
                    double lower1 = HEIGHT_NOISE[subChunkY][1];
                    double lower2 = HEIGHT_NOISE[subChunkY][2];
                    double lower3 = HEIGHT_NOISE[subChunkY][3];
                    
                    double upper0 = HEIGHT_NOISE[subChunkY + 1][0];
                    double upper1 = HEIGHT_NOISE[subChunkY + 1][1];
                    double upper2 = HEIGHT_NOISE[subChunkY + 1][2];
                    double upper3 = HEIGHT_NOISE[subChunkY + 1][3];
                    
                    for (int subY = 0; subY < this.verticalNoiseResolution; ++subY) {
                        int y = subY + subChunkY * this.verticalNoiseResolution;
                        
                        double mixY = subY / 4.0;
                        
                        double nx1 = lower0 + (upper0 - lower0) * mixY;
                        double nx2 = lower1 + (upper1 - lower1) * mixY;
                        double nx3 = lower2 + (upper2 - lower2) * mixY;
                        double nx4 = lower3 + (upper3 - lower3) * mixY;
                        
                        for (int subX = 0; subX < this.horizontalNoiseResolution; ++subX) {
                            int x = subX + subChunkX * this.horizontalNoiseResolution;
                            
                            double mixX = subX / 4.0;
                            
                            double nz1 = nx1 + (nx3 - nx1) * mixX;
                            double nz2 = nx2 + (nx4 - nx2) * mixX;
                            
                            for (int subZ = 0; subZ < this.horizontalNoiseResolution; ++subZ) {
                                int z = subZ + subChunkZ * this.horizontalNoiseResolution;
                                
                                double mixZ = subZ / 4.0;
                                
                                double density = nz1 + (nz2 - nz1) * mixZ;
                                
                                if (density > 0.0) {
                                    CHUNK_Y[x][z] = y;
                                }
                            }
                        }
                    }
                }
            }
        }

        for (int pX = 0; pX < CHUNK_Y.length; pX++) {
            for (int pZ = 0; pZ < CHUNK_Y[pX].length; pZ++) {
                BlockPos structPos = new BlockPos((chunkX << 4) + pX, 0, (chunkZ << 4) + pZ);
                //POS.set((chunkX << 4) + pX, 0, (chunkZ << 4) + pZ);
                
                GROUND_CACHE_Y.put(structPos, CHUNK_Y[pX][pZ] + 1); // +1 because it is one above the ground
            }
        }
    }

}
