/*
Copyright 2009 Zephyr Renner
Copyright 2020 Alessandro Bruno

This file is part of EquirectangulartoCubic.java.
EquirectangulartoCubic is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
EquirectangulartoCubic is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
You should have received a copy of the GNU General Public License along with EquirectangulartoCubic. If not, see http://www.gnu.org/licenses/.

The Equi2Rect.java library is modified from PTViewer 2.8 licenced under the GPL courtesy of Fulvio Senore, originally developed by Helmut Dersch.  Thank you both!

Some basic structural of this code are influenced by / modified from DeepJZoom, Glenn Lawrence, which is also licensed under the GPL.
*/

package com.Equi2Rect;
import java.io.File;
import java.io.IOException;
import java.awt.image.BufferedImage;
import javax.imageio.*;
import java.util.Vector;
import java.util.concurrent.*;

/**
 * Generate six cubemap images which represent the six orthogonal directions of
 * a sphere using a Gnomonic Projection with 90 degree field of view.  The input
 * file is a Cylindrical Equidistant Projection with a 2:1 width to height ratio.
 * Cubemap images format:
 *   +------+
 *   |  4   |
 *   |      |
 *   +------+------+------+------+
 *   |  0   |  1   |  2   |  3   |
 *   |      |      |      |      |
 *   +------+------+------+------+
 *   |  5   |
 *   |      |
 *   +------+
 */
public class EquirectangularToCubic {

    static int overlap = 1;
    static Boolean verboseMode = false;

    /**
     * Process a BufferedImage
     * @param equi The BufferedImage containing a cylindrical equidistant projection of a spherical panorama
     */
    public static BufferedImage[] processImage(BufferedImage equi) throws IOException {

        verboseMode = true;
        int equiWidth = equi.getWidth();
        int equiHeight = equi.getHeight();

        if (equiWidth != equiHeight * 2) {
            String errorMessage = "Image is not equirectangular (" + equiWidth + " x " + equiHeight + ")";
            System.out.println(errorMessage);
            throw new IOException(errorMessage);
        }

        int equiData[][] = new int[equiHeight][equiWidth];
        new ImageTo2DIntArrayExtractor (equiData, equi).doit();
        double fov; // horizontal field of view
        double r = equiWidth / (2D * Math.PI);
        double y = (Math.tan( Math.PI/4D ) * r + overlap);
        fov = Math.atan( y / r ) * 180 / Math.PI * 2;

        int rectWidth;
        int rectHeight;

        rectWidth = (int) (y * 2);
        rectHeight = rectWidth;

        BufferedImage[] outputArray = new BufferedImage[6];

        Equi2Rect.initForIntArray2D(equiData);

        // Flip back the image (bug in the original version)
        for (int j = 0; j < equiHeight/2; j++) {
            int[] tmp = equiData[j];
            equiData[j] = equiData[equiHeight - j - 1];
            equiData[equiHeight - j - 1] = tmp;
        }

        long startTime = System.nanoTime();
        int numberOfThreads = Math.min(6,Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        if(verboseMode) System.out.println("There are " + Runtime.getRuntime().availableProcessors() +
                " cores. Using " + numberOfThreads + " threads.");

        Vector<Future<int[]>>  rectData = new Vector(6);
        double[] yaw = {0.0, 90.0, 180.0, 270.0, 0.0, 0.0};
        double[] pitch = {0.0, 0.0, 0.0, 0.0, 90.0, -90.0};

        // Start threads
        for(int i = 0; i < 6; i++){
            rectData.add(executor.submit(new callableProcessor(yaw[i],pitch[i],fov,equiData,
                    rectWidth,rectHeight,equiWidth)));
        }

        // Allocate memory for results
        for(int i = 0; i < 6; i++) {
            outputArray[i] = new BufferedImage(rectWidth, rectHeight, BufferedImage.TYPE_INT_RGB);
        }

        // Wait for threads to finish computation
        try {
            for(int i = 0; i < 6; i++){
                outputArray[i].setRGB(0,0,rectWidth, rectHeight, rectData.get(i).get(), 0, rectWidth);
            }
        } catch (ExecutionException | InterruptedException e)
        {
            System.err.println("Processing failed.");
        }

        executor.shutdown();
        if(verboseMode) System.out.println("Image processed.");

        long endTime = System.nanoTime();
        long duration = (endTime - startTime) / 1000000L;  //divide by 1000000 to get milliseconds.
        if(verboseMode)  System.out.println("It took " + duration + " ms to generate the skybox.");

        return outputArray;
    }

    /**
     * Loads image from file
     * @param file The file containing the image
     */
    public static BufferedImage loadImage(File file) throws IOException {
        BufferedImage result = null;
        try {
            result = ImageIO.read(file);
        } catch (Exception e) {
            throw new IOException("Cannot read image file: " + file);
        }
        return result;
    }
}

class callableProcessor implements Callable<int[]> {
    /**
     * Callable to compute a gnomonic projection
     */
    public callableProcessor(double yaw, double pitch, double fov, int[][] equiData,
                             int rectWidth, int rectHeight, int equiWidth) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.fov = fov;
        this.equiData = equiData;
        this.rectWidth = rectWidth;
        this.rectHeight = rectHeight;
        this.equiWidth = equiWidth;
    }

    private double yaw;
    private double pitch;
    private double fov;
    private int equiData[][];
    private int rectWidth;
    private int rectHeight;
    private int equiWidth;

    public int[] call() {
        return Equi2Rect.extractRectilinear(yaw,pitch,fov,equiData,rectWidth,
                rectHeight,equiWidth,false,true);
    }
}

