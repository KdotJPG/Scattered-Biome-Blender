import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.io.*;
import javax.swing.*;

public class VariousBlendsDemo {
	
	/*
	 * Configure these
	 */
	 
	private static final int WIDTH = 768;
	private static final int HEIGHT = 768;
	
    private static final double BIOME_NOISE_FREQUENCY = 0.002;
    //private static final int NOISE_SEED = -1503754068;
    //private static final int NOISE_SEED = -1719879851;
    //private static final int NOISE_SEED = 53624434;
    //private static final int NOISE_SEED = 2346864;
    private static final int NOISE_SEED = 99999;
    //private static final int JITTER_SEED = 33333333;
    private static final int JITTER_SEED = 1546881382;
	
	private static final int BLEND_RADIUS = 24;
	private static final int CHUNK_WIDTH_EXPONENT = 4;
	private static final int GRID_INTERVAL_EXPONENT = 3;
    
	private static final int MIN_PADDING_FOR_SCATTERED = 4;
	
	private static final boolean RENDER_CHUNK_BORDERS = false;
	private static final boolean ONLY_RENDER_WEIGHT_BORDERS = false;
	private static final int OVERRIDE_BIOME_FOR_POINTRENDER_OR_NEGATIVE = -1;
	private static final double POINT_RENDER_SIZE = 2;
    
	private static int N_NOISE_OCTAVES = 3;
    
    /*
     * Performance
     */
	
	static final int N_PREP_ITERATIONS = 16;
	static final int N_TIMED_ITERATIONS = 128;
    
	/*
	 * Definitions
	 */
	
	private static final int CHUNK_WIDTH = 1 << CHUNK_WIDTH_EXPONENT;
	private static final int GRID_INTERVAL = 1 << GRID_INTERVAL_EXPONENT;
	private static final double POINT_RENDER_SIZE_SQ = POINT_RENDER_SIZE * POINT_RENDER_SIZE;
	
    // Not a star example of OO design, but a quick way to let performance metrics change these.
    private static int noiseSeed = NOISE_SEED;
    private static int jitterSeed = JITTER_SEED;
    
	private interface ChunkHandler {
		void invoke(int xc, int zc, LinkedBiomeWeightMap blendEntryStart);
	}
	
	private static final Color[] BIOME_COLORS = {
        new Color(8, 112, 32), new Color(133, 161, 90), new Color(104, 112, 112), new Color(242, 232, 52)
	};
    
    private static OpenSimplex2S[] noises = new OpenSimplex2S[BIOME_COLORS.length * N_NOISE_OCTAVES];
    static {
        initNoises();
    }
    
