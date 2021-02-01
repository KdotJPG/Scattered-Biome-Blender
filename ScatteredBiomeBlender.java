import java.util.List;

public class ScatteredBiomeBlender {
    
    int chunkWidth;
    int chunkColumnCount;
    double blendKernelRadiusSq;
    ChunkPointGatherer<BiomeEvaluation> gatherer;
    
    public ScatteredBiomeBlender(double samplingFrequency, double minBlendRadius, int chunkWidth) {
        this.chunkWidth = chunkWidth;
        this.chunkColumnCount = chunkWidth * chunkWidth;
        double blendKernelRadius = minBlendRadius
            + UnfilteredPointGatherer.MAX_GRIDSCALE_DISTANCE_TO_CLOSEST_POINT / samplingFrequency;
        this.blendKernelRadiusSq = blendKernelRadius * blendKernelRadius;
        this.gatherer = new ChunkPointGatherer<BiomeEvaluation>(samplingFrequency, blendKernelRadius, chunkWidth);
    }
    
    public LinkedBiomeWeightMap getBlendForChunk(long seed, int chunkBaseWorldX, int chunkBaseWorldZ, BiomeEvaluationCallback callback) {
        List<GatheredPoint<BiomeEvaluation>> points = gatherer.getPointsFromChunkBase(seed, chunkBaseWorldX, chunkBaseWorldZ);
        
        // Evaluate and aggregate all the biomes to be blended in this chunk.
        LinkedBiomeWeightMap linkedBiomeMapStartEntry = null;
        for (GatheredPoint<BiomeEvaluation> point : points) {
            int biome = callback.getBiomeAt(point.getX(), point.getZ());
            point.setTag(new BiomeEvaluation(biome));
            
            // Find or create chunk biome blend weight layer entry for this biome.
            LinkedBiomeWeightMap entry = linkedBiomeMapStartEntry;
            while (entry != null) {
                if (entry.getBiome() == biome) break;
                entry = entry.getNext();
            }
            if (entry == null) {
                linkedBiomeMapStartEntry =
                    new LinkedBiomeWeightMap(point.getTag().biome, chunkColumnCount, linkedBiomeMapStartEntry);
            }
        }
        
        // If there is only one biome type in range here, we can skip the actual blending step.
        if (linkedBiomeMapStartEntry != null && linkedBiomeMapStartEntry.getNext() == null) {
            double[] weights = linkedBiomeMapStartEntry.getWeights();
            for (int i = 0; i < chunkColumnCount; i++) {
                weights[i] = 1.0;
            }
            return linkedBiomeMapStartEntry;
        }
        
        // Along the Z axis of the chunk...
        for (int zi = 0; zi < chunkWidth; zi++) {
            int z = chunkBaseWorldZ + zi;
            
            // Calculate dz^2 for each point since it will be the same across x.
            for (GatheredPoint<BiomeEvaluation> point : points) {
                double dz = point.getZ() - z;
                point.getTag().tempDzSquared = dz * dz;
            }
            
            // Now along the X axis...
            for (int xi = 0; xi < chunkWidth; xi++) {
                int x = chunkBaseWorldX + xi;
                
                // Go over each point to see if it's inside the radius for this column.
                double columnTotalWeight = 0.0;
                for (GatheredPoint<BiomeEvaluation> point : points) {
                    double dx = point.getX() - x;
                    double distSq = dx * dx + point.getTag().tempDzSquared;
                    
                    // If it's inside the radius...
                    if (distSq < blendKernelRadiusSq) {
                        
                        // Relative weight = [r^2 - (x^2 + z^2)]^2
                        double weight = blendKernelRadiusSq - distSq;
                        weight *= weight;
                        
                        // Find or create chunk biome blend weight layer entry for this biome.
                        LinkedBiomeWeightMap entry = linkedBiomeMapStartEntry;
                        while (entry != null) {
                            if (entry.getBiome() == point.getTag().biome) break;
                            entry = entry.getNext();
                        }
                        
                        entry.getWeights()[zi * chunkWidth + xi] += weight;
                        columnTotalWeight += weight;
                    }
                }
                
                // Normalize so all weights for a column to 1.
                double inverseTotalWeight = 1.0 / columnTotalWeight;
                for (LinkedBiomeWeightMap entry = linkedBiomeMapStartEntry; entry != null; entry = entry.getNext()) {
                    entry.getWeights()[zi * chunkWidth + xi] *= inverseTotalWeight;
                }
            }
        }
        
        return linkedBiomeMapStartEntry;
    }
    
    @FunctionalInterface
    public static interface BiomeEvaluationCallback {
        int getBiomeAt(double x, double z);
    }
    
    private static class BiomeEvaluation {
        int biome;
        double tempDzSquared;
        public BiomeEvaluation(int biome) {
            this.biome = biome;
        }
    }
    
}