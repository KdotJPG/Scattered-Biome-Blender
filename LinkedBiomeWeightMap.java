public class LinkedBiomeWeightMap {
    private int biome;
    private double[] weights;
    private LinkedBiomeWeightMap next;
    
    public LinkedBiomeWeightMap(int biome, int chunkColumnCount, LinkedBiomeWeightMap next) {
        this.biome = biome;
        this.weights = new double[chunkColumnCount];
        this.next = next;
    }
    
    public int getBiome() {
        return biome;
    }
    
    public double[] getWeights() {
        return weights;
    }
    
    public LinkedBiomeWeightMap getNext() {
        return next;
    }
}