import java.util.List;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.io.*;
import javax.swing.*;

public class DemoBiomeBlender {
    private static final int WIDTH = 1024;
    private static final int HEIGHT = 1024;
    private static final int CHUNK_WIDTH = 64;
    
    private static final int MIN_BLEND_RADIUS = 32;
    private static final double POINT_FREQUENCY = 0.02;
    private static final double BIOME_NOISE_FREQUENCY = 0.002;
    
    private static final int SEED = 1234;
    private static final int NOISE_SEED = 333333;
	
	private static final Color[] BIOME_COLORS = {
		Color.GREEN, Color.ORANGE.darker(), Color.GRAY
	};
    
    private static final OpenSimplex2S[] noises = new OpenSimplex2S[BIOME_COLORS.length];
    static {
        for (int i = 0; i < BIOME_COLORS.length; i++) {
            noises[i] = new OpenSimplex2S(NOISE_SEED + i);
        }
    }

    public static void main(String[] args)
            throws IOException {

        ScatteredBiomeBlender blender = new ScatteredBiomeBlender(POINT_FREQUENCY, MIN_BLEND_RADIUS, CHUNK_WIDTH, DemoBiomeBlender::getBiomeAt);

        // Image
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int zc = 0; zc < HEIGHT; zc += CHUNK_WIDTH) {
            for (int xc = 0; xc < WIDTH; xc += CHUNK_WIDTH) {
                
                ScatteredBiomeBlender.LinkedBiomeWeightMap firstBiomeWeightMap = blender.getBlendForChunk(SEED, xc, zc);
                
                for (int zi = 0; zi < CHUNK_WIDTH; zi++) {
                    for (int xi = 0; xi < CHUNK_WIDTH; xi++) {
                        int z = zc + zi;
                        int x = xc + xi;
                        
                        double r, g, b; r = g = b = 0;
                        
                        for (ScatteredBiomeBlender.LinkedBiomeWeightMap entry = firstBiomeWeightMap; entry != null; entry = entry.next) {
                            double weight = entry.weights[zi * CHUNK_WIDTH + xi];
							r += BIOME_COLORS[entry.biome].getRed() * weight;
							g += BIOME_COLORS[entry.biome].getGreen() * weight;
							b += BIOME_COLORS[entry.biome].getBlue() * weight;
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
            double noiseValue = noises[i].noise2(x * BIOME_NOISE_FREQUENCY, z * BIOME_NOISE_FREQUENCY);
            if (noiseValue > maxValue) {
                maxValue = noiseValue;
                biome = i;
            }
        }
        return biome;
    }
}