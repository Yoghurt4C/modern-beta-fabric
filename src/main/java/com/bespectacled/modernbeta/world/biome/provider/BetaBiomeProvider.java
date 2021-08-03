package com.bespectacled.modernbeta.world.biome.provider;

import java.util.List;
import java.util.stream.Collectors;

import com.bespectacled.modernbeta.api.world.biome.BiomeProvider;
import com.bespectacled.modernbeta.api.world.biome.BiomeResolver;
import com.bespectacled.modernbeta.api.world.biome.ClimateSampler;
import com.bespectacled.modernbeta.api.world.biome.ClimateType;
import com.bespectacled.modernbeta.world.biome.OldBiomeSource;
import com.bespectacled.modernbeta.world.biome.beta.climate.BetaClimateMap;
import com.bespectacled.modernbeta.world.biome.beta.climate.BetaClimateSampler;

import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.biome.Biome;

public class BetaBiomeProvider extends BiomeProvider implements BiomeResolver {
    private final ClimateSampler climateSampler;
    private final BetaClimateMap climateMap;
    
    public BetaBiomeProvider(OldBiomeSource biomeSource) {
        super(biomeSource);
        
        this.climateSampler = new BetaClimateSampler(biomeSource.getWorldSeed());
        this.climateMap = new BetaClimateMap(settings);
    }

    @Override
    public Biome getBiomeForNoiseGen(Registry<Biome> biomeRegistry, int biomeX, int biomeY, int biomeZ) {
        int absX = biomeX << 2;
        int absZ = biomeZ << 2;
        
        double temp = this.climateSampler.sampleTemp(absX, absZ);
        double rain = this.climateSampler.sampleRain(absX, absZ);
        
        return biomeRegistry.get(this.climateMap.getBiome(temp, rain, ClimateType.LAND));
    }
 
    @Override
    public Biome getOceanBiomeForNoiseGen(Registry<Biome> biomeRegistry, int biomeX, int biomeY, int biomeZ) {
        int absX = biomeX << 2;
        int absZ = biomeZ << 2;

        double temp = this.climateSampler.sampleTemp(absX, absZ);
        double rain = this.climateSampler.sampleRain(absX, absZ);
        
        return biomeRegistry.get(this.climateMap.getBiome(temp, rain, ClimateType.OCEAN));
    }
    
    @Override
    public Biome getBiome(Registry<Biome> biomeRegistry, int x, int y, int z) {
        double temp = this.climateSampler.sampleTemp(x, z);
        double rain = this.climateSampler.sampleRain(x, z);
        
        return biomeRegistry.get(this.climateMap.getBiome(temp, rain, ClimateType.LAND));
    }

    @Override
    public List<RegistryKey<Biome>> getBiomesForRegistry() {
        return this.climateMap.getBiomeIds().stream().map(i -> RegistryKey.of(Registry.BIOME_KEY, i)).collect(Collectors.toList());
    }

    public ClimateSampler getClimateSampler() {
        return this.climateSampler;
    }
}