    private static void initNoises() {
        for (int i = 0; i < noises.length; i++) {
            noises[i] = new OpenSimplex2S(noiseSeed + i);
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
			for (int j = 0; j < N_NOISE_OCTAVES; j++) {
				noiseValue += noises[i + j * BIOME_COLORS.length].noise2(x * freq, z * freq) * amp;
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
	
	/*
	 * Rendering
	 */
	
	public static void main(String[] args)
			throws IOException {
		String resultType = args.length > 0 ? args[0] : "scattered";
        
        if ("performance".equals(resultType)) {
            runPerformanceMetrics();
            return;
        }
        
		switch (resultType) {
			case "scattered":
			case "simple":
			case "convgrid":
			case "lerpgrid":
			case "raw":
			case "callback":
			case "points":
			case "all":
                break;
            default:
                System.out.println("Invalid argument: " + resultType);
                return;
        }
		
        // Image
        BufferedImage image = new BufferedImage("all".equals(resultType) ? WIDTH*4 : WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
		
		ChunkHandler callback = (int xc, int zc, LinkedBiomeWeightMap blendEntryStart) ->
			applyChunkToImage(image, 0, 0, xc, zc, blendEntryStart);
		
		switch (resultType) {
			default:
			case "scattered":
                System.out.println("Scattered Frequency: " + GRID_EQUIVALENT_FREQUENCY);
                System.out.println("Scattered Padding: " + BLEND_RADIUS_PADDING);
                System.out.println("Scattered Internal Blend Radius: " + (BLEND_RADIUS_PADDING + ScatteredBiomeBlender.getInternalMinBlendRadiusForFrequency(GRID_EQUIVALENT_FREQUENCY)));
				generateBlendScatteredPoint(callback);
				break;
			case "simple":
				generateBlendSimple(callback);
				break;
			case "convgrid":
				generateBlendConvolutedGrid(callback);
				break;
			case "lerpgrid":
				generateBlendLerpedGrid(callback);
				break;
			case "raw":
			case "callback":
				generateCallbackPassthrough(callback);
				break;
			case "points":
				generatePointDistributionPassthrough(callback);
				break;
			case "all":
				generateBlendScatteredPoint((int xc, int zc, LinkedBiomeWeightMap blendEntryStart) ->
					applyChunkToImage(image, 0, 0, xc, zc, blendEntryStart));
				generateBlendSimple((int xc, int zc, LinkedBiomeWeightMap blendEntryStart) ->
					applyChunkToImage(image, WIDTH, 0, xc, zc, blendEntryStart));
				generateBlendConvolutedGrid((int xc, int zc, LinkedBiomeWeightMap blendEntryStart) ->
					applyChunkToImage(image, WIDTH*2, 0, xc, zc, blendEntryStart));
				generateBlendLerpedGrid((int xc, int zc, LinkedBiomeWeightMap blendEntryStart) ->
					applyChunkToImage(image, WIDTH*3, 0, xc, zc, blendEntryStart));
                break;
		};
		
        // Save it or show it
        if (args.length > 1 && args[1] != null) {
            ImageIO.write(image, "png", new File(args[1]));
            System.out.println("Saved image as " + args[1]);
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
	
	private static void applyChunkToImage(BufferedImage image, int xOffset, int zOffset, int xc, int zc, LinkedBiomeWeightMap blendEntryStart) {
		for (int zi = 0; zi < CHUNK_WIDTH; zi++) {
			for (int xi = 0; xi < CHUNK_WIDTH; xi++) {
				int z = zc + zi;
				int x = xc + xi;
				
				double r, g, b; r = g = b = 0;
				
				if (ONLY_RENDER_WEIGHT_BORDERS) {
					double maxWeight = Double.NEGATIVE_INFINITY;
					for (LinkedBiomeWeightMap entry = blendEntryStart; entry != null; entry = entry.getNext()) {
						double weight = entry.getWeights() == null ? 1 : entry.getWeights()[(zi * CHUNK_WIDTH) | xi];
						if (weight > maxWeight) {
							maxWeight = weight;
							int biome = entry.getBiome();
							r = BIOME_COLORS[biome].getRed();
							g = BIOME_COLORS[biome].getGreen();
							b = BIOME_COLORS[biome].getBlue();
						}
					}
				} else {
					for (LinkedBiomeWeightMap entry = blendEntryStart; entry != null; entry = entry.getNext()) {
						double weight = entry.getWeights() == null ? 1 : entry.getWeights()[(zi * CHUNK_WIDTH) | xi];
						int biome = entry.getBiome();
						r += (BIOME_COLORS[biome].getRed() + 0.5) * weight;
						g += (BIOME_COLORS[biome].getGreen() + 0.5) * weight;
						b += (BIOME_COLORS[biome].getBlue() + 0.5) * weight;
					}
				}
				
                if (RENDER_CHUNK_BORDERS) {
                    if (xi == CHUNK_WIDTH - 1 || zi == CHUNK_WIDTH - 1) r = g = b = 191;
                }
				
				int rgb = new Color((int)r, (int)g, (int)b).getRGB();
				image.setRGB(x + xOffset, z + zOffset, rgb);
			}
		}
	}
	
	/*
	 * Performance
	 */
    
    private static void runPerformanceMetrics() {
		List<PerformanceTimer> timers = new ArrayList<>();
        
		timers.add(new PerformanceTimer() {
			{ name = "Lerped Grid"; }
			void execute() {
                generateBlendLerpedGrid(null);
            }
		});
		timers.add(new PerformanceTimer() {
			{ name = "Scattered Blending"; }
			void execute() {
                generateBlendScatteredPoint(null);
            }
		});
		timers.add(new PerformanceTimer() {
			{ name = "Convoluted Grid"; }
			void execute() {
                generateBlendConvolutedGrid(null);
            }
		});
		timers.add(new PerformanceTimer() {
			{ name = "Simple Blending"; }
			void execute() {
                generateBlendSimple(null);
            }
		});
        
		System.out.println("Number of prep iterations: " + N_PREP_ITERATIONS);
		System.out.println("Number of timed iterations: " + N_TIMED_ITERATIONS);
		System.out.println("Size: " + WIDTH  + "x" + HEIGHT);
		System.out.println("Blend Radius: " + BLEND_RADIUS);
		System.out.println("Grid Interval: " + GRID_INTERVAL);
        System.out.println("Scattered Frequency: " + GRID_EQUIVALENT_FREQUENCY);
        System.out.println("Scattered Padding: " + BLEND_RADIUS_PADDING);
        System.out.println("Scattered Internal Blend Radius: " + (BLEND_RADIUS_PADDING + ScatteredBiomeBlender.getInternalMinBlendRadiusForFrequency(GRID_EQUIVALENT_FREQUENCY)));
		System.out.println("Chunk Width: " + CHUNK_WIDTH);
        
		for (PerformanceTimer timer : timers) {
			System.out.println();
			System.out.println("---- " + timer.name + " (No Image Display) ----");
			timer.time();
			System.out.println("Total milliseconds: " + timer.time);
			System.out.println("Nanoseconds per generated value: " + (timer.time * 1_000_000.0 / (N_TIMED_ITERATIONS * WIDTH * HEIGHT)));
		}
    }
    
	private static abstract class PerformanceTimer {
		String name;
		int time, sum;
		
		abstract void execute();
		
		void time() {
			for (int ie = 0; ie < N_PREP_ITERATIONS + N_TIMED_ITERATIONS; ie++) {
				long start = System.currentTimeMillis();
				execute();
				long elapsed = System.currentTimeMillis() - start;
				
				if (ie >= N_PREP_ITERATIONS) time += elapsed;
                
                noiseSeed++;
                jitterSeed++;
                initNoises();
			}
		}
	}
	
	/*
	 * Scattered Point Blending (main focus)
	 */
	
		
    // For comparison, we want effectively the same point density on our jittered triangular grid, that we would get on a square grid.
    // The skewed triangular grid for the jitter compresses a square grid diagonally to turn the length sqrt(2) into sqrt(2/3).
    // Compressing diagonally is equivalent to rotating 45 degrees and compressing in just one coordinate, as far as the change in density goes.
    // The jitter does not affect the overall density, because no points are added or removed.
    // The new density is sqrt(2)/sqrt(2/3)=sqrt(3) times that an unskewed square grid, for the same coordinate scaling ("frequency").
    // To counteract this on the triangular grid, we need a frequency that gives us 1/sqrt(3)=sqrt(1/3) the density that it otherwise would.
    // Since there are two coordinate dimensions, take the square root of that. sqrt(sqrt(1/3)) = (1/3)^(1/4) = 0.7598356856515925...
    // This way, the spanned area by any step size on the coordinates gives us the same number of points on average.
    private static final double GRID_EQUIVALENT_FREQUENCY = (1.0 / GRID_INTERVAL) * 0.7598356856515925;
    
    private static final double BLEND_RADIUS_PADDING = getEffectiveScatteredBlendRadius(GRID_EQUIVALENT_FREQUENCY, true);
    
	private static void generateBlendScatteredPoint(ChunkHandler callback) {
		
		// I include caching here, to avoid repeated biome evaluation calls.
		// This is because the entire biome map is pre-generated for free in the other examples,
		// where they would otherwise need caching to avoid such repetition in an infinite world.
        // If you use this caching technique in an infinite world, then you would need some way of
        // regularly clearing out old entries.
		HashMap<PointXZ, Integer> pointEvaluationCache = new HashMap<>();
		ScatteredBiomeBlender.BiomeEvaluationCallback cachedCallback = (double x, double z) -> {
			return (int)pointEvaluationCache.computeIfAbsent(new PointXZ(x, z), (PointXZ point) -> getBiomeAt(point.x, point.z));
            //return getBiomeAt(x, z);
		};
		
        ScatteredBiomeBlender blender = new ScatteredBiomeBlender(GRID_EQUIVALENT_FREQUENCY, BLEND_RADIUS_PADDING, CHUNK_WIDTH);
        for (int zc = 0; zc < HEIGHT; zc += CHUNK_WIDTH) {
            for (int xc = 0; xc < WIDTH; xc += CHUNK_WIDTH) {
                LinkedBiomeWeightMap firstBiomeWeightMap = blender.getBlendForChunk(jitterSeed, xc, zc, cachedCallback);
				if (callback != null) {
                    callback.invoke(xc, zc, firstBiomeWeightMap);
                }
			}
		}
	}
    
    private static double getEffectiveScatteredBlendRadius(double frequency, boolean justPadding) {
        
		// Since the scattered blender has a minimum blend circle size, and the provided parameter is padding onto that,
		// try to generate a padding to achieve the desired blend radius internally. If this results in too low of a
        // padding value, use the defined minimum padding value instead.
		// Note that, in a real use case, the padding value will probably be tuned by a developer,
		// rather than mathematically generated to meet certain requirements.
        double internalMinBlendRadius = ScatteredBiomeBlender.getInternalMinBlendRadiusForFrequency(frequency);
		double blendRadiusPadding = BLEND_RADIUS - internalMinBlendRadius;
		if (blendRadiusPadding < MIN_PADDING_FOR_SCATTERED) {
			System.out.println("Average blend radius for scattered blending could not be matched to defined blend radius " + BLEND_RADIUS
					+ ", without padding dropping below " + MIN_PADDING_FOR_SCATTERED
					+ ". Padding would have been " + blendRadiusPadding + ".");
            blendRadiusPadding = MIN_PADDING_FOR_SCATTERED;
		}
        
        return justPadding ? blendRadiusPadding : blendRadiusPadding + internalMinBlendRadius;
    }
	
	static class PointXZ {
		public double x, z;
		public PointXZ(double x, double z) {
			this.x = x; this.z = z;
		}
		public int hashCode() {
			return Double.hashCode(x) * 7841 + Double.hashCode(z);
		}
		public boolean equals(Object obj) {
			if (!(obj instanceof PointXZ)) return false;
			PointXZ other = (PointXZ) obj;
			return (other.x == this.x && other.z == this.z);
		}
	}
	
	/*
	 * Simple Blending
	 */
	
	private static final double SIMPLE_BLENDING_INVERSE_TOTAL_WEIGHT;
	static {
		long totalWeight = 0;
		for (int mz = -BLEND_RADIUS; mz <= BLEND_RADIUS; mz++) {
			for (int mx = -BLEND_RADIUS; mx <= BLEND_RADIUS; mx++) {
				int weight = (BLEND_RADIUS + 1) * (BLEND_RADIUS + 1) - mz * mz - mx * mx;
				if (weight <= 0) continue;
				weight *= weight;
				totalWeight += weight;
			}
		}
		SIMPLE_BLENDING_INVERSE_TOTAL_WEIGHT = 1.0 / totalWeight;
	}
	
	private static void generateBlendSimple(ChunkHandler callback) {
	
		final int BIOME_MAP_PADDING = 2 * BLEND_RADIUS;
		final int PADDED_MAP_WIDTH = WIDTH + BIOME_MAP_PADDING;
		final int PADDED_MAP_HEIGHT = HEIGHT + BIOME_MAP_PADDING;
		
		int[] biomeMap = new int[PADDED_MAP_WIDTH * PADDED_MAP_HEIGHT];
		for (int z = 0; z < PADDED_MAP_HEIGHT; z++) {
			int actualZ = z - BLEND_RADIUS;
			for (int x = 0; x < PADDED_MAP_WIDTH; x++) {
				int actualX = x - BLEND_RADIUS;
				biomeMap[z * PADDED_MAP_WIDTH + x] = getBiomeAt(actualX, actualZ);
			}
		}
		
		for (int zc = 0; zc < HEIGHT; zc += CHUNK_WIDTH) {
			for (int xc = 0; xc < WIDTH; xc += CHUNK_WIDTH) {
				
				int chunkBiomeMapStartX = xc - BLEND_RADIUS;
				int chunkBiomeMapStartZ = zc - BLEND_RADIUS;
				int chunkBiomeMapEndX = xc + (BLEND_RADIUS + CHUNK_WIDTH - 1);
				int chunkBiomeMapEndZ = zc + (BLEND_RADIUS + CHUNK_WIDTH - 1);
				LinkedBiomeWeightMap linkedBiomeMapStartEntry = null;
                int localMapWidth = chunkBiomeMapEndX - chunkBiomeMapStartX + 1;
                LinkedBiomeWeightMap[] weightMapLocal = new LinkedBiomeWeightMap[localMapWidth * localMapWidth];
				for (int gz = chunkBiomeMapStartZ; gz <= chunkBiomeMapEndZ; gz++) {
					int igz = gz + BLEND_RADIUS;
					for (int gx = chunkBiomeMapStartX; gx <= chunkBiomeMapEndX; gx++) {
						int igx = gx + BLEND_RADIUS;
						int biome = biomeMap[igz * PADDED_MAP_WIDTH + igx];
						
						// Find or create chunk biome blend weight layer entry for this biome.
						LinkedBiomeWeightMap entry = linkedBiomeMapStartEntry;
						while (entry != null) {
							if (entry.getBiome() == biome) break;
							entry = entry.getNext();
						}
						if (entry == null) {
							entry = linkedBiomeMapStartEntry =
								new LinkedBiomeWeightMap(biome, linkedBiomeMapStartEntry);
						}
                        
                        weightMapLocal[(gz - chunkBiomeMapStartZ) * localMapWidth + (gx - chunkBiomeMapStartX)] = entry;
					}
				}
        
                // If there is only one biome in range here, we can skip the actual blending step.
                if (linkedBiomeMapStartEntry != null && linkedBiomeMapStartEntry.getNext() == null) {
                    /*double[] weights = new double[chunkColumnCount];
                    linkedBiomeMapStartEntry.setWeights(weights);
                    for (int i = 0; i < chunkColumnCount; i++) {
                        weights[i] = 1.0;
                    }*/
                    if (callback != null) {
                        callback.invoke(xc, zc, linkedBiomeMapStartEntry);
                    }
                    continue;
                }
        
                for (LinkedBiomeWeightMap entry = linkedBiomeMapStartEntry; entry != null; entry = entry.getNext()) {
                    entry.setWeights(new double[CHUNK_WIDTH * CHUNK_WIDTH * 4]);
                }
				
                for (int zi = 0; zi < CHUNK_WIDTH; zi++) {
					int z = zc + zi;
					int biomeMapStartZ = z - BLEND_RADIUS;
					int biomeMapEndZ = z + BLEND_RADIUS;
                    for (int xi = 0; xi < CHUNK_WIDTH; xi++) {
                        int x = xc + xi;
						int biomeMapStartX = x - BLEND_RADIUS;
						int biomeMapEndX = x + BLEND_RADIUS;
						
						for (int mz = biomeMapStartZ; mz <= biomeMapEndZ; mz++) {
							int igz = mz + BLEND_RADIUS;
							for (int mx = biomeMapStartX; mx <= biomeMapEndX; mx++) {
								int igx = mx + BLEND_RADIUS;
								
								int biome = biomeMap[igz * PADDED_MAP_HEIGHT + igx];
								
								int weight = (BLEND_RADIUS + 1) * (BLEND_RADIUS + 1) - (z - mz) * (z - mz) - (x - mx) * (x - mx);
								if (weight <= 0) continue;
								weight *= weight;
								
								/*LinkedBiomeWeightMap entry = linkedBiomeMapStartEntry;
								while (entry != null) {
									if (entry.getBiome() == biome) break;
									entry = entry.getNext();
								}*/
                                LinkedBiomeWeightMap entry = weightMapLocal[(mz - chunkBiomeMapStartZ) * localMapWidth + (mx - chunkBiomeMapStartX)];
								
								entry.getWeights()[(zi * CHUNK_WIDTH) | xi] += weight;
								
							}
						}
                
						// Normalize so all weights for a column to 1.
						for (LinkedBiomeWeightMap entry = linkedBiomeMapStartEntry; entry != null; entry = entry.getNext()) {
							entry.getWeights()[(zi * CHUNK_WIDTH) | xi] *= SIMPLE_BLENDING_INVERSE_TOTAL_WEIGHT;
						}
					}
				}
				
				if (callback != null) {
					callback.invoke(xc, zc, linkedBiomeMapStartEntry);
				}
			}
		}
		
	}
	
	/*
	 * Convoluted Grid Blending
	 */
	
	private static void generateBlendConvolutedGrid(ChunkHandler callback) {
	
		final int GRID_PADDING_BACK = -(-BLEND_RADIUS >> GRID_INTERVAL_EXPONENT);
		final int GRID_PADDING_FORWARD = (BLEND_RADIUS + GRID_INTERVAL - 1) >> GRID_INTERVAL_EXPONENT;
		final int GRID_PADDING = GRID_PADDING_BACK + GRID_PADDING_FORWARD;
		
		final int GRID_WIDTH = WIDTH >> GRID_INTERVAL_EXPONENT;
		final int GRID_HEIGHT = HEIGHT >> GRID_INTERVAL_EXPONENT;
		final int PADDED_GRID_WIDTH = GRID_WIDTH + GRID_PADDING;
		final int PADDED_GRID_HEIGHT = GRID_HEIGHT + GRID_PADDING;
		
		int[] biomeGrid = new int[PADDED_GRID_WIDTH * PADDED_GRID_HEIGHT];
		for (int z = 0; z < PADDED_GRID_HEIGHT; z++) {
			int actualZ = (z - GRID_PADDING_BACK) << GRID_INTERVAL_EXPONENT;
			for (int x = 0; x < PADDED_GRID_WIDTH; x++) {
				int actualX = (x - GRID_PADDING_BACK) << GRID_INTERVAL_EXPONENT;
				biomeGrid[z * PADDED_GRID_WIDTH + x] = getBiomeAt(actualX, actualZ);
			}
		}
		
		for (int zc = 0; zc < HEIGHT; zc += CHUNK_WIDTH) {
			for (int xc = 0; xc < WIDTH; xc += CHUNK_WIDTH) {
				
				int chunkGridStartX = (xc - BLEND_RADIUS) >> GRID_INTERVAL_EXPONENT;
				int chunkGridStartZ = (zc - BLEND_RADIUS) >> GRID_INTERVAL_EXPONENT;
				int chunkGridEndX = (xc + (BLEND_RADIUS + CHUNK_WIDTH - 1)) >> GRID_INTERVAL_EXPONENT;
				int chunkGridEndZ = (zc + (BLEND_RADIUS + CHUNK_WIDTH - 1)) >> GRID_INTERVAL_EXPONENT;
				LinkedBiomeWeightMap linkedBiomeMapStartEntry = null;
                int localGridWidth = chunkGridEndX - chunkGridStartX + 1;
                LinkedBiomeWeightMap[] weightMapLocalGrid = new LinkedBiomeWeightMap[localGridWidth * localGridWidth];
				for (int gz = chunkGridStartZ; gz <= chunkGridEndZ; gz++) {
					int igz = gz + GRID_PADDING_BACK;
					for (int gx = chunkGridStartX; gx <= chunkGridEndX; gx++) {
						int igx = gx + GRID_PADDING_BACK;
						
						if (igz * PADDED_GRID_WIDTH + igx < 0) {
							System.out.println(igx+","+igz);
							System.out.println(gx+","+gz);
							System.out.println(xc+","+zc);
							System.out.println(chunkGridStartX+","+chunkGridStartZ);
							System.out.println(chunkGridEndX+","+chunkGridEndZ);
						}
						
						int biome = biomeGrid[igz * PADDED_GRID_WIDTH + igx];
						
						// Find or create chunk biome blend weight layer entry for this biome.
						LinkedBiomeWeightMap entry = linkedBiomeMapStartEntry;
						while (entry != null) {
							if (entry.getBiome() == biome) break;
							entry = entry.getNext();
						}
						if (entry == null) {
							entry = linkedBiomeMapStartEntry =
								new LinkedBiomeWeightMap(biome, linkedBiomeMapStartEntry);
						}
                        
                        weightMapLocalGrid[(gz - chunkGridStartZ) * localGridWidth + (gx - chunkGridStartX)] = entry;
					}
				}
        
                // If there is only one biome in range here, we can skip the actual blending step.
                if (linkedBiomeMapStartEntry != null && linkedBiomeMapStartEntry.getNext() == null) {
                    /*double[] weights = new double[chunkColumnCount];
                    linkedBiomeMapStartEntry.setWeights(weights);
                    for (int i = 0; i < chunkColumnCount; i++) {
                        weights[i] = 1.0;
                    }*/
                    if (callback != null) {
                        callback.invoke(xc, zc, linkedBiomeMapStartEntry);
                    }
                    continue;
                }
        
                for (LinkedBiomeWeightMap entry = linkedBiomeMapStartEntry; entry != null; entry = entry.getNext()) {
                    entry.setWeights(new double[CHUNK_WIDTH * CHUNK_WIDTH * 4]);
                }
				
                for (int zi = 0; zi < CHUNK_WIDTH; zi++) {
					int z = zc + zi;
					int gridStartZ = (z - BLEND_RADIUS) >> GRID_INTERVAL_EXPONENT;
					int gridEndZ = (z + BLEND_RADIUS) >> GRID_INTERVAL_EXPONENT;
                    for (int xi = 0; xi < CHUNK_WIDTH; xi++) {
                        int x = xc + xi;
						int gridStartX = (x - BLEND_RADIUS) >> GRID_INTERVAL_EXPONENT;
						int gridEndX = (x + BLEND_RADIUS) >> GRID_INTERVAL_EXPONENT;
						
						int columnTotalWeight = 0;
						for (int gz = gridStartZ; gz <= gridEndZ; gz++) {
							int igz = gz + GRID_PADDING_BACK;
							int wgz = gz << GRID_INTERVAL_EXPONENT;
							for (int gx = gridStartX; gx <= gridEndX; gx++) {
								int igx = gx + GRID_PADDING_BACK;
								int wgx = gx << GRID_INTERVAL_EXPONENT;
								
								int biome = biomeGrid[igz * PADDED_GRID_WIDTH + igx];
								
								int weight = (BLEND_RADIUS + 1) * (BLEND_RADIUS + 1) - (z - wgz) * (z - wgz) - (x - wgx) * (x - wgx);
								if (weight <= 0) continue;
								weight *= weight;
								
								/*LinkedBiomeWeightMap entry = linkedBiomeMapStartEntry;
								while (entry != null) {
									if (entry.getBiome() == biome) break;
									entry = entry.getNext();
								}*/
                                LinkedBiomeWeightMap entry = weightMapLocalGrid[(gz - chunkGridStartZ) * localGridWidth + (gx - chunkGridStartX)];
								
								entry.getWeights()[(zi * CHUNK_WIDTH) | xi] += weight;
								columnTotalWeight += weight;
								
							}
						}
                
						// Normalize so all weights for a column add to 1.
						double inverseTotalWeight = 1.0 / columnTotalWeight;
						for (LinkedBiomeWeightMap entry = linkedBiomeMapStartEntry; entry != null; entry = entry.getNext()) {
							entry.getWeights()[(zi * CHUNK_WIDTH) | xi] *= inverseTotalWeight;
						}
					}
				}
				
				if (callback != null) {
					callback.invoke(xc, zc, linkedBiomeMapStartEntry);
				}
				
			}
		}
		
	}
	
	/*
	 * Lerped Grid Blending
	 */
	
	private static final double LERP_GRID_BLENDING_INVERSE_TOTAL_WEIGHT;
	static {
		int x = 0, z = 0;
		int gridStartZ = (z - BLEND_RADIUS) >> GRID_INTERVAL_EXPONENT;
		int gridEndZ = (z + BLEND_RADIUS) >> GRID_INTERVAL_EXPONENT;
		int gridStartX = (x - BLEND_RADIUS) >> GRID_INTERVAL_EXPONENT;
		int gridEndX = (x + BLEND_RADIUS) >> GRID_INTERVAL_EXPONENT;
		
		int totalWeight = 0;
		for (int gz = gridStartZ; gz <= gridEndZ; gz++) {
			int wgz = gz << GRID_INTERVAL_EXPONENT;
			for (int gx = gridStartX; gx <= gridEndX; gx++) {
				int wgx = gx << GRID_INTERVAL_EXPONENT;
				int weight = (BLEND_RADIUS + 1) * (BLEND_RADIUS + 1) - (z - wgz) * (z - wgz) - (x - wgx) * (x - wgx);
				if (weight <= 0) continue;
				weight *= weight;
				totalWeight += weight;
			}
		}
		LERP_GRID_BLENDING_INVERSE_TOTAL_WEIGHT = 1.0 / totalWeight;
	}
	
	private static void generateBlendLerpedGrid(ChunkHandler callback) {
		
		final int GRID_PADDING_BACK = -(-BLEND_RADIUS >> GRID_INTERVAL_EXPONENT);
		final int GRID_PADDING_FORWARD = (BLEND_RADIUS + GRID_INTERVAL) >> GRID_INTERVAL_EXPONENT;
		final int GRID_PADDING = GRID_PADDING_BACK + GRID_PADDING_FORWARD;
		
		final int GRID_WIDTH = WIDTH >> GRID_INTERVAL_EXPONENT;
		final int GRID_HEIGHT = HEIGHT >> GRID_INTERVAL_EXPONENT;
		final int PADDED_GRID_WIDTH = GRID_WIDTH + GRID_PADDING;
		final int PADDED_GRID_HEIGHT = GRID_HEIGHT + GRID_PADDING;
		
		int[] biomeGrid = new int[PADDED_GRID_WIDTH * PADDED_GRID_HEIGHT];
		for (int z = 0; z < PADDED_GRID_HEIGHT; z++) {
			int actualZ = (z - GRID_PADDING_BACK) << GRID_INTERVAL_EXPONENT;
			for (int x = 0; x < PADDED_GRID_WIDTH; x++) {
				int actualX = (x - GRID_PADDING_BACK) << GRID_INTERVAL_EXPONENT;
				biomeGrid[z * PADDED_GRID_WIDTH + x] = getBiomeAt(actualX, actualZ);
			}
		}
		
		for (int zc = 0; zc < HEIGHT; zc += CHUNK_WIDTH) {
			for (int xc = 0; xc < WIDTH; xc += CHUNK_WIDTH) {
				
				int chunkGridStartX = (xc - BLEND_RADIUS) >> GRID_INTERVAL_EXPONENT;
				int chunkGridStartZ = (zc - BLEND_RADIUS) >> GRID_INTERVAL_EXPONENT;
				int chunkGridEndX = (xc + (BLEND_RADIUS + CHUNK_WIDTH)) >> GRID_INTERVAL_EXPONENT;
				int chunkGridEndZ = (zc + (BLEND_RADIUS + CHUNK_WIDTH)) >> GRID_INTERVAL_EXPONENT;
				LinkedBiomeWeightMap linkedBiomeMapStartEntry = null;
                int localGridWidth = chunkGridEndX - chunkGridStartX + 1;
                LinkedBiomeWeightMap[] weightMapLocalGrid = new LinkedBiomeWeightMap[localGridWidth * localGridWidth];
				for (int gz = chunkGridStartZ; gz <= chunkGridEndZ; gz++) {
					int igz = gz + GRID_PADDING_BACK;
					for (int gx = chunkGridStartX; gx <= chunkGridEndX; gx++) {
						int igx = gx + GRID_PADDING_BACK;
						int biome = biomeGrid[igz * PADDED_GRID_WIDTH + igx];
						
						// Find or create chunk biome blend weight layer entry for this biome.
						LinkedBiomeWeightMap entry = linkedBiomeMapStartEntry;
						while (entry != null) {
							if (entry.getBiome() == biome) break;
							entry = entry.getNext();
						}
						if (entry == null) {
							entry = linkedBiomeMapStartEntry =
								new LinkedBiomeWeightMap(biome, linkedBiomeMapStartEntry);
						}
                        
                        weightMapLocalGrid[(gz - chunkGridStartZ) * localGridWidth + (gx - chunkGridStartX)] = entry;
					}
				}
        
                // If there is only one biome in range here, we can skip the actual blending step.
                if (linkedBiomeMapStartEntry != null && linkedBiomeMapStartEntry.getNext() == null) {
                    /*double[] weights = new double[CHUNK_WIDTH * CHUNK_WIDTH * 4];
                    linkedBiomeMapStartEntry.setWeights(weights);
                    for (int i = 0; i < CHUNK_WIDTH * CHUNK_WIDTH * 4; i++) {
                        weights[i] = 1.0;
                    }*/
                    if (callback != null) {
                        callback.invoke(xc, zc, linkedBiomeMapStartEntry);
                    }
                    continue;
                }
        
                for (LinkedBiomeWeightMap entry = linkedBiomeMapStartEntry; entry != null; entry = entry.getNext()) {
                    entry.setWeights(new double[CHUNK_WIDTH * CHUNK_WIDTH * 4]);
                }
				
                for (int zi = 0; zi <= CHUNK_WIDTH; zi += GRID_INTERVAL) {
					int z = zc + zi;
					int gridStartZ = (z - BLEND_RADIUS) >> GRID_INTERVAL_EXPONENT;
					int gridEndZ = (z + BLEND_RADIUS) >> GRID_INTERVAL_EXPONENT;
                    for (int xi = 0; xi <= CHUNK_WIDTH; xi += GRID_INTERVAL) {
                        int x = xc + xi;
						int gridStartX = (x - BLEND_RADIUS) >> GRID_INTERVAL_EXPONENT;
						int gridEndX = (x + BLEND_RADIUS) >> GRID_INTERVAL_EXPONENT;
						
						for (int gz = gridStartZ; gz <= gridEndZ; gz++) {
							int igz = gz + GRID_PADDING_BACK;
							int wgz = gz << GRID_INTERVAL_EXPONENT;
							for (int gx = gridStartX; gx <= gridEndX; gx++) {
								int igx = gx + GRID_PADDING_BACK;
								int wgx = gx << GRID_INTERVAL_EXPONENT;
								
								int biome = biomeGrid[igz * PADDED_GRID_WIDTH + igx];
								
								int weight = (BLEND_RADIUS + 1) * (BLEND_RADIUS + 1) - (z - wgz) * (z - wgz) - (x - wgx) * (x - wgx);
								if (weight <= 0) continue;
								weight *= weight;
								
								/*LinkedBiomeWeightMap entry = linkedBiomeMapStartEntry;
								while (entry != null) {
									if (entry.getBiome() == biome) break;
									entry = entry.getNext();
								}*/
                                LinkedBiomeWeightMap entry = weightMapLocalGrid[(gz - chunkGridStartZ) * localGridWidth + (gx - chunkGridStartX)];
								
								entry.getWeights()[zi * (CHUNK_WIDTH * 2) + xi] += weight;
								/*if (entry == null) {
									linkedBiomeMapStartEntry =
										new LinkedBiomeWeightMap(point.getTag(), chunkColumnCount, linkedBiomeMapStartEntry);
								}*/
								
							}
						}
                
						// Normalize so all weights for a column add to 1.
						for (LinkedBiomeWeightMap entry = linkedBiomeMapStartEntry; entry != null; entry = entry.getNext()) {
							entry.getWeights()[zi * (CHUNK_WIDTH * 2) + xi] *= LERP_GRID_BLENDING_INVERSE_TOTAL_WEIGHT;
						}
					}
				}
				
				// Lerp on each layer
                for (LinkedBiomeWeightMap entry = linkedBiomeMapStartEntry; entry != null; entry = entry.getNext()) {
					double[] weights = entry.getWeights();
					for (int zi = 0; zi <= CHUNK_WIDTH; zi += GRID_INTERVAL) {
						int zg = zc + zi;
						for (int xi = 0; xi <= CHUNK_WIDTH; xi += GRID_INTERVAL) {
							int xg = xc + xi;
							
							double w00 = weights[zi * (CHUNK_WIDTH * 2) + xi];
							double w10 = weights[(zi + GRID_INTERVAL) * (CHUNK_WIDTH * 2) + xi];
							double wZ0Step = (w10 - w00) * (1.0 / GRID_INTERVAL);
							double w01 = weights[zi * (CHUNK_WIDTH * 2) + (xi + GRID_INTERVAL)];
							double w11 = weights[(zi + GRID_INTERVAL) * (CHUNK_WIDTH * 2) + (xi + GRID_INTERVAL)];
							double wZ1Step = (w11 - w01) * (1.0 / GRID_INTERVAL);
							double wZ0 = w00, wZ1 = w01;
							for (int zgi = 0; zgi < GRID_INTERVAL; zgi++) {
								double wZX = wZ0;
								double wZXStep = (wZ1 - wZ0) * (1.0 / GRID_INTERVAL);
								for (int xgi = 0; xgi < GRID_INTERVAL; xgi++) {
									weights[(zi + zgi) * (CHUNK_WIDTH * 2) + (xi + xgi)] = wZX;
									wZX += wZXStep;
								}
								wZ0 += wZ0Step;
								wZ1 += wZ1Step;
							}
							
						}
					}
				}
				
				if (callback != null) {
					// I took a coding shortcut that required a larger array, so here I condense it.
					// It's only for image generation, so this won't be run during performance benchmarks when callback is null.
					for (LinkedBiomeWeightMap entry = linkedBiomeMapStartEntry; entry != null; entry = entry.getNext()) {
						double[] weights = entry.getWeights();
						for (int zi = 0; zi < CHUNK_WIDTH; zi++) {
							for (int xi = 0; xi < CHUNK_WIDTH; xi++) {
								weights[(zi * CHUNK_WIDTH) | xi] = weights[zi * (CHUNK_WIDTH * 2) + xi];
							}
						}
					}
					callback.invoke(xc, zc, linkedBiomeMapStartEntry);
				}
			}
		}
		
	}
	
	/*
	 * Passthrough to make it possible to render the callback using the same code as the blending.
	 */
	
	private static void generateCallbackPassthrough(ChunkHandler callback) {
		for (int zc = 0; zc < HEIGHT; zc += CHUNK_WIDTH) {
			for (int xc = 0; xc < WIDTH; xc += CHUNK_WIDTH) {
                
				LinkedBiomeWeightMap linkedBiomeMapStartEntry = null;
                for (int zi = 0; zi < CHUNK_WIDTH; zi++) {
					int z = zc + zi;
                    for (int xi = 0; xi < CHUNK_WIDTH; xi++) {
                        int x = xc + xi;
                        
                        int biome = getBiomeAt(x, z);
						
						// Find or create chunk biome blend weight layer entry for this biome.
						LinkedBiomeWeightMap entry = linkedBiomeMapStartEntry;
						while (entry != null) {
							if (entry.getBiome() == biome) break;
							entry = entry.getNext();
						}
						if (entry == null) {
							entry = linkedBiomeMapStartEntry =
								new LinkedBiomeWeightMap(biome, CHUNK_WIDTH * CHUNK_WIDTH, linkedBiomeMapStartEntry);
						}
                        
						entry.getWeights()[(zi * CHUNK_WIDTH) | xi] = 1.0;
                    }
                }
				
				if (callback != null) {
					callback.invoke(xc, zc, linkedBiomeMapStartEntry);
				}
			}
		}
	}
	
	/*
	 * Passthrough to make it possible to render the points using the same code as the blending.
	 */
	
	private static void generatePointDistributionPassthrough(ChunkHandler callback) {
        
        // These is explained in generateBlendScatteredPoint
		final double GRID_EQUIVALENT_FREQUENCY = (1.0 / GRID_INTERVAL) * 0.7598356856515925;
        
		double radius = getEffectiveScatteredBlendRadius(GRID_EQUIVALENT_FREQUENCY, false);
        ChunkPointGatherer<Integer> gatherer = new ChunkPointGatherer<>(GRID_EQUIVALENT_FREQUENCY, radius, CHUNK_WIDTH);
        
		for (int zc = 0; zc < HEIGHT; zc += CHUNK_WIDTH) {
			for (int xc = 0; xc < WIDTH; xc += CHUNK_WIDTH) {
                
                // Get the list of data points in range.
                List<GatheredPoint<Integer>> points = gatherer.getPointsFromChunkBase(jitterSeed, xc, zc);
                
                // Evaluate and aggregate all biomes to be blended in this chunk.
                LinkedBiomeWeightMap linkedBiomeMapStartEntry = null;
                for (GatheredPoint<Integer> point : points) {
                    
                    // Get the biome for this data point from the callback.
                    // Just a quick demo. For an ingame application, there are more efficient ways for a constant biome ID.
                    int biome = OVERRIDE_BIOME_FOR_POINTRENDER_OR_NEGATIVE >= 0 ? OVERRIDE_BIOME_FOR_POINTRENDER_OR_NEGATIVE : getBiomeAt(point.getX(), point.getZ());
                    point.setTag(biome);
                    
                    // Find or create the chunk biome blend weight layer entry for this biome.
                    LinkedBiomeWeightMap entry = linkedBiomeMapStartEntry;
                    while (entry != null) {
                        if (entry.getBiome() == biome) break;
                        entry = entry.getNext();
                    }
                    if (entry == null) {
                        linkedBiomeMapStartEntry =
                            new LinkedBiomeWeightMap(point.getTag(), CHUNK_WIDTH * CHUNK_WIDTH, linkedBiomeMapStartEntry);
                    }
                }
                
                for (int zi = 0; zi < CHUNK_WIDTH; zi++) {
					int z = zc + zi;
                    for (int xi = 0; xi < CHUNK_WIDTH; xi++) {
                        int x = xc + xi;
						
                        int columnTotalWeight = 0;
                        for (GatheredPoint<Integer> point : points) {
                            double dz = point.getZ() - z;
                            double dx = point.getX() - x;
                            double distSq = dx * dx + dz * dz;
                            
                            // If it's inside the radius...
                            if (distSq < POINT_RENDER_SIZE_SQ) {
                                
                                // Find the chunk biome blend weight layer entry for this biome.
                                LinkedBiomeWeightMap entry = linkedBiomeMapStartEntry;
                                while (entry != null) {
                                    if (entry.getBiome() == point.getTag()) break;
                                    entry = entry.getNext();
                                }
                                
                                entry.getWeights()[(zi * CHUNK_WIDTH) | xi] = 1.0;
                                columnTotalWeight += 1;
                            }
                        }
                
                        // If any points overlap
                        double inverseTotalWeight = 1.0 / columnTotalWeight;
                        for (LinkedBiomeWeightMap entry = linkedBiomeMapStartEntry; entry != null; entry = entry.getNext()) {
                            entry.getWeights()[(zi * CHUNK_WIDTH) | xi] *= inverseTotalWeight;
                        }
                    }
                }
				
				if (callback != null) {
					callback.invoke(xc, zc, linkedBiomeMapStartEntry);
				}
			}
		}
	}
	
}