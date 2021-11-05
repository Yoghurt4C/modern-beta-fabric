package com.bespectacled.modernbeta.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.chunk.BlockColumn;
import net.minecraft.world.gen.surfacebuilder.SurfaceBuilder;

@Mixin(SurfaceBuilder.class)
public interface MixinSurfaceBuilderAccessor {
    @Accessor
    DoublePerlinNoiseSampler getSurfaceNoise();
    
    @Invoker("method_39104")
    public void invokeFrozenOceanBuilder(int surfaceMinY, Biome biome, BlockColumn column, BlockPos.Mutable mutablePos, int x, int z, int height);
    
    @Invoker("method_39102")
    public void invokeErodedBadlandsBuilder(BlockColumn column, int x, int z, int surfaceMinY, HeightLimitView world);
}
