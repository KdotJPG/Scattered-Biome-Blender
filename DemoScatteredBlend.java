import java.util.List;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.io.*;
import javax.swing.*;

public class DemoScatteredBlend {
    private static final int WIDTH = 768;
    private static final int HEIGHT = 768;
    private static final int CHUNK_WIDTH = 64;
    
    private static final int BLEND_RADIUS_PADDING = 24;
    private static final double POINT_FREQUENCY = 0.04;
    private static final double BIOME_NOISE_FREQUENCY = 0.002;
    
    private static final int JITTER_SEED = 1234;
    private static final int NOISE_SEED = 2346864; //-271070861
    private static final int BIOME_NOISE_SEED = 23627243;
    
	private static final boolean ONLY_RENDER_WEIGHT_BORDERS = false;
	private static final boolean GENERATE_ACTUAL_TERRAIN = false;
	
	private static final Color[] BIOME_COLORS = {
        new Color(8, 112, 32), new Color(133, 161, 90), new Color(104, 112, 112), new Color(242, 232, 52)
	};
    
    private interface NoiseGenerator {
        double getNoise(int x, int z);
    }
    
    private static final OpenSimplex2S[] TERRAIN_NOISES = new OpenSimplex2S[10];
    static {
        for (int i = 0; i < TERRAIN_NOISES.length; i++) {
            TERRAIN_NOISES[i] = new OpenSimplex2S(BIOME_NOISE_SEED + i);
        }
    }
    
    private static NoiseGenerator[] BIOME_NOISE_GENERATORS = {
        // Forest
        new NoiseGenerator() {
            public double getNoise(int x, int z) {
                double value = TERRAIN_NOISES[0].noise2(x * 0.01, z * 0.01);
                value += TERRAIN_NOISES[1].noise2(x * 0.02, z * 0.02) * 0.5;
                value *= (2./3.) * 20;
                value += 49;
                return value;
            }
        },
        //Plains
        new NoiseGenerator() {
            public double getNoise(int x, int z) {
                double value = TERRAIN_NOISES[2].noise2(x * 0.015, z * 0.015);
                value += TERRAIN_NOISES[3].noise2(x * 0.03, z * 0.03) * 0.5;
                value *= (2./3.) * 20;
                value += 35;
                return value;
            }
        },
        //Mountains
        new NoiseGenerator() {
            public double getNoise(int x, int z) {
                double value = 1 - Math.abs(TERRAIN_NOISES[4].noise2(x * 0.005, z * 0.005));
                value += (1 - Math.abs(TERRAIN_NOISES[5].noise2(x * 0.01, z * 0.01))) * 0.5;
                value += (1 - Math.abs(TERRAIN_NOISES[6].noise2(x * 0.02, z * 0.02))) * 0.25;
                value += (1 - Math.abs(TERRAIN_NOISES[7].noise2(x * 0.04, z * 0.04))) * 0.0625;
                value *= 122.71428571428571;
                return value;
            }
        },
        //Desert
        new NoiseGenerator() {
            public double getNoise(int x, int z) {
                double value = 1 - Math.abs(TERRAIN_NOISES[8].noise2(x * 0.015, z * 0.015));
                value *= (TERRAIN_NOISES[9].noise2(x * 0.015, z * 0.015) * 0.5 + 0.5);
                value *= 12.571428571428571;
                return value;
            }
        }
    };
    
	private static int N_OCTAVES = 2;
    private static final OpenSimplex2S[] NOISES = new OpenSimplex2S[BIOME_COLORS.length * N_OCTAVES];
    static {
        for (int i = 0; i < NOISES.length; i++) {
            NOISES[i] = new OpenSimplex2S(NOISE_SEED + i);
        }
    }

