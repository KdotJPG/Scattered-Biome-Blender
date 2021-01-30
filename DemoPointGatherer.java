import java.util.List;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import javax.swing.*;

public class DemoPointGatherer {
    private static final int WIDTH = 1024;
    private static final int HEIGHT = 1024;
    private static final int CHUNK_WIDTH = 64;
    
    private static final double POINT_RADIUS = 2;
    private static final double POINT_FREQUENCY = 0.02;
    
    private static final int SEED = 1234;

    public static void main(String[] args)
            throws IOException {

        ChunkPointGatherer<Object> gatherer = new ChunkPointGatherer<Object>(POINT_FREQUENCY, POINT_RADIUS, CHUNK_WIDTH);

        // Image
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int zc = 0; zc < HEIGHT; zc += CHUNK_WIDTH) {
            for (int xc = 0; xc < WIDTH; xc += CHUNK_WIDTH) {
                
                List<GatheredPoint<Object>> points = gatherer.getPointsFromChunkBase(SEED, xc, zc);
                
                for (int zi = 0; zi < CHUNK_WIDTH; zi++) {
                    for (int xi = 0; xi < CHUNK_WIDTH; xi++) {
                        int z = zc + zi;
                        int x = xc + xi;
                        
                        double value = 1;
                        
                        for (GatheredPoint<Object> point : points) {
                            double distSq = (point.getX() - x) * (point.getX() - x) + (point.getZ() - z) * (point.getZ() - z);
                            if (distSq < POINT_RADIUS*POINT_RADIUS) value /= 2;
                        }
                        
                        value = 1 - value;
                        
                        // Render chunk borders
                        if (xi == CHUNK_WIDTH - 1 || zi == CHUNK_WIDTH - 1) value = 0.25;
                        
                        int rgb = 0x010101 * (int)(value * 255.0);
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
}