    public static void main(String[] args)
            throws IOException {

        ScatteredBiomeBlender blender = new ScatteredBiomeBlender(POINT_FREQUENCY, BLEND_RADIUS_PADDING, CHUNK_WIDTH);

        // Image
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int zc = 0; zc < HEIGHT; zc += CHUNK_WIDTH) {
            for (int xc = 0; xc < WIDTH; xc += CHUNK_WIDTH) {
                
                LinkedBiomeWeightMap firstBiomeWeightMap = blender.getBlendForChunk(JITTER_SEED, xc, zc, DemoScatteredBlend::getBiomeAt);
                
                for (int zi = 0; zi < CHUNK_WIDTH; zi++) {
                    for (int xi = 0; xi < CHUNK_WIDTH; xi++) {
                        int z = zc + zi;
                        int x = xc + xi;
                        
                        double r, g, b; r = g = b = 0;
						
                        if (GENERATE_ACTUAL_TERRAIN) {
                            double height = 0;
                            for (LinkedBiomeWeightMap entry = firstBiomeWeightMap; entry != null; entry = entry.getNext()) {
                                double weight = entry.getWeights() == null ? 1 : entry.getWeights()[zi * CHUNK_WIDTH + xi];
                                int biome = entry.getBiome();
                                double thisHeight = BIOME_NOISE_GENERATORS[biome].getNoise(x, z);
                                height += thisHeight * weight;
                            }
                            r = g = b = (int)height;
                        } else if (ONLY_RENDER_WEIGHT_BORDERS) {
                            double maxWeight = Double.NEGATIVE_INFINITY;
                            for (LinkedBiomeWeightMap entry = firstBiomeWeightMap; entry != null; entry = entry.getNext()) {
                                double weight = entry.getWeights() == null ? 1 : entry.getWeights()[zi * CHUNK_WIDTH + xi];
                                if (weight > maxWeight) {
                                    maxWeight = weight;
                                    int biome = entry.getBiome();
                                    Color color = BIOME_COLORS[biome];
                                    r = color.getRed();
                                    g = color.getGreen();
                                    b = color.getBlue();
                                }
                            }
                        } else {
                            for (LinkedBiomeWeightMap entry = firstBiomeWeightMap; entry != null; entry = entry.getNext()) {
                                double weight = entry.getWeights() == null ? 1 : entry.getWeights()[zi * CHUNK_WIDTH + xi];
                                int biome = entry.getBiome();
                                r += (BIOME_COLORS[biome].getRed() + 0.5) * weight;
                                g += (BIOME_COLORS[biome].getGreen() + 0.5) * weight;
                                b += (BIOME_COLORS[biome].getBlue() + 0.5) * weight;
                            }
                        }
                        
                        // Render chunk borders
                        //if (xi == CHUNK_WIDTH - 1 || zi == CHUNK_WIDTH - 1) r = g = b = 191;
                        
						int rgb = new Color((int)r, (int)g, (int)b).getRGB();
                        image.setRGB(x, z, rgb);
                    }
                }
            }
        }

        // Save it or show it
        if (args.length > 0 && args[0] != null) {
            ImageIO.write(image, "png", new File(args[0]));
            System.out.println("Saved image as " + args[0]);
        } else {
            JFrame frame = new JFrame();
            JLabel imageLabel = new JLabel();
            imageLabel.setIcon(new ImageIcon(image));
            frame.add(imageLabel);
            frame.pack();
            frame.setResizable(false);
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.setVisible(true);
        }

    }
    
    // Just pick N biomes based on the greatest value out of N noises
    private static int getBiomeAt(double x, double z) {
        double maxValue = Double.NEGATIVE_INFINITY;
        int biome = 0;
        for (int i = 0; i < BIOME_COLORS.length; i++) {
			double noiseValue = 0;
			double freq = BIOME_NOISE_FREQUENCY;
			double amp = 1;
			for (int j = 0; j < N_OCTAVES; j++) {
				noiseValue += NOISES[i + j * BIOME_COLORS.length].noise2(x * freq, z * freq) * amp;
				freq *= 2;
				amp *= 0.5;
			}
            if (noiseValue > maxValue) {
                maxValue = noiseValue;
                biome = i;
            }
        }
        return biome;
    }
}